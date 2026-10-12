// Applies the SQL files in db/migrations that have not been applied yet, in name order:
//
//   pnpm db:migrate
//
// Each file runs in its own transaction and is recorded in schema_migrations.

import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createPool, explainDbError } from "../src/db.js";

const directory = join(dirname(fileURLToPath(import.meta.url)), "..", "db", "migrations");

export const migrate = async (pool) => {
  await pool.query("create table if not exists schema_migrations (name text primary key, applied_at timestamptz not null default now())");
  const applied = new Set((await pool.query("select name from schema_migrations")).rows.map((row) => row.name));
  const pending = readdirSync(directory).filter((file) => file.endsWith(".sql") && !applied.has(file)).sort();
  for (const file of pending) {
    const client = await pool.connect();
    try {
      await client.query("begin");
      await client.query(readFileSync(join(directory, file), "utf8"));
      await client.query("insert into schema_migrations (name) values ($1)", [file]);
      await client.query("commit");
    } catch (error) {
      await client.query("rollback");
      throw new Error(`${file} failed and was rolled back: ${error.message}`);
    } finally {
      client.release();
    }
  }
  return pending;
};

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  let pool;
  try {
    pool = createPool();
    const done = await migrate(pool);
    console.log(done.length ? `Applied ${done.join(", ")}.` : "Database is up to date.");
  } catch (error) {
    console.error(explainDbError(error));
    process.exitCode = 1;
  } finally {
    await pool?.end();
  }
}
