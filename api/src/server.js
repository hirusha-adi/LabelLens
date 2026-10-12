import { createServer } from "node:http";
import { createHandler } from "./app.js";
import { createCatalogueCache } from "./catalogue-cache.js";
import { createPool, explainDbError } from "./db.js";
import { createRequestLog, keepPartitionsReady } from "./request-log.js";

const port = Number(process.env.PORT ?? 8787);

let pool;
let catalogue;
let partitions;
try {
  pool = createPool();
  catalogue = await createCatalogueCache(pool);
  partitions = keepPartitionsReady(pool);
  await partitions.ready;
} catch (error) {
  console.error(explainDbError(error));
  process.exit(1);
}

const requestLog = createRequestLog(pool);
const server = createServer(
  createHandler({
    catalogue,
    record: requestLog.record,
    // LOG_REQUESTS=1 prints a line per request. Off by default: at volume the
    // console becomes the slowest part of the server.
    options: {
      log: process.env.LOG_REQUESTS === "1",
      trustProxy: process.env.TRUST_PROXY === "1",
      // The website's address(es), comma-separated, when it is served from another origin than the API.
      allowedOrigins: (process.env.ALLOWED_ORIGINS ?? "").split(",").map((origin) => origin.trim()).filter(Boolean),
    },
  }),
);

// Phones on mobile networks keep connections open between requests; holding them a
// little longer than the usual proxy idle timeout avoids a reconnect per request.
server.keepAliveTimeout = 65_000;
server.headersTimeout = 66_000;
// The API takes no request bodies, so nothing legitimate is slow to arrive.
server.requestTimeout = 10_000;
server.maxHeadersCount = 50;

server.listen(port, process.env.HOST ?? "0.0.0.0", () => {
  const { meta, list } = catalogue.current;
  console.log(`Label Lens API on http://localhost:${port}`);
  console.log(`${meta.totalCount} additives in memory (data from ${meta.updatedLabel}, version ${meta.version})`);
  console.log(`List: ${(list.identity.length / 1024).toFixed(0)} KB, ${(list.gzip.length / 1024).toFixed(0)} KB gzip, ${(list.br.length / 1024).toFixed(0)} KB Brotli`);
});

// Stop taking requests, write what is still waiting in the log, then let go of the database.
let stopping = false;
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    if (stopping) return;
    stopping = true;
    server.close(async () => {
      partitions.stop();
      await requestLog.flush().catch(() => {});
      await catalogue.close();
      await pool.end();
      process.exit(0);
    });
    server.closeIdleConnections();
    // Anything still connected after a few seconds is cut off.
    setTimeout(() => server.closeAllConnections(), 5000).unref();
  });
}
