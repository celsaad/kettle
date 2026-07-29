import { mergeLibraries } from '@/domain/merge';
import { programWeekNumbers } from '@/domain/program';
import { parseLibraryYaml, serializeLibraryYaml } from '@/domain/yaml-mapping';
import { nextWeekAfter } from '@/state/selectors';
import { seedLibrary } from '@/storage/seed-library';
import type { Library, Program, Session } from '@/domain/types';

/**
 * The seed is the one library that is written without ever passing through the import path, so
 * nothing else checks it. These pin the invariants a content edit can break silently — each one
 * fails in the app rather than at the point of editing, which is what makes them worth a test.
 */

const emptyLibrary: Library = { version: 1, exercises: [], workouts: [], programs: [] };

it('round-trips through the YAML layer unchanged', () => {
  // loadLibrary reseeds whenever exercises.yaml fails to parse, so a seed that doesn't survive its
  // own serialize/parse doesn't error once — it rewrites and re-fails on every single launch.
  const result = parseLibraryYaml(serializeLibraryYaml(seedLibrary));
  if (!result.ok) throw new Error(`seed failed to parse: ${result.error.kind} — ${result.error.detail}`);
  expect(result.data).toEqual(seedLibrary);
});

it('resolves every exercise and workout reference it makes', () => {
  // mergeLibraries validates references against the merged whole, which for an empty base is
  // exactly "is the seed internally consistent".
  const result = mergeLibraries(emptyLibrary, seedLibrary);
  if (!result.ok) throw new Error(`seed has a dangling reference: ${JSON.stringify(result.error)}`);
});

it('leaves nothing in the library unreachable from a workout', () => {
  // An exercise no workout uses is browsable but not runnable — fine for a user's own library,
  // but in curated starter content it's a content bug (the type it was added to demonstrate has
  // no live example after all).
  const used = new Set(
    seedLibrary.workouts.flatMap((workout) =>
      workout.blocks.flatMap((block) =>
        block.kind === 'circuit' ? block.members.map((m) => m.exerciseId) : [block.exerciseId],
      ),
    ),
  );
  expect(seedLibrary.exercises.filter((exercise) => !used.has(exercise.id)).map((exercise) => exercise.id)).toEqual([]);
});

it('demonstrates all seven exercise types', () => {
  // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
  // oxlint-disable-next-line unicorn/no-array-sort
  expect([...new Set(seedLibrary.exercises.map((exercise) => exercise.type))].sort()).toEqual([
    'amrap',
    'cardio',
    'emom',
    'hiit',
    'reps',
    'rest',
    'timed_hold',
  ]);
});

it('puts the no-equipment program first, since that is what a fresh install runs', () => {
  // activeProgram falls back to programs[0] until a session exists, and there is no picker.
  expect(seedLibrary.programs[0].id).toBe('foundations');
});

describe.each(seedLibrary.programs.map((program): [string, Program] => [program.id, program]))('%s', (_id, program) => {
  it('enumerates every week with no gaps', () => {
    // Weeks resolve sparsely: a missing week 2 is skipped entirely, not filled in from week 1.
    const numbers = programWeekNumbers(program);
    expect(numbers).toEqual(numbers.map((_, index) => index + 1));
  });

  it('walks its weeks in training order', () => {
    // nextWeekAfter orders a multi-day week by day.localeCompare, so weekday names would run
    // Friday → Monday → Wednesday. Walking the whole program is what actually proves the labels
    // chosen here sort the way they read.
    const visited: string[] = [];
    const sessions: Session[] = [];
    for (let step = 0; step < program.weeks.length; step += 1) {
      const week = nextWeekAfter(program, sessions);
      visited.push(`${week.week} ${week.day}`);
      // sessions is newest-first everywhere in the app.
      sessions.unshift({
        version: 1,
        id: `s${step}`,
        workout: week.workoutId,
        program: program.id,
        programWeek: week.week,
        programDay: week.day ?? null,
        startedAt: new Date().toISOString(),
        endedAt: null,
        entries: [],
      });
    }
    expect(visited).toEqual(program.weeks.map((week) => `${week.week} ${week.day}`));
  });
});
