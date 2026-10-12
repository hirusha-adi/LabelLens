// The PostgreSQL side, against the database in DATABASE_URL:
//
//   docker compose up -d --wait && pnpm test:db
//
// Everything happens in a throwaway schema that is dropped at the end, so the
// development data is not touched.

import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { after, before, test } from "node:test";
import pg from "pg";
import { migrate } from "../scripts/migrate.js";
import { seed } from "../scripts/seed.js";
import { createHandler } from "../src/app.js";
import { createCatalogueCache } from "../src/catalogue-cache.js";
import { loadCatalogue } from "../src/catalogue.js";
import { createRequestLog } from "../src/request-log.js";
import { serveHandler } from "./helpers.js";

assert.ok(process.env.DATABASE_URL, "pnpm test:db needs DATABASE_URL (see .env.example)");

const schema = `test_${randomBytes(5).toString("hex")}`;
const admin = new pg.Pool({ connectionString: process.env.DATABASE_URL, max: 1 });
const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL, max: 4, options: `-c search_path=${schema}` });
const files = loadCatalogue();
let api;
let catalogue;
let requestLog;

/** Waits for something that happens shortly after, such as a reload after a notification. */
const eventually = async (check, what) => {
  for (let i = 0; i < 100; i++) {
    if (await check()) return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error(`Timed out waiting for ${what}`);
};

before(async () => {
  await admin.query(`create schema ${schema}`);
  assert.deepEqual(await migrate(pool), ["001_catalogue.sql", "002_request_log.sql", "003_request_log_partitions.sql"]);
  await seed(pool, files);
  catalogue = await createCatalogueCache(pool);
  requestLog = createRequestLog(pool, { flushMs: 20 });
  api = await serveHandler(createHandler({ catalogue, record: requestLog.record, options: { trustProxy: true } }));
});

after(async () => {
  await api.close();
  await requestLog.flush();
  await catalogue.close();
  await pool.end();
  await admin.query(`drop schema ${schema} cascade`);
  await admin.end();
});

test("migrations are applied once", async () => {
  assert.deepEqual(await migrate(pool), []);
});

test("what is served matches what the data files build", async () => {
  const list = await api.get("/additives", { "accept-encoding": "br" });
  assert.deepEqual(list.json.additives, files.additives);
  assert.equal(list.json.meta.version, files.meta.version);
  assert.ok(list.json.options.diets.length > 0);
  for (const { code } of files.additives) assert.deepEqual(catalogue.current.details.has(code), true, code);
  for (const code of ["E102", "E171", "E330", "E951"]) {
    assert.deepEqual((await api.get(`/additives/${code.toLowerCase()}`)).json, files.detail(code), code);
  }
});

test("a new seed reaches the running API without a restart", async () => {
  assert.deepEqual(await seed(pool, files), { loaded: files.additives.length, removed: 0, version: files.meta.version });
  const fewer = { ...files, additives: files.additives.slice(0, 10), meta: { ...files.meta, version: "smaller" } };
  assert.equal((await seed(pool, fewer)).removed, files.additives.length - 10);
  assert.equal((await pool.query("select count(*)::int as n from additive_details")).rows[0].n, 10);
  await eventually(() => catalogue.current.meta.version === "smaller", "the API to notice the smaller catalogue");
  assert.equal((await api.get("/additives")).json.additives.length, 10);
  await seed(pool, files);
  await eventually(() => catalogue.current.meta.version === files.meta.version, "the API to notice the full catalogue");
  assert.equal((await api.get("/additives")).json.additives.length, files.additives.length);
});

test("requests are logged with exactly four facts and a time, in batches", async () => {
  await requestLog.flush();
  await pool.query("truncate request_log");
  const headers = { "x-device-type": "Google Pixel 9", "x-forwarded-for": "203.0.113.7", "user-agent": "LabelLens/2.2.0 secret-looking-string", cookie: "session=abc" };
  await api.get("/additives", headers);
  await api.get("/additives/e102", headers);
  await api.get("/additives/E9999", { "x-forwarded-for": "2001:db8::1" });
  await api.get("/health", headers);
  await requestLog.flush();
  const { rows } = await pool.query("select host(ip) as ip, device_type, resource, code, at, tableoid::regclass::text as partition from request_log order by id");
  assert.deepEqual(
    rows.map(({ at, partition, ...rest }) => rest),
    [
      { ip: "203.0.113.7", device_type: "Google Pixel 9", resource: "list", code: null },
      { ip: "203.0.113.7", device_type: "Google Pixel 9", resource: "detail", code: "E102" },
      { ip: "2001:db8::1", device_type: "unknown", resource: "detail", code: null },
    ],
  );
  assert.ok(rows.every((row) => row.at instanceof Date && Date.now() - row.at.getTime() < 60_000));
  // They land in this month's partition, not the catch-all.
  const month = new Date().toISOString().slice(0, 7).replace("-", "_");
  assert.ok(rows.every((row) => row.partition.endsWith(`request_log_${month}`)), rows[0].partition);
  const columns = (await pool.query("select column_name from information_schema.columns where table_schema = $1 and table_name = 'request_log' order by 1", [schema])).rows.map((r) => r.column_name);
  assert.deepEqual(columns, ["at", "code", "device_type", "id", "ip", "resource"]);
});

test("a month of log is removed by dropping its partition", async () => {
  await pool.query("select ensure_request_log_partitions(3)");
  const partitions = (await pool.query("select inhrelid::regclass::text as name from pg_inherits where inhparent = 'request_log'::regclass order by 1")).rows.map((r) => r.name.split(".").pop());
  assert.ok(partitions.includes("request_log_default"));
  assert.equal(partitions.filter((name) => /^request_log_\d{4}_\d{2}$/.test(name)).length, 4);
  // A row dated outside every monthly partition is still accepted.
  await pool.query("insert into request_log (at, device_type, resource) values ('2001-01-01', 'old', 'list')");
  assert.equal((await pool.query("select count(*)::int as n from request_log_default")).rows[0].n, 1);
  await pool.query("truncate request_log_default");
});
