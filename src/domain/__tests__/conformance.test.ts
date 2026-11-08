import { load } from 'js-yaml';

import { mergeLibraries } from '@/domain/merge';
import { parsePreferences } from '@/domain/preferences';
import { findProgramWeek, programWeekNumbers, resolveWorkoutForWeek } from '@/domain/program';
import { emomIntervalCount } from '@/domain/schema';
import { filterByName, rankByName } from '@/domain/search';
import { parseSupporterState } from '@/domain/tip';
import type { Library, Session, WorkoutBlock } from '@/domain/types';
import { fromDisplayWeight, toDisplayWeight, weightStep, type UnitSystem } from '@/domain/units';
import { entryBest, recordKey, sessionRecords, type RecordKind } from '@/state/selectors/records';
import {
  addSetForMember,
  buildStepsForExercise,
  buildStepsWithLimits,
  dropLastSetForMember,
  setStepsForMember,
  swapExerciseForMember,
  type RunnerStep,
} from '@/hooks/session-steps';
import { upcomingOutline } from '@/hooks/session-outline';
import { localizeLibrary, translationFor, type LibraryTranslation } from '@/storage/library-translation';
import { sessionDurationMinutes, sessionEntryResult, sessionSetCount } from '@/state/selectors/session-summary';
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

/** A case's library: the repo file at `source`, or the inline YAML under `key`. */
function libraryText(testCase: Case, key: string): string {
  return typeof testCase.source === 'string'
    ? readFileSync(`${repoRoot}/${testCase.source}`, 'utf8')
    : (testCase[key] as string);
}

function mustParseSession(yaml: string): Session {
  const result = parseSessionYaml(yaml);
  if (!result.ok) throw new Error(`fixture session does not parse: ${JSON.stringify(result.error)}`);
  return result.data;
}

function mustParseLibrary(yaml: string): Library {
  const result = parseLibraryYaml(yaml);
  if (!result.ok) throw new Error(`fixture library does not parse: ${JSON.stringify(result.error)}`);
  return result.data;
}

type StepOp = { op: string; member?: string; from?: number; exercise?: string; newMember?: string; block?: number };

/** One mid-session op on a step list: a count, or the list it leaves. See the header of `steps.yaml`. */
function stepOp(library: Library, steps: RunnerStep[], op: StepOp): number | RunnerStep[] {
  const exercise = () => library.exercises.find((candidate) => candidate.id === op.exercise)!;
  switch (op.op) {
    case 'count':
      return setStepsForMember(steps, op.member!);
    case 'addSet':
      return addSetForMember(steps, op.member!);
    case 'dropLastSet':
      return dropLastSetForMember(steps, op.member!);
    case 'swap':
      return swapExerciseForMember(steps, op.member!, op.from!, exercise(), op.newMember!);
    case 'append':
      return [...steps, ...buildStepsForExercise(exercise(), op.block!, op.member!)];
    default:
      throw new Error(`unknown op ${op.op}`);
  }
}

/** A case's step list: its workout built (or an empty ad-hoc one), then its `ops` applied in order. */
function caseSteps(testCase: Case): { library: Library; built: { steps: RunnerStep[]; truncated: boolean } } {
  const library = mustParseLibrary(testCase.library as string);
  const workout =
    testCase.workout === undefined
      ? library.workouts[0]
      : library.workouts.find((candidate) => candidate.id === testCase.workout)!;
  let built = testCase.adhoc
    ? { steps: [] as RunnerStep[], truncated: false }
    : buildStepsWithLimits(workout, library.exercises);
  if (testCase.op !== 'mutate') {
    for (const op of (testCase.ops ?? []) as StepOp[]) {
      const result = stepOp(library, built.steps, op);
      if (Array.isArray(result)) built = { ...built, steps: result };
    }
  }
  return { library, built };
}

/** Every index from -1 (nothing on screen yet) to one past the end, or the case's own `at`. */
function caseIndices(testCase: Case, length: number): number[] {
  return (testCase.at as number[] | undefined) ?? Array.from({ length: length + 2 }, (_, i) => i - 1);
}

const runners: Record<string, (testCase: Case) => unknown> = {
  library: (testCase) => normalisedParse(parseLibraryYaml(libraryText(testCase, 'input'))),

  localize: (testCase) => {
    // A table is inline, or the path of a shipped `<language>.json`, read in place like `source`.
    const tables = Object.fromEntries(
      Object.entries(testCase.tables as Record<string, LibraryTranslation | string>).map(([language, table]) => [
        language,
        typeof table === 'string' ? (JSON.parse(readFileSync(`${repoRoot}/${table}`, 'utf8')) as LibraryTranslation) : table,
      ]),
    );
    const library = mustParseLibrary(libraryText(testCase, 'library'));
    return plain(localizeLibrary(library, translationFor(tables, testCase.language as string | undefined)));
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

  preferences: (testCase) => parsePreferences(testCase.input as string),

  supporter: (testCase) => parseSupporterState(testCase.input as string),

  records: (testCase) => {
    const session = () => mustParseSession(testCase.session as string);
    switch (testCase.op) {
      case 'entryBest':
        return session().entries.map((entry) => entryBest(entry));
      case 'recordKey':
        return recordKey(testCase.exercise as string, testCase.kind as RecordKind);
      case 'sessionDurationMinutes':
        return sessionDurationMinutes(session());
      case 'sessionEntryResult':
        return plain(session().entries.map((entry) => sessionEntryResult(entry)));
      case 'sessionSetCount':
        return sessionSetCount(session());
      case 'sessionRecords': {
        const library = mustParseLibrary(testCase.library as string);
        const prior = (testCase.prior as string[]).map(mustParseSession);
        return sessionRecords(session(), prior, library.exercises);
      }
      default:
        throw new Error(`unknown op ${String(testCase.op)}`);
    }
  },

  steps: (testCase) => {
    const { library, built } = caseSteps(testCase);
    switch (testCase.op) {
      case 'build':
        return testCase.brief
          ? plain({
              truncated: built.truncated,
              length: built.steps.length,
              head: built.steps.slice(0, 3),
              tail: built.steps.slice(-3),
            })
          : plain({ truncated: built.truncated, length: built.steps.length, steps: built.steps });
      case 'mutate': {
        let steps = built.steps;
        return plain(
          ((testCase.ops ?? []) as StepOp[]).map((op) => {
            const result = stepOp(library, steps, op);
            if (Array.isArray(result)) steps = result;
            return { op, result };
          }),
        );
      }
      case 'outline':
        return plain(
          caseIndices(testCase, built.steps.length).map((at) => ({ at, items: upcomingOutline(built.steps, at) })),
        );
      default:
        throw new Error(`unknown op ${String(testCase.op)}`);
    }
  },

  search: (testCase) => {
    const names = testCase.names as string[];
    const query = testCase.query as string;
    const name = (value: string) => value;
    return { ranked: rankByName(names, query, name), filtered: filterByName(names, query, name) };
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
