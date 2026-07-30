import { parseLibraryYaml } from '@/domain/yaml-mapping';

/**
 * The same guard `docs-samples.test.ts` puts on the markdown docs, extended to the public site.
 *
 * `site/format.html` is the spec an outside author — or an assistant asked to emit a library — reads,
 * and it is a third hand-maintained copy of `schema.ts` living in a file the test suite would
 * otherwise never open. It shipped its first draft with five of seven type tables wrong: `emom` took
 * `rounds` instead of `total_minutes`, `amrap` invented `target_rounds` and omitted the required
 * `time_cap_sec`, `timed_hold` took `hold_sec` instead of `hold_sec_min`, `rest` took `rest_sec`
 * instead of `duration_sec`, and program weeks were a `days[]` list with `overrides` as a mapping
 * rather than `week`/`workout` with `overrides` as a list. Every one of those would have been copied
 * faithfully by a reader and then refused on import — the exact failure the docs test exists to stop.
 *
 * Only `<pre data-validate="library">` blocks are checked. The other samples on the page are
 * deliberate fragments (a `workouts:` list on its own, a shape sketch with `{ ... }` placeholders)
 * that cannot parse standalone, so the marker is opt-in rather than "every code block".
 */
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };

const { readFileSync } = require('node:fs');

const PAGES = ['index.html', 'format.html'];

/**
 * The samples are syntax-highlighted, so every token sits in its own `<span>`. Stripping tags and
 * decoding entities is what turns the rendered markup back into the YAML a reader would copy —
 * which is the thing that has to parse, rather than any separate source we might keep in step.
 */
function yamlBlocksFrom(html: string): string[] {
  const blocks: string[] = [];
  const re = /<pre[^>]*\bdata-validate="library"[^>]*>([\s\S]*?)<\/pre>/g;
  let match = re.exec(html);
  while (match !== null) {
    blocks.push(
      match[1]
        .replace(/<[^>]+>/g, '')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
        .replace(/&amp;/g, '&'),
    );
    match = re.exec(html);
  }
  return blocks;
}

describe('site YAML samples', () => {
  for (const page of PAGES) {
    const html = readFileSync(`${__dirname}/../../../site/${page}`, 'utf8');
    const blocks = yamlBlocksFrom(html);

    test(`${page} has at least one validated sample`, () => {
      expect(blocks.length).toBeGreaterThan(0);
    });

    blocks.forEach((yaml, index) => {
      test(`${page} sample ${index + 1} parses as a library`, () => {
        const result = parseLibraryYaml(yaml);
        // The failure is the point of the test, so surface which field the schema refused.
        expect(result.ok ? null : JSON.stringify(result)).toBeNull();
      });
    });
  }
});
