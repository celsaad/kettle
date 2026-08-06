import type { WorkoutShape } from '@/domain/format';
import type { Exercise, Workout, WorkoutBlock } from '@/domain/types';
import { exerciseName, findExercise } from '@/state/selectors/exercise-lookup';

export type BlockChip = { name: string; isRest: boolean };

/**
 * Carries each chip's `isRest` alongside its name. Callers used to style rest chips by comparing the
 * rendered name to the literal `'Rest'` — the user's own exercise name, so renaming that exercise (or
 * authoring one in any other language) silently dropped the styling.
 */
export function blockChips(workout: Workout, exercises: Exercise[]): BlockChip[] {
  const chipFor = (exerciseId: string): BlockChip => ({
    name: exerciseName(exercises, exerciseId),
    isRest: findExercise(exercises, exerciseId)?.type === 'rest',
  });
  return workout.blocks.flatMap((block) =>
    block.kind === 'circuit' ? block.members.map((member) => chipFor(member.exerciseId)) : [chipFor(block.exerciseId)],
  );
}

function estimateExerciseSeconds(exercise: Exercise, overrideDurationSec?: number): number {
  switch (exercise.type) {
    case 'hiit':
      return exercise.config.rounds * (exercise.config.workSec + exercise.config.restSec);
    // Estimated from the top of the range, because that's where the hold now ends itself. A
    // max-effort hold has no end to estimate and contributes only its rest, the same way cardio
    // without a duration contributes 0 below.
    case 'timed_hold':
      return (
        exercise.config.sets * (exercise.config.holdSecMax ?? exercise.config.holdSecMin ?? 0) +
        (exercise.config.sets - 1) * exercise.config.restSec
      );
    case 'reps':
      return exercise.config.sets * exercise.config.restSec;
    case 'emom':
      return exercise.config.totalMinutes * 60;
    case 'amrap':
      return exercise.config.timeCapSec;
    case 'cardio':
      return exercise.config.durationSec ?? 0;
    case 'rest':
      return overrideDurationSec ?? exercise.config.durationSec;
  }
}

/** A member's single per-visit cost within a circuit round: one hold/rep pass, not the exercise's own `sets`. */
function memberVisitSeconds(exercise: Exercise): number {
  switch (exercise.type) {
    case 'timed_hold':
      return exercise.config.holdSecMax ?? exercise.config.holdSecMin ?? 0;
    case 'reps':
      return exercise.config.restSec;
    case 'hiit':
      return exercise.config.rounds * (exercise.config.workSec + exercise.config.restSec);
    case 'emom':
      return exercise.config.totalMinutes * 60;
    case 'amrap':
      return exercise.config.timeCapSec;
    case 'cardio':
      return exercise.config.durationSec ?? 0;
    case 'rest':
      return exercise.config.durationSec;
  }
}

function estimateBlockSeconds(block: WorkoutBlock, exercises: Exercise[]): number {
  if (block.kind === 'exercise') {
    const exercise = findExercise(exercises, block.exerciseId);
    if (!exercise) return 0;
    return estimateExerciseSeconds(exercise, block.configOverride?.durationSec);
  }

  const members = block.members
    .map((member) => findExercise(exercises, member.exerciseId))
    .filter((exercise): exercise is Exercise => !!exercise);
  const restBetweenExercises = block.restBetweenExercisesSec ?? 0;
  const restBetweenRounds = block.restBetweenRoundsSec ?? 0;

  const roundSeconds =
    members.reduce((sum, exercise) => sum + memberVisitSeconds(exercise), 0) +
    Math.max(0, members.length - 1) * restBetweenExercises;

  return block.rounds * roundSeconds + Math.max(0, block.rounds - 1) * restBetweenRounds;
}

function blockTypes(block: WorkoutBlock, exercises: Exercise[]): Exercise['type'][] {
  if (block.kind === 'exercise') {
    const exercise = findExercise(exercises, block.exerciseId);
    return exercise ? [exercise.type] : [];
  }
  return block.members
    .map((member) => findExercise(exercises, member.exerciseId)?.type)
    .filter((type): type is Exercise['type'] => !!type);
}

/** Structured, not a sentence — `formatWorkoutShape` in domain/format.ts renders it. */
export function workoutShape(workout: Workout, exercises: Exercise[]): WorkoutShape {
  const types = new Set<Exercise['type']>();
  let totalSec = 0;

  for (const block of workout.blocks) {
    for (const type of blockTypes(block, exercises)) {
      if (type !== 'rest') types.add(type);
    }
    totalSec += estimateBlockSeconds(block, exercises);
  }

  return {
    blockCount: workout.blocks.length,
    types: [...types],
    estimatedMinutes: Math.max(1, Math.round(totalSec / 60)),
  };
}
