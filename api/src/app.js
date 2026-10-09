import { Hono } from "hono";
import { bodyLimit } from "hono/body-limit";
import { HTTPException } from "hono/http-exception";
import {
  FAMILY_MAX,
  FAMILY_NOTE,
  TRIAL_DAYS,
  addDays,
  buildPlans,
  demoAccount,
  emptyPreferences,
  longDate,
  planName,
  preferenceOptions,
  priceOf,
  regions,
  shortDate,
} from "./config.js";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const LEVELS = new Set(["avoid", "warn"]);
const VERDICTS = new Set(["safe", "caution", "avoid"]);
const HISTORY_LIMIT = 500;

const fail = (status, error, message) => {
  throw new HTTPException(status, { res: Response.json({ error, message }, { status }) });
};

const initialsOf = (name, email) => {
  const parts = (name || email).trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "")).toUpperCase() || "?";
};

/** Keeps only what the app can have sent on purpose. Anything unknown is dropped, not rejected. */
export const sanitizePreferences = (input, catalogue) => {
  const source = input && typeof input === "object" ? input : {};
  const seen = new Set();
  const allergies = (Array.isArray(source.allergies) ? source.allergies : [])
    .filter((a) => a && typeof a.name === "string" && a.name.trim())
    .map((a) => ({ name: a.name.trim().slice(0, 40), level: LEVELS.has(a.level) ? a.level : "avoid", custom: Boolean(a.custom) }))
    .filter((a) => !seen.has(a.name.toLowerCase()) && seen.add(a.name.toLowerCase()))
    .slice(0, 40);
  const groups = {};
  for (const { id } of preferenceOptions.groups) {
    const level = source.groups?.[id];
    if (LEVELS.has(level)) groups[id] = level;
  }
  return {
    allergies,
    diet: preferenceOptions.diets.some((d) => d.id === source.diet) ? source.diet : "none",
    dietLevel: LEVELS.has(source.dietLevel) ? source.dietLevel : "avoid",
    groups,
    additives: [...new Set((Array.isArray(source.additives) ? source.additives : []).map((code) => catalogue.resolve(code)).filter(Boolean))].slice(0, 100),
    sensitivities: preferenceOptions.sensitivities.map((s) => s.id).filter((id) => Array.isArray(source.sensitivities) && source.sensitivities.includes(id)),
  };
};

/**
 * @param {object} deps
 * @param {ReturnType<import("./catalogue.js").loadCatalogue>} deps.catalogue
 * @param {ReturnType<import("./db.js").createDb>} deps.db
 * @param {{ failPurchases?: boolean, verifyAfterSeconds?: number, log?: boolean }} [deps.options]
 * @param {() => Date} [deps.clock]
 */
export const createApp = ({ catalogue, db, options = {}, clock = () => new Date() }) => {
  const { failPurchases = false, verifyAfterSeconds = 6 } = options;
  const app = new Hono();

  if (options.log) {
    app.use(async (c, next) => {
      const started = Date.now();
      await next();
      console.log(`${c.req.method} ${c.req.path} ${c.res.status} ${Date.now() - started}ms`);
    });
  }
  app.use(bodyLimit({ maxSize: 64 * 1024, onError: (c) => c.json({ error: "too_large", message: "That request is too large." }, 413) }));

  app.notFound((c) => c.json({ error: "not_found", message: "There's nothing at this address." }, 404));
  app.onError((error, c) => {
    if (error instanceof HTTPException) return error.getResponse();
    console.error(error);
    return c.json({ error: "server_error", message: "Something went wrong on our side. Try again." }, 500);
  });

  const body = async (c) => {
    try {
      const value = await c.req.json();
      if (value && typeof value === "object" && !Array.isArray(value)) return value;
    } catch {
      // Falls through to the error below.
    }
    return fail(400, "invalid_body", "Send a JSON object.");
  };

  const authed = async (c, next) => {
    const token = (c.req.header("authorization") ?? "").replace(/^Bearer\s+/i, "");
    const user = token ? db.userForToken(token) : null;
    if (!user) fail(401, "unauthorized", "Log in again.");
    c.set("user", user);
    c.set("token", token);
    await next();
  };

  // ---------- Views of a user ----------

  const isVerified = (user) => {
    if (!user.verifiedAt && clock().getTime() - new Date(user.createdAt).getTime() >= verifyAfterSeconds * 1000) {
      // No email is sent yet, so the address counts as confirmed after a short wait.
      user.verifiedAt = clock().toISOString();
      db.save();
    }
    return Boolean(user.verifiedAt);
  };

  const userView = (user) => ({
    name: user.name,
    email: user.email,
    initials: initialsOf(user.name, user.email),
    plan: user.plan,
    verified: isVerified(user),
    passwordChangedLabel: "",
    stats: { scans: user.history.length, saved: user.saved.length },
  });

  const subscriptionView = (user) => {
    if (user.plan === "free" || !user.subscribedAt) return null;
    const trialEnds = addDays(new Date(user.subscribedAt), TRIAL_DAYS);
    const renews = new Date(trialEnds);
    while (renews <= clock()) {
      if (user.period === "monthly") renews.setMonth(renews.getMonth() + 1);
      else renews.setFullYear(renews.getFullYear() + 1);
    }
    return {
      planId: user.plan,
      title: `${planName(user.plan)} · ${user.period === "monthly" ? "Monthly" : "Yearly"}`,
      status: trialEnds > clock() ? "Free trial" : "Active",
      renewsOn: longDate(renews),
      renewsShort: shortDate(renews),
      price: priceOf(user.plan, user.period).price,
      billedTo: user.email,
      manageUrl: "https://play.google.com/store/account/subscriptions",
      note: "Cancelling and payment changes happen in Google Play. Your preferences and history stay if you go back to Free; history becomes read-only.",
    };
  };

  const dayLabel = (date) => {
    const startOf = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    const days = Math.round((startOf(clock()) - startOf(date)) / 86_400_000);
    if (days <= 0) return "Today";
    if (days === 1) return "Yesterday";
    return date.getFullYear() === clock().getFullYear() ? shortDate(date) : longDate(date);
  };

  const historyView = (item) => {
    const at = new Date(item.at);
    return {
      id: item.id,
      day: dayLabel(at),
      name: item.name,
      time: at.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }),
      verdict: item.verdict,
      codes: item.codes,
    };
  };

  const familyView = (user) => {
    if (user.plan !== "family") return { max: FAMILY_MAX, profiles: [], note: FAMILY_NOTE };
    if (!user.family.length) {
      const first = user.name.split(/\s+/)[0];
      user.family = [{ id: "me", name: `${first} (you)`, role: "admin", initials: initialsOf(user.name, user.email), icon: null, tone: "primary", short: "Your profile", summary: "Uses your preferences", selected: true }];
      db.save();
    }
    return { max: FAMILY_MAX, profiles: user.family, note: FAMILY_NOTE };
  };

  const regionsView = (user) => ({
    language: regions.language,
    codeStyle: regions.codeStyle,
    selected: user?.region ?? regions.defaultRegion,
    regions: regions.regions,
  });

  const startSession = (c, user, status = 200) => c.json({ token: db.createSession(user), user: userView(user) }, status);

  // ---------- Catalogue (no account needed) ----------

  app.get("/health", (c) => c.json({ ok: true, additives: catalogue.meta.totalCount, version: catalogue.meta.version }));

  app.get("/additives", (c) => {
    // The list only changes when the data does, so the app can skip the download.
    const etag = `"${catalogue.meta.version}"`;
    c.header("ETag", etag);
    c.header("Cache-Control", "no-cache");
    if (c.req.header("if-none-match") === etag) return c.body(null, 304);
    return c.json({ meta: catalogue.meta, additives: catalogue.additives });
  });

  app.get("/additives/:code", (c) => {
    const code = catalogue.resolve(c.req.param("code"));
    if (!code) fail(404, "not_found", `We don't have an additive with the code ${c.req.param("code").slice(0, 12)}.`);
    return c.json(catalogue.detail(code));
  });

  app.get("/preferences/options", (c) => c.json(preferenceOptions));
  app.get("/plans", (c) => c.json(buildPlans({ now: clock(), additiveCount: catalogue.meta.totalCount })));
  app.get("/regions", (c) => c.json(regionsView(null)));

  // ---------- Auth ----------

  app.post("/auth/login", async (c) => {
    const { email, password } = await body(c);
    const user = typeof email === "string" ? db.findByEmail(email) : null;
    if (!user || typeof password !== "string" || !db.checkPassword(user, password)) {
      fail(401, "invalid_credentials", "That email and password don't match. Check them and try again.");
    }
    return startSession(c, user);
  });

  // Google sign-in is not wired up yet: this signs in the demo account.
  app.post("/auth/google", (c) => startSession(c, db.ensureDemoUser()));

  app.post("/auth/signup", async (c) => {
    const { name, email, password } = await body(c);
    if (typeof email !== "string" || !EMAIL.test(email.trim()) || email.length > 200) {
      fail(422, "invalid_email", "Enter a full email, like name@example.com");
    }
    if (typeof password !== "string" || password.length < 8 || password.length > 200) {
      fail(422, "weak_password", "Use a password with at least 8 characters.");
    }
    if (db.findByEmail(email)) fail(409, "email_taken", "There's already an account with that email. Log in instead.");
    const user = db.createUser({ name: typeof name === "string" ? name.slice(0, 80) : "", email, password, now: clock() });
    return startSession(c, user, 201);
  });

  // Always answers the same way, so it can't be used to find out who has an account.
  app.post("/auth/forgot", async (c) => {
    const { email } = await body(c);
    if (typeof email !== "string" || !EMAIL.test(email.trim())) fail(422, "invalid_email", "Enter a full email, like name@example.com");
    return c.json({ ok: true, expiresLabel: "1 hour", resendInSeconds: 42 });
  });

  app.post("/auth/resend-verification", authed, (c) => c.json({ ok: true }));

  app.post("/auth/logout", authed, (c) => {
    db.endSession(c.get("token"));
    return c.json({ ok: true });
  });

  // ---------- Account ----------

  app.get("/me", authed, (c) => c.json(userView(c.get("user"))));

  app.delete("/me", authed, (c) => {
    db.deleteUser(c.get("user"));
    return c.json({ ok: true });
  });

  app.get("/me/preferences", authed, (c) => c.json({ ...emptyPreferences(), ...c.get("user").preferences }));

  app.put("/me/preferences", authed, async (c) => {
    const user = c.get("user");
    user.preferences = sanitizePreferences(await body(c), catalogue);
    db.save();
    return c.json(user.preferences);
  });

  app.get("/me/region", authed, (c) => c.json(regionsView(c.get("user"))));

  app.put("/me/region", authed, async (c) => {
    const user = c.get("user");
    const { region } = await body(c);
    if (!regions.regions.some((r) => r.id === region)) fail(422, "unknown_region", "We don't have that region.");
    user.region = region;
    db.save();
    return c.json(regionsView(user));
  });

  // ---------- Subscription ----------
  // Stands in for Google Play Billing: a purchase always succeeds unless failPurchases is set.

  app.get("/subscription", authed, (c) => c.json({ subscription: subscriptionView(c.get("user")) }));

  app.post("/subscription/purchase", authed, async (c) => {
    const user = c.get("user");
    const { planId, period } = await body(c);
    if (!["plus", "family"].includes(planId)) fail(422, "unknown_plan", "That plan isn't available.");
    if (failPurchases) fail(402, "payment_declined", "Google Play couldn't charge your payment method. You haven't been charged.");
    const startingTrial = user.plan === "free";
    user.plan = planId;
    user.period = period === "monthly" ? "monthly" : "yearly";
    if (startingTrial || !user.subscribedAt) user.subscribedAt = clock().toISOString();
    db.save();
    const trialEnds = addDays(new Date(user.subscribedAt), TRIAL_DAYS);
    return c.json({ user: userView(user), subscription: subscriptionView(user), trialEndsLabel: trialEnds > clock() ? shortDate(trialEnds) : "" });
  });

  app.post("/subscription/restore", authed, (c) => {
    const user = c.get("user");
    return c.json({ user: userView(user), subscription: subscriptionView(user) });
  });

  // ---------- History ----------

  app.get("/history", authed, (c) => c.json({ items: c.get("user").history.map(historyView) }));

  app.post("/history", authed, async (c) => {
    const user = c.get("user");
    const input = await body(c);
    const codes = [...new Set((Array.isArray(input.codes) ? input.codes : []).map((code) => catalogue.resolve(code)).filter(Boolean))].slice(0, 60);
    const item = {
      id: `h-${clock().getTime()}-${user.history.length}`,
      name: typeof input.name === "string" && input.name.trim() ? input.name.trim().slice(0, 60) : "Scanned label",
      at: clock().toISOString(),
      verdict: VERDICTS.has(input.verdict) ? input.verdict : "safe",
      codes,
    };
    user.history.unshift(item);
    user.history.length = Math.min(user.history.length, HISTORY_LIMIT);
    db.save();
    return c.json(historyView(item), 201);
  });

  app.delete("/history", authed, (c) => {
    c.get("user").history = [];
    db.save();
    return c.json({ ok: true });
  });

  // ---------- Saved additives ----------

  app.get("/saved", authed, (c) => c.json({ codes: c.get("user").saved }));

  app.put("/saved/:code", authed, (c) => {
    const user = c.get("user");
    const code = catalogue.resolve(c.req.param("code"));
    if (!code) fail(404, "not_found", "We don't have that additive.");
    if (!user.saved.includes(code)) user.saved.unshift(code);
    db.save();
    return c.json({ codes: user.saved });
  });

  app.delete("/saved/:code", authed, (c) => {
    const user = c.get("user");
    const code = catalogue.resolve(c.req.param("code")) ?? c.req.param("code");
    user.saved = user.saved.filter((saved) => saved !== code);
    db.save();
    return c.json({ codes: user.saved });
  });

  // ---------- Family ----------

  app.get("/family", authed, (c) => c.json(familyView(c.get("user"))));

  app.put("/family/selection", authed, async (c) => {
    const user = c.get("user");
    const { selected } = await body(c);
    const view = familyView(user);
    const chosen = new Set(Array.isArray(selected) ? selected : []);
    view.profiles.forEach((profile) => (profile.selected = chosen.has(profile.id)));
    db.save();
    return c.json(view);
  });

  return app;
};

export { demoAccount };
