// Builds the additive catalogue from the dataset in ../data.
//
//   development/ins-summary-original.json  the master list: code, names, type, approval flags
//   dist/single/<code>.json                one researched entry per additive
//   overrides.json                         optional corrections on top
//
// Each entry carries its own approval flags, an alert icon where a regulator has
// raised a concern, and a list of places that ban it. The verdict the app shows is
// read from those. Groups, sensitivities and origin are still worked out from the
// text by the small rules in this file.

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
    .replace(/^unclassified$/i, "")
    .replace(/^semi-synthetic emulsifiers$/i, "emulsifier")
    .replace(/substitude/i, "substitute");
  // "acidity regulators" says nothing the category "Acidity regulator" has not said already.
  const same = (a, b) => a.toLowerCase().replace(/s$/, "") === b.toLowerCase().replace(/s$/, "");
  if (!descriptor && fn && !same(fn, category)) descriptor = fn.toLowerCase();
  return { category: sentenceCase(category) || "Additive", descriptor };
};

// ---------- Derived facts ----------

const hasAny = (text, pattern) => pattern.test(text);

/** Additive groups a person can choose to avoid. */
export const deriveGroups = ({ category, description, dietText }) => {
  const groups = [];
  const kind = category.toLowerCase();
  const text = description.toLowerCase();
  if (kind === "colour" && /\bsynthetic|\bazo\b|azo dye|coal tar|diazotis/.test(text)) groups.push("artificial_colours");
  if (kind === "sweetener" && !/plant[- ]derived|extract|sugar alcohol|polyol|occurs naturally|naturally occurring/.test(text)) groups.push("artificial_sweeteners");
  if (/preservative/.test(kind)) groups.push("preservatives");
  if (/flavour enhancer/.test(kind)) groups.push("flavour_enhancers");
  if (/\bpalm\b/.test(text) || /\bpalm\b/.test(dietText.toLowerCase())) groups.push("palm_oil");
  return groups;
};

/** Groups of people the source text singles out. */
export const deriveSensitivities = (text) => {
  const lower = text.toLowerCase();
  const found = [];
  if (/hyperactiv|\bchildren|\binfants?\b|\btoddlers?\b|\bbabies\b/.test(lower)) found.push("children");
  if (/pregnan|breastfeed|lactati/.test(lower)) found.push("pregnancy");
  if (/asthma|aspirin|salicylate|sulphite|sulfite|intolerance reaction/.test(lower)) found.push("asthma");
  return found;
};

/** "animal", "either" (can be plant or animal), "suitable" (fine for vegans) or "unknown". */
export const deriveOrigin = (dietText, originText) => {
  const diet = dietText.toLowerCase();
  const origin = originText.toLowerCase();
  // The source could be plant or animal and the entry says it cannot tell which.
  if (/both animal and vegetable|not specified as exclusively plant|requires supplier information|suitability cannot be established|a microbial source/.test(diet)) return "either";
  if (/unsuitable for vegan|not suitable for vegan|is not vegan|animal[- ]derived/.test(diet)) return "animal";
  if (/^animal[- ]derived|animal (collagen|tissues)|from (dried )?(female )?(cochineal )?insects/.test(origin)) return "animal";
  if (/compatible with (vegan|vegetarian)|plant derived/.test(diet)) return "suitable";
  return "unknown";
};

// Checked in order against the entry's regulatory and side-effect text. The patterns
// are narrow on purpose: "did not identify a genotoxicity concern" must not match.
const CONCERNS = [
  [/warning about possible effects on children/, "Warning label required in the EU"],
  [/authorisation was withdrawn|specifications? (were |was )?withdrawn|adi withdrawn/, "Approval withdrawn in places"],
  [/could not rule out|cannot rule out|could not exclude/, "Safety questions raised"],
  [/(?<!no )(allergic|intolerance) reactions/, "Can cause reactions in some people"],
  [/(?<!not )(?<!n't )\bexceed(ed|s)\b/, "Intake can exceed the limit"],
  [/data gap|gaps prevented|unresolved safety/, "Safety data incomplete"],
];

/** Member states listed one by one in the data are shown as "the EU" in the short note. */
const banNote = (bannedIn) => {
  if (bannedIn.length >= 20 && ["Germany", "France", "Italy", "Spain"].every((c) => bannedIn.includes(c))) return "Banned in the EU";
  if (bannedIn.length === 1) return `Banned in ${bannedIn[0].replace(/^(United States|European Union)$/, "the $1").replace("the European Union", "the EU")}`;
  return `Banned in ${bannedIn.length} countries`;
};

/**
 * The general verdict, read from what the entry records:
 *   avoid    it names places that ban the additive
 *   caution  it is flagged with the alert icon (a regulator raised a concern),
 *            or no EU or US approval could be confirmed
 *   safe     approved somewhere and nothing flagged
 * The short note says which. It is a summary of the entry, not a medical rating.
 */
export const deriveVerdict = ({ bannedIn, alert, approved, text }) => {
  if (bannedIn.length) return { status: "avoid", note: banNote(bannedIn) };
  if (alert) {
    const lower = text.toLowerCase();
    return { status: "caution", note: CONCERNS.find(([pattern]) => pattern.test(lower))?.[1] ?? "Regulator concern noted" };
  }
  if (!approved) return { status: "caution", note: "No EU or US approval confirmed" };
  return { status: "safe", note: "No concerns noted" };
};

const shortIntake = (text) => {
  const first = text.split("\n")[0].trim();
  if (!first) return null;
  if (/^(no limit|adi classified as not limited|no numerical adi needed)/i.test(first)) return "No limit";
  if (/^(none (determined|specified)|no adi (specified|allocated))/i.test(first)) return "Not set";
  if (/^adi withdrawn/i.test(first)) return "Withdrawn";
  const amount = first.match(/^(?:up to )?([\d.,]+(?:\s?[–-]\s?[\d.,]+)?) ?(mg|g|µg)\b[^/]{0,40}\/kg/i);
  if (amount) return `${amount[1].replace(/\s?-\s?/, "–")} ${amount[2]}/kg`;
  return first.length <= 22 ? first : null;
};

const shortOrigin = (text) => {
  const lower = text.toLowerCase();
  if (/^animal[- ]derived/.test(lower)) return "Animal";
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
  const parsed = parseNames(single?.names ?? summary?.names ?? single?.name ?? code);
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
  const { category, descriptor } = parseType(single?.type ?? summary?.type ?? "", functionText);

  const originText = cleanText(info.origin);
  const dietText = cleanText(info.dietary_restrictions);
  const sideEffects = cleanText(info.side_effects);
  const overview = cleanText(info.overview ?? info.characteristics);
  const banText = cleanText(info.banned_in);
  const bannedIn = override.bannedIn ?? (banText.match(/Banned in:\s*([^.\n]+)/i)?.[1] ?? "").split(/,\s*/).map((c) => c.trim()).filter(Boolean);

  const statusFlags = (single?.status ?? summary?.status ?? "").split(/\s+/);
  const euApproved = override.euApproved ?? (statusFlags.includes("e") || /^approved in the eu/im.test(banText));
  const usApproved = statusFlags.includes("u") || /^approved (in the us|by the fda)/im.test(banText);

  const origin = deriveOrigin(dietText, originText);
  const hasDetail = Boolean(overview || sideEffects || originText || cleanText(info.uses ?? info.products) || cleanText(info.daily_intake));
  const concernText = `${banText} ${sideEffects} ${cleanText(info.precautions)}`;
  const verdict = deriveVerdict({ bannedIn, alert: single?.icon === "mdi-alert-circle", approved: euApproved || usApproved, text: concernText });
  if (verdict.status === "safe" && origin === "animal") verdict.note = "Animal-derived";

  const regulatory = [];
  if (bannedIn.length) regulatory.push({ kind: "banned", text: `**Banned in:** ${bannedIn.join(", ")}.` });
  if (euApproved) regulatory.push({ kind: "approved", text: "**Approved in the EU.**" });
  if (usApproved) regulatory.push({ kind: "approved", text: "**Approved in the US** by the FDA." });
  if (override.note) regulatory.push({ kind: "note", text: override.note });
  [...new Set(banText.split("\n"))].filter((line) => line && !/^(banned in:|approved (in the|by the))/i.test(line)).forEach((line) => regulatory.push({ kind: "note", text: line }));

  const section = (title, value, summaryText = null) => ({ title, summary: summaryText, paragraphs: paragraphs(value) });
  const sections = [
    section("Overview", info.overview ?? info.characteristics),
    section("Side effects", info.side_effects),
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
    groups: deriveGroups({ category, description: `${overview} ${originText}`, dietText }),
    sensitivities: deriveSensitivities(concernText),
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
      sources: "EFSA, FDA, JECFA, EU additives database",
      version,
    },
    additives,
    has: (code) => byCode.has(code),
    detail: (code) => byCode.get(code)?.detail ?? null,
    /** Finds a code whatever its letter case: "e160A" gives "E160a". */
    resolve: (code) => additives.find((a) => a.code.toLowerCase() === String(code).toLowerCase())?.code ?? null,
  };
};
