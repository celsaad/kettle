import { z } from 'zod';

import { rawExerciseSchema } from '@/domain/schema';

/**
 * Same reasoning as `docs-samples.test.ts`: node's globals are declared locally rather than pulled in
 * with `@types/node`, which would retype `setTimeout` app-wide for the sake of a `readFileSync` in a
 * docs check.
 */
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };

const { readFileSync } = require('node:fs');

const repoRoot = `${__dirname}/../../..`;

/**
 * `schema.ts` has three hand-maintained mirrors — `docs/authoring-exercises-yaml.md`,
 * `docs/product-plan.md` §4, and `site/format.html` — and the samples tests only cover the *samples*
 * on those pages. The field tables around them were checked by eye, which failed the first time it
 * mattered: the published reference shipped with five of seven type tables wrong (`emom` taking
 * `rounds` instead of `total_minutes`, `amrap` missing the required `time_cap_sec`), every bit of it
 * plausible enough to read as correct and every bit of it refused on import.
 *
 * So the tables are parsed and diffed against the schema instead. This pins field *names* and whether
 * each is optional — the two things a reader copies literally. Ranges, units and prose are still on
 * the author.
 */
type FieldSpec = { readonly name: string; readonly optional: boolean };

/** Sorted so a failure prints a stable, readable diff rather than a hash-order one. */
function sortFields(fields: FieldSpec[]): FieldSpec[] {
  // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
  // oxlint-disable-next-line unicorn/no-array-sort
  return [...fields].sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * Derived from the schema through `toJSONSchema` rather than by reading `.shape`, because that's the
 * projection `assistant-brief.ts` already hands to an assistant — so this test and the brief can't
 * disagree about what the format is.
 */
function schemaFields(): Map<string, FieldSpec[]> {
  // Through `unknown`: zod types the result as a general JSON Schema, which has no idea this
  // particular one is a discriminated union of objects. The shape asserted here is what the probe
  // actually prints, and `documents every exercise type` below is what catches it if that changes.
  const json = z.toJSONSchema(rawExerciseSchema, { io: 'input' }) as unknown as {
    oneOf: {
      properties: {
        type: { const: string };
        config: { properties?: Record<string, unknown>; required?: string[] };
      };
    }[];
  };

  return new Map(
    json.oneOf.map((option) => {
      const { config } = option.properties;
      const required = new Set(config.required ?? []);
      const fields = Object.keys(config.properties ?? {}).map((name) => ({ name, optional: !required.has(name) }));
      return [option.properties.type.const, sortFields(fields)];
    }),
  );
}

const expected = schemaFields();
const types = [...expected.keys()];

/**
 * The markdown mirror is one row per type: `` | `emom` | `interval_sec` (>0), … | notes | ``. Fields
 * are read from the second cell only — the notes cell also backticks field names, and a field there
 * is prose rather than a claim about the format.
 *
 * A field is `` `name` `` followed by its parenthesised constraints, which is also what keeps the
 * `` ≥ `target_reps_min` `` inside another field's constraints from being read as a field of its own.
 */
function markdownFields(): Map<string, FieldSpec[]> {
  const text = readFileSync(`${repoRoot}/docs/authoring-exercises-yaml.md`, 'utf8');
  const found = new Map<string, FieldSpec[]>();

  for (const line of text.split('\n')) {
    const cells = line.split('|');
    if (cells.length < 4) continue;

    const type = cells[1].trim().replaceAll('`', '');
    if (!expected.has(type) || found.has(type)) continue;

    const fields = [...cells[2].matchAll(/`([a-z_]+)`\s*\(([^)]*)\)/g)].map((match) => ({
      name: match[1],
      optional: match[2].includes('optional'),
    }));
    found.set(type, sortFields(fields));
  }

  return found;
}

/**
 * The site mirror is one `<h3>type</h3>` per type followed by a table whose first column is the field
 * name. Only the first `<code>` in a row is the field — the meaning column cites other fields by name,
 * same trap as the notes cell above.
 *
 * Each section is cut at its first `</table>`. Without that the last type on the page absorbs every
 * table below it (circuits, programs) and reports their columns as its own config — which is how this
 * parser first failed, and quietly enough to have been mistaken for a documentation bug.
 */
function siteFields(): Map<string, FieldSpec[]> {
  const html = readFileSync(`${repoRoot}/site/format.html`, 'utf8');
  const found = new Map<string, FieldSpec[]>();

  for (const section of html.split(/<h3>/).slice(1)) {
    const type = section.slice(0, section.indexOf('</h3>')).trim();
    if (!expected.has(type) || found.has(type)) continue;

    const table = section.slice(section.indexOf('<table'), section.indexOf('</table>'));
    if (!table.includes('<th>config field</th>')) continue;

    const fields = [...table.matchAll(/<tr>([\s\S]*?)<\/tr>/g)]
      .map((row) => row[1])
      .flatMap((row) => {
        const cells = [...row.matchAll(/<td>([\s\S]*?)<\/td>/g)].map((cell) => cell[1]);
        const name = cells[0]?.match(/<code>([a-z_]+)<\/code>/)?.[1];
        return name === undefined ? [] : [{ name, optional: (cells[1] ?? '').includes('optional') }];
      });
    found.set(type, sortFields(fields));
  }

  return found;
}

describe.each([
  ['docs/authoring-exercises-yaml.md', markdownFields],
  ['site/format.html', siteFields],
])('%s mirrors schema.ts', (_file, parse) => {
  const actual = parse();

  // A guard on the extraction itself, in the same spirit as `docs-samples.test.ts`: a page
  // reorganised so the parser matches nothing would otherwise make every assertion below vacuous.
  it('documents every exercise type', () => {
    const documented = [...actual.keys()];
    const all = [...types];
    // Sorts copies — the spreads are the copies oxlint can't see through (decision log: no `toSorted`).
    // oxlint-disable-next-line unicorn/no-array-sort
    expect(documented.sort()).toEqual(all.sort());
  });

  it.each(types)('%s config fields match the schema', (type) => {
    expect(actual.get(type)).toEqual(expected.get(type));
  });
});
