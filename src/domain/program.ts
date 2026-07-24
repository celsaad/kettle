import { applyBlockOverride, applyExerciseOverride } from '@/domain/yaml-mapping';
import type { Exercise, Library, Program, ProgramWeek, Workout } from '@/domain/types';

export function findProgramWeek(program: Program, weekNumber: number): ProgramWeek | undefined {
  return program.weeks.find((week) => week.week === weekNumber);
}

export function programWeekNumbers(program: Program): number[] {
  return program.weeks.map((week) => week.week).sort((a, b) => a - b);
}

export type ResolvedWeek = { workout: Workout; exercises: Exercise[] };

/**
 * Resolves the effective workout + exercise set for one program week: the week's base workout
 * (with any circuit-block overrides for this week applied), and the library's base exercises
 * (with any per-exercise overrides for this week applied). Anything without an override for this
 * week is returned unchanged.
 */
export function resolveWorkoutForWeek(program: Program, weekNumber: number, library: Library): ResolvedWeek | null {
  const week = findProgramWeek(program, weekNumber);
  if (!week) return null;
  const baseWorkout = library.workouts.find((candidate) => candidate.id === week.workoutId);
  if (!baseWorkout) return null;

  const overrides = week.overrides ?? [];
  const exerciseOverrides = new Map(
    overrides.filter((override) => override.kind === 'exercise').map((override) => [override.exerciseId, override.config]),
  );
  const blockOverrides = new Map(
    overrides.filter((override) => override.kind === 'block').map((override) => [override.blockId, override.config]),
  );

  const exercises = library.exercises.map((exercise) => {
    const overrideConfig = exerciseOverrides.get(exercise.id);
    return overrideConfig ? applyExerciseOverride(exercise, overrideConfig) : exercise;
  });

  const blocks = baseWorkout.blocks.map((block) => {
    if (block.kind !== 'circuit' || !block.id) return block;
    const overrideConfig = blockOverrides.get(block.id);
    return overrideConfig ? applyBlockOverride(block, overrideConfig) : block;
  });

  return { workout: { ...baseWorkout, blocks }, exercises };
}
