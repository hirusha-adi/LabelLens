// App state and the actions that change it.
//
// What the person sets up or collects (list, saved additives, scan history,
// preferences, theme) is one UserData document in this browser's localStorage. It is
// never sent anywhere. The catalogue comes from the API.

import { useSyncExternalStore } from "react";
import { fetchCatalogue } from "./api";
import { allMatches, type ScanResult } from "./matcher";
import { emptyUserData, type Additive, type CatalogueMeta, type PreferenceOptions, type Preferences, type Scan, type UserData } from "./types";
import { allergensIn, personalCheck, worstOf, type Check } from "./verdicts";

const STORAGE_KEY = "labellens_user";
const HISTORY_LIMIT = 500;

export type ScanSource = { kind: "sample" } | { kind: "photo"; url: string };

export interface Snack { id: number; text: string; undo?: () => void }

interface State {
  user: UserData;
  /** True inside the landing page's preview frame: demo content, nothing saved. */
  preview: boolean;
  catalogueStatus: "loading" | "ready" | "error";
  additives: Additive[];
  byCode: Map<string, Additive>;
  meta: CatalogueMeta | null;
  options: PreferenceOptions;
  scan: ScanResult | null;
  scanSource: ScanSource | null;
  snack: Snack | null;
}

const readUser = (): UserData => {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null");
    if (stored && typeof stored === "object") return normalise(stored);
  } catch {
    // Unreadable or blocked storage: start fresh, and keep working without saving.
  }
  return emptyUserData();
};

/** Fills gaps so a document from an older version, or from the Android app, is safe to use. */
const normalise = (input: Partial<UserData>): UserData => {
  const base = emptyUserData();
  const strings = (value: unknown) => (Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : []);
  const prefs = (input.preferences ?? {}) as Partial<Preferences>;
  return {
    formatVersion: base.formatVersion,
    onboarded: true,
    theme: input.theme === "light" || input.theme === "dark" ? input.theme : "system",
    preferences: {
      allergies: Array.isArray(prefs.allergies)
        ? prefs.allergies.filter((a) => a && typeof a.name === "string").map((a) => ({ name: a.name, level: a.level === "warn" ? "warn" : "avoid", custom: Boolean(a.custom) }))
        : [],
      diet: typeof prefs.diet === "string" ? prefs.diet : "none",
      dietLevel: prefs.dietLevel === "warn" ? "warn" : "avoid",
      groups: prefs.groups && typeof prefs.groups === "object" ? { ...prefs.groups } : {},
      additives: strings(prefs.additives),
      sensitivities: strings(prefs.sensitivities),
    },
    list: strings(input.list),
    saved: strings(input.saved),
    scans: Array.isArray(input.scans)
      ? input.scans.filter((s) => s && typeof s.id === "string" && typeof s.at === "number").map((s) => ({ id: s.id, name: s.name || "Scanned label", at: s.at, verdict: s.verdict === "avoid" || s.verdict === "caution" ? s.verdict : "safe", codes: strings(s.codes) }))
      : [],
  };
};

const preview = typeof window !== "undefined" && new URLSearchParams(window.location.search).has("preview");

let state: State = {
  user: preview ? { ...emptyUserData(), theme: "light", list: ["E102", "E211", "E621", "E330"] } : readUser(),
  preview,
  catalogueStatus: "loading",
  additives: [],
  byCode: new Map(),
  meta: null,
  options: { allergens: [], diets: [], groups: [], sensitivities: [] },
  scan: null,
  scanSource: null,
  snack: null,
};

const listeners = new Set<() => void>();
const set = (patch: Partial<State>) => {
  state = { ...state, ...patch };
  listeners.forEach((listener) => listener());
};

export const getState = () => state;
export const useApp = () => useSyncExternalStore((listener) => (listeners.add(listener), () => listeners.delete(listener)), getState);

const setUser = (patch: Partial<UserData>) => {
  const user = { ...state.user, ...patch };
  set({ user });
  if (state.preview) return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
  } catch {
    // Storage is full or blocked. The session carries on; nothing more can be done here.
  }
};

// ---------- Theme ----------

const systemDark = typeof window !== "undefined" ? window.matchMedia("(prefers-color-scheme: dark)") : null;
export const resolvedTheme = (theme = state.user.theme) => (theme === "system" ? (systemDark?.matches ? "dark" : "light") : theme);
const applyTheme = () => {
  document.documentElement.dataset.theme = resolvedTheme();
};
systemDark?.addEventListener("change", applyTheme);

export const setTheme = (theme: "light" | "dark") => {
  setUser({ theme });
  applyTheme();
};

// ---------- Catalogue ----------

let loading: Promise<void> | null = null;

export const loadCatalogue = (): Promise<void> => {
  if (loading) return loading;
  set({ catalogueStatus: state.additives.length ? "ready" : "loading" });
  loading = fetchCatalogue()
    .then((catalogue) => {
      set({ catalogueStatus: "ready", additives: catalogue.additives, byCode: new Map(catalogue.additives.map((a) => [a.code, a])), meta: catalogue.meta, options: catalogue.options });
    })
    .catch(() => {
      if (!state.additives.length) set({ catalogueStatus: "error" });
    })
    .finally(() => {
      loading = null;
    });
  return loading;
};

/** How this additive sits against the person's allergies, diet and things to avoid. */
export const checkOf = (additive: Additive): Check => personalCheck(additive, state.user.preferences, state.options);

// ---------- Snackbar ----------

let snackTimer: ReturnType<typeof setTimeout> | undefined;
export const toast = (text: string, undo?: () => void) => {
  clearTimeout(snackTimer);
  const snack = { id: Date.now(), text, undo };
  set({ snack });
  snackTimer = setTimeout(() => state.snack?.id === snack.id && set({ snack: null }), undo ? 6000 : 3500);
};
export const dismissSnack = () => set({ snack: null });

// ---------- My list ----------

export const addToList = (codes: string[]) => {
  const before = state.user.list;
  const fresh = codes.filter((code) => !before.includes(code));
  if (!fresh.length) return toast("Already in your list");
  setUser({ list: [...before, ...fresh] });
  toast(fresh.length === 1 ? `Added ${fresh[0]} to your list` : `Added ${fresh.length} to your list`, () => setUser({ list: before }));
};

export const removeFromList = (code: string) => {
  const before = state.user.list;
  setUser({ list: before.filter((c) => c !== code) });
  toast(`Removed ${code} from your list`, () => setUser({ list: before }));
};

export const clearList = () => {
  const before = state.user.list;
  setUser({ list: [] });
  toast("Cleared your list", () => setUser({ list: before }));
};

// ---------- Saved ----------

export const toggleSaved = (code: string) => {
  const before = state.user.saved;
  if (before.includes(code)) {
    setUser({ saved: before.filter((c) => c !== code) });
    toast(`Removed ${code} from Saved`, () => setUser({ saved: before }));
  } else {
    setUser({ saved: [code, ...before] });
    toast(`Saved ${code}`);
  }
};

export const saveAll = (codes: string[]) => {
  const fresh = codes.filter((code) => !state.user.saved.includes(code));
  if (!fresh.length) return toast("Already in Saved");
  setUser({ saved: [...fresh, ...state.user.saved] });
  toast(fresh.length === 1 ? `Saved ${fresh[0]}` : `Saved ${fresh.length} additives`);
};

// ---------- Scanning and history ----------

export const setScanSource = (source: ScanSource | null) => {
  const previous = state.scanSource;
  if (previous?.kind === "photo" && previous.url !== (source?.kind === "photo" ? source.url : "")) URL.revokeObjectURL(previous.url);
  set({ scanSource: source });
};

export const showResult = (scan: ScanResult) => set({ scan });

/** Shows a fresh scan and files it in History. Only the codes are kept, never the photo or the text. */
export const recordScan = (scan: ScanResult) => {
  const matches = allMatches(scan);
  const verdict = worstOf([...matches.map((m) => checkOf(m.additive).verdict), ...allergensIn(scan.text, state.user.preferences).map((c) => c.verdict)]);
  const entry: Scan = { id: `s${Date.now()}`, name: "Scanned label", at: Date.now(), verdict, codes: matches.map((m) => m.additive.code) };
  set({ scan });
  setUser({ scans: [entry, ...state.user.scans].slice(0, HISTORY_LIMIT) });
};

export const clearHistory = () => {
  const before = state.user.scans;
  if (!before.length) return toast("There's no scan history to clear");
  setUser({ scans: [] });
  toast("Scan history cleared", () => setUser({ scans: before }));
};

// ---------- Preferences and backup ----------

export const setPreferences = (preferences: Preferences) => setUser({ preferences });

/** The whole document as text, in the same format the Android app's backup uses. */
export const exportData = () => JSON.stringify(state.user, null, 2);

/** Replaces everything with the document in `text`. Returns false, changing nothing, if it is not one. */
export const importData = (text: string) => {
  try {
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return false;
    if (typeof parsed.formatVersion === "number" && parsed.formatVersion > emptyUserData().formatVersion) return false;
    if (!("list" in parsed) && !("preferences" in parsed) && !("scans" in parsed) && !("saved" in parsed)) return false;
    setUser(normalise(parsed));
    applyTheme();
    return true;
  } catch {
    return false;
  }
};
