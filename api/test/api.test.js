import assert from "node:assert/strict";
import { test } from "node:test";
import { createApp } from "../src/app.js";
import { loadCatalogue } from "../src/catalogue.js";
import { demoAccount } from "../src/config.js";
import { createDb } from "../src/db.js";

const catalogue = loadCatalogue();

const setup = (options = {}) => {
  let now = new Date("2026-10-09T15:00:00");
  const db = createDb(null);
  db.ensureDemoUser();
  const app = createApp({ catalogue, db, options: { verifyAfterSeconds: 6, ...options }, clock: () => now });
  const call = async (method, path, { token, body, headers = {} } = {}) => {
    const response = await app.request(path, {
      method,
      headers: { ...(body !== undefined ? { "content-type": "application/json" } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}), ...headers },
      body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body),
    });
    const text = await response.text();
    return { status: response.status, headers: response.headers, json: text ? JSON.parse(text) : null };
  };
  const signUp = async (email = "new@example.com") => (await call("POST", "/auth/signup", { body: { name: "Nia Okafor", email, password: "correct horse" } })).json;
  return { call, signUp, advance: (seconds) => (now = new Date(now.getTime() + seconds * 1000)) };
};

test("catalogue is public and cacheable", async () => {
  const { call } = setup();
  const list = await call("GET", "/additives");
  assert.equal(list.status, 200);
  assert.equal(list.json.additives.length, list.json.meta.totalCount);
  const again = await call("GET", "/additives", { headers: { "if-none-match": list.headers.get("etag") } });
  assert.equal(again.status, 304);
});

test("additive detail, any letter case, and unknown codes", async () => {
  const { call } = setup();
  const detail = await call("GET", "/additives/e102");
  assert.equal(detail.json.code, "E102");
  assert.ok(detail.json.sections.length > 0);
  assert.equal(detail.json.regulatory[0].kind, "banned");
  const missing = await call("GET", "/additives/E9999");
  assert.equal(missing.status, 404);
  assert.equal(missing.json.error, "not_found");
});

test("plans carry today's trial end date and the real additive count", async () => {
  const { call } = setup();
  const plans = (await call("GET", "/plans")).json;
  assert.equal(plans.trialEndsLabel, "Oct 16");
  assert.ok(plans.comparison.some((row) => row.name === `Search ${catalogue.meta.totalCount} additives`));
});

test("sign up, confirm by waiting, log in again", async () => {
  const { call, signUp, advance } = setup();
  const created = await signUp();
  assert.equal(created.user.plan, "free");
  assert.equal(created.user.initials, "NO");
  assert.equal(created.user.verified, false);
  advance(7);
  assert.equal((await call("GET", "/me", { token: created.token })).json.verified, true);

  const wrong = await call("POST", "/auth/login", { body: { email: "new@example.com", password: "nope" } });
  assert.equal(wrong.status, 401);
  assert.equal(wrong.json.error, "invalid_credentials");
  const right = await call("POST", "/auth/login", { body: { email: "NEW@example.com", password: "correct horse" } });
  assert.equal(right.status, 200);
  assert.notEqual(right.json.token, created.token);
});

test("sign up rejects bad input and duplicate emails", async () => {
  const { call, signUp } = setup();
  assert.equal((await call("POST", "/auth/signup", { body: { email: "nope", password: "correct horse" } })).json.error, "invalid_email");
  assert.equal((await call("POST", "/auth/signup", { body: { email: "a@b.co", password: "short" } })).json.error, "weak_password");
  await signUp();
  const duplicate = await call("POST", "/auth/signup", { body: { email: "new@example.com", password: "correct horse" } });
  assert.equal(duplicate.status, 409);
  assert.equal((await call("POST", "/auth/signup", { body: "{not json" })).status, 400);
});

test("account routes need a valid token, and log out ends it", async () => {
  const { call, signUp } = setup();
  assert.equal((await call("GET", "/me")).status, 401);
  assert.equal((await call("GET", "/history", { token: "made-up" })).status, 401);
  const { token } = await signUp();
  assert.equal((await call("POST", "/auth/logout", { token })).status, 200);
  assert.equal((await call("GET", "/me", { token })).status, 401);
});

test("demo account comes with a filled-in profile", async () => {
  const { call } = setup();
  const { token, user } = (await call("POST", "/auth/login", { body: { email: demoAccount.email, password: demoAccount.password } })).json;
  assert.equal(user.plan, "family");
  const history = (await call("GET", "/history", { token })).json.items;
  assert.deepEqual([...new Set(history.map((h) => h.day))], ["Today", "Yesterday"]);
  assert.equal((await call("GET", "/family", { token })).json.profiles.length, 3);
  assert.equal((await call("GET", "/subscription", { token })).json.subscription.title, "Family · Yearly");
});

test("preferences are cleaned before they are stored", async () => {
  const { call, signUp } = setup();
  const { token } = await signUp();
  const saved = (await call("PUT", "/me/preferences", {
    token,
    body: {
      allergies: [{ name: " Milk ", level: "warn" }, { name: "milk" }, { name: "" }, { name: "Kiwi", level: "sideways", custom: true }],
      diet: "carnivore",
      groups: { artificial_colours: "avoid", preservatives: "off", made_up: "avoid" },
      additives: ["e951", "E9999", "E951"],
      sensitivities: ["children", "robots"],
      extra: "ignored",
    },
  })).json;
  assert.deepEqual(saved, {
    allergies: [{ name: "Milk", level: "warn", custom: false }, { name: "Kiwi", level: "avoid", custom: true }],
    diet: "none",
    dietLevel: "avoid",
    groups: { artificial_colours: "avoid" },
    additives: ["E951"],
    sensitivities: ["children"],
  });
  assert.deepEqual((await call("GET", "/me/preferences", { token })).json, saved);
});

test("purchase moves a free account onto a trial", async () => {
  const { call, signUp } = setup();
  const { token } = await signUp();
  assert.equal((await call("GET", "/subscription", { token })).json.subscription, null);
  assert.equal((await call("POST", "/subscription/purchase", { token, body: { planId: "gold" } })).status, 422);
  const bought = (await call("POST", "/subscription/purchase", { token, body: { planId: "plus", period: "yearly" } })).json;
  assert.equal(bought.user.plan, "plus");
  assert.equal(bought.trialEndsLabel, "Oct 16");
  assert.equal(bought.subscription.status, "Free trial");
  assert.equal(bought.subscription.renewsOn, "Oct 16, 2026");
});

test("failed purchases leave the plan alone", async () => {
  const { call, signUp } = setup({ failPurchases: true });
  const { token } = await signUp();
  const failed = await call("POST", "/subscription/purchase", { token, body: { planId: "plus" } });
  assert.equal(failed.status, 402);
  assert.equal((await call("GET", "/me", { token })).json.plan, "free");
});

test("history keeps real codes, newest first, and can be cleared", async () => {
  const { call, signUp } = setup();
  const { token } = await signUp();
  const added = await call("POST", "/history", { token, body: { codes: ["e102", "E102", "E9999", "E330"], verdict: "avoid" } });
  assert.equal(added.status, 201);
  assert.deepEqual(added.json.codes, ["E102", "E330"]);
  assert.equal(added.json.day, "Today");
  await call("POST", "/history", { token, body: { codes: [], verdict: "nonsense", name: "Oats" } });
  const items = (await call("GET", "/history", { token })).json.items;
  assert.deepEqual(items.map((i) => [i.name, i.verdict]), [["Oats", "safe"], ["Scanned label", "avoid"]]);
  assert.equal((await call("GET", "/me", { token })).json.stats.scans, 2);
  await call("DELETE", "/history", { token });
  assert.deepEqual((await call("GET", "/history", { token })).json.items, []);
});

test("saved additives", async () => {
  const { call, signUp } = setup();
  const { token } = await signUp();
  assert.deepEqual((await call("PUT", "/saved/e330", { token })).json.codes, ["E330"]);
  assert.deepEqual((await call("PUT", "/saved/E102", { token })).json.codes, ["E102", "E330"]);
  assert.deepEqual((await call("PUT", "/saved/E102", { token })).json.codes, ["E102", "E330"]);
  assert.equal((await call("PUT", "/saved/E9999", { token })).status, 404);
  assert.deepEqual((await call("DELETE", "/saved/E330", { token })).json.codes, ["E102"]);
});

test("region and account deletion", async () => {
  const { call, signUp } = setup();
  const { token } = await signUp();
  assert.equal((await call("PUT", "/me/region", { token, body: { region: "mars" } })).status, 422);
  assert.equal((await call("PUT", "/me/region", { token, body: { region: "eu" } })).json.selected, "eu");
  assert.equal((await call("DELETE", "/me", { token })).status, 200);
  assert.equal((await call("GET", "/me", { token })).status, 401);
  assert.equal((await call("POST", "/auth/login", { body: { email: "new@example.com", password: "correct horse" } })).status, 401);
});

test("unknown routes answer in the same error shape", async () => {
  const { call } = setup();
  const response = await call("GET", "/nope");
  assert.equal(response.status, 404);
  assert.equal(response.json.error, "not_found");
});
