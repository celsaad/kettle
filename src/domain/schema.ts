/**
 * Zod schemas for the hand-editable/app-written YAML file shapes (snake_case), per the product
 * plan's file formats. Validated on every load and import so malformed files fail with a clear
 * error instead of a crash.
 */
import { z } from 'zod';

const idSchema = z.string().min(1);
const notesSchema = z.string().optional();

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

const repsConfigSchema = z
  .object({
    sets: z.number().int().positive(),
    target_reps_min: z.number().int().positive(),
    target_reps_max: z.number().int().positive().optional(),
    target_weight: z.number().nonnegative().optional(),
    rest_sec: z.number().nonnegative(),
  })
  .refine((config) => config.target_reps_max === undefined || config.target_reps_max >= config.target_reps_min, {
    message: 'target_reps_max must be >= target_reps_min',
    path: ['target_reps_max'],
  });

/**
 * `hold_sec_min` is optional, which is what makes a max-effort hold expressible: with no target the
 * runner counts up and only the Done button ends the set, exactly as `cardio` behaves without
 * `duration_sec`. With a target, the hold ends itself at the top of the range — see
 * `docs/timed-hold-auto-end-plan.md` for why the end is the maximum rather than the minimum.
 *
 * A bare `hold_sec_max` is refused rather than treated as a fixed target: a range needs both ends,
 * and allowing it would give one meaning two spellings.
 */
const timedHoldConfigSchema = z
  .object({
    sets: z.number().int().positive(),
    hold_sec_min: z.number().positive().optional(),
    hold_sec_max: z.number().positive().optional(),
    rest_sec: z.number().nonnegative(),
  })
  .refine((config) => config.hold_sec_max === undefined || config.hold_sec_min !== undefined, {
    message: 'hold_sec_max needs hold_sec_min',
    path: ['hold_sec_max'],
  })
  .refine(
    (config) =>
      config.hold_sec_max === undefined || config.hold_sec_min === undefined || config.hold_sec_max >= config.hold_sec_min,
    {
      message: 'hold_sec_max must be >= hold_sec_min',
      path: ['hold_sec_max'],
    },
  );

const cardioConfigSchema = z.object({
  duration_sec: z.number().positive().optional(),
  distance_meters: z.number().positive().optional(),
});

const restConfigSchema = z.object({
  duration_sec: z.number().nonnegative(),
});

export const rawExerciseSchema = z.discriminatedUnion('type', [
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('hiit'), config: hiitConfigSchema, notes: notesSchema }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('emom'), config: emomConfigSchema, notes: notesSchema }),
  z.object({
    id: idSchema,
    name: z.string().min(1),
    type: z.literal('amrap'),
    config: amrapConfigSchema,
    notes: notesSchema,
  }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('reps'), config: repsConfigSchema, notes: notesSchema }),
  z.object({
    id: idSchema,
    name: z.string().min(1),
    type: z.literal('timed_hold'),
    config: timedHoldConfigSchema,
    notes: notesSchema,
  }),
  z.object({
    id: idSchema,
    name: z.string().min(1),
    type: z.literal('cardio'),
    config: cardioConfigSchema,
    notes: notesSchema,
  }),
  z.object({ id: idSchema, name: z.string().min(1), type: z.literal('rest'), config: restConfigSchema, notes: notesSchema }),
]);

const workoutBlockConfigOverrideSchema = z.object({ duration_sec: z.number().nonnegative() }).partial();

const circuitMemberSchema = z.object({
  exercise: idSchema,
  config: workoutBlockConfigOverrideSchema.optional(),
});

export const rawWorkoutBlockSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('exercise'),
    exercise: idSchema,
    config: workoutBlockConfigOverrideSchema.optional(),
  }),
  z.object({
    type: z.literal('circuit'),
    // Optional: only needed so a program week's overrides can target this circuit's own params
    // (rounds, rest) by id — see rawProgramWeekSchema below. Members run one visit per round
    // regardless of their own `sets`/`hold_sec`/etc. multi-set config — the circuit's `rounds` is
    // the sole repeat driver for a member appearing in a circuit.
    id: idSchema.optional(),
    rounds: z.number().int().positive(),
    rest_between_exercises_sec: z.number().nonnegative().optional(),
    rest_between_rounds_sec: z.number().nonnegative().optional(),
    exercises: z.array(circuitMemberSchema).min(2),
  }),
]);

export const rawWorkoutSchema = z.object({
  id: idSchema,
  name: z.string().min(1),
  blocks: z.array(rawWorkoutBlockSchema),
});

// --- Programs ---
// A program is a periodized wrapper: each week points at a workout and, optionally, a set of
// per-exercise config overrides layered on top of that exercise's base library definition for
// that week only (e.g. "add a 5th set in Week 3"). The base workout/exercises are untouched.

// An override targets exactly one of an exercise (patches that exercise's config, e.g. an added
// set) or a circuit block by its `id` (patches the circuit's own rounds/rest, e.g. a deload week
// dropping a circuit from 3 rounds to 2) — never both.
const programOverrideSchema = z
  .object({
    exercise: idSchema.optional(),
    block: idSchema.optional(),
    config: z.record(z.string(), z.union([z.number(), z.string()])),
  })
  .refine((override) => (override.exercise ? 1 : 0) + (override.block ? 1 : 0) === 1, {
    message: 'override must specify exactly one of `exercise` or `block`',
  });

export const rawProgramWeekSchema = z.object({
  week: z.number().int().positive(),
  // Freeform label ("Monday", "Tue", ...) rather than a strict weekday enum, since not every
  // multi-session-per-week program maps onto a literal calendar week. Omit for the common case of
  // one session per week number.
  day: z.string().optional(),
  workout: idSchema,
  notes: z.string().optional(),
  overrides: z.array(programOverrideSchema).optional(),
});

export const rawProgramSchema = z
  .object({
    id: idSchema,
    name: z.string().min(1),
    weeks: z.array(rawProgramWeekSchema).min(1),
  })
  .refine(
    (program) => {
      const seen = new Set<string>();
      for (const week of program.weeks) {
        const key = `${week.week}::${week.day ?? ''}`;
        if (seen.has(key)) return false;
        seen.add(key);
      }
      return true;
    },
    { message: 'weeks must not repeat the same (week, day) pair', path: ['weeks'] },
  );

export const rawLibrarySchema = z.object({
  version: z.number().int().positive(),
  exercises: z.array(rawExerciseSchema),
  workouts: z.array(rawWorkoutSchema),
  programs: z.array(rawProgramSchema),
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

const emomMinuteLogSchema = z.object({
  reps: z.number().int().nonnegative().optional(),
});

export const rawSessionEntrySchema = z.discriminatedUnion('type', [
  z.object({ exercise: idSchema, type: z.literal('timed_hold'), sets: z.array(timedHoldSetLogSchema) }),
  z.object({ exercise: idSchema, type: z.literal('reps'), sets: z.array(repsSetLogSchema) }),
  z.object({ exercise: idSchema, type: z.literal('rest'), rest_taken_sec: z.number().nonnegative() }),
  z.object({ exercise: idSchema, type: z.literal('hiit'), rounds_completed: z.number().int().nonnegative() }),
  z.object({ exercise: idSchema, type: z.literal('emom'), minutes: z.array(emomMinuteLogSchema) }),
  z.object({
    exercise: idSchema,
    type: z.literal('amrap'),
    rounds_completed: z.number().int().nonnegative(),
    extra_reps: z.number().int().nonnegative().optional(),
  }),
  z.object({
    exercise: idSchema,
    type: z.literal('cardio'),
    duration_sec: z.number().nonnegative().optional(),
    distance_meters: z.number().nonnegative().optional(),
  }),
]);

export const rawSessionSchema = z.object({
  version: z.number().int().positive(),
  id: idSchema,
  workout: idSchema.nullable(),
  // Optional/defaulted so session files written before program tracking existed still parse.
  program: idSchema.nullable().default(null),
  // Same reasoning: added after program tracking itself, so older session files (including ones
  // written between program tracking and this field existing) still parse, just with no tracked week.
  program_week: z.number().int().positive().nullable().default(null),
  program_day: z.string().nullable().default(null),
  started_at: z.string(),
  ended_at: z.string().nullable(),
  entries: z.array(rawSessionEntrySchema),
});
