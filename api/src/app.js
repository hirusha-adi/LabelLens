import { getConnInfo } from "@hono/node-server/conninfo";
import { Hono } from "hono";
import { bodyLimit } from "hono/body-limit";
import { HTTPException } from "hono/http-exception";
import { secureHeaders } from "hono/secure-headers";
import {
  FAMILY_MAX,
  FAMILY_NOTE,
  TRIAL_DAYS,
  addDays,
  buildPlans,
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
const MIN_PASSWORD = 8;

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

/** Turns a Supabase Auth error into the app's error shape. Never passes the raw message on. */
const authFailure = (error) => {
  switch (error?.code) {
    case "invalid_credentials":
      return fail(401, "invalid_credentials", "That email and password don't match. Check them and try again.");
    case "email_not_confirmed":
      return fail(403, "email_not_confirmed", "Confirm your email first. We sent you a link.");
    case "user_already_exists":
    case "email_exists":
      return fail(409, "email_taken", "There's already an account with that email. Log in instead.");
    case "weak_password":
      return fail(422, "weak_password", "That password is too easy to guess. Use 10+ characters, or a few unrelated words.");
    case "email_address_invalid":
    case "validation_failed":
      return fail(422, "invalid_email", "Enter a full email, like name@example.com");
    case "over_email_send_rate_limit":
    case "over_request_rate_limit":
      return fail(429, "rate_limited", "Too many tries. Wait a minute and try again.");
    case "provider_disabled":
      return fail(501, "not_configured", "Google sign-in isn't set up yet. Use your email and password.");
    case "bad_jwt":
    case "bad_oauth_callback":
    case "bad_oauth_state":
    case "invalid_grant":
      return fail(401, "invalid_token", "Google sign-in didn't complete. Try again.");
    case "signup_disabled":
      return fail(403, "signup_disabled", "New accounts are switched off at the moment.");
    case "refresh_token_not_found":
    case "refresh_token_already_used":
    case "session_not_found":
    case "session_expired":
      return fail(401, "unauthorized", "Log in again.");
    default:
      if (error?.status === 400 && /id token|nonce|audience|issuer/i.test(error?.message ?? "")) {
        console.error("Google ID token refused:", error.message);
        return fail(401, "invalid_token", "Google sign-in didn't complete. Try again.");
      }
      console.error("Supabase auth error:", error?.code ?? error?.status, error?.message);
      return fail(502, "auth_unavailable", "We couldn't reach the sign-in service. Try again.");
  }
};

/** Unwraps a database result. Row level security refusals become 403, everything else 502. */
const rows = ({ data, error }) => {
  if (!error) return data;
  if (error.code === "42501") fail(403, "plus_required", "That's part of Plus.");
  console.error("Supabase database error:", error.code, error.message);
  return fail(502, "database_unavailable", "We couldn't reach the database. Try again.");
};

/** Fixed-window limiter, kept in memory. Supabase Auth has its own limits behind this one. */
const rateLimit = ({ limit, windowSeconds, trustProxy }) => {
  const hits = new Map();
  return async (c, next) => {
    let address = "unknown";
    try {
      address = (trustProxy && c.req.header("x-forwarded-for")?.split(",")[0].trim()) || getConnInfo(c).remote.address || "unknown";
    } catch {
      // No socket (tests call the app directly).
    }
    const now = Date.now();
    const entry = hits.get(address);
    if (!entry || now - entry.start > windowSeconds * 1000) {
      hits.set(address, { start: now, count: 1 });
      if (hits.size > 10_000) for (const [key, value] of hits) if (now - value.start > windowSeconds * 1000) hits.delete(key);
    } else if (++entry.count > limit) {
      c.header("Retry-After", String(Math.ceil((entry.start + windowSeconds * 1000 - now) / 1000)));
      return c.json({ error: "rate_limited", message: "Too many tries. Wait a minute and try again." }, 429);
    }
    await next();
  };
};

/**
 * @param {object} deps
 * @param {Awaited<ReturnType<import("./catalogue-store.js").createCatalogueStore>>} deps.catalogue
 * @param {ReturnType<import("./supabase.js").createSupabase>} deps.supabase
 * @param {{ failPurchases?: boolean, log?: boolean, trustProxy?: boolean, authRedirectUrl?: string, authLimitPerMinute?: number }} [deps.options]
 * @param {() => Date} [deps.clock]
 */
export const createApp = ({ catalogue, supabase, options = {}, clock = () => new Date() }) => {
  const { failPurchases = false, authLimitPerMinute = 20 } = options;
  const app = new Hono();

  if (options.log) {
    app.use(async (c, next) => {
      const started = Date.now();
      await next();
      // Path only: query strings and bodies can hold emails and tokens.
      console.log(`${c.req.method} ${c.req.path} ${c.res.status} ${Date.now() - started}ms`);
    });
  }
  app.use(secureHeaders());
  app.use(bodyLimit({ maxSize: 64 * 1024, onError: (c) => c.json({ error: "too_large", message: "That request is too large." }, 413) }));
  app.use("/auth/*", rateLimit({ limit: authLimitPerMinute, windowSeconds: 60, trustProxy: options.trustProxy }));

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

  const emailFrom = (input) => {
    if (typeof input !== "string" || !EMAIL.test(input.trim()) || input.length > 200) fail(422, "invalid_email", "Enter a full email, like name@example.com");
    return input.trim();
  };

  /** Verifies the bearer token, then hands the route a client that acts as that user. */
  const authed = async (c, next) => {
    const caller = await supabase.verify(c.req.raw);
    if (!caller) fail(401, "unauthorized", "Log in again.");
    c.set("caller", caller);
    c.set("db", supabase.forUser(caller.token));
    await next();
  };

  // ---------- Views ----------

  const profileOf = async (c) => {
    const profile = rows(await c.get("db").from("profiles").select("*").eq("id", c.get("caller").id).maybeSingle());
    // A valid token for a user with no profile means the account was deleted.
    if (!profile) fail(401, "unauthorized", "Log in again.");
    return profile;
  };

  const userView = async (db, id, email, profile) => {
    const [scans, saved] = await Promise.all([
      db.from("scans").select("id", { count: "exact", head: true }).eq("user_id", id),
      db.from("saved_additives").select("code", { count: "exact", head: true }).eq("user_id", id),
    ]);
    return {
      name: profile.name,
      email,
      initials: initialsOf(profile.name, email),
      plan: profile.plan,
      verified: true,
      passwordChangedLabel: "",
      stats: { scans: scans.count ?? 0, saved: saved.count ?? 0 },
    };
  };

  const subscriptionView = (profile, email) => {
    if (profile.plan === "free" || !profile.subscribed_at) return null;
    const trialEnds = addDays(new Date(profile.subscribed_at), TRIAL_DAYS);
    const renews = new Date(trialEnds);
    while (renews <= clock()) {
      if (profile.billing_period === "monthly") renews.setMonth(renews.getMonth() + 1);
      else renews.setFullYear(renews.getFullYear() + 1);
    }
    return {
      planId: profile.plan,
      title: `${planName(profile.plan)} · ${profile.billing_period === "monthly" ? "Monthly" : "Yearly"}`,
      status: trialEnds > clock() ? "Free trial" : "Active",
      renewsOn: longDate(renews),
      renewsShort: shortDate(renews),
      price: priceOf(profile.plan, profile.billing_period).price,
      billedTo: email,
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

  const scanView = (row) => {
    const at = new Date(row.scanned_at);
    return {
      id: row.id,
      day: dayLabel(at),
      name: row.name,
      time: at.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }),
      verdict: row.verdict,
      codes: row.codes,
    };
  };

  const preferencesView = (row) =>
    row
      ? { allergies: row.allergies, diet: row.diet, dietLevel: row.diet_level, groups: row.groups, additives: row.additives, sensitivities: row.sensitivities }
      : emptyPreferences();

  const familyView = async (c, profile) => {
    if (profile.plan !== "family") return { max: FAMILY_MAX, profiles: [], note: FAMILY_NOTE };
    const stored = rows(await c.get("db").from("family_profiles").select("*").order("position"));
    const profiles = stored.length
      ? stored.map((p) => ({ id: p.id, name: p.name, role: p.role, initials: p.initials, icon: p.icon, tone: p.tone, short: p.short, summary: p.summary, selected: p.selected }))
      : // Until profiles can be added in the app, a family plan starts with just its owner.
        [{ id: "me", name: `${profile.name.split(/\s+/)[0] || "You"} (you)`, role: "admin", initials: initialsOf(profile.name, c.get("caller").email), icon: null, tone: "primary", short: "Your profile", summary: "Uses your preferences", selected: true }];
    return { max: FAMILY_MAX, profiles, note: FAMILY_NOTE };
  };

  const regionsView = (selected) => ({
    language: regions.language,
    codeStyle: regions.codeStyle,
    selected: selected ?? regions.defaultRegion,
    regions: regions.regions,
  });

  /** The body the app gets after logging in, signing up or refreshing. */
  const sessionResponse = async (c, session, status = 200) => {
    const db = supabase.forUser(session.access_token);
    const profile = rows(await db.from("profiles").select("*").eq("id", session.user.id).maybeSingle());
    if (!profile) fail(401, "unauthorized", "Log in again.");
    return c.json(
      {
        token: session.access_token,
        refreshToken: session.refresh_token,
        expiresAt: session.expires_at ?? null,
        user: await userView(db, session.user.id, session.user.email ?? "", profile),
      },
      status,
    );
  };

  // ---------- Catalogue (no account needed) ----------

  app.get("/health", (c) => c.json({ ok: true, additives: catalogue.meta.totalCount, version: catalogue.meta.version }));

  app.get("/additives", async (c) => {
    await catalogue.refreshIfStale();
    // The list only changes when the data does, so the app can skip the download.
    const etag = `"${catalogue.meta.version}"`;
    c.header("ETag", etag);
    c.header("Cache-Control", "no-cache");
    if (c.req.header("if-none-match") === etag) return c.body(null, 304);
    return c.json({ meta: catalogue.meta, additives: catalogue.additives });
  });

  app.get("/additives/:code", async (c) => {
    const code = catalogue.resolve(c.req.param("code"));
    if (!code) fail(404, "not_found", `We don't have an additive with the code ${c.req.param("code").slice(0, 12)}.`);
    let detail;
    try {
      detail = await catalogue.detail(code);
    } catch (error) {
      console.error(error.message);
      fail(502, "database_unavailable", "We couldn't reach the database. Try again.");
    }
    return c.json(detail);
  });

  app.get("/preferences/options", (c) => c.json(preferenceOptions));
  app.get("/plans", (c) => c.json(buildPlans({ now: clock(), additiveCount: catalogue.meta.totalCount })));
  app.get("/regions", (c) => c.json(regionsView(null)));

  // ---------- Auth (Supabase Auth does the work; this shapes it for the app) ----------

  app.post("/auth/signup", async (c) => {
    const input = await body(c);
    const email = emailFrom(input.email);
    if (typeof input.password !== "string" || input.password.length < MIN_PASSWORD || input.password.length > 200) {
      fail(422, "weak_password", `Use a password with at least ${MIN_PASSWORD} characters.`);
    }
    const name = typeof input.name === "string" ? input.name.trim().slice(0, 80) : "";
    const { data, error } = await supabase.anon().auth.signUp({
      email,
      password: input.password,
      options: { data: { name }, emailRedirectTo: options.authRedirectUrl },
    });
    if (error) authFailure(error);
    if (data.session) return sessionResponse(c, data.session, 201);
    // Email confirmation is on: no session until the link is opened. The answer is the same
    // whether or not the address already has an account, so it can't be used to find out.
    return c.json({ token: null, refreshToken: null, expiresAt: null, needsVerification: true, user: { name: name || email.split("@")[0], email, initials: initialsOf(name, email), plan: "free", verified: false } }, 202);
  });

  app.post("/auth/login", async (c) => {
    const input = await body(c);
    if (typeof input.email !== "string" || typeof input.password !== "string" || !input.email.trim() || !input.password) {
      fail(401, "invalid_credentials", "That email and password don't match. Check them and try again.");
    }
    const { data, error } = await supabase.anon().auth.signInWithPassword({ email: input.email.trim(), password: input.password });
    if (error) authFailure(error);
    return sessionResponse(c, data.session);
  });

  app.post("/auth/refresh", async (c) => {
    const { refreshToken } = await body(c);
    if (typeof refreshToken !== "string" || !refreshToken) fail(401, "unauthorized", "Log in again.");
    const { data, error } = await supabase.anon().auth.refreshSession({ refresh_token: refreshToken });
    if (error || !data.session) authFailure(error ?? { code: "session_not_found" });
    return sessionResponse(c, data.session);
  });

  // The app gets an ID token from Google on the phone and sends it here. Supabase checks
  // its signature, that it was issued for this project's Google client, and the nonce.
  app.post("/auth/google", async (c) => {
    const { idToken, nonce } = await body(c);
    if (typeof idToken !== "string" || idToken.length < 100 || idToken.length > 8192) fail(422, "invalid_token", "Google sign-in didn't complete. Try again.");
    const { data, error } = await supabase.anon().auth.signInWithIdToken({
      provider: "google",
      token: idToken,
      nonce: typeof nonce === "string" && nonce ? nonce.slice(0, 200) : undefined,
    });
    if (error || !data.session) authFailure(error ?? { code: "session_not_found" });
    return sessionResponse(c, data.session);
  });

  // Always answers the same way, so it can't be used to find out who has an account.
  app.post("/auth/forgot", async (c) => {
    const email = emailFrom((await body(c)).email);
    const { error } = await supabase.anon().auth.resetPasswordForEmail(email, { redirectTo: options.authRedirectUrl });
    if (error && ["over_email_send_rate_limit", "over_request_rate_limit"].includes(error.code)) authFailure(error);
    if (error) console.error("Password reset email failed:", error.code, error.message);
    return c.json({ ok: true, expiresLabel: "1 hour", resendInSeconds: 60 });
  });

  app.post("/auth/resend-verification", async (c) => {
    const email = emailFrom((await body(c)).email);
    const { error } = await supabase.anon().auth.resend({ type: "signup", email, options: { emailRedirectTo: options.authRedirectUrl } });
    if (error && ["over_email_send_rate_limit", "over_request_rate_limit"].includes(error.code)) authFailure(error);
    if (error) console.error("Resending confirmation failed:", error.code, error.message);
    return c.json({ ok: true });
  });

  // Where the link in the confirmation email lands. The app notices by itself.
  app.get("/auth/confirmed", (c) =>
    c.html(
      '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Label Lens</title>' +
        '<body style="font-family:system-ui,sans-serif;background:#FAFBEF;color:#1A1C15;display:grid;place-items:center;min-height:100vh;margin:0;text-align:center;padding:24px">' +
        "<main><h1>You're all set</h1><p>Go back to Label Lens to carry on.</p></main>",
    ),
  );

  app.post("/auth/logout", authed, async (c) => {
    if (supabase.hasSecretKey) {
      const { error } = await supabase.admin().auth.admin.signOut(c.get("caller").token, "local");
      if (error) console.error("Sign-out failed:", error.code, error.message);
    }
    return c.json({ ok: true });
  });

  // ---------- Account ----------

  app.get("/me", authed, async (c) => {
    const caller = c.get("caller");
    return c.json(await userView(c.get("db"), caller.id, caller.email, await profileOf(c)));
  });

  app.delete("/me", authed, async (c) => {
    if (!supabase.hasSecretKey) fail(503, "not_configured", "Account deletion isn't available right now. Contact support.");
    // The token was verified above; the id comes from it, never from the request.
    const { error } = await supabase.admin().auth.admin.deleteUser(c.get("caller").id);
    if (error) {
      console.error("Account deletion failed:", error.code, error.message);
      fail(502, "auth_unavailable", "We couldn't delete your account. Try again.");
    }
    return c.json({ ok: true });
  });

  app.get("/me/preferences", authed, async (c) =>
    c.json(preferencesView(rows(await c.get("db").from("preferences").select("*").eq("user_id", c.get("caller").id).maybeSingle()))),
  );

  app.put("/me/preferences", authed, async (c) => {
    const clean = sanitizePreferences(await body(c), catalogue);
    const saved = rows(
      await c.get("db").from("preferences").upsert({
        user_id: c.get("caller").id,
        allergies: clean.allergies,
        diet: clean.diet,
        diet_level: clean.dietLevel,
        groups: clean.groups,
        additives: clean.additives,
        sensitivities: clean.sensitivities,
      }).select().single(),
    );
    return c.json(preferencesView(saved));
  });

  app.get("/me/region", authed, async (c) => c.json(regionsView((await profileOf(c)).region)));

  app.put("/me/region", authed, async (c) => {
    const { region } = await body(c);
    if (!regions.regions.some((r) => r.id === region)) fail(422, "unknown_region", "We don't have that region.");
    rows(await c.get("db").from("profiles").update({ region }).eq("id", c.get("caller").id));
    return c.json(regionsView(region));
  });

  // ---------- Subscription ----------
  // Stands in for Google Play Billing: the plan is granted without taking payment.

  app.get("/subscription", authed, async (c) => c.json({ subscription: subscriptionView(await profileOf(c), c.get("caller").email) }));

  app.post("/subscription/purchase", authed, async (c) => {
    const { planId, period } = await body(c);
    if (!["plus", "family"].includes(planId)) fail(422, "unknown_plan", "That plan isn't available.");
    if (failPurchases) fail(402, "payment_declined", "Google Play couldn't charge your payment method. You haven't been charged.");
    if (!supabase.hasSecretKey) fail(503, "not_configured", "Purchases aren't available right now.");
    const caller = c.get("caller");
    // Only the server may change a plan. This is where a Google Play purchase token gets verified.
    const profile = rows(await supabase.admin().rpc("grant_plan", { p_user: caller.id, p_plan: planId, p_period: period === "monthly" ? "monthly" : "yearly" }));
    const trialEnds = addDays(new Date(profile.subscribed_at), TRIAL_DAYS);
    return c.json({
      user: await userView(c.get("db"), caller.id, caller.email, profile),
      subscription: subscriptionView(profile, caller.email),
      trialEndsLabel: trialEnds > clock() ? shortDate(trialEnds) : "",
    });
  });

  app.post("/subscription/restore", authed, async (c) => {
    const caller = c.get("caller");
    const profile = await profileOf(c);
    return c.json({ user: await userView(c.get("db"), caller.id, caller.email, profile), subscription: subscriptionView(profile, caller.email) });
  });

  // ---------- History (the database only accepts new scans from paid plans) ----------

  app.get("/history", authed, async (c) =>
    c.json({ items: rows(await c.get("db").from("scans").select("*").order("scanned_at", { ascending: false }).limit(500)).map(scanView) }),
  );

  app.post("/history", authed, async (c) => {
    const input = await body(c);
    const codes = [...new Set((Array.isArray(input.codes) ? input.codes : []).map((code) => catalogue.resolve(code)).filter(Boolean))].slice(0, 60);
    const scan = rows(
      await c.get("db").from("scans").insert({
        name: typeof input.name === "string" && input.name.trim() ? input.name.trim().slice(0, 60) : "Scanned label",
        verdict: VERDICTS.has(input.verdict) ? input.verdict : "safe",
        codes,
      }).select().single(),
    );
    return c.json(scanView(scan), 201);
  });

  app.delete("/history", authed, async (c) => {
    rows(await c.get("db").from("scans").delete().eq("user_id", c.get("caller").id));
    return c.json({ ok: true });
  });

  // ---------- Saved additives ----------

  const savedCodes = async (c) =>
    rows(await c.get("db").from("saved_additives").select("code").order("created_at", { ascending: false })).map((row) => row.code);

  app.get("/saved", authed, async (c) => c.json({ codes: await savedCodes(c) }));

  app.put("/saved/:code", authed, async (c) => {
    const code = catalogue.resolve(c.req.param("code"));
    if (!code) fail(404, "not_found", "We don't have that additive.");
    rows(await c.get("db").from("saved_additives").upsert({ user_id: c.get("caller").id, code }, { onConflict: "user_id,code", ignoreDuplicates: true }));
    return c.json({ codes: await savedCodes(c) });
  });

  app.delete("/saved/:code", authed, async (c) => {
    const code = catalogue.resolve(c.req.param("code")) ?? c.req.param("code");
    rows(await c.get("db").from("saved_additives").delete().eq("user_id", c.get("caller").id).eq("code", code));
    return c.json({ codes: await savedCodes(c) });
  });

  // ---------- Family ----------

  app.get("/family", authed, async (c) => c.json(await familyView(c, await profileOf(c))));

  app.put("/family/selection", authed, async (c) => {
    const { selected } = await body(c);
    const chosen = new Set((Array.isArray(selected) ? selected : []).filter((id) => typeof id === "string"));
    const view = await familyView(c, await profileOf(c));
    for (const profile of view.profiles) {
      const next = chosen.has(profile.id);
      if (profile.id !== "me" && profile.selected !== next) rows(await c.get("db").from("family_profiles").update({ selected: next }).eq("id", profile.id));
      profile.selected = next;
    }
    return c.json(view);
  });

  return app;
};
