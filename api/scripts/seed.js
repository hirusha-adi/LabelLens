// Loads the additive catalogue into Supabase.
//
//   pnpm seed
//
// Builds the catalogue from the files in data/ (see src/catalogue.js) and upserts it
// through the seed_catalogue() database function, which only the secret key may call.
// Safe to run again: unchanged rows stay as they are.

import { createAdminClient } from "@supabase/server/core";
import { loadCatalogue } from "../src/catalogue.js";

if (!process.env.SUPABASE_URL || !(process.env.SUPABASE_SECRET_KEY || process.env.SUPABASE_SECRET_KEYS)) {
  console.error("Seeding needs SUPABASE_URL and SUPABASE_SECRET_KEY. Put them in .env (see .env.example).");
  process.exit(1);
}

const catalogue = loadCatalogue();
const admin = createAdminClient();
const codes = catalogue.additives.map((a) => a.code);

console.log(`Seeding ${codes.length} additives into ${new URL(process.env.SUPABASE_URL).host} ...`);

const payload = { meta: catalogue.meta, additives: codes.map((code) => catalogue.detail(code)) };
const { data: loaded, error } = await admin.rpc("seed_catalogue", { payload });
if (error) {
  console.error(`Seeding failed: ${error.message}`);
  process.exit(1);
}

// Remove anything that has left the dataset. Saved additives pointing at it go too.
const { data: existing, error: listError } = await admin.from("additives").select("code");
if (listError) {
  console.error(`Seeded, but could not check for stale rows: ${listError.message}`);
  process.exit(1);
}
const known = new Set(codes);
const stale = existing.map((row) => row.code).filter((code) => !known.has(code));
if (stale.length) {
  const { error: deleteError } = await admin.from("additives").delete().in("code", stale);
  if (deleteError) {
    console.error(`Seeded, but could not remove ${stale.length} stale rows: ${deleteError.message}`);
    process.exit(1);
  }
}

console.log(`Done. ${loaded - stale.length} additives in the database, ${catalogue.meta.detailedCount} with a full entry, ${stale.length} removed. Version ${catalogue.meta.version}.`);
