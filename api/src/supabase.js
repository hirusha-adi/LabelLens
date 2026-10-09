// The three ways this API talks to Supabase, in order of privilege.
//
//   anon()         publishable key, no user. Reads the public catalogue and runs sign-in flows.
//   forUser(token) publishable key plus the caller's JWT. Every query runs under row level
//                  security as that user, so the database decides what they can touch.
//   admin()        secret key, bypasses row level security. Used for exactly three things,
//                  each for the caller whose token was just verified: ending a session,
//                  deleting the account, and changing its plan.
//
// A new client is made per request on purpose: the auth methods keep the signed-in
// session on the client object, and that must never leak into another request.

import { createAdminClient, createContextClient, resolveEnv, verifyAuth } from "@supabase/server/core";

export const createSupabase = () => {
  const { data: env, error } = resolveEnv();
  if (error) throw new Error(`Supabase is not configured: ${error.message}. Copy .env.example to .env and fill it in.`);
  if (!process.env.SUPABASE_PUBLISHABLE_KEY && !process.env.SUPABASE_PUBLISHABLE_KEYS) {
    throw new Error("Supabase is not configured: SUPABASE_PUBLISHABLE_KEY is missing. Copy .env.example to .env and fill it in.");
  }
  return {
    url: env.url,
    hasSecretKey: Boolean(process.env.SUPABASE_SECRET_KEY || process.env.SUPABASE_SECRET_KEYS),
    anon: () => createContextClient(),
    forUser: (token) => createContextClient({ auth: { token } }),
    admin: () => createAdminClient(),
    /** Checks the request's bearer token against the project's signing keys. */
    verify: async (request) => {
      const { data, error: authError } = await verifyAuth(request, { auth: "user" });
      if (authError || !data?.token || !data.userClaims?.id) return null;
      return { token: data.token, id: data.userClaims.id, email: data.userClaims.email ?? "" };
    },
  };
};
