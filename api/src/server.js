import { serve } from "@hono/node-server";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createApp } from "./app.js";
import { loadCatalogue } from "./catalogue.js";
import { demoAccount } from "./config.js";
import { createDb } from "./db.js";

const here = dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT ?? 8787);
const dbFile = process.env.DB_FILE ?? join(here, "..", "var", "db.json");

const catalogue = loadCatalogue();
const db = createDb(dbFile);
db.ensureDemoUser();

const app = createApp({
  catalogue,
  db,
  options: {
    log: true,
    // FAIL_PURCHASE=1 makes every purchase fail, to see the "payment didn't go through" dialog.
    failPurchases: process.env.FAIL_PURCHASE === "1",
    verifyAfterSeconds: Number(process.env.VERIFY_AFTER_SECONDS ?? 6),
  },
});

serve({ fetch: app.fetch, port, hostname: process.env.HOST ?? "0.0.0.0" }, (info) => {
  const { totalCount, detailedCount, updatedLabel } = catalogue.meta;
  console.log(`Label Lens API on http://localhost:${info.port}`);
  console.log(`${totalCount} additives loaded, ${detailedCount} with full details (data from ${updatedLabel})`);
  console.log(`Accounts are kept in ${dbFile}`);
  console.log(`Demo login: ${demoAccount.email} / ${demoAccount.password}`);
});
