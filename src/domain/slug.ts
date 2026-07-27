/**
 * Derives a stable id from a user-typed name. Ids are the join key across the whole YAML library
 * (workout blocks → exercises, program weeks → workouts), so this must stay locale-independent: the
 * same name has to produce the same id regardless of the device's language.
 *
 * Previously copy-pasted into four screens. Known limitation, deliberately unchanged here so the
 * dedup stays behaviour-preserving: a name with no ASCII letters or digits — most non-Latin scripts —
 * slugifies to an empty string, which callers surface as "could not derive an id". That makes the app
 * effectively unusable for naming exercises in those scripts and needs a real fix (transliteration, or
 * falling back to a generated id) rather than a wider character class.
 */
export function slugify(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '');
}
