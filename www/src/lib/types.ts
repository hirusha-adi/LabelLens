// The shapes of what the API returns and of what is kept in the browser.
// UserData matches the Android app's saved document, so a backup made on a phone
// restores here and the other way round.

export type VerdictName = "safe" | "caution" | "avoid";

export interface CatalogueMeta {
  totalCount: number;
  detailedCount: number;
  updatedLabel: string;
  sources: string;
  version: string;
}

export interface Additive {
  code: string;
  name: string;
  shortName: string;
  category: string;
  descriptor: string;
  categoryIcon: string;
  status: VerdictName;
  note: string;
  aliases: string[];
  groups: string[];
  sensitivities: string[];
  origin: "animal" | "either" | "suitable" | "unknown";
  bannedIn: string[];
  hasDetail: boolean;
}

export interface AdditiveDetail extends Additive {
  regulatory: Array<{ kind: "banned" | "approved" | "note"; text: string }>;
  sections: Array<{ title: string; summary: string | null; paragraphs: string[] }>;
  sources: Array<{ label: string; url: string }>;
  footnote: string;
}

export interface DietOption { id: string; label: string; icon: string; strictness: string }
export interface GroupOption { id: string; label: string; icon: string; reason: string; shortReason: string }
export interface SensitivityOption { id: string; label: string; description: string; icon: string; reason: string }

export interface PreferenceOptions {
  allergens: string[];
  diets: DietOption[];
  groups: GroupOption[];
  sensitivities: SensitivityOption[];
}

export interface Catalogue {
  meta: CatalogueMeta;
  options: PreferenceOptions;
  additives: Additive[];
}

export type Level = "avoid" | "warn";

export interface Allergy { name: string; level: Level; custom: boolean }

export interface Preferences {
  allergies: Allergy[];
  diet: string;
  dietLevel: Level;
  groups: Record<string, Level | "off">;
  additives: string[];
  sensitivities: string[];
}

export interface Scan {
  id: string;
  name: string;
  /** Epoch milliseconds. */
  at: number;
  verdict: VerdictName;
  codes: string[];
}

export interface UserData {
  formatVersion: number;
  onboarded: boolean;
  theme: "system" | "light" | "dark";
  preferences: Preferences;
  list: string[];
  saved: string[];
  scans: Scan[];
}

export const emptyPreferences = (): Preferences => ({ allergies: [], diet: "none", dietLevel: "avoid", groups: {}, additives: [], sensitivities: [] });

export const emptyUserData = (): UserData => ({ formatVersion: 1, onboarded: true, theme: "system", preferences: emptyPreferences(), list: [], saved: [], scans: [] });

export const preferencesAreEmpty = (p: Preferences) =>
  p.allergies.length === 0 && p.diet === "none" && Object.values(p.groups).every((level) => level === "off") && p.additives.length === 0 && p.sensitivities.length === 0;

/** "Colour · lemon yellow", or just the category when the descriptor adds nothing. */
export const categoryLine = (a: Additive) => (!a.descriptor || a.descriptor.toLowerCase() === a.category.toLowerCase() ? a.category : `${a.category} · ${a.descriptor}`);
