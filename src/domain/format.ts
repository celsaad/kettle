// Imported from `i18next` directly, not from `@/i18n`. It's the same singleton — `@/i18n` configures
// this instance — but going via that module would pull `expo-localization` into the domain layer, and
// into every test that touches formatting. The jest setup initialises the same singleton with the
// English resources, so these render identically under test.
//
// The named `t` is that instance's own method, bound to it at module load (i18next binds its
// prototype members in the constructor), so it reads the *current* language on every call — switching
// locale at runtime is picked up here, exactly as `i18next.t(…)` was.
import { t } from 'i18next';

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

/** A circuit block's configuration, as data — see `circuitShape`. */
export type CircuitShape = { rounds: number; restBetweenExercisesSec: number; restBetweenRoundsSec: number };

export function formatCircuitShape(shape: CircuitShape): string {
  return t('format.circuitShape', {
    rounds: t('format.round', { count: shape.rounds }),
    between: shape.restBetweenExercisesSec,
    rounds_rest: shape.restBetweenRoundsSec,
  });
}
