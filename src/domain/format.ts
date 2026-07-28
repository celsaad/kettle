// Imported from `i18next` directly, not from `@/i18n`. It's the same singleton — `@/i18n` configures
// this instance — but going via that module would pull `expo-localization` into the domain layer, and
// into every test that touches formatting. The jest setup initialises the same singleton with the
// English resources, so these render identically under test.
import i18next from 'i18next';

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
  const labels = shape.types.map((type) => i18next.t(`format.type.${type}`));
  const typeLabel =
    labels.length === 0
      ? i18next.t('format.restOnly')
      : labels.length === 1
        ? labels[0]
        : i18next.t('format.mixed', { types: labels.join(' + ') });
  return i18next.t('format.workoutShape', {
    blocks: i18next.t('format.block', { count: shape.blockCount }),
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
      return result.holdSecs.map((sec) => i18next.t('format.seconds', { n: sec })).join(' · ');
    case 'reps':
      return i18next.t('format.repsList', { list: result.reps.join(' · ') });
    case 'rounds': {
      const rounds = i18next.t('format.round', { count: result.rounds });
      return result.extraReps
        ? i18next.t('format.roundsPlusReps', { rounds, reps: i18next.t('format.rep', { count: result.extraReps }) })
        : rounds;
    }
    case 'intervals': {
      const intervals = i18next.t('format.interval', { count: result.intervals });
      return result.totalReps
        ? i18next.t('format.intervalsWithReps', {
            intervals,
            reps: i18next.t('format.rep', { count: result.totalReps }),
          })
        : intervals;
    }
    case 'cardio': {
      const parts: string[] = [];
      if (result.durationSec !== undefined) parts.push(i18next.t('format.seconds', { n: result.durationSec }));
      if (result.distanceMeters !== undefined) parts.push(i18next.t('format.metres', { n: result.distanceMeters }));
      return parts.join(' · ');
    }
    case 'rest':
      return i18next.t('format.seconds', { n: result.restTakenSec });
  }
}

/**
 * A logged session's display name: the workout's own name, which is user data and renders verbatim,
 * or a translated stand-in when the session was started ad-hoc and has no workout behind it. The
 * selector returns `null` for that case rather than the English label it used to assemble itself.
 */
export function formatSessionName(workoutName: string | null): string {
  return workoutName ?? i18next.t('format.adHocSession');
}

/** A logged session's duration. Abbreviated, so it doesn't pluralise in either locale. */
export function formatSessionDuration(minutes: number): string {
  return i18next.t('format.minutes', { n: minutes });
}

/** Through `count`, not a template literal: History and Today both rendered "1 sets" before this. */
export function formatSetCount(sets: number): string {
  return i18next.t('format.set', { count: sets });
}

/** A circuit block's configuration, as data — see `circuitShape`. */
export type CircuitShape = { rounds: number; restBetweenExercisesSec: number; restBetweenRoundsSec: number };

export function formatCircuitShape(shape: CircuitShape): string {
  return i18next.t('format.circuitShape', {
    rounds: i18next.t('format.round', { count: shape.rounds }),
    between: shape.restBetweenExercisesSec,
    rounds_rest: shape.restBetweenRoundsSec,
  });
}
