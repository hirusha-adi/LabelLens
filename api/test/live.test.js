// End-to-end checks against the real Supabase project named in the env file:
//
//   pnpm test:live
//
// Creates two throwaway accounts with the secret key, exercises the API and the
// database's row level security through them, and deletes both at the end.

import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { after, before, test } from "node:test";
import { createApp } from "../src/app.js";
import { createCatalogueStore } from "../src/catalogue-store.js";
import { createSupabase } from "../src/supabase.js";

const supabase = createSupabase();
assert.ok(supabase.hasSecretKey, "pnpm test:live needs SUPABASE_SECRET_KEY");

const catalogue = await createCatalogueStore(supabase.anon);
const app = createApp({ catalogue, supabase, options: { authLimitPerMinute: 1000 } });

const call = async (method, path, { token, body } = {}) => {
  const response = await app.request(path, {
    method,
    headers: { ...(body !== undefined ? { "content-type": "application/json" } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, json: text.startsWith("{") ? JSON.parse(text) : null };
};

const people = {};
const makePerson = async (label) => {
  const email = `labellens-test-${label}-${randomBytes(6).toString("hex")}@example.com`;
  const password = randomBytes(18).toString("base64url");
  const { data, error } = await supabase.admin().auth.admin.createUser({ email, password, email_confirm: true, user_metadata: { name: `Test ${label}` } });
  assert.ifError(error);
  const login = await call("POST", "/auth/login", { body: { email, password } });
  assert.equal(login.status, 200, JSON.stringify(login.json));
  return { id: data.user.id, email, password, token: login.json.token, refreshToken: login.json.refreshToken, user: login.json.user };
};

before(async () => {
  people.ana = await makePerson("ana");
  people.ben = await makePerson("ben");
});

after(async () => {
  for (const person of Object.values(people)) await supabase.admin().auth.admin.deleteUser(person.id);
});

test("the seeded catalogue is served from the database", async () => {
  const list = await call("GET", "/additives");
  assert.ok(list.json.meta.totalCount > 500);
  const detail = await call("GET", "/additives/e102");
  assert.equal(detail.json.name, "Tartrazine");
  assert.ok(detail.json.sections.length > 0);
});

test("a new account gets a profile and empty preferences from the trigger", async () => {
  const { ana } = people;
  assert.equal(ana.user.plan, "free");
  assert.equal(ana.user.name, "Test ana");
  assert.equal(ana.user.initials, "TA");
  assert.deepEqual((await call("GET", "/me/preferences", { token: ana.token })).json.allergies, []);
});

test("wrong password is refused; refresh gives a working token", async () => {
  const { ana } = people;
  assert.equal((await call("POST", "/auth/login", { body: { email: ana.email, password: "not-the-password" } })).json.error, "invalid_credentials");
  const refreshed = await call("POST", "/auth/refresh", { body: { refreshToken: ana.refreshToken } });
  assert.equal(refreshed.status, 200);
  assert.equal((await call("GET", "/me", { token: refreshed.json.token })).status, 200);
  ana.token = refreshed.json.token;
});

test("a forged Google token is refused by Supabase", async () => {
  // Right shape, wrong signature: a real check needs a real Google account on a phone.
  const part = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const forged = `${part({ alg: "RS256", kid: "nope", typ: "JWT" })}.${part({ iss: "https://accounts.google.com", aud: "x", sub: "1", email: "a@b.co", exp: 4102444800 })}.${"A".repeat(342)}`;
  const response = await call("POST", "/auth/google", { body: { idToken: forged } });
  assert.ok([401, 501].includes(response.status), JSON.stringify(response));
  assert.equal(response.json.token, undefined);
});

test("preferences round-trip", async () => {
  const { ana } = people;
  const body = { allergies: [{ name: "Milk", level: "warn", custom: false }], diet: "vegan", dietLevel: "warn", groups: { artificial_colours: "avoid" }, additives: ["E951"], sensitivities: ["children"] };
  assert.deepEqual((await call("PUT", "/me/preferences", { token: ana.token, body })).json, body);
  assert.deepEqual((await call("GET", "/me/preferences", { token: ana.token })).json, body);
});

test("free accounts cannot record scans or save additives", async () => {
  const { ana } = people;
  assert.equal((await call("POST", "/history", { token: ana.token, body: { codes: ["E102"], verdict: "avoid" } })).status, 403);
  assert.equal((await call("PUT", "/saved/E330", { token: ana.token })).json.error, "plus_required");
});

test("after a purchase: history, saved additives and subscription work", async () => {
  const { ana } = people;
  const bought = await call("POST", "/subscription/purchase", { token: ana.token, body: { planId: "plus", period: "yearly" } });
  assert.equal(bought.json.user.plan, "plus");
  assert.equal(bought.json.subscription.status, "Free trial");

  const scan = await call("POST", "/history", { token: ana.token, body: { codes: ["e102", "E9999", "E330"], verdict: "avoid" } });
  assert.equal(scan.status, 201);
  assert.deepEqual(scan.json.codes, ["E102", "E330"]);
  assert.equal(scan.json.day, "Today");

  assert.deepEqual((await call("PUT", "/saved/e330", { token: ana.token })).json.codes, ["E330"]);
  assert.deepEqual((await call("PUT", "/saved/E330", { token: ana.token })).json.codes, ["E330"]);
  assert.deepEqual((await call("GET", "/me", { token: ana.token })).json.stats, { scans: 1, saved: 1 });
});

test("one account cannot see or change another's data, even talking to the database directly", async () => {
  const { ana, ben } = people;
  assert.deepEqual((await call("GET", "/history", { token: ben.token })).json.items, []);
  assert.deepEqual((await call("GET", "/saved", { token: ben.token })).json.codes, []);

  const asBen = supabase.forUser(ben.token);
  assert.deepEqual((await asBen.from("profiles").select("id")).data.map((p) => p.id), [ben.id]);
  assert.deepEqual((await asBen.from("scans").select("id").eq("user_id", ana.id)).data, []);
  assert.deepEqual((await asBen.from("preferences").select("user_id").eq("user_id", ana.id)).data, []);
  // Writing as someone else, or to columns that are not his to set.
  assert.ok((await asBen.from("scans").insert({ user_id: ana.id, verdict: "safe" })).error);
  assert.ok((await asBen.from("profiles").update({ plan: "family" }).eq("id", ben.id)).error);
  assert.deepEqual((await asBen.from("preferences").update({ diet: "vegan" }).eq("user_id", ana.id).select()).data, []);
  assert.equal((await call("GET", "/me", { token: ben.token })).json.plan, "free");
});

test("without an account: the catalogue is read-only and nothing else is visible", async () => {
  const anon = supabase.anon();
  assert.equal((await anon.from("additives").select("code").limit(1)).data.length, 1);
  assert.ok((await anon.from("additives").insert({ code: "E999", sort_order: 1, name: "x", short_name: "x", category: "x", status: "safe", note: "x" })).error);
  assert.ok((await anon.from("additives").delete().eq("code", "E102")).error);
  for (const table of ["profiles", "preferences", "scans", "saved_additives", "family_profiles"]) {
    const { data, error } = await anon.from(table).select("*").limit(1);
    assert.ok(error || data.length === 0, `${table} leaked to anon`);
  }
  assert.ok((await anon.rpc("seed_catalogue", { payload: { additives: [] } })).error);
  // Nobody can change a plan or reseed the catalogue through the Data API.
  const asBen = supabase.forUser(people.ben.token);
  assert.ok((await anon.rpc("grant_plan", { p_user: people.ben.id, p_plan: "plus" })).error);
  assert.ok((await asBen.rpc("grant_plan", { p_user: people.ben.id, p_plan: "plus" })).error);
  assert.ok((await asBen.rpc("purchase_plan", { p_plan: "plus" })).error);
  assert.ok((await asBen.rpc("seed_catalogue", { payload: { additives: [] } })).error);
});

test("history can be cleared and the account deleted, taking its data with it", async () => {
  const { ana } = people;
  await call("DELETE", "/history", { token: ana.token });
  assert.deepEqual((await call("GET", "/history", { token: ana.token })).json.items, []);
  assert.equal((await call("DELETE", "/me", { token: ana.token })).status, 200);
  assert.equal((await call("POST", "/auth/login", { body: { email: ana.email, password: ana.password } })).status, 401);
  const admin = supabase.admin();
  assert.deepEqual((await admin.from("profiles").select("id").eq("id", ana.id)).data, []);
  assert.deepEqual((await admin.from("saved_additives").select("code").eq("user_id", ana.id)).data, []);
  delete people.ana;
});
