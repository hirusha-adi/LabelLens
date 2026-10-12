// What is recorded about each catalogue request, and nothing more:
//
//   the caller's IP address
//   the kind of device ("Google Pixel 9", "desktop browser", "unknown")
//   what was asked for (the list, or one additive's code)
//   when
//
// Headers, user-agent strings and anything else about the request are not kept.
//
// Rows are collected in memory and written together a few times a second: one
// INSERT for hundreds of requests instead of one each. A request never waits for
// the database, and the database sees a trickle of writes however busy the API is.

import { isIP } from "node:net";

const MAX_DEVICE = 80;

/**
 * The device type to store. The app names its device in X-Device-Type. Anything
 * else is reduced to a broad class, so a full user-agent string is never kept.
 * Takes Node's lower-cased header object.
 */
export const deviceTypeOf = (headers) => {
  const sent = headers["x-device-type"];
  const named = (typeof sent === "string" ? sent : "").replace(/[^\x20-\x7e]/g, "").replace(/\s+/g, " ").trim();
  if (named) return named.slice(0, MAX_DEVICE);
  const agent = (headers["user-agent"] ?? "").toLowerCase();
  if (!agent) return "unknown";
  if (/bot|crawl|spider|curl|wget|python|node|okhttp|java\/|autocannon/.test(agent)) return "script";
  if (/android/.test(agent)) return "android browser";
  if (/iphone|ipad|ios/.test(agent)) return "ios browser";
  if (/mozilla|chrome|safari|firefox|edg\//.test(agent)) return "desktop browser";
  return "other";
};

/** The caller's address, or null when it cannot be told. IPv4 mapped into IPv6 is unwrapped. */
export const ipOf = ({ forwardedFor, remoteAddress, trustProxy }) => {
  const candidate = (trustProxy && typeof forwardedFor === "string" && forwardedFor.split(",")[0].trim()) || remoteAddress || "";
  const address = candidate.replace(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/i, "$1");
  return isIP(address) ? address : null;
};

/**
 * @param {import("pg").Pool} pool
 * @param {{ flushMs?: number, flushRows?: number, maxBuffered?: number }} [settings]
 *   flushMs      how long a row may wait before being written
 *   flushRows    write early once this many are waiting
 *   maxBuffered  if the database is away, keep at most this many and drop the oldest
 */
export const createRequestLog = (pool, { flushMs = 250, flushRows = 500, maxBuffered = 50_000 } = {}) => {
  let buffer = [];
  let timer = null;
  let writing = null;
  let dropped = 0;

  const write = async (rows) => {
    const client = await pool.connect();
    try {
      await client.query("begin");
      // Losing the last fraction of a second of analytics in a crash is an
      // acceptable price for not waiting on the disk for every batch.
      await client.query("set local synchronous_commit = off");
      await client.query(
        `insert into request_log (at, ip, device_type, resource, code)
         select * from unnest($1::timestamptz[], $2::inet[], $3::text[], $4::text[], $5::text[])`,
        [rows.map((r) => r.at), rows.map((r) => r.ip), rows.map((r) => r.deviceType), rows.map((r) => r.resource), rows.map((r) => r.code)],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    } finally {
      client.release();
    }
  };

  const flush = async () => {
    clearTimeout(timer);
    timer = null;
    if (writing) await writing;
    if (!buffer.length) return;
    const rows = buffer;
    buffer = [];
    writing = write(rows)
      .catch((error) => {
        // Put them back for the next try, oldest first, within the cap.
        console.error(`Could not record ${rows.length} requests, will retry:`, error.message);
        buffer = rows.concat(buffer);
        if (buffer.length > maxBuffered) {
          dropped += buffer.length - maxBuffered;
          buffer = buffer.slice(-maxBuffered);
          console.error(`Request log is full; ${dropped} rows dropped so far.`);
        }
        schedule(flushMs * 8);
      })
      .finally(() => {
        writing = null;
      });
    await writing;
  };

  const schedule = (delay = flushMs) => {
    if (timer) return;
    timer = setTimeout(() => flush().catch(() => {}), delay);
    timer.unref();
  };

  /** Notes one request. Returns at once. */
  const record = (entry) => {
    buffer.push({ at: new Date(), ip: entry.ip, deviceType: entry.deviceType, resource: entry.resource, code: entry.code ?? null });
    if (buffer.length >= flushRows) flush().catch(() => {});
    else schedule();
  };

  return { record, flush, get waiting() { return buffer.length; } };
};

/** Makes sure this month's and the coming months' partitions exist, now and once a day. */
export const keepPartitionsReady = (pool) => {
  const ensure = () => pool.query("select ensure_request_log_partitions()").catch((error) => console.error("Could not prepare log partitions:", error.message));
  const timer = setInterval(ensure, 24 * 60 * 60 * 1000);
  timer.unref();
  return { ready: ensure(), stop: () => clearInterval(timer) };
};
