import type { Library } from '@/domain/types';

/**
 * Merge-by-id semantics (product plan §6): an incoming item replaces an existing one with the
 * same id, or is added if new. Existing items whose id isn't present in the import are untouched.
 * No duplicate ids can result, since both sides are deduped through the same Map.
 */
function mergeById<T extends { id: string }>(
  existing: T[],
  incoming: T[],
): { merged: T[]; added: string[]; updated: string[] } {
  const added: string[] = [];
  const updated: string[] = [];
  const byId = new Map(existing.map((item) => [item.id, item]));

  for (const item of incoming) {
    if (byId.has(item.id)) updated.push(item.id);
    else added.push(item.id);
    byId.set(item.id, item);
  }

  return { merged: [...byId.values()], added, updated };
}

export type MergeSummary = {
  newExercises: string[];
  updatedExercises: string[];
  newWorkouts: string[];
  updatedWorkouts: string[];
};

export type MergeResult = { ok: true; library: Library; summary: MergeSummary } | { ok: false; error: string };

export function mergeLibraries(existing: Library, incoming: Library): MergeResult {
  const exerciseMerge = mergeById(existing.exercises, incoming.exercises);
  const workoutMerge = mergeById(existing.workouts, incoming.workouts);

  const library: Library = {
    version: existing.version,
    exercises: exerciseMerge.merged,
    workouts: workoutMerge.merged,
  };

  // Validate the merged whole (§6): every workout block must reference an exercise that exists
  // post-merge. (No-duplicate-ids and known-type/required-config are already guaranteed: the
  // former by construction above, the latter by zod validation at parse time before merge runs.)
  const exerciseIds = new Set(library.exercises.map((exercise) => exercise.id));
  for (const workout of library.workouts) {
    for (const block of workout.blocks) {
      if (!exerciseIds.has(block.exerciseId)) {
        return { ok: false, error: `Workout "${workout.id}" references unknown exercise "${block.exerciseId}"` };
      }
    }
  }

  return {
    ok: true,
    library,
    summary: {
      newExercises: exerciseMerge.added,
      updatedExercises: exerciseMerge.updated,
      newWorkouts: workoutMerge.added,
      updatedWorkouts: workoutMerge.updated,
    },
  };
}
