/** Where additive information comes from. In development, /api is forwarded to the local API. */
export const API_URL = (import.meta.env.VITE_API_URL ?? "/api").replace(/\/$/, "");

/** Placeholder destinations for links that leave the site. */
const REPOSITORY = "https://github.com/hirusha-adi/LabelLens";
export const LINKS = {
  repository: REPOSITORY,
  help: `${REPOSITORY}#readme`,
  licence: `${REPOSITORY}/blob/main/LICENSE`,
  legal: `${REPOSITORY}/blob/main/LEGAL.md`,
  contributing: `${REPOSITORY}/blob/main/CONTRIBUTING.md`,
  play: import.meta.env.VITE_PLAY_URL ?? "https://play.google.com/store",
};

/** The label the "Use a sample label" option reads. The same text as the design's example. */
export const SAMPLE_LABEL =
  "INGREDIENTS: Sugar, glucose syrup, wheat flour, water, palm oil, gelatine, citric acid (E330), acidity regulator: sodium citrate, " +
  "flavourings, colours: tartrazine (E102), sunset yelow FCF, preservative: sodium benzoate (E 211), flavour enhancer: monosodium glutamate, " +
  "glazing agent: carnauba wax. May contain traces of milk and soy. Nutrition per 100 g: energy 1490 kJ / 352 kcal";
