import { foldName, nameWords } from '@/domain/slug';

/**
 * Name search for every search field in the app: case- and accent-insensitive, words in any order, and
 * tolerant of one typo per word. Hand-rolled rather than a dependency (Fuse.js et al.) so it can be
 * ported to Kotlin/Swift line for line; `conformance/search.yaml` is what a port is held to.
 *
 * Each word of the query has to match some word of the name, and how well it matched is its tier:
 *   0 — it starts a name word ("squ" → "Squat")
 *   1 — it sits inside one ("quat" → "Squat")
 *   2 — it is one edit (a swap of two neighbours counts as one) from a name word, or from the start of
 *       a longer one so a typo is forgiven mid-typing ("bulgr" → "Bulgarian").
 * A name ranks by its *worst* word, so one typo drops it below every clean match.
 *
 * Typos are forgiven only in words of Latin, Greek or Cyrillic letters, from 4 characters: below that
 * one edit matches nearly everything; in kana or kanji one edit is a quarter of a word, so "スクワット"
 * would find unrelated names; and in "2024" or "1000m" a one-digit difference is a different workout.
 * Kana get hiragana folded to katakana instead, so either finds the other.
 */

const TYPO_MIN_LENGTH = 4;
/**
 * The shortest start of a name word a typo is checked against. Without it, a 4-letter query is one
 * deletion from its own first three letters, so "bend" found "Bench" and "squz" found "Squat".
 */
const TYPO_MIN_PREFIX = 5;

type Tier = 0 | 1 | 2;

const HIRAGANA_FIRST = 0x3041;
const HIRAGANA_LAST = 0x3096;
const KATAKANA_OFFSET = 0x60;

function foldKana(value: string): string {
  let folded = '';
  for (const char of value) {
    const code = char.codePointAt(0)!;
    folded += code >= HIRAGANA_FIRST && code <= HIRAGANA_LAST ? String.fromCodePoint(code + KATAKANA_OFFSET) : char;
  }
  return folded;
}

function wordsOf(value: string): string[] {
  return nameWords(foldKana(foldName(value)));
}

/**
 * Explicit code-point ranges rather than `\p{Script=…}`: these are the lowercase, accent-stripped
 * forms `foldName` leaves (a–z, Latin-1 and Latin Extended-A/B, Greek, Cyrillic), and plain ranges
 * port as they are and can't depend on what the JS engine's regex supports.
 */
const TYPO_RANGES: readonly (readonly [number, number])[] = [
  [0x61, 0x7a],
  [0xdf, 0x24f],
  [0x370, 0x3ff],
  [0x400, 0x52f],
];

function forgivesTypos(word: string[]): boolean {
  return (
    word.length >= TYPO_MIN_LENGTH &&
    word.every((char) => {
      const code = char.codePointAt(0)!;
      return TYPO_RANGES.some(([first, last]) => code >= first && code <= last);
    })
  );
}

/**
 * Optimal string alignment distance: Levenshtein plus a swap of two neighbours as one edit, which is
 * the typo "bulgarain" is. Over code points, so an astral character is one edit, not two.
 */
function editDistance(a: string[], b: string[]): number {
  const rows = a.length + 1;
  const cols = b.length + 1;
  const d: number[][] = Array.from({ length: rows }, (_row, i) =>
    Array.from({ length: cols }, (_col, j) => (i === 0 ? j : j === 0 ? i : 0)),
  );
  for (let i = 1; i < rows; i++) {
    for (let j = 1; j < cols; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
        d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
      }
    }
  }
  return d[rows - 1][cols - 1];
}

/**
 * Starts one shorter and one longer than the query are tried as well as the same length: a dropped or
 * doubled letter shifts everything after it, so "bulgaian" is two edits from "bulgaria" but one from
 * "bulgarian".
 */
function withinOneTypo(query: string[], word: string[]): boolean {
  if (Math.abs(word.length - query.length) <= 1 && editDistance(query, word) <= 1) return true;
  const shortest = Math.max(query.length - 1, TYPO_MIN_PREFIX);
  for (let length = shortest; length <= query.length + 1 && length < word.length; length++) {
    if (editDistance(query, word.slice(0, length)) <= 1) return true;
  }
  return false;
}

function tierOf(token: string, words: string[]): Tier | null {
  if (words.some((word) => word.startsWith(token))) return 0;
  if (words.some((word) => word.includes(token))) return 1;
  const chars = Array.from(token);
  if (forgivesTypos(chars) && words.some((word) => withinOneTypo(chars, Array.from(word)))) return 2;
  return null;
}

function matchTier(tokens: string[], name: string): Tier | null {
  const words = wordsOf(name);
  let worst: Tier = 0;
  for (const token of tokens) {
    const tier = tierOf(token, words);
    if (tier === null) return null;
    if (tier > worst) worst = tier;
  }
  return worst;
}

/** Every matching item with its tier, in the original order; `null` when the query has no words. */
function matches<T>(items: readonly T[], query: string, nameOf: (item: T) => string) {
  const tokens = wordsOf(query);
  if (tokens.length === 0) return null;
  return items.flatMap((item, index) => {
    const tier = matchTier(tokens, nameOf(item));
    return tier === null ? [] : [{ item, index, tier }];
  });
}

/** The items whose name matches, best matches first and the original order kept within a tier. */
export function rankByName<T>(items: readonly T[], query: string, nameOf: (item: T) => string): readonly T[] {
  const found = matches(items, query, nameOf);
  if (found === null) return items;
  return found.toSorted((a, b) => a.tier - b.tier || a.index - b.index).map((entry) => entry.item);
}

/** The same match as `rankByName`, in the original order — for a list whose order means something. */
export function filterByName<T>(items: readonly T[], query: string, nameOf: (item: T) => string): readonly T[] {
  return matches(items, query, nameOf)?.map((entry) => entry.item) ?? items;
}
