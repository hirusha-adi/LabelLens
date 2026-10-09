// Product configuration: what the app offers, as opposed to what the dataset says.
// Prices are placeholders until Google Play Billing supplies them.

export const FREE_SCANS_PER_DAY = 3;
export const TRIAL_DAYS = 7;

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
    { id: "flavour_enhancers", label: "Flavour enhancers", icon: "local_fire_department", reason: "a flavour enhancer you flagged", shortReason: "a flavour enhancer you flagged" },
    { id: "palm_oil", label: "Palm oil", icon: "forest", reason: "made with palm oil, which you flagged", shortReason: "made with palm oil" },
  ],
  sensitivities: [
    { id: "children", label: "Children", description: "Flags additives linked to hyperactivity or restricted for infants", icon: "child_care", reason: "stricter for children" },
    { id: "pregnancy", label: "Pregnant or breastfeeding", description: "Flags additives with pregnancy guidance", icon: "pregnant_woman", reason: "stricter during pregnancy" },
    { id: "asthma", label: "Asthma or aspirin-sensitive", description: "Flags sulphites, benzoates and azo dyes", icon: "pulmonology", reason: "stricter for asthma" },
  ],
};

const PRICES = {
  plus: {
    yearly: { price: "$29.99", per: "/yr", was: "$47.88", note: "$2.50/mo", full: "$29.99/year" },
    monthly: { price: "$3.99", per: "/mo", was: null, note: "Billed monthly", full: "$3.99/month" },
  },
  family: {
    yearly: { price: "$44.99", per: "/yr", was: null, note: "$3.75/mo", full: "$44.99/year" },
    monthly: { price: "$5.99", per: "/mo", was: null, note: "Billed monthly", full: "$5.99/month" },
  },
};

export const priceOf = (planId, period) => PRICES[planId]?.[period] ?? null;

export const planName = (planId) => ({ plus: "Plus", family: "Family", free: "Free" })[planId] ?? planId;

const shortDate = (date) => date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
export const longDate = (date) => date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
export { shortDate };

export const addDays = (date, days) => new Date(date.getTime() + days * 86_400_000);

/** Plans as shown on the plans screen. The trial end date and the additive count are filled in per request. */
export const buildPlans = ({ now = new Date(), additiveCount }) => ({
  trialDays: TRIAL_DAYS,
  trialEndsLabel: shortDate(addDays(now, TRIAL_DAYS)),
  yearlySavePercent: 37,
  storeNote: "Cancel any time in Google Play.",
  plans: [
    {
      id: "plus",
      name: "Plus",
      tagline: `${TRIAL_DAYS}-day free trial`,
      recommended: true,
      features: ["Unlimited scans", "Personal allergy and diet alerts", "Scan history and favourites"],
      ...PRICES.plus,
    },
    { id: "family", name: "Family", tagline: "Plus for up to 5 profiles", recommended: false, features: [], ...PRICES.family },
    { id: "free", name: "Free", tagline: `${FREE_SCANS_PER_DAY} scans a day, search, basic info`, recommended: false, features: [], yearly: null, monthly: null },
  ],
  comparison: [
    { name: "Scans per day", free: String(FREE_SCANS_PER_DAY), plus: "∞", family: "∞" },
    { name: `Search ${additiveCount} additives`, free: "✓", plus: "✓", family: "✓" },
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
});

export const regions = {
  language: "English (US) · follows system",
  codeStyle: "E-numbers (E330)",
  defaultRegion: "us",
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

export const emptyPreferences = () => ({ allergies: [], diet: "none", dietLevel: "avoid", groups: {}, additives: [], sensitivities: [] });

export const FAMILY_MAX = 5;
export const FAMILY_NOTE =
  "Shared history: every family member sees scans made with Family. Grown-ups can invite others by email; children's profiles don't need an account.";
