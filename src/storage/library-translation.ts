import type { Exercise, Library, Program } from '@/domain/types';
import { parseLibraryYaml } from '@/domain/yaml-mapping';

/**
 * The shape of a per-language string table for a library the app ships, and the machinery that
 * applies one. The tables themselves are the `<language>.json` files beside each library in
 * `src/content/`, which the native app reads too.
 *
 * It is shared by the seed and the content packs rather than copied into each for the reason the
 * string-table shape was chosen in the first place: **structure is single-sourced, strings are
 * per-language.** A second copy of `localizeLibrary` would let the seed and the packs disagree about
 * which fields are translatable — and the field this would silently get wrong is `restDay`, where
 * "translated" and "structural" is the difference between a day off and a training day.
 *
 * Nothing here knows about the seed or about packs. It takes a library and a table and returns the
 * library with that table's strings applied; who holds which table is the caller's business.
 */
export type LibraryTranslation = {
  /**
   * Day labels are a closed set shared by every program in a shipped library, so they map by their
   * English label rather than being repeated on every week entry. A replacement is pure display text
   * and can say anything — weeks run in the order the structure writes them, not in label order, so a
   * translation cannot reorder anyone's week.
   */
  days: Record<string, string>;
  exercises: Record<string, { name: string; notes?: string }>;
  /** Workouts have no translatable text beyond their name, so the value is the name itself. */
  workouts: Record<string, string>;
  programs: Record<string, { name: string; weekNotes?: Record<string, string> }>;
};

/**
 * Program weeks are the one thing that can't be keyed by id: `ProgramWeek` has none, and a week is
 * addressed by its `(week, day)` pair — the same pair the schema requires to be unique within a
 * program. Keyed on the **English** day label, since that's what the structural definition holds.
 */
export function libraryWeekKey(week: number, day: string | undefined): string {
  return `${week}|${day ?? ''}`;
}

function localizeExercise(exercise: Exercise, strings: LibraryTranslation): Exercise {
  const text = strings.exercises[exercise.id];
  if (!text) return exercise;
  // `notes` falls back rather than being dropped: a half-finished table should degrade to English on
  // the strings it's missing, not silently delete the content's coaching model. The parity test is
  // what stops that from shipping; this is what keeps it harmless if it does.
  return { ...exercise, name: text.name, notes: text.notes ?? exercise.notes };
}

function localizeProgram(program: Program, strings: LibraryTranslation): Program {
  const text = strings.programs[program.id];
  if (!text) return program;
  return {
    ...program,
    name: text.name,
    weeks: program.weeks.map((week) => ({
      ...week,
      day: week.day === undefined ? undefined : (strings.days[week.day] ?? week.day),
      notes: text.weekNotes?.[libraryWeekKey(week.week, week.day)] ?? week.notes,
    })),
  };
}

/**
 * `library` with `strings` applied, or unchanged when there is no table for the language.
 *
 * Only `name`, `notes` and `day` are touched. Everything else — ids, types, configs, block structure,
 * week layout, overrides — is language-agnostic by construction, which is the whole reason this is a
 * string table and not a second library.
 */
export function localizeLibrary(library: Library, strings: LibraryTranslation | undefined): Library {
  if (!strings) return library;

  return {
    ...library,
    exercises: library.exercises.map((exercise) => localizeExercise(exercise, strings)),
    workouts: library.workouts.map((workout) => ({ ...workout, name: strings.workouts[workout.id] ?? workout.name })),
    programs: library.programs.map((program) => localizeProgram(program, strings)),
  };
}

/**
 * The table for a language tag like `pt-BR`, narrowed to its base subtag so callers can hand over
 * whatever i18next is holding. Keyed by language, not region, exactly as the locale bundles are.
 */
export function translationFor(
  tables: Record<string, LibraryTranslation>,
  language: string | undefined,
): LibraryTranslation | undefined {
  return tables[(language ?? '').split('-')[0]];
}

/**
 * A library the app ships, from its YAML text in `src/content/`. It goes through the same parser as a
 * user's file, so shipped content can't use a shape an import would refuse.
 *
 * **Throws rather than degrading.** A shipped file that doesn't parse is a build defect, there is
 * nothing honest to fall back to, and `seed-library.test.ts` and `content-packs.test.ts` parse every
 * one, so it can't reach a release.
 */
export function parseShippedLibrary(text: string, name: string): Library {
  const result = parseLibraryYaml(text);
  if (!result.ok) throw new Error(`shipped library ${name} does not parse: ${result.error.detail}`);
  return result.data;
}
