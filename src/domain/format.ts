import type { Exercise } from '@/domain/types';

/**
 * The one place English display strings are assembled.
 *
 * The logic layer (`selectors.ts`, `exercise-badge.tsx`) used to return finished sentences, which made
 * two things hard: tests had to assert on prose that i18n was about to rewrite, and pluralisation was
 * scattered across a dozen template literals — several of which were simply wrong ("1 blocks",
 * "1 rounds", "1 reps"). Those functions now return structured descriptors and this module renders
 * them, so when i18n lands only this file and the views change.
 *
 * **`plural` is English-only on purpose.** The obvious implementation is `Intl.PluralRules`, but
 * Hermes doesn't ship it (it has Collator/DateTimeFormat/NumberFormat and not much else), so calling
 * it would work in tests and on web and crash on device. When i18next + the `intl-pluralrules`
 * polyfill land, this function is the single seam to replace with CLDR categories — which matters
 * because languages like Polish and Arabic have three to six forms, not two.
 */
export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : pluralForm}`;
}

const TYPE_LABEL: Record<Exercise['type'], string> = {
  hiit: 'hiit',
  emom: 'emom',
  amrap: 'amrap',
  reps: 'reps',
  timed_hold: 'hold',
  cardio: 'cardio',
  rest: 'rest',
};

/** What a workout is made of, as data — see `workoutSummary`. */
export type WorkoutShape = {
  blockCount: number;
  /** Distinct non-rest exercise types, in first-seen order. */
  types: Exercise['type'][];
  estimatedMinutes: number;
};

export function formatWorkoutShape(shape: WorkoutShape): string {
  const labels = shape.types.map((type) => TYPE_LABEL[type]);
  const typeLabel = labels.length === 0 ? 'rest only' : labels.length === 1 ? labels[0] : `mixed ${labels.join(' + ')}`;
  return `${plural(shape.blockCount, 'block')} · ${typeLabel} · ~${shape.estimatedMinutes} min`;
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
      return result.holdSecs.map((sec) => `${sec}s`).join(' · ');
    case 'reps':
      return `${result.reps.join(' · ')} reps`;
    case 'rounds': {
      const rounds = plural(result.rounds, 'round');
      return result.extraReps ? `${rounds} + ${plural(result.extraReps, 'rep')}` : rounds;
    }
    case 'intervals': {
      const intervals = plural(result.intervals, 'interval');
      return result.totalReps ? `${intervals} · ${plural(result.totalReps, 'rep')}` : intervals;
    }
    case 'cardio': {
      const parts: string[] = [];
      if (result.durationSec !== undefined) parts.push(`${result.durationSec}s`);
      if (result.distanceMeters !== undefined) parts.push(`${result.distanceMeters} m`);
      return parts.join(' · ');
    }
    case 'rest':
      return `${result.restTakenSec}s`;
  }
}

/** A circuit block's configuration, as data — see `circuitShape`. */
export type CircuitShape = { rounds: number; restBetweenExercisesSec: number; restBetweenRoundsSec: number };

export function formatCircuitShape(shape: CircuitShape): string {
  return `${plural(shape.rounds, 'round')} · ${shape.restBetweenExercisesSec}s / ${shape.restBetweenRoundsSec}s rest`;
}
