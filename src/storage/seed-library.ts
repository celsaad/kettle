import ja from '@/content/seed/ja.json';
import seedYaml from '@/content/seed/library.yaml';
import pt from '@/content/seed/pt.json';
import type { Library } from '@/domain/types';
import type { LibraryTranslation } from '@/storage/library-translation';
import { localizeLibrary, parseShippedLibrary, translationFor } from '@/storage/library-translation';

/**
 * The starter library: written to exercises.yaml the first time the app launches and no library file
 * exists yet, so the app never opens empty. It is also what a corrupt exercises.yaml is reset to, which is why it stays deliberately small.
 *
 * The content is `src/content/seed/library.yaml`, a file in the user's own format that the native app
 * reads as well. Its authoring constraints (`foundations` first, weeks in written order, every week
 * enumerated) are YAML comments at the top of that file, beside what they constrain.
 *
 * **Why a string table per language rather than one library per language:** structure is what makes
 * the seed correct — ids wire blocks to exercises, weeks resolve sparsely, overrides repeat per week —
 * and a second library would fork all of it to change the strings. A content edit changes structure
 * once, and `seed-library.test.ts` fails the language that wasn't updated with it.
 *
 * **Why not `en.json`/`pt.json`:** the seed becomes the user's own data the moment it is written.
 * Strings in the locale bundles re-render on every language change, which would rename exercises the
 * user has since edited and logged against — exactly what the never-translate-user-data rule exists to
 * prevent. The tables are read once, at seed time, and frozen.
 */

/** Keyed by language, not region, exactly as the locale bundles are: `pt` serves pt-BR and pt-PT. */
export const seedTranslations: Record<string, LibraryTranslation> = { pt, ja };

let parsed: Library | undefined;

/**
 * The seed's English structure. Parsed on first use and cached: the seed is only needed on a first
 * launch or a reseed, so a launch that finds a library never pays for it.
 */
export function seedLibrary(): Library {
  parsed ??= parseShippedLibrary(seedYaml, 'seed');
  return parsed;
}

/**
 * The seed as the user's language will read it — the single entry point anything seeding a library
 * should call. Falls back to the English structure for any language with no table.
 *
 * The language is a parameter rather than read here so it stays testable per-language; `library-file`
 * passes whatever i18next is currently on, which is the language the rest of the UI is rendering in.
 */
export function seedLibraryFor(language: string | undefined): Library {
  return localizeLibrary(seedLibrary(), translationFor(seedTranslations, language));
}
