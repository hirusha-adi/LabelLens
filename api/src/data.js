// Hardcoded sample data. Nothing here is authoritative: it exists so the app
// has something realistic to render. Replace with a real data source later.

export const catalogueMeta = {
  totalCount: 510,
  updatedLabel: "May 2026",
  syncedLabel: "3 days ago",
  sources: "EFSA, FDA, JECFA, Codex",
};

const sourcesFor = (code, name) => {
  const q = encodeURIComponent(name);
  return [
    { label: "Wikipedia", url: `https://en.wikipedia.org/wiki/Special:Search?search=${q}` },
    { label: "FAO / JECFA", url: `https://apps.who.int/food-additives-contaminants-jecfa-database/Home/Search?q=${q}` },
    { label: "EU database", url: "https://food.ec.europa.eu/food-safety/food-improvement-agents/additives/database_en" },
    { label: "PubMed", url: `https://pubmed.ncbi.nlm.nih.gov/?term=${q}` },
  ];
};

// status: the general verdict, "safe" | "caution" | "avoid".
// groups / sensitivities / origin feed the personal checks done on the phone.
const additive = (code, name, category, descriptor, icon, status, note, extra = {}) => ({
  code,
  name,
  shortName: extra.shortName ?? name,
  category,
  descriptor,
  categoryIcon: icon,
  status,
  note,
  aliases: extra.aliases ?? [],
  groups: extra.groups ?? [],
  sensitivities: extra.sensitivities ?? [],
  origin: extra.origin ?? "synthetic",
  bannedIn: extra.bannedIn ?? [],
  detail: {
    approval:
      extra.approval ??
      (status === "safe"
        ? "**Approved** in every region we track, including by the FDA."
        : "**Approved by the FDA** in the United States (your region)."),
    sideEffects: extra.sideEffects ?? ["None at normal levels."],
    overview: extra.overview ?? [`${name} is used in food as a ${category.toLowerCase()}.`],
    uses: extra.uses ?? ["Found in a range of packaged foods and drinks."],
    originLabel: extra.originLabel ?? "Synthetic",
    originText: extra.originText ?? ["Made by chemical synthesis."],
    adi: extra.adi ?? "Not specified",
    adiText: extra.adiText ?? ["The acceptable daily intake is set by JECFA and reviewed by regional agencies."],
  },
});

export const additives = [
  additive("E100", "Curcumin", "Colour", "turmeric yellow", "palette", "safe", "No concerns", {
    aliases: ["Turmeric extract", "CI 75300", "Natural Yellow 3"],
    origin: "plant", originLabel: "Plant", originText: ["Extracted from the turmeric root."],
    adi: "0–3 mg/kg", uses: ["Mustard, curry powders, dairy desserts and pickles."],
  }),
  additive("E101", "Riboflavin", "Colour", "vitamin B2", "palette", "safe", "No concerns", {
    aliases: ["Vitamin B2", "Lactoflavin"],
    origin: "either", originLabel: "Fermentation", originText: ["Usually made by fermentation. Can be produced from plant or animal sources."],
    adi: "No limit", uses: ["Breakfast cereals, sauces and energy drinks."],
  }),
  additive("E102", "Tartrazine", "Colour", "lemon yellow", "palette", "avoid", "Banned in 4 countries", {
    aliases: ["FD&C Yellow 5", "CI 19140", "Acid Yellow 23"],
    groups: ["artificial_colours"], sensitivities: ["children", "asthma"],
    bannedIn: ["Austria", "Germany", "Norway", "United Arab Emirates"],
    approval: "**Approved by the FDA** in the United States (your region). Must be named on the label.",
    sideEffects: [
      "Linked to hyperactivity in children in combination with benzoates. The 2007 Southampton study led the EU to require a warning label on foods that contain it.",
      "Rare allergic reactions such as hives and asthma have been reported, more often in people who are sensitive to aspirin.",
    ],
    overview: ["A synthetic lemon-yellow azo dye. One of the most widely used food colours in the world."],
    uses: ["Sweets, soft drinks, instant puddings, crisps, sauces and some medicines."],
    adi: "0–10 mg/kg",
  }),
  additive("E104", "Quinoline yellow", "Colour", "greenish yellow", "palette", "caution", "Limited evidence", {
    aliases: ["CI 47005", "D&C Yellow 10"],
    groups: ["artificial_colours"], sensitivities: ["children"],
    bannedIn: ["United States", "Japan", "Norway"],
    approval: "**Not approved** for food by the FDA in the United States (your region).",
    sideEffects: ["One of the six colours in the Southampton study. Foods in the EU must carry a warning about activity and attention in children."],
    adi: "0–3 mg/kg",
  }),
  additive("E110", "Sunset yellow FCF", "Colour", "orange yellow", "palette", "avoid", "Banned in 2 countries", {
    aliases: ["FD&C Yellow 6", "CI 15985", "Orange Yellow S"],
    groups: ["artificial_colours"], sensitivities: ["children", "asthma"],
    bannedIn: ["Norway", "Finland"],
    sideEffects: ["Linked to hyperactivity in children in the Southampton study. Can trigger reactions in people who are sensitive to aspirin."],
    adi: "0–4 mg/kg", uses: ["Orange squash, jelly, marzipan, apricot jam and packet soups."],
  }),
  additive("E120", "Carmine", "Colour", "crimson red", "palette", "caution", "Animal-derived", {
    aliases: ["Cochineal", "Carminic acid", "Natural Red 4", "CI 75470"],
    origin: "animal", originLabel: "Insects", originText: ["Made from crushed cochineal insects. Not suitable for vegans or vegetarians."],
    sideEffects: ["Can cause allergic reactions, including rare cases of anaphylaxis."],
    adi: "0–5 mg/kg",
  }),
  additive("E129", "Allura red AC", "Colour", "red", "palette", "caution", "Limited evidence", {
    aliases: ["FD&C Red 40", "CI 16035"],
    groups: ["artificial_colours"], sensitivities: ["children"],
    sideEffects: ["One of the six colours in the Southampton study. Requires a warning label in the EU."],
    adi: "0–7 mg/kg",
  }),
  additive("E133", "Brilliant blue FCF", "Colour", "blue", "palette", "safe", "No concerns", {
    aliases: ["FD&C Blue 1", "CI 42090"], groups: ["artificial_colours"], adi: "0–6 mg/kg",
  }),
  additive("E160a", "Beta-carotene", "Colour", "yellow-orange", "palette", "safe", "No concerns", {
    aliases: ["Carotenes", "Provitamin A", "CI 40800"],
    origin: "plant", originLabel: "Plant", originText: ["Extracted from carrots, palm fruit or algae, or made by synthesis."],
    adi: "0–5 mg/kg",
  }),
  additive("E161b", "Lutein", "Colour", "yellow", "palette", "safe", "No concerns", {
    aliases: ["Xanthophyll"], origin: "plant", originLabel: "Plant", originText: ["Extracted from marigold petals."],
    adi: "0–1 mg/kg",
  }),
  additive("E171", "Titanium dioxide", "Colour", "banned in the EU", "palette", "avoid", "Banned in the EU", {
    aliases: ["CI 77891", "Titania"],
    groups: ["artificial_colours"],
    bannedIn: ["European Union", "Switzerland"],
    sideEffects: ["EFSA could not rule out genotoxicity in 2021, which led to the EU ban in 2022."],
    originLabel: "Mineral", originText: ["Refined from mineral ores."], adi: "Withdrawn",
    uses: ["Chewing gum, sweets, icing and white sauces."],
  }),
  additive("E202", "Potassium sorbate", "Preservative", "mould inhibitor", "inventory_2", "safe", "No concerns", {
    aliases: ["Sorbic acid potassium salt"], groups: ["preservatives"], adi: "0–11 mg/kg",
  }),
  additive("E211", "Sodium benzoate", "Preservative", "stricter for children", "inventory_2", "caution", "Limited evidence", {
    aliases: ["Benzoate of soda"],
    groups: ["preservatives"], sensitivities: ["children", "asthma"],
    sideEffects: [
      "Linked to hyperactivity in children when combined with certain colours.",
      "Can form small amounts of benzene in drinks that also contain vitamin C.",
    ],
    adi: "0–5 mg/kg", uses: ["Soft drinks, pickles, sauces and fruit juices."],
  }),
  additive("E220", "Sulphur dioxide", "Preservative", "sulphite", "inventory_2", "caution", "Allergen", {
    aliases: ["Sulfur dioxide", "Sulphites", "Sulfites"],
    groups: ["preservatives"], sensitivities: ["asthma"],
    sideEffects: ["Can trigger asthma symptoms. Must be declared as an allergen above 10 mg/kg."],
    adi: "0–0.7 mg/kg", uses: ["Dried fruit, wine, cider and sausages."],
  }),
  additive("E250", "Sodium nitrite", "Preservative", "curing salt", "inventory_2", "caution", "Limited evidence", {
    groups: ["preservatives"], sensitivities: ["children", "pregnancy"],
    sideEffects: ["Can form nitrosamines when cooked at high heat. Processed meat is classified as carcinogenic by the IARC."],
    adi: "0–0.07 mg/kg", uses: ["Bacon, ham, salami and other cured meats."],
  }),
  additive("E300", "Ascorbic acid", "Antioxidant", "vitamin C", "science", "safe", "No concerns", {
    aliases: ["Vitamin C", "L-ascorbic acid"], originLabel: "Fermentation",
    originText: ["Made from glucose by fermentation."], adi: "No limit",
  }),
  additive("E322", "Lecithins", "Emulsifier", "soy or sunflower", "bubble_chart", "safe", "No concerns", {
    aliases: ["Lecithin", "Soy lecithin", "Sunflower lecithin"],
    origin: "either", originLabel: "Plant or egg", originText: ["Usually from soy or sunflower. Sometimes from egg yolk."],
    adi: "No limit",
  }),
  additive("E330", "Citric acid", "Acidity regulator", "acidity regulator", "science", "safe", "No concerns", {
    aliases: ["INS 330", "2-hydroxypropane-1,2,3-tricarboxylic acid"],
    sideEffects: ["None at normal levels; rare intolerance reported."],
    originLabel: "Fermentation", originText: ["Made by fermenting sugars with the mould Aspergillus niger."],
    adi: "No limit", adiText: ["JECFA has not set a limit because intake from food is not a concern."],
    overview: ["The acid that makes citrus fruit sour. One of the most common additives."],
    uses: ["Soft drinks, sweets, jams, tinned fruit and cheese."],
  }),
  additive("E331", "Sodium citrates", "Acidity regulator", "buffer", "science", "safe", "No concerns", {
    aliases: ["Sodium citrate", "Trisodium citrate"], originLabel: "Fermentation", adi: "No limit",
  }),
  additive("E415", "Xanthan gum", "Thickener", "stabiliser", "grain", "safe", "No concerns", {
    originLabel: "Fermentation", originText: ["Made by bacterial fermentation of sugars."], adi: "No limit",
  }),
  additive("E441", "Gelatine", "Gelling agent", "animal-derived", "labs", "safe", "Animal-derived", {
    aliases: ["Gelatin"],
    origin: "animal", originLabel: "Animal", originText: ["Made from the skin and bones of pigs, cattle or fish."],
    adi: "No limit",
  }),
  additive("E621", "Monosodium glutamate", "Flavour enhancer", "flavour enhancer", "local_fire_department", "caution", "Limited evidence", {
    shortName: "MSG",
    aliases: ["MSG", "Sodium glutamate", "INS 621"],
    groups: ["flavour_enhancers"],
    sideEffects: ["Some people report headaches or flushing after large amounts. Controlled studies have not confirmed a consistent effect."],
    originLabel: "Fermentation", originText: ["Made by fermenting starch or sugar."],
    adi: "0–30 mg/kg", uses: ["Crisps, stock cubes, instant noodles and ready meals."],
  }),
  additive("E903", "Carnauba wax", "Glazing agent", "plant wax", "spa", "safe", "No concerns", {
    origin: "plant", originLabel: "Plant", originText: ["From the leaves of the carnauba palm."], adi: "0–7 mg/kg",
  }),
  additive("E950", "Acesulfame K", "Sweetener", "calorie-free", "water_drop", "safe", "No concerns", {
    aliases: ["Acesulfame potassium", "Ace-K"], groups: ["artificial_sweeteners"], adi: "0–15 mg/kg",
  }),
  additive("E951", "Aspartame", "Sweetener", "status updated Jul 2026", "water_drop", "caution", "Limited evidence", {
    aliases: ["NutraSweet", "Canderel"],
    groups: ["artificial_sweeteners"], sensitivities: ["pregnancy"],
    sideEffects: [
      "Classified as possibly carcinogenic to humans (Group 2B) by the IARC in 2023. JECFA kept the daily intake unchanged.",
      "Not suitable for people with phenylketonuria (PKU).",
    ],
    adi: "0–40 mg/kg", uses: ["Diet soft drinks, sugar-free gum and tabletop sweeteners."],
  }),
  additive("E954", "Saccharin", "Sweetener", "calorie-free", "water_drop", "safe", "No concerns", {
    aliases: ["Sodium saccharin"], groups: ["artificial_sweeteners"], adi: "0–5 mg/kg",
  }),
];

export const summaryOf = ({ detail, ...summary }) => summary;

export const detailOf = (a) => {
  const d = a.detail;
  const regulatory = [];
  if (a.bannedIn.length) regulatory.push({ kind: "banned", text: `**Banned in:** ${a.bannedIn.join(", ")}.` });
  regulatory.push({ kind: d.approval.startsWith("**Not") ? "banned" : "approved", text: d.approval });
  return {
    ...summaryOf(a),
    regulatory,
    sections: [
      { title: "Side effects", summary: null, paragraphs: d.sideEffects },
      { title: "Overview", summary: null, paragraphs: d.overview },
      { title: "Uses", summary: null, paragraphs: d.uses },
      { title: "Origin", summary: d.originLabel, paragraphs: d.originText },
      { title: "Daily intake", summary: d.adi, paragraphs: d.adiText },
    ],
    sources: sourcesFor(a.code, a.name),
    footnote: `Information, not medical advice. Updated ${catalogueMeta.updatedLabel}.`,
  };
};

export const preferenceOptions = {
  allergens: [
    "Peanuts", "Tree nuts", "Milk", "Gluten", "Eggs", "Soy", "Fish", "Shellfish",
    "Sesame", "Celery", "Mustard", "Lupin", "Sulphites", "Molluscs",
  ],
  diets: [
    { id: "vegan", label: "Vegan", icon: "eco", strictness: "animal" },
    { id: "vegetarian", label: "Vegetarian", icon: "egg_alt", strictness: "animal" },
    { id: "pescatarian", label: "Pescatarian", icon: "set_meal", strictness: "animal" },
    { id: "halal", label: "Halal", icon: "mosque", strictness: "animal" },
    { id: "kosher", label: "Kosher", icon: "synagogue", strictness: "animal" },
    { id: "none", label: "No specific diet", icon: "restaurant", strictness: "none" },
  ],
  groups: [
    { id: "artificial_colours", label: "Artificial colours", icon: "palette", reason: "a colour you always avoid", shortReason: "a colour you avoid" },
    { id: "artificial_sweeteners", label: "Artificial sweeteners", icon: "water_drop", reason: "a sweetener you flagged", shortReason: "a sweetener you flagged" },
    { id: "preservatives", label: "Preservatives", icon: "inventory_2", reason: "a preservative you flagged", shortReason: "a preservative you flagged" },
    { id: "flavour_enhancers", label: "Flavour enhancers", icon: "local_fire_department", reason: "a flavour enhancer you flagged", shortReason: "flavour enhancer" },
    { id: "palm_oil", label: "Palm oil", icon: "forest", reason: "palm oil, which you flagged", shortReason: "palm oil" },
  ],
  sensitivities: [
    { id: "children", label: "Children", description: "Flags colours linked to hyperactivity, and caffeine", icon: "child_care", reason: "stricter for children" },
    { id: "pregnancy", label: "Pregnant or breastfeeding", description: "Flags additives with pregnancy guidance", icon: "pregnant_woman", reason: "stricter during pregnancy" },
    { id: "asthma", label: "Asthma or aspirin-sensitive", description: "Flags sulphites, benzoates and azo dyes", icon: "pulmonology", reason: "stricter for asthma" },
  ],
};

export const demoPreferences = () => ({
  allergies: [
    { name: "Peanuts", level: "avoid", custom: false },
    { name: "Tree nuts", level: "avoid", custom: false },
    { name: "Milk", level: "warn", custom: false },
  ],
  diet: "vegan",
  groups: {
    artificial_colours: "avoid",
    artificial_sweeteners: "warn",
    preservatives: "off",
    flavour_enhancers: "warn",
    palm_oil: "off",
  },
  additives: ["E951", "E171"],
  sensitivities: ["children"],
});

export const emptyPreferences = () => ({
  allergies: [],
  diet: "none",
  groups: {},
  additives: [],
  sensitivities: [],
});

export const plans = {
  trialDays: 7,
  trialEndsLabel: "Oct 16",
  yearlySavePercent: 37,
  storeNote: "Cancel any time in Google Play.",
  plans: [
    {
      id: "plus",
      name: "Plus",
      tagline: "7-day free trial",
      recommended: true,
      features: ["Unlimited scans", "Personal allergy and diet alerts", "Scan history and favourites"],
      yearly: { price: "$29.99", per: "/yr", was: "$47.88", note: "$2.50/mo", full: "$29.99/year" },
      monthly: { price: "$3.99", per: "/mo", was: null, note: "Billed monthly", full: "$3.99/month" },
    },
    {
      id: "family",
      name: "Family",
      tagline: "Plus for up to 5 profiles",
      recommended: false,
      features: [],
      yearly: { price: "$44.99", per: "/yr", was: null, note: "$3.75/mo", full: "$44.99/year" },
      monthly: { price: "$5.99", per: "/mo", was: null, note: "Billed monthly", full: "$5.99/month" },
    },
    {
      id: "free",
      name: "Free",
      tagline: "3 scans a day, search, basic info",
      recommended: false,
      features: [],
      yearly: null,
      monthly: null,
    },
  ],
  comparison: [
    { name: "Scans per day", free: "3", plus: "∞", family: "∞" },
    { name: "Search 510 additives", free: "✓", plus: "✓", family: "✓" },
    { name: "Basic additive info", free: "✓", plus: "✓", family: "✓" },
    { name: "Bans by region", free: "✓", plus: "✓", family: "✓" },
    { name: "Personal allergy alerts", free: "—", plus: "✓", family: "✓" },
    { name: "Diet checks", free: "—", plus: "✓", family: "✓" },
    { name: "Product verdict for you", free: "—", plus: "✓", family: "✓" },
    { name: "Scan history", free: "—", plus: "✓", family: "Shared" },
    { name: "Favourites + change alerts", free: "—", plus: "✓", family: "✓" },
    { name: "Profiles", free: "1", plus: "1", family: "Up to 5" },
  ],
  comparisonNote:
    "✓ included · — not included. Prices are placeholders and come from Google Play Billing (ProductDetails), so they localise automatically.",
};

export const demoHistory = () => [
  { id: "h1", day: "Today", name: "Fruit gummies", time: "4:12 PM", verdict: "avoid", codes: ["E102", "E211", "E330", "E621", "E110"] },
  { id: "h2", day: "Today", name: "Sparkling lemonade", time: "11:03 AM", verdict: "caution", codes: ["E330", "E211", "E950"] },
  { id: "h3", day: "Yesterday", name: "Oat crackers", time: "6:40 PM", verdict: "safe", codes: ["E322"] },
  { id: "h4", day: "Yesterday", name: "Instant noodles", time: "1:15 PM", verdict: "caution", codes: ["E621", "E330", "E331", "E415", "E160a", "E300"] },
  { id: "h5", day: "Yesterday", name: "Greek yogurt", time: "9:02 AM", verdict: "safe", codes: [] },
];

export const demoFamily = () => ({
  max: 5,
  profiles: [
    { id: "sam", name: "Sam (you)", role: "admin", initials: "SR", icon: null, tone: "primary", short: "Vegan · 3 allergies", summary: "Vegan · peanuts, tree nuts, milk", selected: true },
    { id: "alex", name: "Alex", role: null, initials: "AL", icon: null, tone: "secondary", short: "Gluten · Halal", summary: "Halal · gluten", selected: false },
    { id: "mia", name: "Mia, 6", role: null, initials: null, icon: "child_care", tone: "tertiary", short: "Peanuts · child", summary: "Peanuts · stricter for children", selected: true },
  ],
  note: "Shared history: every family member sees scans made with Family. Grown-ups can invite others by email; children's profiles don't need an account.",
});

export const regions = {
  language: "English (US) · follows system",
  codeStyle: "E-numbers (E330)",
  selected: "us",
  regions: [
    { id: "us", label: "United States · FDA" },
    { id: "ca", label: "Canada · Health Canada" },
    { id: "eu", label: "European Union · EFSA" },
    { id: "uk", label: "United Kingdom · FSA" },
    { id: "anz", label: "Australia / NZ · FSANZ" },
    { id: "jp", label: "Japan · MHLW" },
    { id: "in", label: "India · FSSAI" },
  ],
};

export const demoAccount = {
  email: "sam.rivera@gmail.com",
  password: "labellens",
  name: "Sam Rivera",
};
