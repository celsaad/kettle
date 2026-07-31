import { mergeLibraries } from '@/domain/merge';
import { parseLibraryYaml } from '@/domain/yaml-mapping';
import { seedLibrary } from '@/storage/seed-library';

/**
 * `site/examples/*.yaml` are complete libraries published for readers to import as they are — the
 * one thing on the site that isn't prose about the format but the format itself, handed over whole.
 * Nothing in `src/` imports them, so without this they'd be four hand-written files that only a
 * user's failed import would ever check.
 *
 * Three things are asserted, in the order they'd bite someone:
 *
 * 1. Each file parses. Same guard as `docs-samples`/`site-samples`, on files that are pure YAML.
 * 2. Each file imports. `parseLibraryYaml` validates shape but not references — a typo'd
 *    `exercise: bfb-pushups` parses fine and is only refused when `mergeLibraries` validates the
 *    merged whole, which is exactly what the import screen does.
 * 3. No id in any example collides with the seed library or with another example. Every example
 *    prefixes its ids (`bfb-`, `ppl-`, `bw-`, `cnd-`) so that importing one can never silently
 *    overwrite an exercise the reader already has — the examples page states that as a promise, and
 *    merge-by-id would quietly break it the moment a file used a bare `rest` or `pushups`.
 */
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): {
  readFileSync(path: string, encoding: 'utf8'): string;
  readdirSync(path: string): string[];
};

const { readFileSync, readdirSync } = require('node:fs');

const siteDir = `${__dirname}/../../../site`;
const files = readdirSync(`${siteDir}/examples`)
  .filter((name) => name.endsWith('.yaml'))
  .toSorted();

describe('site example libraries', () => {
  it('finds the published examples', () => {
    expect(files.length).toBeGreaterThan(0);
  });

  describe.each(files)('%s', (file) => {
    const yaml = readFileSync(`${siteDir}/examples/${file}`, 'utf8');
    const parsed = parseLibraryYaml(yaml);

    it('parses as a library', () => {
      // The failure is the point of the test, so surface which field the schema refused.
      expect(parsed.ok ? null : JSON.stringify(parsed)).toBeNull();
    });

    it('imports into a library that already has the seed content', () => {
      if (!parsed.ok) throw new Error('did not parse');
      const merged = mergeLibraries(seedLibrary, parsed.data);
      expect(merged.ok ? null : JSON.stringify(merged)).toBeNull();
    });

    it('replaces nothing that was already there', () => {
      if (!parsed.ok) throw new Error('did not parse');
      const merged = mergeLibraries(seedLibrary, parsed.data);
      if (!merged.ok) throw new Error('did not merge');
      expect({
        exercises: merged.summary.updatedExercises,
        workouts: merged.summary.updatedWorkouts,
        programs: merged.summary.updatedPrograms,
      }).toEqual({ exercises: [], workouts: [], programs: [] });
    });
  });

  it('keeps the examples from colliding with each other', () => {
    let library = seedLibrary;
    for (const file of files) {
      const parsed = parseLibraryYaml(readFileSync(`${siteDir}/examples/${file}`, 'utf8'));
      if (!parsed.ok) throw new Error(`${file} did not parse`);
      const merged = mergeLibraries(library, parsed.data);
      if (!merged.ok) throw new Error(`${file} did not merge`);
      expect({
        file,
        replaced: [...merged.summary.updatedExercises, ...merged.summary.updatedWorkouts, ...merged.summary.updatedPrograms],
      }).toEqual({ file, replaced: [] });
      library = merged.library;
    }
  });

  /**
   * The page and the directory are maintained by hand and in different files: a fifth example added
   * without a card is published but unreachable, and a card pointing at a renamed file is a 404 on
   * both its buttons.
   */
  it('is exactly what examples.html offers', () => {
    const html = readFileSync(`${siteDir}/examples.html`, 'utf8');
    const referenced = new Set((html.match(/examples\/[\w-]+\.yaml/g) ?? []).map((path) => path.replace('examples/', '')));
    expect([...referenced].toSorted()).toEqual(files);
  });
});
