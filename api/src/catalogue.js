// Builds the additive catalogue from the dataset in ../data.
//
// The dataset has three parts that are merged here:
//   development/ins-summary-original.json  every additive: code, names, type, approval status
//   dist/single/<code>.json                details for about two thirds of them, in two layouts
//   dist/ins-summary.json                  the published list (same codes, used as a fallback)
//
// The dataset has no safety rating. The verdict, groups, sensitivities and origin the
// app relies on are worked out by the rules in this file. Each rule is small and named
// so it can be read, tested and changed.

import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { createHash } from "node:crypto";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export const DATA_DIR = process.env.DATA_DIR ?? join(dirname(fileURLToPath(import.meta.url)), "..", "data");

const readJson = (path) => JSON.parse(readFileSync(path, "utf8"));

// ---------- Text clean-up ----------

/** Collapses whitespace and drops sentences whose links were lost when the source was scraped. */
export const cleanText = (value) => {
  if (typeof value !== "string") return "";
  const text = value
    .replace(/[\u0080-\u009f]/g, " – ")
    .replace(/\[[^\]]{0,20}\]/g, "")
    .replace(/[ \t]+/g, " ")
    .trim();
  // Sentences that pointed at a link ("See also  and .") end in nothing once the link is gone.
  const pointsNowhere = /(\bclick|\bsee( also| the following| here)?|background file on|information on)( and)?\.?$/i;
  return text
    .split(/\n+/)
    .map((line) =>
      line
        .replace(/\s*\((see[^()]*|[^()]*\bsee\s*|\s*)\)/gi, "")
        .replace(/\s+([.,;)])/g, "$1")
        .replace(/\(\s+/g, "(")
        .split(/(?<=[.!?])\s+(?=[A-Z(])/)
        .filter((sentence) => !pointsNowhere.test(sentence.trim()))
        .join(" ")
        .trim(),
    )
    .filter(Boolean)
    .join("\n");
};

const paragraphs = (value) => cleanText(value).split("\n").filter(Boolean);

const sentenceCase = (text) => (text ? text[0].toUpperCase() + text.slice(1) : text);

// ---------- Names ----------

// Words that appear in brackets after a name but describe what it does, not what it is called.
const NOT_A_NAME = /^(preservative|acid|acidity regulator|emulsifier|antioxidant|anti-oxidant|sweetener|thickener|stabili[sz]er|colou?r|flavou?r enhancer|carrier|natural|synthetic|sodium|potassium|calcium|acidic|basic|tribasic|mono|di|no longer valid|from .+|.*\bagent)$/i;

const isUsableAlias = (value) =>
  !NOT_A_NAME.test(value) &&
  !/[()]/.test(value) &&
  !/-$|- |-\d$/.test(value) &&
  !/^\d/.test(value) &&
  (value.length >= 4 || /^[A-Z]{3}$/.test(value));

/** Pulls every "(...)" group off the end of a string: "Agar (thickener) (gelling agent)". */
const stripTrailingGroups = (text) => {
  const groups = [];
  let rest = text.trim();
  for (;;) {
    const match = rest.match(/^(.*?)\s*\(([^()]*)\)$/);
    if (!match) break;
    groups.unshift(match[2]);
    rest = match[1].trim();
  }
  return { rest, groups };
};

/** Splits "Sunset Yellow FCF (Orange Yellow S, FD&C Yellow 6)" into a name and its other names. */
export const parseNames = (raw) => {
  const text = cleanText(raw).replace(/\s+/g, " ");
  const names = [];
  const add = (value) => {
    const name = value.trim().replace(/^and\s+/i, "").replace(/\s*\(\(.*$/, "").replace(/[\s,(.;]+$/, "");
    if (name && !names.some((n) => n.toLowerCase() === name.toLowerCase())) names.push(name);
  };
  // "Sodium citrates (i) Monosodium citrate (ii) Disodium citrate": the head, then each numbered form.
  for (const part of text.split(/\s*\((?:i{1,3}|iv|v)\)\s*/)) {
    const formerly = part.match(/,?\s*formerly (?:called |known as )?(.+)$/i);
    const { rest, groups } = stripTrailingGroups(formerly ? part.slice(0, formerly.index) : part);
    // A comma followed by a space separates names. "1,4-Heptonolactone" has no space.
    // Unless what comes first is not a name by itself: "Sodium, potassium and calcium salts of fatty acids".
    const pieces = rest.split(/,\s+|\s+\/\s+/);
    if (pieces.length > 1 && !isUsableAlias(pieces[0])) add(rest);
    else pieces.forEach(add);
    if (formerly) add(formerly[1]);
    groups.flatMap((group) => group.split(/,\s+|;\s*/)).forEach(add);
  }
  const [name = "", ...rest] = names;
  return { name, aliases: rest.filter(isUsableAlias) };
};

// ---------- Category ----------

const CATEGORY_ICONS = [
  [/colou?r/i, "palette"],
  [/preservative|antibiotic/i, "inventory_2"],
  [/sweetener/i, "water_drop"],
  [/flavour|seasoning/i, "local_fire_department"],
  [/emulsifier/i, "bubble_chart"],
  [/thick|gelling|stabili/i, "grain"],
  [/glazing|coating/i, "spa"],
  [/gas|propellant/i, "labs"],
];

const iconFor = (category) => CATEGORY_ICONS.find(([pattern]) => pattern.test(category))?.[1] ?? "science";

/** "color (Lemon yellow)" becomes { category: "Colour", descriptor: "lemon yellow" }. */
export const parseType = (type, fallbackFunction) => {
  const match = cleanText(type).match(/^([^(]*)(?:\((.*)\))?/);
  let category = (match?.[1] ?? "").split(",")[0].trim();
  let descriptor = cleanText(match?.[2] ?? "").toLowerCase();
  const fn = cleanText(fallbackFunction);
  if (!category && fn) {
    const known = ["colour", "preservative", "sweetener", "emulsifier", "thickening agent", "flavour enhancer", "anti-oxidant", "acid", "stabiliser"];
    category = known.find((word) => fn.toLowerCase().includes(word)) ?? fn.split(",")[0];
  }
  category = category
    .replace(/^(food colouring|color)$/i, "colour")
    .replace(/^anti-oxidant$/i, "antioxidant")
    .replace(/^anti[- ]caking agen.*$/i, "anti-caking agent")
    .replace(/substitude/i, "substitute");
  // "acidity regulators" says nothing the category "Acidity regulator" has not said already.
  const same = (a, b) => a.toLowerCase().replace(/s$/, "") === b.toLowerCase().replace(/s$/, "");
  if (!descriptor && fn && !same(fn, category)) descriptor = fn.toLowerCase();
  return { category: sentenceCase(category) || "Additive", descriptor };
};

// ---------- Derived facts ----------

const hasAny = (text, pattern) => pattern.test(text);

/** Additive groups a person can choose to avoid. */
export const deriveGroups = ({ category, functionText, originText, dietText }) => {
  const groups = [];
  const kind = category.toLowerCase();
  const fn = functionText.toLowerCase();
  const origin = originText.toLowerCase();
  if (kind === "colour" && (/azo|synthetic/.test(fn) || /^synthetic|azo dye|coal tar/.test(origin))) groups.push("artificial_colours");
  if (kind === "sweetener" && !/natural/.test(fn) && !/^natural/.test(origin)) groups.push("artificial_sweeteners");
  if (/preservative/.test(kind) || /preservative/.test(fn)) groups.push("preservatives");
  if (/flavour enhancer/.test(kind) || /flavour enhancer/.test(fn)) groups.push("flavour_enhancers");
  if (/\bpalm\b/.test(origin) || /\bpalm\b/.test(dietText.toLowerCase())) groups.push("palm_oil");
  return groups;
};

/** Groups of people the source text singles out. */
export const deriveSensitivities = (text) => {
  const lower = text.toLowerCase();
  const found = [];
  if (hasAny(lower, /hyperactiv|\bchildren\b|\binfants?\b|\bbabies\b/)) found.push("children");
  if (hasAny(lower, /pregnan/)) found.push("pregnancy");
  if (hasAny(lower, /asthma|aspirin|salicylate|azo dye/)) found.push("asthma");
  return found;
};

/** "animal", "either" (can be plant or animal), "suitable" (fine for vegans) or "unknown". */
export const deriveOrigin = (dietText, originText) => {
  const diet = dietText.toLowerCase();
  const origin = originText.toLowerCase();
  if (/animal[^.]*(can ?not|cannot) be (fully |completely )?(excluded|ruled out)|may thus not|use of animal|animal origin can|some vegans|egg lecithin|sometimes lactose|partly also from/.test(diet) && !/generally produced from meat/.test(diet)) return "either";
  if (/(can ?not|cannot) be (used|consumed) by vegans|not suitable for vegans|avoided by[^.]*vegans|generally produced from meat/.test(diet)) return "animal";
  if (/isolated from the insects|made (of|from) animal/.test(origin)) return "animal";
  if (/all religious groups,? (vegans|vegetarians)/.test(diet)) return "suitable";
  return "unknown";
};

const CONCERNS = [
  [/hyperactiv/, "Linked to hyperactivity"],
  [/carcinogen|nitrosamine/, "Possible cancer link studied"],
  [/asthma/, "Can affect people with asthma"],
];

/**
 * The general verdict. The dataset does not rate additives, so this is rule-based:
 *   avoid    the source (or data/overrides.json) names places that ban it
 *   caution  the source reports a concern or a restriction, or we know almost nothing about it
 *   safe     nothing reported
 * A missing EU approval flag is shown on the additive's page but does not change the
 * verdict: the flag is absent for many additives that are in fact approved.
 */
export const deriveVerdict = ({ bannedIn, euApproved, hasDetail, sideEffects, characteristics }) => {
  if (bannedIn.length) {
    return { status: "avoid", note: bannedIn.length === 1 ? `Banned in ${bannedIn[0].replace(/^European Union$/, "the EU")}` : `Banned in ${bannedIn.length} countries` };
  }
  const text = `${sideEffects} ${characteristics}`.toLowerCase();
  if (/may be banned|\bis banned|not permitted|prohibited/.test(text)) return { status: "caution", note: "Restricted in some countries" };
  // "None known ..." sentences describe very high doses, not normal use.
  const reported = /^(none|no side effects)\b/.test(sideEffects.trim().toLowerCase()) ? "" : sideEffects.toLowerCase();
  const concern = CONCERNS.find(([pattern]) => pattern.test(reported));
  if (concern) return { status: "caution", note: concern[1] };
  if (!hasDetail && !euApproved) return { status: "caution", note: "Limited information" };
  return { status: "safe", note: hasDetail ? "No concerns noted" : "Approved in the EU" };
};

const shortIntake = (text) => {
  const first = text.split(/[.\n]/)[0].trim();
  if (!first) return null;
  if (/^no limit/i.test(first)) return "No limit";
  if (/^none (determined|specified)/i.test(first)) return "Not set";
  const amount = first.match(/^up to ([\d.,]+) ?(mg|g)\/kg/i);
  if (amount) return `Up to ${amount[1]} ${amount[2]}/kg`;
  return first.length <= 22 ? first : null;
};

const shortOrigin = (text) => {
  const lower = text.toLowerCase();
  if (/ferment/.test(lower)) return "Fermentation";
  if (/^natural|^nature/.test(lower)) return "Natural";
  if (/^synthetic|^semi-synthetic/.test(lower)) return sentenceCase(lower.match(/^(semi-)?synthetic/)[0]);
  return null;
};

// ---------- Build ----------

const searchLinks = (name) => {
  const q = encodeURIComponent(name);
  return [
    { label: "Wikipedia", url: `https://en.wikipedia.org/wiki/Special:Search?search=${q}` },
    { label: "EU database", url: "https://food.ec.europa.eu/food-safety/food-improvement-agents/additives/database_en" },
    { label: "PubMed", url: `https://pubmed.ncbi.nlm.nih.gov/?term=${q}` },
  ];
};

const buildOne = (code, summary, single, footnote, override = {}) => {
  const info = single?.more_info ?? {};
  const parsed = parseNames(summary?.names ?? single?.names ?? single?.name ?? code);
  const aliases = [...parsed.aliases];
  // The detail files sometimes spell the name differently ("Mono sodium glutamate").
  if (summary && single?.name) {
    const other = parseNames(single.name);
    for (const name of [other.name, ...other.aliases]) {
      const known = [parsed.name, ...aliases].some((n) => n.toLowerCase() === name.toLowerCase());
      if (!known && isUsableAlias(name)) aliases.push(name);
    }
  }
  const functionText = cleanText(single?.function ?? "");
  const { category, descriptor } = parseType(summary?.type ?? single?.type ?? "", functionText);

  const originText = cleanText(info.origin);
  const dietText = cleanText(info.dietary_restrictions);
  const sideEffects = cleanText(info.side_effects);
  const characteristics = cleanText(info.characteristics);
  const banText = cleanText(info.banned_in);
  const bannedIn = override.bannedIn ?? (banText.match(/Banned in:\s*([^.\n]+)/i)?.[1] ?? "").split(/,\s*/).map((c) => c.trim()).filter(Boolean);

  const statusFlags = (summary?.status ?? single?.status ?? "").split(/\s+/);
  const euApproved = override.euApproved ?? (statusFlags.includes("e") || /approved in the eu/i.test(banText));
  const usApproved = statusFlags.includes("u") || /approved (in the us|by the fda)/i.test(banText);

  const origin = deriveOrigin(dietText, originText);
  const hasDetail = ["side_effects", "overview", "characteristics", "uses", "products", "origin", "daily_intake", "dietary_restrictions"].some((key) => cleanText(info[key]));
  const verdict = deriveVerdict({ bannedIn, euApproved, hasDetail, sideEffects, characteristics });
  if (verdict.status === "safe" && origin === "animal") verdict.note = "Animal-derived";

  const regulatory = [];
  if (bannedIn.length) regulatory.push({ kind: "banned", text: `**Banned in:** ${bannedIn.join(", ")}.` });
  if (euApproved) regulatory.push({ kind: "approved", text: "**Approved in the EU.**" });
  else if (!bannedIn.includes("European Union")) regulatory.push({ kind: "note", text: "**No EU approval on record** in our data. That can mean it is not approved, or that the record is incomplete." });
  if (usApproved) regulatory.push({ kind: "approved", text: "**Approved in the US** by the FDA." });
  if (override.note) regulatory.push({ kind: "note", text: override.note });
  banText.split("\n").filter((line) => line && !/^(banned in:|approved (in the|by the))/i.test(line)).forEach((line) => regulatory.push({ kind: "note", text: line }));

  const section = (title, value, summaryText = null) => ({ title, summary: summaryText, paragraphs: paragraphs(value) });
  const sections = [
    section("Side effects", info.side_effects),
    section("Overview", info.overview ?? info.characteristics),
    section("Uses", info.uses ?? info.products),
    section("Origin", info.origin, shortOrigin(originText)),
    section("Daily intake", info.daily_intake, shortIntake(cleanText(info.daily_intake))),
    section("Dietary restrictions", info.dietary_restrictions),
    section("Interactions", info.interactions),
    section("Precautions", info.precautions),
    section("Other", info.other),
  ].filter((s) => s.paragraphs.length);

  const articles = info.articles && typeof info.articles === "object" ? Object.entries(info.articles) : [];
  const sources = articles.length
    ? articles.filter(([, url]) => /^https?:\/\//.test(url)).map(([label, url]) => ({ label, url }))
    : searchLinks(parsed.name);

  const shortName = aliases.find((a) => /^[A-Z]{2,5}$/.test(a)) ?? parsed.name;
  const summaryFields = {
    code,
    name: parsed.name,
    shortName,
    category,
    descriptor,
    categoryIcon: iconFor(category),
    status: verdict.status,
    note: verdict.note,
    aliases,
    groups: deriveGroups({ category, functionText, originText, dietText }),
    sensitivities: deriveSensitivities(`${sideEffects} ${characteristics} ${functionText}`),
    origin,
    bannedIn,
    hasDetail,
  };
  return { summary: summaryFields, detail: { ...summaryFields, regulatory, sections, sources, footnote } };
};

const monthLabel = (date) => date.toLocaleDateString("en-US", { month: "short", year: "numeric" });

export const loadCatalogue = (dataDir = DATA_DIR) => {
  const summaries = readJson(join(dataDir, "development", "ins-summary-original.json"));
  const singleDir = join(dataDir, "dist", "single");
  const singles = new Map();
  let newest = 0;
  for (const file of readdirSync(singleDir)) {
    if (!file.endsWith(".json")) continue;
    // The file name is the reliable code: the "code" inside is sometimes missing its E.
    singles.set(file.slice(0, -5), readJson(join(singleDir, file)));
    newest = Math.max(newest, statSync(join(singleDir, file)).mtimeMs);
  }
  // SOURCE.json records which commit of the dataset was copied in; file times only say when.
  const source = existsSync(join(dataDir, "SOURCE.json")) ? readJson(join(dataDir, "SOURCE.json")) : null;
  const updatedLabel = monthLabel(new Date(source?.date ?? (newest || Date.now())));
  const overrides = existsSync(join(dataDir, "overrides.json")) ? readJson(join(dataDir, "overrides.json")).additives ?? {} : {};
  const footnote = `Information, not medical advice. Data updated ${updatedLabel}.`;

  const byCode = new Map();
  for (const summary of summaries) byCode.set(summary.code, buildOne(summary.code, summary, singles.get(summary.code), footnote, overrides[summary.code]));
  // A few additives only exist as detail files.
  for (const [code, single] of singles) if (!byCode.has(code)) byCode.set(code, buildOne(code, null, single, footnote, overrides[code]));

  const collator = new Intl.Collator("en", { numeric: true });
  const all = [...byCode.values()].sort((a, b) => collator.compare(a.summary.code, b.summary.code));
  const additives = all.map((entry) => entry.summary);
  const version = createHash("sha1").update(JSON.stringify(additives)).digest("hex").slice(0, 12);

  return {
    meta: {
      totalCount: additives.length,
      detailedCount: additives.filter((a) => a.hasDetail).length,
      updatedLabel,
      sources: "is-it-healthy dataset, food-e",
      version,
    },
    additives,
    has: (code) => byCode.has(code),
    detail: (code) => byCode.get(code)?.detail ?? null,
    /** Finds a code whatever its letter case: "e160A" gives "E160a". */
    resolve: (code) => additives.find((a) => a.code.toLowerCase() === String(code).toLowerCase())?.code ?? null,
  };
};
