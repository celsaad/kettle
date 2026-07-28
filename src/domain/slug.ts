/**
 * Derives a stable id from a user-typed name. Ids are the join key across the whole YAML library
 * (workout blocks → exercises, program weeks → workouts), so this must stay locale-independent: the
 * same name has to produce the same id regardless of the device's language.
 *
 * Two rules, in order. Latin diacritics are stripped rather than replaced, so `Flexão` gives `flexao`
 * instead of the `flex-o` the old ASCII-only class produced — it mangled every accented name, in a
 * language the app itself ships in. Everything that is a letter or a digit in *any* script is then
 * kept as-is, so `Приседания` and `腕立て伏せ` get real ids instead of the empty string that surfaced
 * as "could not derive an id" and made the app unusable for naming exercises in most of the world's
 * scripts.
 *
 * Transliterating non-Latin names into ASCII was the alternative. It needs a per-script table or a
 * dependency, and it buys nothing here: an id is a join key inside the user's own YAML, never a URL
 * or a filename (`schema.ts`'s `idSchema` is `min(1)`, no character class), and keeping the user's own
 * characters is what keeps a hand-edited library readable to the person editing it.
 *
 * The NFD → strip → NFC round-trip is what makes the result independent of how the text was *typed*:
 * an `ã` entered as one codepoint and the same `ã` entered as `a` + a combining tilde — which
 * keyboards and IMEs choose between freely — would otherwise be two different ids for one name.
 * Only U+0300–U+036F is stripped, not `\p{M}` at large: Indic and Thai vowel signs are combining
 * marks too, and dropping those deletes letters rather than accents (`योग` → `यग`).
 *
 * Which is why the tokens are *matched* rather than the separators replaced. A token has to begin
 * with a letter or a digit and may then carry marks, so a surviving vowel sign stays attached to its
 * consonant (a `[^...]` class would hyphenate it into `य-ग`) while a mark with no letter in front of
 * it is dropped entirely. That last part is load-bearing: an emoji carries U+FE0F, a *mark*, so
 * keeping marks unconditionally would have turned an emoji-only name into an id of invisible
 * characters instead of the '' that callers surface as "could not derive an id".
 */
export function slugify(name: string): string {
  const stripped = name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .normalize('NFC')
    .toLowerCase();
  return (stripped.match(/[\p{L}\p{N}][\p{L}\p{N}\p{M}]*/gu) ?? []).join('-');
}
