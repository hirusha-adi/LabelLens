import react from "@vitejs/plugin-react";
import { copyFileSync, existsSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import type { Plugin } from "vite";
import { defineConfig } from "vitest/config";

const require = createRequire(import.meta.url);

/**
 * Copies the text-recognition engine into public/ocr so the site serves it itself.
 * Left to its defaults, tesseract.js would download these from a public CDN in the
 * visitor's browser, which is a request to a third party the site promises not to make.
 */
const ocrAssets = (): Plugin => ({
  name: "labellens-ocr-assets",
  buildStart() {
    const tesseract = dirname(require.resolve("tesseract.js/package.json"));
    // The core version tesseract.js itself depends on, not whichever is hoisted.
    const core = dirname(createRequire(join(tesseract, "package.json")).resolve("tesseract.js-core/package.json"));
    const language = dirname(require.resolve("@tesseract.js-data/eng/package.json"));
    const out = join(import.meta.dirname, "public", "ocr");
    mkdirSync(out, { recursive: true });
    const files: Array<[string, string]> = [
      [join(tesseract, "dist", "worker.min.js"), "worker.min.js"],
      // One build per CPU capability; the worker picks the best the browser supports.
      [join(core, "tesseract-core-lstm.wasm.js"), "tesseract-core-lstm.wasm.js"],
      [join(core, "tesseract-core-simd-lstm.wasm.js"), "tesseract-core-simd-lstm.wasm.js"],
      [join(core, "tesseract-core-relaxedsimd-lstm.wasm.js"), "tesseract-core-relaxedsimd-lstm.wasm.js"],
      [join(language, "4.0.0_best_int", "eng.traineddata.gz"), "eng.traineddata.gz"],
    ];
    for (const [from, name] of files) {
      if (!existsSync(from)) this.error(`Missing OCR file ${from}. Run pnpm install.`);
      copyFileSync(from, join(out, name));
    }
  },
});

export default defineConfig({
  plugins: [react(), ocrAssets()],
  server: {
    port: 5173,
    // In development the API runs beside the site; /api reaches it without CORS.
    proxy: { "/api": { target: process.env.API_PROXY ?? "http://127.0.0.1:8787", changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, "") } },
  },
  build: { target: "es2022", sourcemap: false },
  test: { environment: "node", include: ["src/**/*.test.ts"] },
});
