import pg from "pg";

/**
 * A small connection pool for the database named by DATABASE_URL.
 *
 * Small on purpose. No request reads from the database: the catalogue is held in
 * memory. The pool only carries the periodic log flush and catalogue reloads.
 */
export const createPool = (connectionString = process.env.DATABASE_URL, settings = {}) => {
  if (!connectionString) {
    throw new Error("DATABASE_URL is not set. Copy .env.example to .env, and start the database with `docker compose up -d --wait`.");
  }
  const pool = new pg.Pool({ connectionString, max: 4, connectionTimeoutMillis: 5000, ...settings });
  // A dropped idle connection must not take the process down; the next query reconnects.
  pool.on("error", (error) => console.error("Database connection lost:", error.message));
  return pool;
};

/** Turns the usual "can't connect" errors into one line that says what to do. */
export const explainDbError = (error) => {
  if (["ECONNREFUSED", "ENOTFOUND", "ETIMEDOUT"].includes(error.code) || /timeout|terminated/i.test(error.message)) {
    return "Can't reach PostgreSQL. Start it with `docker compose up -d --wait` and check DATABASE_URL in api/.env.";
  }
  if (error.code === "42P01") return "The database has no tables yet. Run `pnpm db:migrate`, then `pnpm seed`.";
  return error.message;
};
