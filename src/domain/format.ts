// Imported from `i18next` directly, not from `@/i18n`. It's the same singleton — `@/i18n` configures
// this instance — but going via that module would pull `expo-localization` into the domain layer, and
// into every test that touches formatting. The jest setup initialises the same singleton with the
// English resources, so these render identically under test.
//
// The named `t` is that instance's own method, bound to it at module load (i18next binds its
// prototype members in the constructor), so it reads the *current* language on every call — switching
// locale at runtime is picked up here, exactly as `i18next.t(…)` was.
import { t } from 'i18next';

import type { Exercise } from '@/domain/types';

/**
 * The one place display strings are assembled.
 *
 * The logic layer (`selectors.ts`, `exercise-badge.tsx`) used to return finished sentences, which made
 * two things hard: tests had to assert on prose that i18n was about to rewrite, and pluralisation was
 * scattered across a dozen template literals — several of which were simply wrong ("1 blocks",
 * "1 rounds", "1 reps"). Those functions now return structured descriptors and this module renders
 * them, so a new locale changes only translation files.
 *
 * Pluralisation goes through i18next's `count`, which resolves CLDR categories — so a locale with
 * three or six plural forms is a matter of adding `_few`/`_many` keys, not of changing code here.
 */

/** What a workout is made of, as data — see `workoutShape`. */
export type WorkoutShape = {
  blockCount: number;
  /** Distinct non-rest exercise types, in first-seen order. */
  types: Exercise['type'][];
  estimatedMinutes: number;
};

export function formatWorkoutShape(shape: WorkoutShape): string {
  const labels = shape.types.map((type) => t(`format.type.${type}`));
  const typeLabel =
    labels.length === 0
      ? t('format.restOnly')
      : labels.length === 1
        ? labels[0]
        : t('format.mixed', { types: labels.join(' + ') });
  return t('format.workoutShape', {
    blocks: t('format.block', { count: shape.blockCount }),
    types: typeLabel,
    minutes: shape.estimatedMinutes,
  });
}

/**
 * What was actually logged for one session entry, as data. One variant per shape rather than per
 * exercise type — hiit and amrap both reduce to "rounds", so the renderer doesn't need to care which
 * produced it.
 */
export type EntryResult =
  | { kind: 'holds'; holdSecs: number[] }
  | { kind: 'reps'; reps: number[] }
  | { kind: 'rounds'; rounds: number; extraReps?: number }
  | { kind: 'intervals'; intervals: number; totalReps?: number }
  | { kind: 'cardio'; durationSec?: number; distanceMeters?: number }
  | { kind: 'rest'; restTakenSec: number };

export function formatEntryResult(result: EntryResult): string {
  switch (result.kind) {
    case 'holds':
      return result.holdSecs.map((sec) => t('format.seconds', { n: sec })).join(' · ');
    case 'reps':
      return t('format.repsList', { list: result.reps.join(' · ') });
    case 'rounds': {
      const rounds = t('format.round', { count: result.rounds });
      return result.extraReps
        ? t('format.roundsPlusReps', { rounds, reps: t('format.rep', { count: result.extraReps }) })
        : rounds;
    }
    case 'intervals': {
      const intervals = t('format.interval', { count: result.intervals });
      return result.totalReps
        ? t('format.intervalsWithReps', {
            intervals,
            reps: t('format.rep', { count: result.totalReps }),
          })
        : intervals;
    }
    case 'cardio': {
      const parts: string[] = [];
      if (result.durationSec !== undefined) parts.push(t('format.seconds', { n: result.durationSec }));
      if (result.distanceMeters !== undefined) parts.push(t('format.metres', { n: result.distanceMeters }));
      return parts.join(' · ');
    }
    case 'rest':
      return t('format.seconds', { n: result.restTakenSec });
  }
}

/**
 * A logged session's display name: the workout's own name, which is user data and renders verbatim,
 * or a translated stand-in when the session was started ad-hoc and has no workout behind it. The
 * selector returns `null` for that case rather than the English label it used to assemble itself.
 */
export function formatSessionName(workoutName: string | null): string {
  return workoutName ?? t('format.adHocSession');
}

/** A logged session's duration. Abbreviated, so it doesn't pluralise in either locale. */
export function formatSessionDuration(minutes: number): string {
  return t('format.minutes', { n: minutes });
}

/** Through `count`, not a template literal: History and Today both rendered "1 sets" before this. */
export function formatSetCount(sets: number): string {
  return t('format.set', { count: sets });
}

/**
 * A personal record, as data — see `sessionRecords`.
 *
 * `weight` arrives as a finished string rather than as kilograms, which is the one departure from the
 * descriptors above and is forced: a weight can't be rendered without the user's display-unit
 * preference, that preference lives in a zustand store, and this module is imported by the domain
 * layer and by tests that mount nothing. So the view converts through `toDisplayWeight` and hands the
 * result down, exactly as `session-reps.tsx` already does for the live load.
 */
export type RecordResult =
  | { kind: 'heaviestSet'; weight: string; reps: number }
  | { kind: 'mostReps'; reps: number }
  | { kind: 'longestHold'; holdSec: number }
  | { kind: 'mostRounds'; rounds: number };

export function formatRecord(result: RecordResult): string {
  switch (result.kind) {
    case 'heaviestSet':
      return t('format.record.heaviestSet', { weight: result.weight, reps: t('format.rep', { count: result.reps }) });
    case 'mostReps':
      return t('format.record.mostReps', { reps: t('format.rep', { count: result.reps }) });
    case 'longestHold':
      return t('format.record.longestHold', { seconds: t('format.seconds', { n: result.holdSec }) });
    case 'mostRounds':
      return t('format.record.mostRounds', { rounds: t('format.round', { count: result.rounds }) });
  }
}

/**
 * The estimated one-rep max line. The formula's name is deliberately *not* in here: it belongs in the
 * one-line note the screen renders below the records (`session.complete.oneRepMaxNote`), where it
 * reads as the explanation it is. Inline, "(Epley)" competed with the number for attention while
 * telling a lifter mid-celebration nothing they wanted at that moment — it only matters to someone
 * who has already decided to question the figure, and that person will read the note.
 */
export function formatOneRepMax(weight: string): string {
  return t('format.oneRepMax', { weight });
}

/**
 * What was logged for this set last time, as data — see `previousSetFor`.
 *
 * `weight` arrives as a finished string for the same reason `RecordResult`'s does: this module can't
 * reach the display-unit preference, so the view converts and hands the result down. Absent means the
 * set was bodyweight, which reads differently rather than as "0 kg".
 */
export type PreviousSetResult = { kind: 'reps'; reps: number; weight?: string } | { kind: 'hold'; holdSec: number };

export function formatPreviousSet(result: PreviousSetResult): string {
  if (result.kind === 'hold') return t('format.previous.hold', { seconds: t('format.seconds', { n: result.holdSec }) });
  const reps = t('format.rep', { count: result.reps });
  return result.weight
    ? t('format.previous.loaded', { weight: result.weight, reps })
    : t('format.previous.bodyweight', { reps });
}

/** A circuit block's configuration, as data — see `circuitShape`. */
export type CircuitShape = { rounds: number; restBetweenExercisesSec: number; restBetweenRoundsSec: number };

export function formatCircuitShape(shape: CircuitShape): string {
  return t('format.circuitShape', {
    rounds: t('format.round', { count: shape.rounds }),
    between: shape.restBetweenExercisesSec,
    rounds_rest: shape.restBetweenRoundsSec,
  });
}
