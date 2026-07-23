/**
 * Zod schemas for the hand-editable/app-written YAML file shapes (snake_case), per the product
 * plan's file formats. Validated on every load and import so malformed files fail with a clear
 * error instead of a crash.
 */
import { z } from 'zod';

const idSchema = z.string().min(1);

const hiitConfigSchema = z.object({
  work_sec: z.number().positive(),
  rest_sec: z.number().nonnegative(),
  rounds: z.number().int().positive(),
});

const emomConfigSchema = z.object({
  interval_sec: z.number().positive(),
  total_minutes: z.number().positive(),
  target_reps: z.number().int().positive().optional(),
});

const amrapConfigSchema = z.object({
  time_cap_sec: z.number().positive(),
});

const repsConfigSchema = z.object({
  sets: z.number().int().positive(),
  target_reps: z.number().int().positive(),
  target_weight: z.number().nonnegative().optional(),
  rest_sec: z.number().nonnegative(),
});

const timedHoldConfigSchema = z.object({
  sets: z.number().int().positive(),
  hold_sec: z.number().positive(),
  rest_sec: z.number().nonnegative(),
});

const cardioConfigSchema = z.object({
  duration_sec: z.number().positive().optional(),
  distance_meters: z.number().positive().optional(),
});

const restConfigSchema = z.object({
  duration_sec: z.number().nonnegative(),
});

export const rawExerciseSchema = z.discriminatedUnion('type', [
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('hiit'), config: hiitConfigSchema }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('emom'), config: emomConfigSchema }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('amrap'), config: amrapConfigSchema }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('reps'), config: repsConfigSchema }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('timed_hold'), config: timedHoldConfigSchema }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('cardio'), config: cardioConfigSchema }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('rest'), config: restConfigSchema }),
]);

const workoutBlockConfigOverrideSchema = z.object({ duration_sec: z.number().nonnegative() }).partial();

export const rawWorkoutBlockSchema = z.object({
  exercise: idSchema,
  config: workoutBlockConfigOverrideSchema.optional(),
});

export const rawWorkoutSchema = z.object({
  id: idSchema,
  name: z.string().min(1),
  blocks: z.array(rawWorkoutBlockSchema),
});

export const rawLibrarySchema = z.object({
  version: z.number().int().positive(),
  exercises: z.array(rawExerciseSchema),
  workouts: z.array(rawWorkoutSchema),
});

const timedHoldSetLogSchema = z.object({
  hold_sec: z.number().nonnegative(),
  rest_taken_sec: z.number().nonnegative(),
});

const repsSetLogSchema = z.object({
  reps: z.number().int().nonnegative(),
  weight: z.number().nonnegative().optional(),
  rpe: z.number().min(1).max(10).optional(),
  rest_taken_sec: z.number().nonnegative(),
});

export const rawSessionEntrySchema = z.discriminatedUnion('type', [
  z.object({ exercise: idSchema, type: z.literal('timed_hold'), sets: z.array(timedHoldSetLogSchema) }),
  z.object({ exercise: idSchema, type: z.literal('reps'), sets: z.array(repsSetLogSchema) }),
  z.object({ exercise: idSchema, type: z.literal('rest'), rest_taken_sec: z.number().nonnegative() }),
]);

export const rawSessionSchema = z.object({
  version: z.number().int().positive(),
  id: idSchema,
  workout: idSchema.nullable(),
  started_at: z.string(),
  ended_at: z.string().nullable(),
  entries: z.array(rawSessionEntrySchema),
});
