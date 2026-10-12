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
    assert.ok(additive.category, `${additive.code} has a category`);
    assert.ok(["safe", "caution", "avoid"].includes(additive.status));
    assert.ok(additive.note);
  }
});

test("every additive has a page with an overview and links", () => {
  for (const { code } of catalogue.additives) {
    const detail = catalogue.detail(code);
    assert.ok(detail.sections.some((s) => s.title === "Overview"), `${code} overview`);
    assert.ok(detail.sources.length >= 2, `${code} sources`);
    assert.ok(detail.regulatory.length >= 1, `${code} regulatory`);
    for (const source of detail.sources) assert.match(source.url, /^https:\/\//, `${code} ${source.label}`);
    for (const section of detail.sections) for (const paragraph of section.paragraphs) assert.ok(paragraph.trim(), `${code} ${section.title}`);
  }
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
  assert.equal(parseType("unclassified", "").category, "Additive");
});

test("verdict rules", () => {
  const base = { bannedIn: [], alert: false, approved: true, text: "" };
  assert.deepEqual(deriveVerdict(base), { status: "safe", note: "No concerns noted" });
  assert.deepEqual(deriveVerdict({ ...base, bannedIn: ["Canada", "India"] }), { status: "avoid", note: "Banned in 2 countries" });
  assert.equal(deriveVerdict({ ...base, bannedIn: ["United States"] }).note, "Banned in the United States");
  // A ban outranks everything else the entry says.
  assert.equal(deriveVerdict({ ...base, bannedIn: ["Canada"], alert: true, approved: false }).status, "avoid");
  assert.deepEqual(deriveVerdict({ ...base, alert: true }), { status: "caution", note: "Regulator concern noted" });
  assert.equal(deriveVerdict({ ...base, alert: true, text: "Some estimated exposures exceeded the limit." }).note, "Intake can exceed the limit");
  assert.deepEqual(deriveVerdict({ ...base, approved: false }), { status: "caution", note: "No EU or US approval confirmed" });
});

test("the reason for a caution is not taken from a negated sentence", () => {
  const note = (text) => deriveVerdict({ bannedIn: [], alert: true, approved: true, text }).note;
  assert.equal(note("The review did not identify a new genotoxicity concern."), "Regulator concern noted");
  assert.equal(note("Estimated intake did not exceed the limit."), "Regulator concern noted");
  assert.equal(note("EFSA could not rule out DNA damage."), "Safety questions raised");
});

test("verdicts on real entries", () => {
  assert.equal(find("E171").status, "avoid");
  assert.equal(find("E171").note, "Banned in the EU");
  assert.ok(find("E171").bannedIn.includes("Germany"));
  assert.equal(find("E123").note, "Banned in the United States");
  assert.equal(find("E330").status, "safe");
  assert.equal(find("E102").status, "caution");
  assert.equal(find("E102").note, "Warning label required in the EU");
  // The entry says "did not identify a new genotoxicity concern": no alarming note from that.
  assert.notEqual(find("E951").note, "Safety questions raised");
});

test("origin is read from the dietary notes", () => {
  assert.equal(find("E120").origin, "animal");
  assert.equal(find("E441").origin, "animal");
  assert.equal(find("E904").origin, "animal");
  assert.equal(find("E471").origin, "either");
  assert.equal(find("E322").origin, "either");
  assert.equal(find("E440").origin, "suitable");
  assert.equal(deriveOrigin("", ""), "unknown");
});

test("groups and sensitivities", () => {
  assert.ok(find("E102").groups.includes("artificial_colours"));
  assert.ok(!find("E100").groups.includes("artificial_colours"));
  assert.ok(find("E211").groups.includes("preservatives"));
  assert.ok(find("E621").groups.includes("flavour_enhancers"));
  assert.ok(find("E951").groups.includes("artificial_sweeteners"));
  assert.ok(!find("E960").groups.includes("artificial_sweeteners"));
  assert.ok(find("E102").sensitivities.includes("children"));
});

test("daily intake gets a short form for the section header", () => {
  const intake = (code) => catalogue.detail(code).sections.find((s) => s.title === "Daily intake")?.summary;
  assert.equal(intake("E951"), "40 mg/kg");
  assert.match(intake("E102"), /mg\/kg$/);
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
