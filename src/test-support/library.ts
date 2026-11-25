import type { Exercise } from '@/domain/types';

/**
 * Fixture builders for the selector tests.
 */

export function anExercise(overrides: Partial<Exercise> = {}): Exercise {
  return {
    id: 'pull-ups',
    name: 'Pull-ups',
    type: 'reps',
    config: { sets: 4, targetRepsMin: 6, restSec: 90 },
    ...overrides,
  } as Exercise;
}
