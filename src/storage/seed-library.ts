import seedYaml from '@/content/seed/library.yaml';
import type { Library } from '@/domain/types';
import { parseLibraryYaml } from '@/domain/yaml-mapping';

/**
 * The starter library: written to exercises.yaml the first time the app launches and no library file
 * exists yet, so the app never opens empty. It is also what a corrupt exercises.yaml is reset to,
 * which is why it stays deliberately small.
 *
 * The content is `src/content/seed/library.yaml`, a file in the user's own format that the native app
 * reads as well. Its authoring constraints (`foundations` first, weeks in written order, every week
 * enumerated) are YAML comments at the top of that file, beside what they constrain.
 *
 * **Why a string table per language rather than one library per language:** structure is what makes
 * the seed correct — ids wire blocks to exercises, weeks resolve sparsely, overrides repeat per week —
 * and a second library would fork all of it to change the strings. A content edit changes structure
 * once, and `:app`'s `ShippedLibrariesTest` fails the language that wasn't updated with it.
 *
 * **Why not `en.json`/`pt.json`:** the seed becomes the user's own data the moment it is written.
 * Strings in the locale bundles re-render on every language change, which would rename exercises the
 * user has since edited and logged against — exactly what the never-translate-user-data rule exists to
 * prevent. The tables are read once, at seed time, and frozen.
 */

let parsed: Library | undefined;

/**
 * The seed's English structure, which `site-examples.test.ts` merges the published examples into.
 * Parsed on first use and cached. **Throws** on a seed that doesn't parse: it is a file the app
 * ships, with nothing honest to fall back to.
 */
export function seedLibrary(): Library {
  if (!parsed) {
    const result = parseLibraryYaml(seedYaml);
    if (!result.ok) throw new Error(`the shipped seed does not parse: ${result.error.detail}`);
    parsed = result.data;
  }
  return parsed;
}
