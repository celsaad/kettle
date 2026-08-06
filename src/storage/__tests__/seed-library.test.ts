import { mergeLibraries } from '@/domain/merge';
import { programWeekNumbers } from '@/domain/program';
import { parseLibraryYaml, serializeLibraryYaml } from '@/domain/yaml-mapping';
import { nextWeekAfter } from '@/state/selectors';
import { seedLibrary, seedLibraryFor } from '@/storage/seed-library';
import { seedTranslations, seedWeekKey } from '@/storage/seed-translations';
import type { Library, Program, Session } from '@/domain/types';

/**
 * The seed is the one library that is written without ever passing through the import path, so
 * nothing else checks it. These pin the invariants a content edit can break silently — each one
 * fails in the app rather than at the point of editing, which is what makes them worth a test.
 *
 * Every invariant runs against **every language**, because a translated seed is a real library a real
 * first launch lands on: a table that drops a name or reorders a day label breaks the app for that
 * language only, and the English run would keep passing.
 */

const emptyLibrary: Library = { version: 1, exercises: [], workouts: [], programs: [] };

const seedLanguages = ['en', ...Object.keys(seedTranslations)];

describe.each(seedLanguages)('the %s seed', (language) => {
  const seed = seedLibraryFor(language);

  it('round-trips through the YAML layer unchanged', () => {
    // loadLibrary reseeds whenever exercises.yaml fails to parse, so a seed that doesn't survive its
    // own serialize/parse doesn't error once — it rewrites and re-fails on every single launch.
    const result = parseLibraryYaml(serializeLibraryYaml(seed));
    if (!result.ok) throw new Error(`seed failed to parse: ${result.error.kind} — ${result.error.detail}`);
    expect(result.data).toEqual(seed);
  });

  it('resolves every exercise and workout reference it makes', () => {
    // mergeLibraries validates references against the merged whole, which for an empty base is
    // exactly "is the seed internally consistent".
    const result = mergeLibraries(emptyLibrary, seed);
    if (!result.ok) throw new Error(`seed has a dangling reference: ${JSON.stringify(result.error)}`);
  });

  it('leaves nothing in the library unreachable from a workout', () => {
    // An exercise no workout uses is browsable but not runnable — fine for a user's own library,
    // but in curated starter content it's a content bug (the type it was added to demonstrate has
    // no live example after all).
    const used = new Set(
      seed.workouts.flatMap((workout) =>
        workout.blocks.flatMap((block) =>
          block.kind === 'circuit' ? block.members.map((m) => m.exerciseId) : [block.exerciseId],
        ),
      ),
    );
    expect(seed.exercises.filter((exercise) => !used.has(exercise.id)).map((exercise) => exercise.id)).toEqual([]);
  });

  it('demonstrates all seven exercise types', () => {
    // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
    // oxlint-disable-next-line unicorn/no-array-sort
    expect([...new Set(seed.exercises.map((exercise) => exercise.type))].sort()).toEqual([
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
    expect(seed.programs[0].id).toBe('foundations');
  });

  it('names everything it shows', () => {
    // A missing table entry falls back to English rather than to an empty string, so this can only
    // fail on a structural edit — but an unnamed exercise renders as a blank row, which is worth
    // catching here rather than on a screenshot.
    const named = [...seed.exercises, ...seed.workouts, ...seed.programs].every((item) => item.name.trim().length > 0);
    expect(named).toBe(true);
  });

  describe.each(seed.programs.map((program): [string, Program] => [program.id, program]))('%s', (_id, program) => {
    it('enumerates every week with no gaps', () => {
      // Weeks resolve sparsely: a missing week 2 is skipped entirely, not filled in from week 1.
      const numbers = programWeekNumbers(program);
      expect(numbers).toEqual(numbers.map((_, index) => index + 1));
    });

    it('walks its weeks in training order', () => {
      // nextWeekAfter orders a multi-day week by day.localeCompare, so weekday names would run
      // Friday → Monday → Wednesday. Walking the whole program is what actually proves the labels
      // chosen here sort the way they read — including a translated set, which is why this runs per
      // language: `Dia 1`/`Dia 2`/`Dia 3` sorts, and a translation that reached for weekday names
      // would fail right here.
      const visited: string[] = [];
      const sessions: Session[] = [];
      for (let step = 0; step < program.weeks.length; step += 1) {
        const week = nextWeekAfter(program, sessions);
        visited.push(`${week.week} ${week.day}`);
        // sessions is newest-first everywhere in the app.
        sessions.unshift({
          version: 1,
          id: `s${step}`,
          workout: week.restDay ? null : week.workoutId,
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
});

/**
 * The parity guard the string-table shape exists to make possible: a content edit to the English
 * structure that nobody translated fails here, naming the ids it left behind, instead of shipping a
 * half-Portuguese first launch nobody looks at.
 */
describe.each(Object.entries(seedTranslations))('the %s translation', (language, strings) => {
  const seed = seedLibraryFor(language);

  it('translates every exercise, workout and program', () => {
    expect({
      exercises: seedLibrary.exercises.filter((item) => !strings.exercises[item.id]).map((item) => item.id),
      workouts: seedLibrary.workouts.filter((item) => !strings.workouts[item.id]).map((item) => item.id),
      programs: seedLibrary.programs.filter((item) => !strings.programs[item.id]).map((item) => item.id),
    }).toEqual({ exercises: [], workouts: [], programs: [] });
  });

  it('translates every note and day label', () => {
    const missingNotes = seedLibrary.exercises.filter((item) => item.notes && !strings.exercises[item.id]?.notes);
    const missingWeekNotes = seedLibrary.programs.flatMap((program) =>
      program.weeks
        .filter((week) => week.notes && !strings.programs[program.id]?.weekNotes?.[seedWeekKey(week.week, week.day)])
        .map((week) => `${program.id} ${seedWeekKey(week.week, week.day)}`),
    );
    const missingDays = seedLibrary.programs.flatMap((program) =>
      program.weeks.filter((week) => week.day && !strings.days[week.day]).map((week) => week.day),
    );

    expect({
      notes: missingNotes.map((item) => item.id),
      weekNotes: missingWeekNotes,
      days: [...new Set(missingDays)],
    }).toEqual({ notes: [], weekNotes: [], days: [] });
  });

  it('has no keys left pointing at content that no longer exists', () => {
    // The other direction, and the one a rename breaks: an id dropped or renamed in the structure
    // leaves a table entry that silently stops applying, so the language quietly reverts to English.
    const ids = {
      exercises: new Set(seedLibrary.exercises.map((item) => item.id)),
      workouts: new Set(seedLibrary.workouts.map((item) => item.id)),
      programs: new Set(seedLibrary.programs.map((item) => item.id)),
      days: new Set(seedLibrary.programs.flatMap((program) => program.weeks.map((week) => week.day))),
    };

    expect({
      exercises: Object.keys(strings.exercises).filter((id) => !ids.exercises.has(id)),
      workouts: Object.keys(strings.workouts).filter((id) => !ids.workouts.has(id)),
      programs: Object.keys(strings.programs).filter((id) => !ids.programs.has(id)),
      days: Object.keys(strings.days).filter((day) => !ids.days.has(day)),
      weekNotes: Object.entries(strings.programs).flatMap(([programId, program]) => {
        const weeks = new Set(
          seedLibrary.programs
            .find((candidate) => candidate.id === programId)
            ?.weeks.map((week) => seedWeekKey(week.week, week.day)) ?? [],
        );
        return Object.keys(program.weekNotes ?? {})
          .filter((key) => !weeks.has(key))
          .map((key) => `${programId} ${key}`);
      }),
    }).toEqual({ exercises: [], workouts: [], programs: [], days: [], weekNotes: [] });
  });

  it('changes nothing but the strings', () => {
    // The argument for a string table over a second Library literal is that structure stays
    // single-sourced. This is what holds it to that: ids, types, configs, blocks, week numbers and
    // overrides must come through a translation byte-identical.
    expect(structureOf(seed)).toEqual(structureOf(seedLibrary));
  });
});

/** Everything a translation is not allowed to touch — deliberately listed rather than omitted, so a
 * new translatable field has to be added here consciously. */
function structureOf(library: Library) {
  return {
    version: library.version,
    exercises: library.exercises.map((exercise) => ({ id: exercise.id, type: exercise.type, config: exercise.config })),
    workouts: library.workouts.map((workout) => ({ id: workout.id, blocks: workout.blocks })),
    programs: library.programs.map((program) => ({
      id: program.id,
      // `restDay` belongs here too: which days are rest is structure, not string, and a translation
      // that turned a training day into a day off would be exactly the kind of change this catches.
      weeks: program.weeks.map((week) =>
        week.restDay
          ? { week: week.week, restDay: true }
          : { week: week.week, workoutId: week.workoutId, overrides: week.overrides },
      ),
    })),
  };
}
