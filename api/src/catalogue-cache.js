// The catalogue, ready to send.
//
// The data only changes when someone runs `pnpm seed`, so everything is worked
// out once per version instead of once per request:
//
//   * all of it is read from PostgreSQL into memory in two queries
//   * every response body is serialised to JSON once
//   * each body is compressed once, with Brotli and gzip at their highest levels
//
// Answering a request is then a map lookup and a write of bytes that already
// exist. No request touches the database, parses JSON or compresses anything.

import { createHash } from "node:crypto";
import { brotliCompressSync, constants, gzipSync } from "node:zlib";

const brotli = (buffer) =>
  brotliCompressSync(buffer, { params: { [constants.BROTLI_PARAM_QUALITY]: constants.BROTLI_MAX_QUALITY, [constants.BROTLI_PARAM_SIZE_HINT]: buffer.length } });

/** One response body in every encoding a client may ask for, with its validator. */
export const prepare = (value) => {
  const identity = Buffer.from(JSON.stringify(value));
  return {
    etag: `"${createHash("sha1").update(identity).digest("base64url").slice(0, 16)}"`,
    identity,
    gzip: gzipSync(identity, { level: 9 }),
    br: brotli(identity),
  };
};

const toSummary = (row) => ({
  code: row.code,
  name: row.name,
  shortName: row.short_name,
  category: row.category,
  descriptor: row.descriptor,
  categoryIcon: row.category_icon,
  status: row.status,
  note: row.note,
  aliases: row.aliases,
  groups: row.groups,
  sensitivities: row.sensitivities,
  origin: row.origin,
  bannedIn: row.banned_in,
  hasDetail: row.has_detail,
});

/** Builds the in-memory snapshot from catalogue rows. Exported so tests can build one without a database. */
export const buildSnapshot = ({ metaRow, additiveRows, detailRows }) => {
  const additives = additiveRows.map(toSummary);
  const detailByCode = new Map(detailRows.map((row) => [row.code, row]));
  const meta = {
    totalCount: additives.length,
    detailedCount: additives.filter((a) => a.hasDetail).length,
    updatedLabel: metaRow.updated_label,
    sources: metaRow.sources,
    version: metaRow.version,
  };
  const details = new Map();
  const codes = new Map();
  for (const summary of additives) {
    const row = detailByCode.get(summary.code);
    codes.set(summary.code.toLowerCase(), summary.code);
    details.set(summary.code, prepare({ ...summary, regulatory: row?.regulatory ?? [], sections: row?.sections ?? [], sources: row?.sources ?? [], footnote: row?.footnote ?? "" }));
  }
  return {
    meta,
    list: prepare({ meta, options: metaRow.options, additives }),
    health: prepare({ ok: true, additives: additives.length, version: meta.version }),
    details,
    codes,
  };
};

const CHANNEL = "labellens_catalogue";

/**
 * Loads the catalogue and keeps it current. `pnpm seed` announces a new version
 * on a PostgreSQL channel; this listens on one dedicated connection and reloads
 * the moment that arrives. A slow timer covers a missed announcement.
 *
 * @param {import("pg").Pool} pool
 * @param {{ recheckSeconds?: number }} [settings]
 */
export const createCatalogueCache = async (pool, { recheckSeconds = 300 } = {}) => {
  let snapshot;
  let listener = null;
  let closed = false;

  const load = async () => {
    const [meta, additives, details] = await Promise.all([
      pool.query("select * from catalogue_meta"),
      pool.query("select * from additives order by sort_order"),
      pool.query("select * from additive_details"),
    ]);
    if (!meta.rows[0]) throw new Error("The catalogue is empty. Run `pnpm seed` to load it.");
    snapshot = buildSnapshot({ metaRow: meta.rows[0], additiveRows: additives.rows, detailRows: details.rows });
  };

  const reload = async () => {
    try {
      const before = snapshot.meta.version;
      await load();
      if (snapshot.meta.version !== before) console.log(`Catalogue reloaded: version ${snapshot.meta.version}, ${snapshot.meta.totalCount} additives`);
    } catch (error) {
      console.error("Catalogue reload failed, keeping the copy in memory:", error.message);
    }
  };

  const listen = async () => {
    if (closed) return;
    try {
      listener = await pool.connect();
      listener.on("notification", reload);
      // If the connection drops, pick up anything missed and listen again.
      listener.on("error", () => {
        listener?.release(true);
        listener = null;
        setTimeout(() => reload().then(listen), 2000).unref();
      });
      await listener.query(`listen ${CHANNEL}`);
    } catch (error) {
      console.error("Could not listen for catalogue changes; relying on the periodic check:", error.message);
      listener?.release(true);
      listener = null;
    }
  };

  await load();
  await listen();
  const timer = setInterval(async () => {
    try {
      const current = (await pool.query("select version from catalogue_meta")).rows[0];
      if (current && current.version !== snapshot.meta.version) await reload();
    } catch {
      // The database is away. The copy in memory keeps serving.
    }
  }, recheckSeconds * 1000);
  timer.unref();

  return {
    /** The current snapshot. Read it once per request; a reload swaps it whole. */
    get current() {
      return snapshot;
    },
    reload,
    close: async () => {
      closed = true;
      clearInterval(timer);
      if (listener) {
        listener.removeAllListeners();
        await listener.query(`unlisten ${CHANNEL}`).catch(() => {});
        listener.release();
        listener = null;
      }
    },
  };
};

export { CHANNEL as CATALOGUE_CHANNEL };
