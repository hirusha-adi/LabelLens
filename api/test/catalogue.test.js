import assert from "node:assert/strict";
import { test } from "node:test";
import { cleanText, deriveOrigin, deriveVerdict, loadCatalogue, parseNames, parseType } from "../src/catalogue.js";

const catalogue = loadCatalogue();
const find = (code) => catalogue.additives.find((a) => a.code === code);

test("loads every additive in the dataset once", () => {
  assert.ok(catalogue.meta.totalCount > 500);
  assert.equal(new Set(catalogue.additives.map((a) => a.code)).size, catalogue.additives.length);
  for (const additive of catalogue.additives) {
    assert.match(additive.code, /^E\d{3,4}[a-z]{0,2}$/, additive.code);
    assert.ok(additive.name, `${additive.code} has a name`);
    assert.ok(["safe", "caution", "avoid"].includes(additive.status));
    assert.ok(additive.note);
  }
});

test("detail files whose inner code lost its E are still matched by file name", () => {
  assert.equal(find("E441").hasDetail, true);
  assert.ok(catalogue.detail("E441").sections.some((s) => s.title === "Origin"));
});

test("both detail layouts produce the same sections", () => {
  const rich = catalogue.detail("E102").sections.map((s) => s.title);
  const basic = catalogue.detail("E330").sections.map((s) => s.title);
  for (const title of ["Side effects", "Overview", "Uses", "Origin", "Daily intake"]) {
    assert.ok(rich.includes(title), `E102 ${title}`);
    assert.ok(basic.includes(title), `E330 ${title}`);
  }
  assert.equal(catalogue.detail("E330").sections.find((s) => s.title === "Daily intake").summary, "No limit");
});

test("additives without a detail file say so and still have a page", () => {
  const aspartame = find("E951");
  assert.equal(aspartame.hasDetail, false);
  assert.deepEqual(catalogue.detail("E951").sections, []);
  assert.ok(catalogue.detail("E951").sources.length > 0);
});

test("names are split into a name and other names", () => {
  assert.deepEqual(parseNames("Sunset Yellow FCF (Orange Yellow S, FD&C Yellow 6)"), { name: "Sunset Yellow FCF", aliases: ["Orange Yellow S", "FD&C Yellow 6"] });
  assert.deepEqual(parseNames("Acetic acid (preservative)"), { name: "Acetic acid", aliases: [] });
  assert.deepEqual(parseNames("1,4-Heptonolactone"), { name: "1,4-Heptonolactone", aliases: [] });
  assert.deepEqual(parseNames("Agar (thickener) (gelling agent)"), { name: "Agar", aliases: [] });
  assert.deepEqual(parseNames("(i) Magnesium silicate (ii) Magnesium trisilicate"), { name: "Magnesium silicate", aliases: ["Magnesium trisilicate"] });
  assert.equal(parseNames("Riboflavin (Vitamin B2), formerly called lactoflavin").name, "Riboflavin");
  assert.ok(find("E621").aliases.includes("MSG"));
});

test("type becomes a category and a descriptor", () => {
  assert.deepEqual(parseType("color (Lemon yellow)", ""), { category: "Colour", descriptor: "lemon yellow" });
  assert.deepEqual(parseType("", "Synthetic preservative"), { category: "Preservative", descriptor: "synthetic preservative" });
  assert.deepEqual(parseType("acidity regulator", "Acidity regulators"), { category: "Acidity regulator", descriptor: "" });
});

test("verdict rules", () => {
  const base = { bannedIn: [], euApproved: true, hasDetail: true, sideEffects: "", characteristics: "" };
  assert.equal(deriveVerdict(base).status, "safe");
  assert.equal(deriveVerdict({ ...base, bannedIn: ["Norway", "Austria"] }).status, "avoid");
  assert.equal(deriveVerdict({ ...base, sideEffects: "Since it is an azo dye, it may elicit hyperactivity." }).status, "caution");
  // High-dose remarks after "None known" are not a concern at normal use.
  assert.equal(deriveVerdict({ ...base, sideEffects: "None known in the concentrations used. Asthma was seen at very high doses." }).status, "safe");
  assert.deepEqual(deriveVerdict({ ...base, euApproved: false, hasDetail: false }), { status: "caution", note: "Limited information" });
  // A missing EU flag alone does not change the verdict.
  assert.equal(deriveVerdict({ ...base, euApproved: false }).status, "safe");
});

test("verdicts on real entries", () => {
  assert.equal(find("E102").status, "avoid");
  assert.deepEqual(find("E102").bannedIn, ["Austria", "Germany", "Norway", "United Arab Emirates"]);
  assert.equal(find("E330").status, "safe");
  assert.equal(find("E250").status, "caution");
});

test("origin is read from the dietary notes", () => {
  assert.equal(find("E120").origin, "animal");
  assert.equal(find("E441").origin, "animal");
  assert.equal(find("E471").origin, "either");
  assert.equal(find("E330").origin, "suitable");
  assert.equal(deriveOrigin("", ""), "unknown");
});

test("groups and sensitivities", () => {
  assert.ok(find("E102").groups.includes("artificial_colours"));
  assert.ok(find("E211").groups.includes("preservatives"));
  assert.ok(find("E621").groups.includes("flavour_enhancers"));
  assert.ok(find("E951").groups.includes("artificial_sweeteners"));
  assert.ok(find("E102").sensitivities.includes("children"));
  assert.ok(find("E220").sensitivities.includes("asthma"));
});

test("overrides are applied on top of the dataset", () => {
  assert.equal(find("E171").status, "avoid");
  assert.deepEqual(find("E171").bannedIn, ["European Union"]);
  assert.ok(catalogue.detail("E171").regulatory.some((r) => r.text.includes("2022")));
});

test("text clean-up drops sentences whose links were lost", () => {
  assert.equal(cleanText("Many side effects are reported. See also   and ."), "Many side effects are reported.");
  assert.equal(cleanText("It does not cause cancer (see the following )."), "It does not cause cancer.");
  assert.equal(cleanText("a   b\u0096c"), "a b – c");
});

test("codes resolve whatever their case", () => {
  assert.equal(catalogue.resolve("e160A"), "E160a");
  assert.equal(catalogue.resolve("E9999"), null);
});
