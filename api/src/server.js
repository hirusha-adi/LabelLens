import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { createCatalogueStore } from "./catalogue-store.js";
import { createSupabase } from "./supabase.js";

const port = Number(process.env.PORT ?? 8787);

let supabase;
let catalogue;
try {
  supabase = createSupabase();
  catalogue = await createCatalogueStore(supabase.anon);
} catch (error) {
  console.error(error.message);
  process.exit(1);
}

const app = createApp({
  catalogue,
  supabase,
  options: {
    log: true,
    trustProxy: process.env.TRUST_PROXY === "1",
    authRedirectUrl: process.env.AUTH_REDIRECT_URL || undefined,
    // FAIL_PURCHASE=1 makes every purchase fail, to see the "payment didn't go through" dialog.
    failPurchases: process.env.FAIL_PURCHASE === "1",
  },
});

serve({ fetch: app.fetch, port, hostname: process.env.HOST ?? "0.0.0.0" }, (info) => {
  const { totalCount, detailedCount, updatedLabel } = catalogue.meta;
  console.log(`Label Lens API on http://localhost:${info.port}`);
  console.log(`Supabase project ${new URL(supabase.url).host}`);
  console.log(`${totalCount} additives, ${detailedCount} with full details (data from ${updatedLabel})`);
  if (!supabase.hasSecretKey) console.log("No SUPABASE_SECRET_KEY set: logging out everywhere and deleting accounts are off.");
});
