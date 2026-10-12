import { createServer, request } from "node:http";
import { brotliDecompressSync, gunzipSync } from "node:zlib";
import { buildSnapshot } from "../src/catalogue-cache.js";
import { preferenceOptions } from "../src/config.js";

/** The catalogue built from data/, as the rows PostgreSQL would return. */
export const rowsFrom = (files) => ({
  metaRow: { version: files.meta.version, updated_label: files.meta.updatedLabel, sources: files.meta.sources, options: preferenceOptions },
  additiveRows: files.additives.map((a) => ({
    code: a.code, name: a.name, short_name: a.shortName, category: a.category, descriptor: a.descriptor, category_icon: a.categoryIcon,
    status: a.status, note: a.note, aliases: a.aliases, groups: a.groups, sensitivities: a.sensitivities, origin: a.origin,
    banned_in: a.bannedIn, has_detail: a.hasDetail,
  })),
  detailRows: files.additives.map(({ code }) => {
    const { regulatory, sections, sources, footnote } = files.detail(code);
    return { code, regulatory, sections, sources, footnote };
  }),
});

export const snapshotFrom = (files) => buildSnapshot(rowsFrom(files));

/** Runs a handler on a free port. Returns a `get` that sends exactly the headers given, and a `close`. */
export const serveHandler = async (handler) => {
  const server = createServer(handler);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  const get = (path, headers = {}, method = "GET") =>
    new Promise((resolve, reject) => {
      const req = request({ host: "127.0.0.1", port, path, method, headers }, (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          const raw = Buffer.concat(chunks);
          const encoding = res.headers["content-encoding"];
          const text = (encoding === "br" ? brotliDecompressSync(raw) : encoding === "gzip" ? gunzipSync(raw) : raw).toString();
          resolve({ status: res.statusCode, headers: res.headers, bytes: raw.length, json: text.startsWith("{") ? JSON.parse(text) : null });
        });
      });
      req.on("error", reject);
      req.end();
    });
  return { get, close: () => new Promise((resolve) => server.close(resolve)) };
};
