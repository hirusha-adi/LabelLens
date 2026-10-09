// A small file-backed store for accounts and sessions. One JSON file, written
// atomically after each change. Fine for one process; swap for a real database
// before running more than one.

import { randomBytes, randomUUID, scryptSync, timingSafeEqual } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { demoAccount, emptyPreferences } from "./config.js";

const hashPassword = (password, salt = randomBytes(16).toString("hex")) => ({
  salt,
  hash: scryptSync(password, salt, 32).toString("hex"),
});

const passwordMatches = (password, user) => {
  const attempt = Buffer.from(hashPassword(password, user.salt).hash, "hex");
  const stored = Buffer.from(user.hash, "hex");
  return attempt.length === stored.length && timingSafeEqual(attempt, stored);
};

/**
 * @param {string | null} file  Where to keep the data. Null keeps it in memory only (used by tests).
 */
export const createDb = (file) => {
  let state = { users: {}, sessions: {} };
  if (file && existsSync(file)) {
    try {
      state = { users: {}, sessions: {}, ...JSON.parse(readFileSync(file, "utf8")) };
    } catch (error) {
      throw new Error(`Could not read ${file}: ${error.message}. Fix or delete the file and start again.`);
    }
  }

  const save = () => {
    if (!file) return;
    mkdirSync(dirname(file), { recursive: true });
    const temporary = `${file}.tmp`;
    writeFileSync(temporary, JSON.stringify(state, null, 2));
    renameSync(temporary, file);
  };

  const findByEmail = (email) => Object.values(state.users).find((u) => u.email.toLowerCase() === email.trim().toLowerCase()) ?? null;

  const createUser = ({ name, email, password, plan = "free", verified = false, now = new Date() }) => {
    const id = randomUUID();
    const user = {
      id,
      name: name?.trim() || email.split("@")[0],
      email: email.trim(),
      ...hashPassword(password),
      plan,
      period: "yearly",
      createdAt: now.toISOString(),
      verifiedAt: verified ? now.toISOString() : null,
      subscribedAt: plan === "free" ? null : now.toISOString(),
      preferences: emptyPreferences(),
      saved: [],
      history: [],
      family: [],
      region: null,
    };
    state.users[id] = user;
    save();
    return user;
  };

  const ensureDemoUser = () => {
    const existing = findByEmail(demoAccount.email);
    if (existing) return existing;
    const now = new Date();
    const user = createUser({ ...demoAccount, verified: true, now });
    user.preferences = structuredClone(demoAccount.preferences);
    user.saved = [...demoAccount.saved];
    user.family = structuredClone(demoAccount.family);
    user.history = demoAccount.history.map((item, i) => ({
      id: `demo-${i}`,
      name: item.name,
      at: new Date(now.getTime() - item.hoursAgo * 3_600_000).toISOString(),
      verdict: item.verdict,
      codes: item.codes,
    }));
    save();
    return user;
  };

  return {
    save,
    findByEmail,
    createUser,
    ensureDemoUser,
    checkPassword: (user, password) => passwordMatches(password, user),
    createSession: (user) => {
      const token = randomBytes(32).toString("base64url");
      state.sessions[token] = { userId: user.id, createdAt: new Date().toISOString() };
      save();
      return token;
    },
    userForToken: (token) => {
      const session = state.sessions[token];
      return session ? state.users[session.userId] ?? null : null;
    },
    endSession: (token) => {
      delete state.sessions[token];
      save();
    },
    deleteUser: (user) => {
      delete state.users[user.id];
      for (const [token, session] of Object.entries(state.sessions)) if (session.userId === user.id) delete state.sessions[token];
      save();
    },
  };
};
