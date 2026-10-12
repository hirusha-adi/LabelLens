// Writes the additive catalogue into the Android app, which ships with it and
// never downloads anything:
//
//   pnpm export:app
//
//   android/app/src/main/assets/catalogue.json  what lists, search and scanning need
//   android/app/src/main/assets/details.json    the long-form page for each additive
//
// Run it whenever data/ or the rules in src/catalogue.js change, then rebuild the app.

import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { loadCatalogue } from "../src/catalogue.js";
import { preferenceOptions } from "../src/config.js";

const assets = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "android", "app", "src", "main", "assets");
const catalogue = loadCatalogue();

const details = {};
for (const { code } of catalogue.additives) {
  const { regulatory, sections, sources, footnote } = catalogue.detail(code);
  details[code] = { code, regulatory, sections, sources, footnote };
}

mkdirSync(assets, { recursive: true });
const write = (name, value) => {
  const text = JSON.stringify(value);
  writeFileSync(join(assets, name), text);
  return `${name} ${(text.length / 1024).toFixed(0)} KB`;
};

console.log(
  `Wrote ${catalogue.additives.length} additives (version ${catalogue.meta.version}): ` +
    [write("catalogue.json", { meta: catalogue.meta, options: preferenceOptions, additives: catalogue.additives }), write("details.json", details)].join(", "),
);
