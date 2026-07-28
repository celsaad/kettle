import type { Exercise, Library, Program, Workout } from '@/domain/types';

/**
 * Fixture builders for screen tests.
 *
 * Screens read the library through the real zustand store rather than props, so a test's setup is
 * "put this library in the store" — see `setLibrary`. The store itself is left real and only its
 * storage dependency is mocked, per the plan's rule of mocking at *our* boundary (`@/storage/*`):
 * that keeps the store's own add-vs-update-by-id logic under test instead of stubbed out.
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

export function aWorkout(overrides: Partial<Workout> = {}): Workout {
  return { id: 'push-day', name: 'Push day', blocks: [], ...overrides };
}

export function aProgram(overrides: Partial<Program> = {}): Program {
  return { id: 'base', name: 'Base', weeks: [{ week: 1, workoutId: 'push-day' }], ...overrides };
}

export function aLibrary(overrides: Partial<Library> = {}): Library {
  return { version: 1, exercises: [], workouts: [], programs: [], ...overrides };
}
