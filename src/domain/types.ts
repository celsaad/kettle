/**
 * Canonical in-code domain model (camelCase). This is what the app runtime and UI work with.
 * The hand-editable YAML files use snake_case keys per the product plan; src/domain/yaml-mapping.ts
 * bridges the two.
 */

export type ExerciseType = 'hiit' | 'emom' | 'amrap' | 'reps' | 'timed_hold' | 'cardio' | 'rest';

export type HiitConfig = { workSec: number; restSec: number; rounds: number };
export type EmomConfig = { intervalSec: number; totalMinutes: number; targetReps?: number };
export type AmrapConfig = { timeCapSec: number };
export type RepsConfig = { sets: number; targetReps: number; targetWeightKg?: number; restSec: number };
export type TimedHoldConfig = { sets: number; holdSec: number; restSec: number };
export type CardioConfig = { durationSec?: number; distanceMeters?: number };
export type RestConfig = { durationSec: number };

export type Exercise =
  | { id: string; name: string; type: 'hiit'; config: HiitConfig }
  | { id: string; name: string; type: 'emom'; config: EmomConfig }
  | { id: string; name: string; type: 'amrap'; config: AmrapConfig }
  | { id: string; name: string; type: 'reps'; config: RepsConfig }
  | { id: string; name: string; type: 'timed_hold'; config: TimedHoldConfig }
  | { id: string; name: string; type: 'cardio'; config: CardioConfig }
  | { id: string; name: string; type: 'rest'; config: RestConfig };

/** A workout is an ordered list of blocks; each references an exercise, optionally overriding its config. */
export type WorkoutBlock = {
  exerciseId: string;
  configOverride?: Partial<{ durationSec: number }>;
};

export type Workout = {
  id: string;
  name: string;
  blocks: WorkoutBlock[];
};

export type Library = {
  version: number;
  exercises: Exercise[];
  workouts: Workout[];
};

// --- Sessions ---
// Only the entry shapes the current session runner can produce (timed_hold, reps, rest) are
// modeled. Extending to hiit/emom/amrap/cardio logging is future work tied to extending the runner.

export type TimedHoldSetLog = { holdSec: number; restTakenSec: number };
export type RepsSetLog = { reps: number; weightKg?: number; rpe?: number; restTakenSec: number };

export type SessionEntry =
  | { exercise: string; type: 'timed_hold'; sets: TimedHoldSetLog[] }
  | { exercise: string; type: 'reps'; sets: RepsSetLog[] }
  | { exercise: string; type: 'rest'; restTakenSec: number };

export type Session = {
  version: number;
  id: string;
  workout: string | null;
  startedAt: string;
  endedAt: string | null;
  entries: SessionEntry[];
};
