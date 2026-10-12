// Personal checks: how an additive, or an allergen named on the label, sits against
// the person's profile. Runs in the browser. A port of the Android app's Verdicts.

import type { Additive, PreferenceOptions, Preferences, VerdictName } from "./types";

export interface Check {
  verdict: VerdictName;
  /** Short phrase such as "a colour you avoid". Null when nothing in the profile applies. */
  reason: string | null;
  /** Longer phrase for the verdict card. */
  longReason: string | null;
  /** Sentence for the additive page banner. */
  explanation: string | null;
  icon: string;
}

export interface Conflict { title: string; reason: string; verdict: VerdictName; icon: string }

export const RANK: Record<VerdictName, number> = { safe: 0, caution: 1, avoid: 2 };

const stricter = (v: VerdictName): VerdictName => (v === "safe" ? "caution" : "avoid");
const plain = (verdict: VerdictName): Check => ({ verdict, reason: null, longReason: null, explanation: null, icon: "person_alert" });

export const personalCheck = (additive: Additive, prefs: Preferences, options: PreferenceOptions): Check => {
  if (prefs.additives.includes(additive.code)) {
    return { verdict: "avoid", reason: "an additive you avoid", longReason: "an additive you always avoid", explanation: `You added ${additive.code} to the additives you avoid.`, icon: "block" };
  }
  const found: Check[] = [];
  for (const group of options.groups) {
    if (!additive.groups.includes(group.id)) continue;
    const level = prefs.groups[group.id];
    if (level === "avoid" || level === "warn") {
      found.push({
        verdict: level === "avoid" ? "avoid" : "caution",
        reason: group.shortReason,
        longReason: group.reason,
        explanation: `You set ${group.label.toLowerCase()} to "${level === "avoid" ? "Always avoid" : "Warn me"}".`,
        icon: group.icon,
      });
    }
  }
  const diet = options.diets.find((d) => d.id === prefs.diet);
  if (diet && diet.strictness === "animal") {
    const name = diet.label.toLowerCase();
    if (additive.origin === "animal") {
      found.push({ verdict: prefs.dietLevel === "warn" ? "caution" : "avoid", reason: `not ${name}`, longReason: `animal-derived, not ${name}`, explanation: `It comes from an animal source and you follow a ${name} diet.`, icon: diet.icon });
    } else if (additive.origin === "either") {
      // Origin is not always certain, so this is never stronger than Caution.
      found.push({ verdict: "caution", reason: "may be animal-derived", longReason: "can be plant or animal based", explanation: `It can be plant or animal based and you follow a ${name} diet.`, icon: diet.icon });
    }
  }
  for (const sensitivity of options.sensitivities) {
    if (prefs.sensitivities.includes(sensitivity.id) && additive.sensitivities.includes(sensitivity.id)) {
      found.push({ verdict: stricter(additive.status), reason: sensitivity.reason, longReason: sensitivity.reason, explanation: `You turned on stricter checks: ${sensitivity.label.toLowerCase()}.`, icon: sensitivity.icon });
    }
  }
  // The first of the strongest findings, as on Android.
  const worst = found.reduce<Check | null>((best, c) => (!best || RANK[c.verdict] > RANK[best.verdict] ? c : best), null);
  return worst && RANK[worst.verdict] >= RANK[additive.status] ? worst : plain(additive.status);
};

const escapeRegExp = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** Allergens from the profile that appear in the text read from the label. */
export const allergensIn = (labelText: string, prefs: Preferences): Conflict[] => {
  if (!labelText.trim()) return [];
  const text = labelText.toLowerCase();
  return prefs.allergies
    .filter((allergy) => {
      const name = allergy.name.toLowerCase();
      const stem = name.replace(/s$/, "");
      return new RegExp(`\\b${escapeRegExp(stem)}(s|es)?\\b`).test(text) || text.includes(name);
    })
    .map((allergy) => {
      const avoid = allergy.level === "avoid";
      return {
        title: allergy.name,
        reason: avoid ? "an allergen you always avoid" : "an allergen you asked to be warned about",
        verdict: avoid ? "avoid" : "caution",
        icon: avoid ? "block" : "notifications",
      };
    });
};

export const worstOf = (verdicts: VerdictName[]): VerdictName => verdicts.reduce<VerdictName>((worst, v) => (RANK[v] > RANK[worst] ? v : worst), "safe");

/** One-line summary for the general (not personal) verdict. */
export const generalSummary = (additives: Additive[]) => {
  const banned = additives.filter((a) => a.bannedIn.length > 0).length;
  const flagged = additives.filter((a) => a.status === "caution").length;
  const parts: string[] = [];
  if (banned > 0) parts.push(banned === 1 ? "1 additive is banned in some countries." : `${banned} additives are banned in some countries.`);
  if (flagged > 0) {
    // Reads on from the sentence before when there is one: "2 additives are banned... 1 needs caution."
    const subject = parts.length ? String(flagged) : flagged === 1 ? "1 additive" : `${flagged} additives`;
    parts.push(`${subject} ${flagged === 1 ? "needs" : "need"} caution.`);
  }
  return parts.length ? parts.join(" ") : "No concerns found in the additives we matched.";
};
