import { load } from 'js-yaml';

import { mergeLibraries } from '@/domain/merge';
import { findProgramWeek, programWeekNumbers, resolveWorkoutForWeek } from '@/domain/program';
import { emomIntervalCount } from '@/domain/schema';
import type { Library, WorkoutBlock } from '@/domain/types';
import { fromDisplayWeight, toDisplayWeight, weightStep, type UnitSystem } from '@/domain/units';
import {
  diffBlockOverride,
  diffExerciseOverride,
  mergeBlockOverride,
  mergeExerciseOverride,
  parseLibraryYaml,
  parseSessionYaml,
  repairLibraryBounds,
  serializeLibraryYaml,
  serializeSessionYaml,
  type ParseResult,
} from '@/domain/yaml-mapping';

/**
 * Runs `conformance/` — the format's behaviour pinned as data, so a second implementation (the Kotlin
 * port in `docs/native-migration-plan.md`) can be held to exactly what this one does. Each suite is a
 * hand-written `<suite>.yaml` of inputs and a generated `<suite>.expected.json` of outputs; this test
 * fails when the TS output drifts from the committed one. `pnpm run conformance:update` rewrites the
 * goldens, and the diff it leaves is the review: a change there is a change to the format's behaviour.
 *
 * Outputs are normalised to what both implementations can promise. A refusal is its kind plus the
 * sorted refused paths, never zod's or js-yaml's wording. Absent optionals are absent, not null.
 */
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): {
  readFileSync(path: string, encoding: 'utf8'): string;
  writeFileSync(path: string, data: string): void;
};

const { readFileSync, writeFileSync } = require('node:fs');

const repoRoot = `${__dirname}/../../..`;
const updating = process.env.UPDATE_CONFORMANCE === '1';

type Case = { name: string } & Record<string, unknown>;

function readSuite(suite: string): Case[] {
  return (load(readFileSync(`${repoRoot}/conformance/${suite}.yaml`, 'utf8')) as { cases: Case[] }).cases;
}

/** What JSON can carry: drops `undefined` keys, the same way the expected files were written. */
function plain(value: unknown): unknown {
  return value === undefined ? null : JSON.parse(JSON.stringify(value));
}

function normalisedParse(result: ParseResult<unknown>): unknown {
  if (result.ok) return { ok: true, value: plain(result.data) };
  if (result.error.kind === 'invalidYaml') return { ok: false, kind: 'invalidYaml' };
  return { ok: false, kind: 'schemaMismatch', paths: [...new Set(result.error.paths)].toSorted() };
}

function mustParseLibrary(yaml: string): Library {
  const result = parseLibraryYaml(yaml);
  if (!result.ok) throw new Error(`fixture library does not parse: ${JSON.stringify(result.error)}`);
  return result.data;
}

const runners: Record<string, (testCase: Case) => unknown> = {
  library: (testCase) => {
    const text =
      typeof testCase.source === 'string'
        ? readFileSync(`${repoRoot}/${testCase.source}`, 'utf8')
        : (testCase.input as string);
    return normalisedParse(parseLibraryYaml(text));
  },

  session: (testCase) => normalisedParse(parseSessionYaml(testCase.input as string)),

  repair: (testCase) => {
    const repaired = repairLibraryBounds(testCase.input as string);
    return repaired === null ? { repaired: false } : { repaired: true, result: normalisedParse(parseLibraryYaml(repaired)) };
  },

  merge: (testCase) =>
    plain(mergeLibraries(mustParseLibrary(testCase.existing as string), mustParseLibrary(testCase.incoming as string))),

  program: (testCase) => {
    const library = mustParseLibrary(testCase.library as string);
    const queries = testCase.queries as { program: string; week: number; day?: string }[];
    return {
      weekNumbers: Object.fromEntries(library.programs.map((program) => [program.id, programWeekNumbers(program)])),
      queries: queries.map((query) => {
        const program = library.programs.find((candidate) => candidate.id === query.program)!;
        return {
          query,
          found: findProgramWeek(program, query.week, query.day) !== undefined,
          resolved: plain(resolveWorkoutForWeek(program, query.week, library, query.day)),
        };
      }),
    };
  },

  overrides: (testCase) => {
    const library = mustParseLibrary(testCase.library as string);
    const exercise = (id: unknown) => library.exercises.find((candidate) => candidate.id === id)!;
    const block = (workout: unknown, index: unknown): WorkoutBlock =>
      library.workouts.find((candidate) => candidate.id === workout)!.blocks[index as number];
    type Op = { op: string } & Record<string, unknown>;
    return (testCase.ops as Op[]).map((op) => {
      const config = op.config as Record<string, number | string>;
      switch (op.op) {
        case 'mergeExercise':
          return { op, result: plain(mergeExerciseOverride(exercise(op.exercise), config)) };
        case 'mergeBlock':
          return { op, result: plain(mergeBlockOverride(block(op.workout, op.block), config)) };
        case 'diffExercise':
          return { op, result: diffExerciseOverride(exercise(op.base), exercise(op.edited)) };
        case 'diffBlock':
          return { op, result: diffBlockOverride(block(op.workout, op.base), block(op.workout, op.edited)) };
        default:
          throw new Error(`unknown op ${op.op}`);
      }
    });
  },

  units: (testCase) => {
    const system = testCase.system as UnitSystem;
    switch (testCase.op) {
      case 'toDisplayWeight':
        return toDisplayWeight(testCase.kg as number, system);
      case 'fromDisplayWeight':
        return fromDisplayWeight(testCase.value as number, system);
      case 'weightStep':
        return weightStep(system);
      case 'emomIntervalCount':
        return emomIntervalCount(testCase.interval_sec as number, testCase.total_minutes as number);
      default:
        throw new Error(`unknown op ${String(testCase.op)}`);
    }
  },
};

describe.each(Object.keys(runners))('conformance/%s', (suite) => {
  const cases = readSuite(suite);
  const expectedPath = `${repoRoot}/conformance/${suite}.expected.json`;
  const actual = Object.fromEntries(cases.map((testCase) => [testCase.name, runners[suite](testCase)]));

  if (updating) writeFileSync(expectedPath, `${JSON.stringify(actual, null, 2)}\n`);
  const expected = JSON.parse(readFileSync(expectedPath, 'utf8')) as Record<string, unknown>;

  it('has unique case names', () => {
    expect(new Set(cases.map((testCase) => testCase.name)).size).toBe(cases.length);
  });

  it('has a golden for every case and no golden without a case', () => {
    expect(Object.keys(expected)).toEqual(cases.map((testCase) => testCase.name));
  });

  it.each(cases.map((testCase) => testCase.name))('%s', (name) => {
    expect(actual[name]).toEqual(expected[name]);
  });
});

/**
 * The goldens hold what a file *parses to*, never what the writer emits — js-yaml's layout is not
 * something a second implementation should copy. What both writers do owe is that a written file
 * reads back as the value it was written from, which is pinned here for every case that parses.
 */
describe('conformance round-trips', () => {
  const parsedCases = <T>(suite: string, parse: (text: string) => ParseResult<T>) =>
    readSuite(suite)
      .filter((testCase) => typeof testCase.input === 'string')
      .map((testCase) => ({ name: testCase.name, result: parse(testCase.input as string) }))
      .flatMap(({ name, result }) => (result.ok ? [{ name, data: result.data }] : []));

  it.each(parsedCases('library', parseLibraryYaml))('library $name survives serialize → parse', ({ data }) => {
    expect(parseLibraryYaml(serializeLibraryYaml(data))).toEqual({ ok: true, data });
  });

  it.each(parsedCases('session', parseSessionYaml))('session $name survives serialize → parse', ({ data }) => {
    expect(parseSessionYaml(serializeSessionYaml(data))).toEqual({ ok: true, data });
  });
});
