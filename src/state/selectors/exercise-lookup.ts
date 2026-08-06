import type { Exercise } from '@/domain/types';

/**
 * Resolving an exercise id against the library, which three of the selector modules need: the shape
 * selectors read a block's members, and the history and record selectors name a logged entry. Its own
 * module rather than living in one of them, so `records.ts` doesn't have to import from
 * `workout-shape.ts` for a name lookup that has nothing to do with either.
 */
export function findExercise(exercises: Exercise[], id: string): Exercise | undefined {
  return exercises.find((exercise) => exercise.id === id);
}

/** Falls back to the raw id for an exercise since deleted from the library — user data either way. */
export function exerciseName(exercises: Exercise[], id: string): string {
  return findExercise(exercises, id)?.name ?? id;
}
