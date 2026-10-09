import { serve } from "@hono/node-server";
import { Hono } from "hono";
import { logger } from "hono/logger";
import {
  additives,
  catalogueMeta,
  demoAccount,
  demoFamily,
  demoHistory,
  demoPreferences,
  detailOf,
  emptyPreferences,
  plans,
  preferenceOptions,
  regions,
  summaryOf,
} from "./data.js";

const PORT = Number(process.env.PORT ?? 8787);
// Set FAIL_PURCHASE=1 to make every purchase fail, to see the "payment didn't go through" dialog.
const FAIL_PURCHASE = process.env.FAIL_PURCHASE === "1";
// Seconds after sign-up until the email counts as confirmed.
const VERIFY_AFTER_SECONDS = Number(process.env.VERIFY_AFTER_SECONDS ?? 6);

// Everything lives in memory and resets when the server restarts.
const sessions = new Map();
let nextToken = 1;

const initialsOf = (name, email) => {
  const parts = (name || email).trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "")).toUpperCase() || "?";
};

const createSession = ({ name, email, plan, verified, demo }) => {
  const token = `sample-token-${nextToken++}`;
  const session = {
    token,
    name: name || email.split("@")[0],
    email,
    plan,
    period: "yearly",
    createdAt: Date.now(),
    verified,
    preferences: demo ? demoPreferences() : emptyPreferences(),
    saved: demo ? ["E171", "E102", "E951", "E621"] : [],
    history: demo ? demoHistory() : [],
    family: demoFamily(),
    region: regions.selected,
    shareStats: false,
  };
  sessions.set(token, session);
  return session;
};

const userOf = (s) => ({
  name: s.name,
  email: s.email,
  initials: initialsOf(s.name, s.email),
  plan: s.plan,
  verified: s.verified || Date.now() - s.createdAt > VERIFY_AFTER_SECONDS * 1000,
  passwordChangedLabel: "Last changed 3 months ago",
  stats: { scans: s.history.length > 5 ? s.history.length : 214, saved: s.saved.length > 4 ? s.saved.length : 12 },
});

const subscriptionOf = (s) => {
  if (s.plan === "free") return null;
  const plan = plans.plans.find((p) => p.id === s.plan);
  const price = plan[s.period];
  return {
    planId: s.plan,
    title: `${plan.name} · ${s.period === "yearly" ? "Yearly" : "Monthly"}`,
    status: "Active",
    renewsOn: s.period === "yearly" ? "Oct 16, 2027" : "Nov 16, 2026",
    renewsShort: "Oct 16",
    price: price.price,
    billedTo: s.email,
    manageUrl: "https://play.google.com/store/account/subscriptions",
    note: "Cancelling and payment changes happen in Google Play. Your preferences and history stay if you go back to Free; history becomes read-only.",
  };
};

const app = new Hono();
app.use(logger());

app.onError((err, c) => {
  console.error(err);
  return c.json({ error: "server_error", message: "Something went wrong." }, 500);
});

const authed = async (c, next) => {
  const token = (c.req.header("authorization") ?? "").replace(/^Bearer\s+/i, "");
  const session = sessions.get(token);
  if (!session) return c.json({ error: "unauthorized", message: "Log in again." }, 401);
  c.set("session", session);
  await next();
};

app.get("/health", (c) => c.json({ ok: true }));

// ---------- Catalogue (no account needed) ----------

app.get("/additives", (c) => c.json({ meta: catalogueMeta, additives: additives.map(summaryOf) }));

app.get("/additives/:code", (c) => {
  const code = c.req.param("code").toUpperCase();
  const found = additives.find((a) => a.code.toUpperCase() === code);
  if (!found) return c.json({ error: "not_found", message: `No additive with code ${code}.` }, 404);
  return c.json(detailOf(found));
});

app.get("/preferences/options", (c) => c.json(preferenceOptions));
app.get("/plans", (c) => c.json(plans));
app.get("/regions", (c) => c.json(regions));

// ---------- Auth ----------

app.post("/auth/login", async (c) => {
  const { email = "", password = "" } = await c.req.json();
  if (email.trim().toLowerCase() !== demoAccount.email || password !== demoAccount.password) {
    return c.json({ error: "invalid_credentials", message: "That email and password don't match. Check them and try again." }, 401);
  }
  const s = createSession({ name: demoAccount.name, email: demoAccount.email, plan: "family", verified: true, demo: true });
  return c.json({ token: s.token, user: userOf(s) });
});

app.post("/auth/google", (c) => {
  const s = createSession({ name: demoAccount.name, email: demoAccount.email, plan: "family", verified: true, demo: true });
  return c.json({ token: s.token, user: userOf(s) });
});

app.post("/auth/signup", async (c) => {
  const { name = "", email = "", password = "" } = await c.req.json();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    return c.json({ error: "invalid_email", message: "Enter a full email, like name@example.com" }, 422);
  }
  if (password.length < 6) {
    return c.json({ error: "weak_password", message: "Weak. Use 10+ characters, or a few unrelated words." }, 422);
  }
  const s = createSession({ name, email: email.trim(), plan: "free", verified: false, demo: false });
  return c.json({ token: s.token, user: userOf(s) });
});

app.post("/auth/forgot", async (c) => {
  await c.req.json();
  return c.json({ ok: true, expiresLabel: "1 hour", resendInSeconds: 42 });
});

app.post("/auth/resend-verification", authed, (c) => c.json({ ok: true }));

app.post("/auth/logout", authed, (c) => {
  sessions.delete(c.get("session").token);
  return c.json({ ok: true });
});

// ---------- Account ----------

app.get("/me", authed, (c) => c.json(userOf(c.get("session"))));

app.delete("/me", authed, (c) => {
  sessions.delete(c.get("session").token);
  return c.json({ ok: true });
});

app.get("/me/preferences", authed, (c) => c.json(c.get("session").preferences));

app.put("/me/preferences", authed, async (c) => {
  const s = c.get("session");
  s.preferences = { ...emptyPreferences(), ...(await c.req.json()) };
  return c.json(s.preferences);
});

app.put("/me/region", authed, async (c) => {
  const s = c.get("session");
  s.region = (await c.req.json()).region;
  return c.json({ ...regions, selected: s.region });
});

// ---------- Subscription ----------

app.get("/subscription", authed, (c) => c.json({ subscription: subscriptionOf(c.get("session")) }));

app.post("/subscription/purchase", authed, async (c) => {
  const s = c.get("session");
  const { planId, period = "yearly" } = await c.req.json();
  if (FAIL_PURCHASE) {
    return c.json({ error: "payment_declined", message: "Google Play couldn't charge your payment method. You haven't been charged." }, 402);
  }
  if (!["plus", "family"].includes(planId)) {
    return c.json({ error: "unknown_plan", message: "That plan isn't available." }, 422);
  }
  s.plan = planId;
  s.period = period === "monthly" ? "monthly" : "yearly";
  return c.json({ user: userOf(s), subscription: subscriptionOf(s), trialEndsLabel: plans.trialEndsLabel });
});

app.post("/subscription/restore", authed, (c) => {
  const s = c.get("session");
  return c.json({ user: userOf(s), subscription: subscriptionOf(s) });
});

// ---------- History ----------

app.get("/history", authed, (c) => c.json({ items: c.get("session").history }));

app.post("/history", authed, async (c) => {
  const s = c.get("session");
  const { codes = [], verdict = "safe", name } = await c.req.json();
  const time = new Date().toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
  const item = { id: `h${Date.now()}`, day: "Today", name: name || "Scanned label", time, verdict, codes };
  s.history.unshift(item);
  return c.json(item, 201);
});

app.delete("/history", authed, (c) => {
  c.get("session").history = [];
  return c.json({ ok: true });
});

// ---------- Saved additives ----------

app.get("/saved", authed, (c) => c.json({ codes: c.get("session").saved }));

app.put("/saved/:code", authed, (c) => {
  const s = c.get("session");
  const code = c.req.param("code");
  if (!s.saved.includes(code)) s.saved.unshift(code);
  return c.json({ codes: s.saved });
});

app.delete("/saved/:code", authed, (c) => {
  const s = c.get("session");
  s.saved = s.saved.filter((x) => x !== c.req.param("code"));
  return c.json({ codes: s.saved });
});

// ---------- Family ----------

app.get("/family", authed, (c) => c.json(c.get("session").family));

app.put("/family/selection", authed, async (c) => {
  const s = c.get("session");
  const { selected = [] } = await c.req.json();
  s.family.profiles.forEach((p) => (p.selected = selected.includes(p.id)));
  return c.json(s.family);
});

serve({ fetch: app.fetch, port: PORT, hostname: "0.0.0.0" }, (info) => {
  console.log(`Label Lens sample API on http://localhost:${info.port}`);
  console.log(`Demo login: ${demoAccount.email} / ${demoAccount.password}`);
});
