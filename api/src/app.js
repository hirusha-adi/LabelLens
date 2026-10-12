// The HTTP handler. Plain node:http, no framework: there are three routes and
// every response body already exists as bytes (see catalogue-cache.js), so the
// work per request is choosing which bytes and writing them.

import { deviceTypeOf, ipOf } from "./request-log.js";
import { prepare } from "./catalogue-cache.js";

// Sent on every response. The API serves JSON to an app, so these only matter if
// someone opens a URL in a browser.
const BASE_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Cross-Origin-Resource-Policy": "same-origin",
  Vary: "Accept-Encoding",
};

// The app asks "has this changed?" on every launch, so always revalidate, but let
// a CDN or proxy hold a copy and answer those checks itself.
const CATALOGUE_CACHE = "public, max-age=0, must-revalidate";

const NOT_FOUND = prepare({ error: "not_found", message: "There's nothing at this address." });
const NO_SUCH_ADDITIVE = prepare({ error: "not_found", message: "We don't have an additive with that code." });
const METHOD_NOT_ALLOWED = prepare({ error: "method_not_allowed", message: "Only GET is supported." });
const SERVER_ERROR = prepare({ error: "server_error", message: "Something went wrong on our side. Try again." });

/** Picks the smallest encoding the client accepts. */
const encodingFor = (acceptEncoding) => {
  if (typeof acceptEncoding !== "string" || !acceptEncoding) return "identity";
  // q=0 means "not this one"; anything else counts as accepted.
  const accepts = (name) => new RegExp(`(^|[,\\s])${name}(?!\\s*;\\s*q=0(\\.0+)?\\s*(,|$))`, "i").test(acceptEncoding);
  if (accepts("br")) return "br";
  if (accepts("gzip")) return "gzip";
  return "identity";
};

const send = (req, res, status, body, extraHeaders) => {
  const encoding = encodingFor(req.headers["accept-encoding"]);
  const bytes = body[encoding];
  const headers = { ...BASE_HEADERS, ...extraHeaders, ETag: body.etag, "Content-Length": bytes.length };
  // Keep "Origin" in Vary when the CORS step added it.
  if (res.getHeader("Access-Control-Allow-Origin")) headers.Vary = "Origin, Accept-Encoding";
  if (encoding !== "identity") headers["Content-Encoding"] = encoding;
  res.writeHead(status, headers);
  res.end(req.method === "HEAD" ? undefined : bytes);
};

/** True, and answers 304, when the client already has this exact body. */
const notModified = (req, res, body, extraHeaders) => {
  const held = req.headers["if-none-match"];
  if (!held || !(held === body.etag || held === "*" || held.split(",").some((tag) => tag.trim().replace(/^W\//, "") === body.etag))) return false;
  res.writeHead(304, { ...extraHeaders, ETag: body.etag, Vary: res.getHeader("Access-Control-Allow-Origin") ? "Origin, Accept-Encoding" : "Accept-Encoding" });
  res.end();
  return true;
};

/**
 * @param {object} deps
 * @param {{ current: ReturnType<import("./catalogue-cache.js").buildSnapshot> }} deps.catalogue
 * @param {(entry: { ip: string | null, deviceType: string, resource: "list" | "detail", code: string | null }) => void} deps.record
 * @param {{ log?: boolean, trustProxy?: boolean, allowedOrigins?: string[] }} [deps.options]
 * @returns {(req: import("node:http").IncomingMessage, res: import("node:http").ServerResponse) => void}
 */
export const createHandler = ({ catalogue, record, options = {} }) => {
  // Web pages on these origins may read the API. The Android app is not a browser
  // and needs none of this.
  const allowedOrigins = new Set(options.allowedOrigins ?? []);
  const cors = (req, res) => {
    const origin = req.headers.origin;
    if (!origin || !allowedOrigins.has(origin)) return;
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Access-Control-Expose-Headers", "ETag");
    res.setHeader("Vary", "Origin, Accept-Encoding");
  };

  const track = (req, resource, code) =>
    record({
      ip: ipOf({ forwardedFor: req.headers["x-forwarded-for"], remoteAddress: req.socket?.remoteAddress, trustProxy: options.trustProxy }),
      deviceType: deviceTypeOf(req.headers),
      resource,
      code,
    });

  const route = (req, res) => {
    cors(req, res);
    if (req.method === "OPTIONS" && req.headers["access-control-request-method"]) {
      // A browser asking permission before a request with extra headers.
      res.writeHead(204, { "Access-Control-Allow-Methods": "GET, HEAD", "Access-Control-Allow-Headers": "X-Device-Type, If-None-Match", "Access-Control-Max-Age": "86400", "Content-Length": 0 });
      return res.end();
    }
    if (req.method !== "GET" && req.method !== "HEAD") return send(req, res, 405, METHOD_NOT_ALLOWED, { Allow: "GET, HEAD" });
    const query = req.url.indexOf("?");
    const path = query < 0 ? req.url : req.url.slice(0, query);
    // One read of the snapshot per request, so a reload mid-request cannot mix versions.
    const snapshot = catalogue.current;

    if (path === "/additives") {
      track(req, "list", null);
      const headers = { "Cache-Control": CATALOGUE_CACHE };
      return notModified(req, res, snapshot.list, headers) || send(req, res, 200, snapshot.list, headers);
    }
    if (path.startsWith("/additives/")) {
      const asked = path.slice(11);
      const code = asked.length <= 12 ? snapshot.codes.get(asked.toLowerCase()) ?? null : null;
      track(req, "detail", code);
      if (!code) return send(req, res, 404, NO_SUCH_ADDITIVE);
      const body = snapshot.details.get(code);
      const headers = { "Cache-Control": CATALOGUE_CACHE };
      return notModified(req, res, body, headers) || send(req, res, 200, body, headers);
    }
    if (path === "/health") return send(req, res, 200, snapshot.health, { "Cache-Control": "no-store" });
    return send(req, res, 404, NOT_FOUND);
  };

  return (req, res) => {
    const started = options.log ? process.hrtime.bigint() : 0n;
    try {
      route(req, res);
    } catch (error) {
      console.error(error);
      if (!res.headersSent) send(req, res, 500, SERVER_ERROR);
      else res.destroy();
    }
    if (options.log) {
      const micros = Number((process.hrtime.bigint() - started) / 1000n);
      console.log(`${req.method} ${req.url.split("?")[0]} ${res.statusCode} ${micros}µs`);
    }
  };
};
