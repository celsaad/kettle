import packList from '@/content/packs.json';
import barbellGymJa from '@/content/packs/barbell-gym/ja.json';
import barbellGymYaml from '@/content/packs/barbell-gym/library.yaml';
import barbellGymPt from '@/content/packs/barbell-gym/pt.json';
import kettlebellJa from '@/content/packs/kettlebell-basics/ja.json';
import kettlebellYaml from '@/content/packs/kettlebell-basics/library.yaml';
import kettlebellPt from '@/content/packs/kettlebell-basics/pt.json';
import steadyStrengthJa from '@/content/packs/steady-strength/ja.json';
import steadyStrengthYaml from '@/content/packs/steady-strength/library.yaml';
import steadyStrengthPt from '@/content/packs/steady-strength/pt.json';
import type { Library } from '@/domain/types';
import type { LibraryTranslation } from '@/storage/library-translation';
import { localizeLibrary, parseShippedLibrary, translationFor } from '@/storage/library-translation';

/**
 * Curated libraries the app ships **without seeding** — browsable on the import screen and merged in
 * on a tap, exactly as a hand-written file would be. Each is a directory under `src/content/packs/`
 * (`library.yaml` plus a string table per language, the seed's shape), listed in display order by
 * `src/content/packs.json`, which the native app reads too.
 *
 * **Why these aren't in the seed.** The seed is written once, on the first launch that finds
 * no `exercises.yaml`, which makes it structurally incapable of reaching a device that already has
 * the app — the population most likely to want more content. It is also the web build's entire
 * library and the target a corrupt library is reset to, so every exercise added to it enlarges the
 * thing a recovery hands back (decision log). Packs answer both: they arrive with an app update,
 * every install can take them or leave them, and the seed stays the small honest thing a reseed can
 * restore.
 *
 * **Ids are prefixed per pack, and that is the load-bearing part.** `mergeLibraries` replaces whole
 * objects by id, so an unprefixed `squat` in a pack would overwrite a `squat` the user has written
 * and logged against for months. Every id in a pack carries its prefix — the same promise
 * `site/examples/*.yaml` makes, held here by `content-packs.test.ts`.
 *
 * Each pack ships its own rest exercise rather than referencing the seed's. That looks like
 * duplication and isn't: a merge validates references against the *merged* library, so a pack
 * pointing at `rest` would be refused outright for anyone who had deleted or renamed it.
 *
 * The content bar is the seed's, unchanged: `notes` describe **the app's own progression model** —
 * how a range is beaten, what a rep counts, which number to log — and never form, injury or diet.
 * That line is what keeps the app describing its data rather than prescribing training, and it is
 * why a pack that suits older adults still says nothing about anybody's body.
 */
export type ContentPack = {
  id: string;
  /**
   * The prefix every id in `library` carries. Declared rather than inferred so the promise above is
   * a value the test can check the content against — inferring it from the content would make the
   * test agree with whatever the content happened to say, including a typo.
   */
  idPrefix: string;
  /**
   * The structural definition, in English, parsed on first read. Name and blurb are **not** here:
   * those are the import screen's own chrome, they re-render on a language change, and they live in
   * the locale bundles under `import.packs.<id>`. What a pack *writes* is frozen at merge time; what
   * the row says about it is not.
   */
  readonly library: Library;
  /**
   * Keyed by language, not region, exactly as the locale bundles are, so `pt` serves pt-BR and pt-PT.
   * A pack with no table for a language falls back to its English structure.
   */
  translations: Record<string, LibraryTranslation>;
};

/**
 * Each pack's files, by id. Metro can only bundle a file it can see a static import of, so this map is
 * written by hand; `content-packs.test.ts` holds its keys, `packs.json` and the directories under
 * `src/content/packs/` to one set, so a pack added in one place and not the others fails there.
 */
export const contentPackSources: Record<string, { yaml: string; translations: Record<string, LibraryTranslation> }> = {
  'steady-strength': { yaml: steadyStrengthYaml, translations: { pt: steadyStrengthPt, ja: steadyStrengthJa } },
  'barbell-gym': { yaml: barbellGymYaml, translations: { pt: barbellGymPt, ja: barbellGymJa } },
  'kettlebell-basics': { yaml: kettlebellYaml, translations: { pt: kettlebellPt, ja: kettlebellJa } },
};

/**
 * Every pack the app ships, in the order the import screen lists them.
 *
 * Order is display order and nothing else — unlike the seed's first program, no pack is anybody's
 * default, because a pack only exists once somebody has chosen it. Each library is parsed when first
 * read rather than here, since expo-router loads the import screen's module at launch.
 */
export const contentPacks: ContentPack[] = packList.map(({ id, idPrefix }) => {
  const source = contentPackSources[id];
  if (!source) throw new Error(`packs.json lists ${id}, which contentPackSources has no files for`);
  const { yaml, translations } = source;
  let parsed: Library | undefined;
  return {
    id,
    idPrefix,
    translations,
    get library() {
      parsed ??= parseShippedLibrary(yaml, id);
      return parsed;
    },
  };
});

/**
 * A pack's library as the user's language will read it — the single entry point anything merging a
 * pack should call.
 *
 * The same freeze applies as to the seed: the strings are picked once, at merge time, and from that
 * moment the merged content is the user's own data. Switching device language afterwards renames
 * nothing, which is exactly what the never-translate-user-data rule requires.
 */
export function contentPackLibrary(pack: ContentPack, language: string | undefined): Library {
  return localizeLibrary(pack.library, translationFor(pack.translations, language));
}
