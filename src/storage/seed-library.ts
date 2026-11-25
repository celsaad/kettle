import seedYaml from '@/content/seed/library.yaml';
import type { Library } from '@/domain/types';
import { parseLibraryYaml } from '@/domain/yaml-mapping';

/**
 * The starter library, as `site-examples.test.ts` reads it until #352 moves that test; native writes
 * the real seed on a first launch (`PreparedLibrary.kt`). Why it is one English structure plus a
 * string table per language, and why those strings never live in the locale bundles, is the decision
 * log's seed entry.
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
