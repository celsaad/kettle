import { mergeLibraries } from '@/domain/merge';
import { programWeekNumbers } from '@/domain/program';
import { parseLibraryYaml, serializeLibraryYaml } from '@/domain/yaml-mapping';
import { nextWeekAfter } from '@/state/selectors/next-up';
import { contentPackLibrary, contentPacks } from '@/storage/content-packs';
import { contentPackTranslations } from '@/storage/content-pack-translations';
import { libraryWeekKey } from '@/storage/library-translation';
import { seedLibrary } from '@/storage/seed-library';
import type { Library, Program, Session } from '@/domain/types';

/**
 * A pack is a library the app writes into somebody's own, so it owes every invariant the seed owes —
 * and one the seed doesn't: it has to be safe to merge into a library that already exists.
 *
 * Everything runs against **every language**, because a merged pack is real user data in whatever
 * language it landed in: a table that drops a name or reorders a day label breaks that pack for that
 * language only, and the English run would keep passing.
 */

const emptyLibrary: Library = { version: 1, exercises: [], workouts: [], programs: [] };
const packLanguages = ['en', 'pt', 'ja'];

describe.each(contentPacks.map((pack) => [pack.id, pack] as const))('the %s pack', (_id, pack) => {
  it('prefixes every id it ships', () => {
    // The whole safety argument for merging a pack into a library somebody has been editing for
    // months: merge replaces whole objects by id, so an unprefixed id is a silent overwrite of
    // their work. Checked against the declared prefix, not against whatever the content happens
    // to share.
    const ids = [
      ...pack.library.exercises.map((item) => item.id),
      ...pack.library.workouts.map((item) => item.id),
      ...pack.library.programs.map((item) => item.id),
    ];
    expect(ids.filter((id) => !id.startsWith(pack.idPrefix))).toEqual([]);
  });

  it('collides with nothing in the seed', () => {
    // The prefix test above proves the ids are *shaped* right; this proves the shape actually bought
    // what it was for. A fresh install merging this pack must add, never replace.
    const seeded = new Set([
      ...seedLibrary.exercises.map((item) => item.id),
      ...seedLibrary.workouts.map((item) => item.id),
      ...seedLibrary.programs.map((item) => item.id),
    ]);
    const packIds = [
      ...pack.library.exercises.map((item) => item.id),
      ...pack.library.workouts.map((item) => item.id),
      ...pack.library.programs.map((item) => item.id),
    ];
    expect(packIds.filter((id) => seeded.has(id))).toEqual([]);
  });

  describe.each(packLanguages)('in %s', (language) => {
    const library = contentPackLibrary(pack, language);

    it('round-trips through the YAML layer unchanged', () => {
      // A merged pack is written straight back out to exercises.yaml, so a pack that doesn't survive
      // its own serialize/parse doesn't fail at merge time — it fails on the next launch, by which
      // point it is indistinguishable from the user having corrupted their own library.
      const result = parseLibraryYaml(serializeLibraryYaml(library));
      if (!result.ok) throw new Error(`pack failed to parse: ${result.error.kind} — ${result.error.detail}`);
      expect(result.data).toEqual(library);
    });

    it('resolves every exercise and workout reference it makes', () => {
      const result = mergeLibraries(emptyLibrary, library);
      if (!result.ok) throw new Error(`pack has a dangling reference: ${JSON.stringify(result.error)}`);
    });

    it('merges into the seed library, adding and replacing nothing', () => {
      // The actual thing the import screen does on a tap, and the only test that exercises the two
      // halves together: references are validated against the *merged* whole, which is what would
      // catch a pack that reached for the seed's `rest` instead of shipping its own.
      const result = mergeLibraries(seedLibrary, library);
      if (!result.ok) throw new Error(`pack refused against the seed: ${JSON.stringify(result.error)}`);
      expect({
        updatedExercises: result.summary.updatedExercises,
        updatedWorkouts: result.summary.updatedWorkouts,
        updatedPrograms: result.summary.updatedPrograms,
      }).toEqual({ updatedExercises: [], updatedWorkouts: [], updatedPrograms: [] });
    });

    it('leaves nothing in the pack unreachable from a workout', () => {
      // An exercise no workout uses is browsable but not runnable. In a user's own library that's
      // their business; in curated content it means somebody added an exercise and forgot to put it
      // in the session it was written for.
      const used = new Set(
        library.workouts.flatMap((workout) =>
          workout.blocks.flatMap((block) =>
            block.kind === 'circuit' ? block.members.map((member) => member.exerciseId) : [block.exerciseId],
          ),
        ),
      );
      expect(library.exercises.filter((exercise) => !used.has(exercise.id)).map((exercise) => exercise.id)).toEqual([]);
    });

    it('names everything it shows', () => {
      const named = [...library.exercises, ...library.workouts, ...library.programs].every(
        (item) => item.name.trim().length > 0,
      );
      expect(named).toBe(true);
    });

    describe.each(library.programs.map((program): [string, Program] => [program.id, program]))(
      '%s',
      (_programId, program) => {
        it('enumerates every week with no gaps', () => {
          // Weeks resolve sparsely: a missing week 2 is skipped entirely, not filled in from week 1.
          const numbers = programWeekNumbers(program);
          expect(numbers).toEqual(numbers.map((_, index) => index + 1));
        });

        it('walks its weeks in training order', () => {
          // `nextWeekAfter` orders a multi-day week by `day.localeCompare`, so a translation that
          // reached for weekday names would run the week in dictionary order. Walking the whole
          // program is what proves the labels sort the way they read, per language.
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
      },
    );
  });
});

/**
 * The parity guard, per pack and per language: a content edit nobody translated fails here, naming
 * the ids it left behind, rather than shipping a half-Portuguese merge nobody looks at.
 */
describe.each(
  contentPacks.flatMap((pack) =>
    Object.entries(contentPackTranslations[pack.id] ?? {}).map(
      ([language, strings]) => [pack.id, language, pack, strings] as const,
    ),
  ),
)('the %s pack in %s', (_id, _language, pack, strings) => {
  const structure = pack.library;

  it('translates every exercise, workout and program', () => {
    expect({
      exercises: structure.exercises.filter((item) => !strings.exercises[item.id]).map((item) => item.id),
      workouts: structure.workouts.filter((item) => !strings.workouts[item.id]).map((item) => item.id),
      programs: structure.programs.filter((item) => !strings.programs[item.id]).map((item) => item.id),
    }).toEqual({ exercises: [], workouts: [], programs: [] });
  });

  it('translates every note and day label', () => {
    const missingNotes = structure.exercises.filter((item) => item.notes && !strings.exercises[item.id]?.notes);
    const missingWeekNotes = structure.programs.flatMap((program) =>
      program.weeks
        .filter((week) => week.notes && !strings.programs[program.id]?.weekNotes?.[libraryWeekKey(week.week, week.day)])
        .map((week) => `${program.id} ${libraryWeekKey(week.week, week.day)}`),
    );
    const missingDays = structure.programs.flatMap((program) =>
      program.weeks.filter((week) => week.day && !strings.days[week.day]).map((week) => week.day),
    );

    expect({
      notes: missingNotes.map((item) => item.id),
      weekNotes: missingWeekNotes,
      days: [...new Set(missingDays)],
    }).toEqual({ notes: [], weekNotes: [], days: [] });
  });

  it('has no keys left pointing at content that no longer exists', () => {
    // The direction a rename breaks: an id dropped or renamed in the structure leaves a table entry
    // that silently stops applying, so the language quietly reverts to English.
    const ids = {
      exercises: new Set(structure.exercises.map((item) => item.id)),
      workouts: new Set(structure.workouts.map((item) => item.id)),
      programs: new Set(structure.programs.map((item) => item.id)),
      days: new Set(structure.programs.flatMap((program) => program.weeks.map((week) => week.day))),
    };

    expect({
      exercises: Object.keys(strings.exercises).filter((id) => !ids.exercises.has(id)),
      workouts: Object.keys(strings.workouts).filter((id) => !ids.workouts.has(id)),
      programs: Object.keys(strings.programs).filter((id) => !ids.programs.has(id)),
      days: Object.keys(strings.days).filter((day) => !ids.days.has(day)),
      weekNotes: Object.entries(strings.programs).flatMap(([programId, program]) => {
        const weeks = new Set(
          structure.programs
            .find((candidate) => candidate.id === programId)
            ?.weeks.map((week) => libraryWeekKey(week.week, week.day)) ?? [],
        );
        return Object.keys(program.weekNotes ?? {})
          .filter((key) => !weeks.has(key))
          .map((key) => `${programId} ${key}`);
      }),
    }).toEqual({ exercises: [], workouts: [], programs: [], days: [], weekNotes: [] });
  });
});

describe('every language a pack ships', () => {
  it('changes nothing but the strings', () => {
    // The argument for a string table over a second Library literal is that structure stays
    // single-sourced. This holds it to that: ids, types, configs, blocks, week numbers and overrides
    // must come through a translation byte-identical, `restDay` included — turning a training day
    // into a day off is exactly the kind of change a translation must not be able to make.
    for (const pack of contentPacks) {
      for (const language of Object.keys(contentPackTranslations[pack.id] ?? {})) {
        expect(structureOf(contentPackLibrary(pack, language))).toEqual(structureOf(pack.library));
      }
    }
  });

  it('falls back to English for a language it has no table for', () => {
    for (const pack of contentPacks) {
      expect(contentPackLibrary(pack, 'de')).toEqual(pack.library);
    }
  });

  it('reads a regional tag as its base language', () => {
    // Callers hand over whatever i18next is holding, which on a Brazilian device is `pt-BR`.
    for (const pack of contentPacks) {
      expect(contentPackLibrary(pack, 'pt-BR')).toEqual(contentPackLibrary(pack, 'pt'));
    }
  });
});

describe('the pack list', () => {
  it('gives every pack a distinct id and prefix', () => {
    expect(new Set(contentPacks.map((pack) => pack.id)).size).toBe(contentPacks.length);
    expect(new Set(contentPacks.map((pack) => pack.idPrefix)).size).toBe(contentPacks.length);
  });

  it('lets any two packs be merged together', () => {
    // Nothing stops somebody taking all three, and two packs sharing an id would only show up then —
    // as one quietly replacing part of the other.
    let library: Library = seedLibrary;
    for (const pack of contentPacks) {
      const result = mergeLibraries(library, contentPackLibrary(pack, 'en'));
      if (!result.ok) throw new Error(`${pack.id} refused: ${JSON.stringify(result.error)}`);
      expect(result.summary.updatedExercises).toEqual([]);
      library = result.library;
    }
  });
});

/** Everything a translation is not allowed to touch — listed rather than omitted, so a new
 * translatable field has to be added here consciously. */
function structureOf(library: Library) {
  return {
    version: library.version,
    exercises: library.exercises.map((exercise) => ({ id: exercise.id, type: exercise.type, config: exercise.config })),
    workouts: library.workouts.map((workout) => ({ id: workout.id, blocks: workout.blocks })),
    programs: library.programs.map((program) => ({
      id: program.id,
      weeks: program.weeks.map((week) =>
        week.restDay
          ? { week: week.week, restDay: true }
          : { week: week.week, workoutId: week.workoutId, overrides: week.overrides },
      ),
    })),
  };
}
