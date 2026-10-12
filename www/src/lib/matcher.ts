// Finds additive codes and names in the text read from a label. Runs in the browser.
// A port of the Android app's Matcher, kept in step with it by the shared test cases.

import type { Additive } from "./types";

export interface Match {
  additive: Additive;
  /** The text on the label that produced the match, as read. */
  readAs: string;
  /** Similarity for name matches, 0 to 100. Null for code matches. */
  percent: number | null;
}

export interface ScanResult {
  text: string;
  codeMatches: Match[];
  nameMatches: Match[];
  otherNumbers: string[];
  /** Character ranges in `text` to highlight, as [start, end) pairs. */
  highlights: Array<[number, number]>;
  /** True when rebuilt from History, where the label text is not kept. */
  fromHistory: boolean;
}

export const allMatches = (result: ScanResult) => [...result.codeMatches, ...result.nameMatches];

const CODE = /\b(?:E|INS)[\s\-.]{0,2}(\d{3,4})\s?([a-j]{1,2})?\b/gi;
const WORD = /[\p{L}\p{N}&'-]+/gu;
const NUMBER = /\b\d{3,4}\b/g;
const FUZZY_THRESHOLD = 0.88;
// Names shorter than this must match exactly: "MSG", "Agar" and "Talc" are one typo away from ordinary words.
const FUZZY_MIN_LENGTH = 8;

const levenshtein = (a: string, b: string) => {
  let previous = Array.from({ length: b.length + 1 }, (_, i) => i);
  let current = new Array<number>(b.length + 1);
  for (let i = 1; i <= a.length; i++) {
    current[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      current[j] = Math.min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost);
    }
    [previous, current] = [current, previous];
  }
  return previous[b.length];
};

const similarity = (a: string, b: string) => {
  if (a === b) return 1;
  const longest = Math.max(a.length, b.length);
  if (longest === 0 || Math.abs(a.length - b.length) > longest * (1 - FUZZY_THRESHOLD) + 1) return 0;
  return 1 - levenshtein(a, b) / longest;
};

const mergeRanges = (ranges: Array<[number, number]>) => {
  const merged: Array<[number, number]> = [];
  for (const range of [...ranges].sort((a, b) => a[0] - b[0])) {
    const last = merged[merged.length - 1];
    if (last && range[0] <= last[1]) last[1] = Math.max(last[1], range[1]);
    else merged.push([range[0], range[1]]);
  }
  return merged;
};

export const scanText = (text: string, catalogue: Additive[]): ScanResult => {
  const byCode = new Map(catalogue.map((a) => [a.code.toLowerCase(), a]));
  const codeMatches = new Map<string, Match>();
  const highlights: Array<[number, number]> = [];
  const usedNumbers = new Set<string>();

  for (const m of text.matchAll(CODE)) {
    const digits = m[1];
    const suffix = (m[2] ?? "").toLowerCase();
    const exact = suffix ? byCode.get(`e${digits}${suffix}`) : undefined;
    const additive = exact ?? byCode.get(`e${digits}`);
    if (!additive) continue;
    // Letters after the number only belong to the code when that code exists ("E160a", not "E330 is").
    const digitsEnd = m.index + m[0].indexOf(digits) + digits.length;
    const end = exact ? m.index + m[0].length : digitsEnd;
    usedNumbers.add(digits);
    highlights.push([m.index, end]);
    if (!codeMatches.has(additive.code)) codeMatches.set(additive.code, { additive, readAs: text.slice(m.index, end).trim(), percent: null });
  }

  // Every place a name could match, best first. A stretch of text is given to one additive only,
  // so "sodium citrate" is not also reported as a misread "sodium nitrate".
  const words = [...text.matchAll(WORD)].map((m) => ({ start: m.index, end: m.index + m[0].length }));
  const lower = text.toLowerCase();
  type Candidate = { additive: Additive; start: number; end: number; fragment: string; score: number };
  const candidates: Candidate[] = [];
  for (const additive of catalogue) {
    if (codeMatches.has(additive.code)) continue;
    let best: Candidate | null = null;
    for (const name of new Set([additive.name, additive.shortName, ...additive.aliases])) {
      const target = name.toLowerCase();
      const size = target.match(WORD)?.length ?? 0;
      if (size === 0 || words.length < size) continue;
      for (let i = 0; i + size <= words.length; i++) {
        const start = words[i].start;
        const end = words[i + size - 1].end;
        const score = similarity(lower.slice(start, end), target);
        const enough = target.length < FUZZY_MIN_LENGTH ? score === 1 : score >= FUZZY_THRESHOLD;
        if (enough && (!best || score > best.score || (score === best.score && end - start > best.end - best.start))) {
          best = { additive, start, end, fragment: text.slice(start, end), score };
        }
      }
    }
    if (best) candidates.push(best);
  }
  const taken = highlights.map(([a, b]) => [a, b] as [number, number]);
  const nameMatches: Array<Match & { at: number }> = [];
  for (const c of candidates.sort((a, b) => b.score - a.score || b.fragment.length - a.fragment.length)) {
    if (taken.some(([a, b]) => a < c.end && c.start < b)) continue;
    taken.push([c.start, c.end]);
    highlights.push([c.start, c.end]);
    nameMatches.push({ additive: c.additive, readAs: c.fragment, percent: Math.floor(c.score * 100), at: c.start });
  }

  const otherNumbers = [...new Set([...text.matchAll(NUMBER)].map((m) => m[0]).filter((n) => !usedNumbers.has(n)))].slice(0, 6);
  return {
    text,
    codeMatches: [...codeMatches.values()],
    // In the order they appear on the label.
    nameMatches: nameMatches.sort((a, b) => a.at - b.at).map(({ at: _at, ...match }) => match),
    otherNumbers,
    highlights: mergeRanges(highlights),
    fromHistory: false,
  };
};

/** A result for a scan loaded from History, where only the codes were kept. */
export const resultFromCodes = (codes: string[], catalogue: Additive[]): ScanResult => {
  const byCode = new Map(catalogue.map((a) => [a.code, a]));
  return {
    text: "",
    codeMatches: codes.flatMap((code) => (byCode.has(code) ? [{ additive: byCode.get(code)!, readAs: code, percent: null }] : [])),
    nameMatches: [],
    otherNumbers: [],
    highlights: [],
    fromHistory: true,
  };
};

/** Search: codes ignore spaces and dashes, and "INS 330" finds E330. */
export const searchAdditives = (query: string, catalogue: Additive[]): Additive[] => {
  const q = query.trim().toLowerCase();
  if (!q) return catalogue;
  const compact = q.replace(/[\s\-.]/g, "");
  const asCode = compact.replace(/^ins/, "e");
  return catalogue.filter((a) => {
    const code = a.code.toLowerCase();
    return (
      code.includes(asCode) ||
      code.slice(1).startsWith(compact) ||
      a.name.toLowerCase().includes(q) ||
      a.category.toLowerCase().includes(q) ||
      a.descriptor.toLowerCase().includes(q) ||
      a.aliases.some((alias) => alias.toLowerCase().includes(q))
    );
  });
};
