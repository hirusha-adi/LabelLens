// Routes and rules that don't need a database. The catalogue comes from the files
// in data/, and Supabase is replaced by a stub that knows no users.
// test/live.test.js covers everything that does need Supabase.

import assert from "node:assert/strict";
import { test } from "node:test";
import { createApp, sanitizePreferences } from "../src/app.js";
import { loadCatalogue } from "../src/catalogue.js";

const files = loadCatalogue();
const catalogue = { meta: files.meta, additives: files.additives, resolve: files.resolve, detail: async (code) => files.detail(code), refreshIfStale: async () => {} };

const setup = (options = {}) => {
  const unreachable = () => {
    throw new Error("This test should not reach Supabase");
  };
  const supabase = { url: "https://example.supabase.co", hasSecretKey: false, anon: unreachable, forUser: unreachable, admin: unreachable, verify: async () => null };
  const app = createApp({ catalogue, supabase, options, clock: () => new Date("2026-10-09T15:00:00") });
  return async (method, path, { body, headers = {} } = {}) => {
    const response = await app.request(path, {
      method,
      headers: { ...(body !== undefined ? { "content-type": "application/json" } : {}), ...headers },
      body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body),
    });
    const text = await response.text();
    return { status: response.status, headers: response.headers, json: text.startsWith("{") ? JSON.parse(text) : null };
  };
};

test("catalogue is public and cacheable", async () => {
  const call = setup();
  const list = await call("GET", "/additives");
  assert.equal(list.status, 200);
  assert.equal(list.json.additives.length, list.json.meta.totalCount);
  assert.equal((await call("GET", "/additives", { headers: { "if-none-match": list.headers.get("etag") } })).status, 304);
});

test("additive detail, any letter case, and unknown codes", async () => {
  const call = setup();
  const detail = await call("GET", "/additives/e102");
  assert.equal(detail.json.code, "E102");
  assert.equal(detail.json.regulatory[0].kind, "banned");
  const missing = await call("GET", "/additives/E9999");
  assert.equal(missing.status, 404);
  assert.equal(missing.json.error, "not_found");
});

test("plans carry today's trial end date and the real additive count", async () => {
  const plans = (await setup()("GET", "/plans")).json;
  assert.equal(plans.trialEndsLabel, "Oct 16");
  assert.ok(plans.comparison.some((row) => row.name === `Search ${catalogue.meta.totalCount} additives`));
});

test("account routes refuse callers without a valid token", async () => {
  const call = setup();
  for (const [method, path] of [["GET", "/me"], ["DELETE", "/me"], ["GET", "/history"], ["POST", "/history"], ["GET", "/saved"], ["PUT", "/saved/E330"], ["PUT", "/me/preferences"], ["POST", "/subscription/purchase"], ["GET", "/family"], ["POST", "/auth/logout"]]) {
    const response = await call(method, path, { headers: { authorization: "Bearer made-up" }, body: method === "GET" ? undefined : {} });
    assert.equal(response.status, 401, `${method} ${path}`);
    assert.equal(response.json.error, "unauthorized");
  }
});

test("sign-up and log-in input is checked before Supabase is called", async () => {
  const call = setup();
  assert.equal((await call("POST", "/auth/signup", { body: { email: "nope", password: "long enough" } })).json.error, "invalid_email");
  assert.equal((await call("POST", "/auth/signup", { body: { email: "a@b.co", password: "short" } })).json.error, "weak_password");
  assert.equal((await call("POST", "/auth/signup", { body: "{not json" })).status, 400);
  assert.equal((await call("POST", "/auth/login", { body: { email: "", password: "" } })).status, 401);
  assert.equal((await call("POST", "/auth/refresh", { body: {} })).status, 401);
  assert.equal((await call("POST", "/auth/forgot", { body: { email: "nope" } })).status, 422);
  assert.equal((await call("POST", "/auth/google", { body: {} })).json.error, "invalid_token");
  assert.equal((await call("POST", "/auth/google", { body: { idToken: "too-short" } })).status, 422);
});

test("auth routes are rate limited", async () => {
  const call = setup({ authLimitPerMinute: 3 });
  const statuses = [];
  for (let i = 0; i < 5; i++) statuses.push((await call("POST", "/auth/google", { body: {} })).status);
  assert.deepEqual(statuses, [422, 422, 422, 429, 429]);
  // Other routes are not affected.
  assert.equal((await call("GET", "/health")).status, 200);
});

test("responses carry security headers and errors share one shape", async () => {
  const call = setup();
  const health = await call("GET", "/health");
  assert.equal(health.headers.get("x-content-type-options"), "nosniff");
  const missing = await call("GET", "/nope");
  assert.equal(missing.status, 404);
  assert.deepEqual(Object.keys(missing.json), ["error", "message"]);
  const large = await call("POST", "/auth/login", { body: { email: "a@b.co", password: "x".repeat(70_000) } });
  assert.equal(large.status, 413);
});

test("preferences are cleaned before they are stored", () => {
  assert.deepEqual(
    sanitizePreferences(
      {
        allergies: [{ name: " Milk ", level: "warn" }, { name: "milk" }, { name: "" }, { name: "Kiwi", level: "sideways", custom: true }],
        diet: "carnivore",
        groups: { artificial_colours: "avoid", preservatives: "off", made_up: "avoid" },
        additives: ["e951", "E9999", "E951"],
        sensitivities: ["children", "robots"],
        extra: "ignored",
      },
      catalogue,
    ),
    {
      allergies: [{ name: "Milk", level: "warn", custom: false }, { name: "Kiwi", level: "avoid", custom: true }],
      diet: "none",
      dietLevel: "avoid",
      groups: { artificial_colours: "avoid" },
      additives: ["E951"],
      sensitivities: ["children"],
    },
  );
  assert.deepEqual(sanitizePreferences(null, catalogue).allergies, []);
});
