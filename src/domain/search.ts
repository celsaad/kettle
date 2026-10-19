/**
 * Name search for every search field in the app: case- and accent-insensitive, words in any order, and
 * tolerant of one typo per word. Hand-rolled rather than a dependency (Fuse.js et al.) because the rules
 * below are small enough to port verbatim to Kotlin/Swift, and `__tests__/search-cases.json` is written
 * as data so a native port can be held to the same cases.
 *
 * Each word of the query has to match some word of the name, and how well it matched is its tier:
 *   0 — it starts a name word ("squ" → "Squat")
 *   1 — it sits inside one ("quat" → "Squat")
 *   2 — it is one Damerau edit (a swap of two neighbours counts as one) from a name word or from that
 *       word's prefix, so a typo is forgiven mid-typing too ("bulgr" → "Bulgarian").
 * A name ranks by its *worst* word, so one typo drops it below every clean match.
 *
 * Typo tolerance is for alphabetic scripts only, and only from 4 characters: below that one edit
 * matches nearly everything, and in kana or kanji one edit is a quarter of a word — "スクワット" would
 * find unrelated names. Those scripts get substring matching, with hiragana folded to katakana so
 * either kana finds the other.
 */

const TYPO_MIN_LENGTH = 4;
const TYPO_SCRIPTS = /^[\p{Script=Latin}\p{Script=Cyrillic}\p{Script=Greek}\p{N}]+$/u;
// Same token rule as `slug.ts`: a word starts with a letter or digit and keeps the marks after it, so
// Indic vowel signs stay attached and punctuation ("push-up") separates words on both sides alike.
const WORD = /[\p{L}\p{N}][\p{L}\p{N}\p{M}]*/gu;

type Tier = 0 | 1 | 2;

/**
 * Strips only U+0300–U+036F rather than `\p{Diacritic}`, matching `slug.ts`: the broader class also
 * takes the Japanese voicing marks, which made タ and ダ the same letter.
 */
function normalise(value: string): string {
  const stripped = value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .normalize('NFC')
    .toLowerCase();
  // Hiragana ぁ–ゖ sit exactly 0x60 below their katakana.
  return stripped.replace(/[ぁ-ゖ]/g, (kana) => String.fromCharCode(kana.charCodeAt(0) + 0x60));
}

function wordsOf(value: string): string[] {
  return normalise(value).match(WORD) ?? [];
}

/** Optimal string alignment distance, over code points so an astral character is one edit, not two. */
function damerau(a: string[], b: string[]): number {
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
 * Prefixes one shorter and one longer than the query are tried as well as the same length: a dropped
 * or doubled letter shifts everything after it, so "bulgaian" is two edits from "bulgaria" but one from
 * "bulgarian". The whole word needs no check of its own: it is either one of those three prefixes or
 * more than one character off the query's length, which is already more than one edit.
 */
function withinOneTypo(query: string, word: string): boolean {
  const q = Array.from(query);
  const w = Array.from(word);
  for (let length = q.length - 1; length <= Math.min(q.length + 1, w.length); length++) {
    if (damerau(q, w.slice(0, length)) <= 1) return true;
  }
  return false;
}

function tierOf(token: string, words: string[]): Tier | null {
  if (words.some((word) => word.startsWith(token))) return 0;
  if (words.some((word) => word.includes(token))) return 1;
  const typoable = Array.from(token).length >= TYPO_MIN_LENGTH && TYPO_SCRIPTS.test(token);
  if (typoable && words.some((word) => withinOneTypo(token, word))) return 2;
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

/** The items whose name matches, best matches first and the original order kept within a tier. */
export function rankByName<T>(items: readonly T[], query: string, nameOf: (item: T) => string): readonly T[] {
  const tokens = wordsOf(query);
  if (tokens.length === 0) return items;
  return items
    .map((item, index) => ({ item, index, tier: matchTier(tokens, nameOf(item)) }))
    .filter((entry): entry is { item: T; index: number; tier: Tier } => entry.tier !== null)
    .toSorted((a, b) => a.tier - b.tier || a.index - b.index)
    .map((entry) => entry.item);
}

/** The same match as `rankByName`, in the original order — for a list whose order means something. */
export function filterByName<T>(items: readonly T[], query: string, nameOf: (item: T) => string): readonly T[] {
  const tokens = wordsOf(query);
  if (tokens.length === 0) return items;
  return items.filter((item) => matchTier(tokens, nameOf(item)) !== null);
}
