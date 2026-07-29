import { parseLibraryYaml, serializeSessionYaml } from '@/domain/yaml-mapping';

/**
 * Declared here rather than pulled in with `@types/node`. This is the only test that touches the
 * filesystem, and installing node's globals would retype `setTimeout` app-wide from `number` to
 * `NodeJS.Timeout` — a real behaviour change to every timer in the app, traded for one `readFileSync`
 * in a docs check. Node's own `fs` is available at runtime regardless; only its types are missing.
 */
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };

const { readFileSync } = require('node:fs');

/**
 * The docs are the only spec an outside author — or an assistant emitting YAML — ever reads, and they
 * are a hand-maintained second copy of `schema.ts`. They have drifted before: the product plan's file
 * format sample predated the block `type` discriminator and the rep/hold range fields, so the very
 * thing it was showing people how to write was refused on import.
 *
 * So the samples are run through the real parser rather than proofread. Anything the schema stops
 * accepting fails here, in the file that would otherwise keep telling people to write it.
 */
// Forward slashes read fine through node's fs on every platform, so this skips `path` too.
const docsDir = `${__dirname}/../../../docs`;

/**
 * Every fenced YAML block that's a complete library: `version: 1` at the top, and no `<placeholder>`
 * angle brackets (the shape sketches use those, and they aren't meant to parse).
 */
function librarySamples(file: string): string[] {
  const text = readFileSync(`${docsDir}/${file}`, 'utf8');
  const blocks = text.match(/```yaml\n([\s\S]*?)```/g) ?? [];
  return blocks
    .map((block) => block.replace(/^```yaml\n/, '').replace(/```$/, ''))
    .filter((yaml) => yaml.startsWith('version: 1') && yaml.includes('exercises:') && !yaml.includes('<'));
}

describe.each([['authoring-exercises-yaml.md'], ['exercise-tracker-product-plan.md']])('%s', (file) => {
  const samples = librarySamples(file);

  // A guard on the extraction itself: a doc reorganised so the regex matches nothing would otherwise
  // make this suite pass by testing zero samples.
  it('has a complete library sample to check', () => {
    expect(samples.length).toBeGreaterThan(0);
  });

  it.each(samples.map((yaml, index) => [index, yaml]))('sample %i parses and validates', (_index, yaml) => {
    const result = parseLibraryYaml(yaml);

    // Surfaces the schema's own complaint rather than "expected true, got false", so a failure here
    // names the field to fix in the doc.
    expect(result.ok ? null : result.error).toBeNull();
  });
});

/**
 * The session sample in the product plan is app-written rather than hand-authored, so what matters is
 * that it looks like what the app actually emits. js-yaml resolves an unquoted `2026-07-22T18:30:00Z`
 * to a Date, so `dump` quotes those timestamps on the way out — and a sample showing them bare would
 * be teaching a shape the app never writes and its own parser would reject.
 */
it('the product plan session sample quotes timestamps the way the app writes them', () => {
  const written = serializeSessionYaml({
    version: 1,
    id: '2026-07-22T18-30-00',
    workout: 'calisthenics-a',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt: '2026-07-22T18:30:00Z',
    endedAt: '2026-07-22T19:05:12Z',
    entries: [],
  });
  expect(written).toContain("started_at: '2026-07-22T18:30:00Z'");

  const plan = readFileSync(`${docsDir}/exercise-tracker-product-plan.md`, 'utf8');
  expect(plan).toContain("started_at: '2026-07-22T18:30:00Z'");
});
