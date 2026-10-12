// The API's routes over real HTTP, with the catalogue built from the files in
// data/ and the request log captured in memory. test/db.test.js covers PostgreSQL.

import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { createHandler } from "../src/app.js";
import { loadCatalogue } from "../src/catalogue.js";
import { createRequestLog, deviceTypeOf, ipOf } from "../src/request-log.js";
import { serveHandler, snapshotFrom } from "./helpers.js";

const files = loadCatalogue();
const catalogue = { current: snapshotFrom(files) };
const recorded = [];
let api;

before(async () => {
  api = await serveHandler(createHandler({ catalogue, record: (entry) => recorded.push(entry), options: { trustProxy: true } }));
});
after(() => api.close());

test("the list has every additive and the preference choices", async () => {
  const list = await api.get("/additives");
  assert.equal(list.status, 200);
  assert.equal(list.json.additives.length, list.json.meta.totalCount);
  assert.ok(list.json.additives.length > 500);
  assert.ok(list.json.options.allergens.length > 0);
  assert.deepEqual(list.json.additives, files.additives);
});

test("one additive in full, any letter case, and unknown codes", async () => {
  const detail = await api.get("/additives/e171");
  assert.deepEqual(detail.json, files.detail("E171"));
  assert.equal(detail.json.regulatory[0].kind, "banned");
  for (const path of ["/additives/E9999", "/additives/", "/additives/" + "x".repeat(500), "/additives/E102/extra"]) {
    const missing = await api.get(path);
    assert.equal(missing.status, 404, path);
    assert.deepEqual(Object.keys(missing.json), ["error", "message"]);
  }
});

test("responses are compressed ahead of time, smallest encoding the client accepts", async () => {
  const plain = await api.get("/additives");
  const gzip = await api.get("/additives", { "accept-encoding": "gzip" });
  const brotli = await api.get("/additives", { "accept-encoding": "gzip, deflate, br" });
  assert.equal(plain.headers["content-encoding"], undefined);
  assert.equal(gzip.headers["content-encoding"], "gzip");
  assert.equal(brotli.headers["content-encoding"], "br");
  assert.deepEqual(gzip.json, plain.json);
  assert.deepEqual(brotli.json, plain.json);
  assert.ok(brotli.bytes < gzip.bytes && gzip.bytes < plain.bytes / 4, `${plain.bytes} > ${gzip.bytes} > ${brotli.bytes}`);
  assert.equal(Number(brotli.headers["content-length"]), brotli.bytes);
  assert.equal(brotli.headers.vary, "Accept-Encoding");
  // An encoding ruled out with q=0 is not used.
  assert.equal((await api.get("/additives", { "accept-encoding": "br;q=0, gzip" })).headers["content-encoding"], "gzip");
  assert.equal((await api.get("/additives/E330", { "accept-encoding": "br" })).headers["content-encoding"], "br");
});

test("unchanged data is answered with 304 and no body, for the list and for a page", async () => {
  for (const path of ["/additives", "/additives/E330"]) {
    const first = await api.get(path, { "accept-encoding": "gzip" });
    assert.match(first.headers.etag, /^"[\w-]{16}"$/);
    assert.equal(first.headers["cache-control"], "public, max-age=0, must-revalidate");
    const again = await api.get(path, { "if-none-match": first.headers.etag, "accept-encoding": "gzip" });
    assert.equal(again.status, 304, path);
    assert.equal(again.bytes, 0);
    assert.equal((await api.get(path, { "if-none-match": '"something-else"' })).status, 200);
  }
  assert.notEqual((await api.get("/additives/E330")).headers.etag, (await api.get("/additives/E102")).headers.etag);
});

test("each catalogue request records device type, address and what was asked, and nothing else", async () => {
  recorded.length = 0;
  const headers = { "x-device-type": "Google Pixel 9", "x-forwarded-for": "203.0.113.7, 10.0.0.1", "user-agent": "LabelLens/2.2.0", cookie: "a=b", authorization: "Bearer secret" };
  const list = await api.get("/additives", headers);
  await api.get("/additives", { ...headers, "if-none-match": list.headers.etag });
  await api.get("/additives/e102?utm=x", headers);
  await api.get("/additives/E9999", headers);
  await api.get("/health", headers);
  await api.get("/nope", headers);
  assert.deepEqual(recorded, [
    { ip: "203.0.113.7", deviceType: "Google Pixel 9", resource: "list", code: null },
    { ip: "203.0.113.7", deviceType: "Google Pixel 9", resource: "list", code: null },
    { ip: "203.0.113.7", deviceType: "Google Pixel 9", resource: "detail", code: "E102" },
    { ip: "203.0.113.7", deviceType: "Google Pixel 9", resource: "detail", code: null },
  ]);
});

test("only listed websites may read the API from a browser", async () => {
  const site = await serveHandler(createHandler({ catalogue, record: () => {}, options: { allowedOrigins: ["https://label-lens.example"] } }));
  try {
    const allowed = await site.get("/additives/E330", { origin: "https://label-lens.example", "accept-encoding": "gzip" });
    assert.equal(allowed.headers["access-control-allow-origin"], "https://label-lens.example");
    assert.equal(allowed.headers.vary, "Origin, Accept-Encoding");
    assert.equal(allowed.headers["access-control-expose-headers"], "ETag");
    const again = await site.get("/additives/E330", { origin: "https://label-lens.example", "if-none-match": allowed.headers.etag });
    assert.equal(again.status, 304);
    assert.equal(again.headers["access-control-allow-origin"], "https://label-lens.example");
    const other = await site.get("/additives/E330", { origin: "https://elsewhere.example" });
    assert.equal(other.status, 200);
    assert.equal(other.headers["access-control-allow-origin"], undefined);
    const preflight = await site.get("/additives", { origin: "https://label-lens.example", "access-control-request-method": "GET", "access-control-request-headers": "x-device-type" }, "OPTIONS");
    assert.equal(preflight.status, 204);
    assert.equal(preflight.headers["access-control-allow-origin"], "https://label-lens.example");
  } finally {
    await site.close();
  }
});

test("only GET and HEAD are accepted", async () => {
  const head = await api.get("/additives/E330", {}, "HEAD");
  assert.equal(head.status, 200);
  assert.equal(head.bytes, 0);
  assert.ok(Number(head.headers["content-length"]) > 0);
  const post = await api.get("/additives", {}, "POST");
  assert.equal(post.status, 405);
  assert.equal(post.headers.allow, "GET, HEAD");
});

test("health, and headers that keep a browser from misreading the JSON", async () => {
  const health = await api.get("/health");
  assert.deepEqual(health.json, { ok: true, additives: files.additives.length, version: files.meta.version });
  assert.equal(health.headers["x-content-type-options"], "nosniff");
  assert.equal(health.headers["cache-control"], "no-store");
  assert.match(health.headers["content-type"], /^application\/json/);
});

test("a reload swaps the whole catalogue at once", async () => {
  const before = (await api.get("/additives")).headers.etag;
  const smaller = { ...files, additives: files.additives.slice(0, 5), meta: { ...files.meta, version: "next" } };
  catalogue.current = snapshotFrom(smaller);
  const list = await api.get("/additives");
  assert.equal(list.json.additives.length, 5);
  assert.notEqual(list.headers.etag, before);
  assert.equal((await api.get("/additives", { "if-none-match": before })).status, 200);
  assert.equal((await api.get(`/additives/${files.additives[10].code}`)).status, 404);
  catalogue.current = snapshotFrom(files);
});

test("a forwarded address is only believed behind a trusted proxy", () => {
  assert.equal(ipOf({ forwardedFor: "203.0.113.7", remoteAddress: "198.51.100.2", trustProxy: false }), "198.51.100.2");
  assert.equal(ipOf({ forwardedFor: "203.0.113.7", remoteAddress: "198.51.100.2", trustProxy: true }), "203.0.113.7");
  assert.equal(ipOf({ remoteAddress: "::ffff:192.0.2.9" }), "192.0.2.9");
  assert.equal(ipOf({ remoteAddress: "2001:db8::1" }), "2001:db8::1");
  assert.equal(ipOf({ forwardedFor: "not-an-address", remoteAddress: undefined, trustProxy: true }), null);
});

test("behind a CDN, the address comes from the header the CDN sets", async () => {
  // As deployed: Cloudflare, then Caddy. X-Forwarded-For only holds Docker's gateway.
  assert.equal(ipOf({ named: "203.0.113.7", forwardedFor: "172.18.0.1", remoteAddress: "172.19.0.5", trustProxy: true }), "203.0.113.7");
  assert.equal(ipOf({ named: "2001:db8::7", forwardedFor: "172.18.0.1", trustProxy: true }), "2001:db8::7");
  // Without trust, or with rubbish in the header, it is ignored.
  assert.equal(ipOf({ named: "203.0.113.7", remoteAddress: "198.51.100.2", trustProxy: false }), "198.51.100.2");
  assert.equal(ipOf({ named: "<script>", forwardedFor: "203.0.113.9", trustProxy: true }), "203.0.113.9");

  const seen = [];
  const site = await serveHandler(createHandler({ catalogue, record: (entry) => seen.push(entry.ip), options: { trustProxy: true, clientIpHeader: "CF-Connecting-IP" } }));
  try {
    await site.get("/additives/E330", { "cf-connecting-ip": "203.0.113.7", "x-forwarded-for": "172.18.0.1" });
    await site.get("/additives/E330", { "x-forwarded-for": "198.51.100.4" });
    assert.deepEqual(seen, ["203.0.113.7", "198.51.100.4"]);
  } finally {
    await site.close();
  }
});

test("device type: the app's own name, or a broad class, never the user-agent string", () => {
  assert.equal(deviceTypeOf({ "x-device-type": "  Samsung   SM-S921B " }), "Samsung SM-S921B");
  assert.equal(deviceTypeOf({ "x-device-type": "x".repeat(300) }).length, 80);
  assert.equal(deviceTypeOf({ "user-agent": "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36" }), "android browser");
  assert.equal(deviceTypeOf({ "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140 Safari/537.36" }), "desktop browser");
  assert.equal(deviceTypeOf({ "user-agent": "curl/8.9.1" }), "script");
  assert.equal(deviceTypeOf({}), "unknown");
});

test("the request log writes in batches, retries after a failure and caps what it holds", async () => {
  const batches = [];
  let failing = false;
  const client = {
    query: async (sql, params) => {
      if (failing && /insert/.test(sql)) throw new Error("database is down");
      if (/insert/.test(sql)) batches.push(params[2].length);
    },
    release: () => {},
  };
  const log = createRequestLog({ connect: async () => client }, { flushMs: 10_000, flushRows: 3, maxBuffered: 4 });
  const entry = { ip: "203.0.113.7", deviceType: "Phone", resource: "list", code: null };

  log.record(entry);
  log.record(entry);
  assert.equal(log.waiting, 2);
  await log.flush();
  assert.deepEqual(batches, [2]);

  // Reaching flushRows writes without waiting for the timer.
  for (let i = 0; i < 3; i++) log.record(entry);
  await log.flush();
  assert.deepEqual(batches, [2, 3]);

  // While the database is away rows wait, up to the cap, and go out when it is back.
  failing = true;
  for (let i = 0; i < 2; i++) log.record(entry);
  await log.flush();
  assert.equal(log.waiting, 2);
  for (let i = 0; i < 2; i++) log.record(entry);
  await log.flush();
  for (let i = 0; i < 2; i++) log.record(entry);
  await log.flush();
  assert.equal(log.waiting, 4);
  failing = false;
  await log.flush();
  assert.equal(log.waiting, 0);
  assert.deepEqual(batches, [2, 3, 4]);
});
