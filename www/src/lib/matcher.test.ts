// The same cases as the Android app's MatcherTest and RealCatalogueTest, so the two
// implementations cannot drift apart unnoticed.

import { readFileSync } from "node:fs";
import { describe, expect, test } from "vitest";
import { allMatches, scanText, searchAdditives } from "./matcher";
import type { Additive, Catalogue, PreferenceOptions, Preferences } from "./types";
import { allergensIn, generalSummary, personalCheck } from "./verdicts";

const make = (code: string, name: string, extra: Partial<Additive> = {}): Additive => ({
  code, name, shortName: name, category: "", descriptor: "", categoryIcon: "science", status: "safe", note: "",
  aliases: [], groups: [], sensitivities: [], origin: "unknown", bannedIn: [], hasDetail: true, ...extra,
});

const small = [
  make("E102", "Tartrazine", { status: "avoid", groups: ["artificial_colours"], sensitivities: ["children"] }),
  make("E110", "Sunset yellow FCF", { status: "avoid" }),
  make("E211", "Sodium benzoate", { status: "caution", sensitivities: ["children"] }),
  make("E330", "Citric acid"),
  make("E331", "Sodium citrates", { aliases: ["Sodium citrate"] }),
  make("E441", "Gelatine", { origin: "animal" }),
  make("E621", "Monosodium glutamate", { shortName: "MSG", status: "caution", aliases: ["MSG"] }),
  make("E160a", "Beta-carotene"),
];

const label =
  "INGREDIENTS: Sugar, glucose syrup, wheat flour, water, palm oil, citric acid (E330), " +
  "colours: tartrazine (E102), sunset yelow FCF, preservative: sodium benzoate (E 211), " +
  "flavour enhancer: monosodium glutamate. May contain traces of milk and soy. " +
  "Nutrition per 100 g: energy 1490 kJ / 352 kcal";

describe("matcher", () => {
  test("finds codes however they are spaced", () => {
    const result = scanText(label, small);
    expect(result.codeMatches.map((m) => m.additive.code)).toEqual(["E330", "E102", "E211"]);
    expect(result.codeMatches.at(-1)!.readAs).toBe("E 211");
  });

  test("finds names, including misread ones", () => {
    const byCode = new Map(scanText(label, small).nameMatches.map((m) => [m.additive.code, m]));
    expect([...byCode.keys()].sort()).toEqual(["E110", "E621"]);
    expect(byCode.get("E621")!.percent).toBe(100);
    expect(byCode.get("E110")!.readAs).toBe("sunset yelow FCF");
    expect(byCode.get("E110")!.percent).toBeGreaterThanOrEqual(85);
    expect(byCode.get("E110")!.percent).toBeLessThan(100);
  });

  test("reports numbers that are not codes, and highlights what matched", () => {
    const result = scanText(label, small);
    expect(result.otherNumbers).toEqual(["100", "1490", "352"]);
    expect(result.highlights.map(([a, b]) => label.slice(a, b))).toEqual(["E330", "E102", "sunset yelow FCF", "E 211", "monosodium glutamate"]);
  });

  test("code with a letter suffix, and the INS prefix", () => {
    expect(scanText("colour (E160a), acid INS 330", small).codeMatches.map((m) => m.additive.code)).toEqual(["E160a", "E330"]);
    // Letters after the number are not part of the code unless that code exists.
    expect(scanText("E330 is sour", small).codeMatches[0].readAs).toBe("E330");
  });

  test("plain text matches nothing", () => {
    expect(allMatches(scanText("Oats, water, salt.", small))).toEqual([]);
  });

  test("search ignores spacing and understands INS", () => {
    expect(searchAdditives("ins 330", small).map((a) => a.code)).toEqual(["E330"]);
    expect(searchAdditives("e-330", small).map((a) => a.code)).toEqual(["E330"]);
    expect(searchAdditives("msg", small).map((a) => a.code)).toEqual(["E621"]);
    expect(searchAdditives("E9999", small)).toEqual([]);
    expect(searchAdditives("  ", small)).toHaveLength(small.length);
  });
});

describe("personal checks", () => {
  const options: PreferenceOptions = {
    allergens: [],
    diets: [{ id: "vegan", label: "Vegan", icon: "eco", strictness: "animal" }],
    groups: [{ id: "artificial_colours", label: "Artificial colours", icon: "palette", reason: "a colour you always avoid", shortReason: "a colour you avoid" }],
    sensitivities: [{ id: "children", label: "Children", description: "", icon: "child_care", reason: "stricter for children" }],
  };
  const prefs: Preferences = {
    allergies: [{ name: "Milk", level: "warn", custom: false }, { name: "Peanuts", level: "avoid", custom: false }],
    diet: "vegan", dietLevel: "avoid", groups: { artificial_colours: "avoid" }, additives: [], sensitivities: ["children"],
  };
  const check = (code: string) => personalCheck(small.find((a) => a.code === code)!, prefs, options);

  test("follow the profile", () => {
    expect(check("E102")).toMatchObject({ verdict: "avoid", reason: "a colour you avoid" });
    expect(check("E211")).toMatchObject({ verdict: "avoid", reason: "stricter for children" });
    expect(check("E441").verdict).toBe("avoid");
    expect(check("E330")).toMatchObject({ verdict: "safe", reason: null });
    expect(allergensIn(label, prefs).map((c) => c.title)).toEqual(["Milk"]);
  });

  test("general summary counts bans and cautions", () => {
    expect(generalSummary([make("E1", "a", { bannedIn: ["X"], status: "avoid" }), make("E2", "b", { status: "caution" }), make("E3", "c")])).toBe("1 additive is banned in some countries. 1 needs caution.");
    expect(generalSummary([make("E2", "b", { status: "caution" }), make("E4", "d", { status: "caution" })])).toBe("2 additives need caution.");
    expect(generalSummary([make("E3", "c")])).toBe("No concerns found in the additives we matched.");
  });
});

describe("against the full catalogue", () => {
  // The copy of the catalogue that ships in the Android app is the same data the API serves.
  const catalogue = (JSON.parse(readFileSync(new URL("../../../android/app/src/main/assets/catalogue.json", import.meta.url), "utf8")) as Catalogue).additives;
  const codes = (text: string) => allMatches(scanText(text, catalogue)).map((m) => m.additive.code).sort();

  test("plain foods match nothing", () => {
    const text =
      "INGREDIENTS: Whole grain oats, sugar, wheat flour, water, salt, palm oil, natural flavourings, skimmed milk powder, " +
      "cocoa butter, yeast, barley malt extract, sunflower oil, dried egg, vinegar, tomato puree, onion powder, garlic, spices, " +
      "raising agent, contains sodium and potassium. Packaged in a protective atmosphere. Store in a cool dry place. " +
      "Nutrition per 100 g: energy 1490 kJ, fat 12 g of which saturates 3 g, carbohydrate 60 g, protein 8 g, basic recipe, natural colour";
    expect(codes(text)).toEqual([]);
  });

  test("a realistic label", () => {
    const text =
      "Ingredients: Sugar, glucose syrup, water, gelatine, citric acid, acidity regulator: sodium citrate, " +
      "colours: tartrazine, sunset yelow FCF (E110), preservative: sodium benzoate (E 211), potassium sorbate, " +
      "flavour enhancer: monosodium glutamate, glazing agent: carnauba wax, emulsifier: soy lecithin, antioxidant: ascorbic acid, " +
      "thickener: xanthan gum, sweetener: aspartame, carbon dioxide, E160a, INS 330.";
    const found = codes(text);
    for (const code of ["E102", "E110", "E211", "E202", "E621", "E903", "E300", "E415", "E951", "E290", "E160a", "E330", "E331", "E441"]) expect(found).toContain(code);
    // "sodium citrate" must not also be read as a misspelt "sodium nitrate".
    expect(found).not.toContain("E251");
    expect(found.length).toBeLessThanOrEqual(16);
  });
});
