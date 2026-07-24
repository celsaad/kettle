/**
 * Canonical in-code domain model (camelCase). This is what the app runtime and UI work with.
 * The hand-editable YAML files use snake_case keys per the product plan; src/domain/yaml-mapping.ts
 * bridges the two.
 */

export type ExerciseType = 'hiit' | 'emom' | 'amrap' | 'reps' | 'timed_hold' | 'cardio' | 'rest';

export type HiitConfig = { workSec: number; restSec: number; rounds: number };
export type EmomConfig = { intervalSec: number; totalMinutes: number; targetReps?: number };
export type AmrapConfig = { timeCapSec: number };
/** targetRepsMax is optional: omit it for a fixed target, set it for a double-progression range. */
export type RepsConfig = {
  sets: number;
  targetRepsMin: number;
  targetRepsMax?: number;
  targetWeightKg?: number;
  restSec: number;
};
/** holdSecMax is optional: omit it for a fixed target, set it for a double-progression range. */
export type TimedHoldConfig = { sets: number; holdSecMin: number; holdSecMax?: number; restSec: number };
export type CardioConfig = { durationSec?: number; distanceMeters?: number };
export type RestConfig = { durationSec: number };

export type Exercise =
  | { id: string; name: string; type: 'hiit'; config: HiitConfig; notes?: string }
  | { id: string; name: string; type: 'emom'; config: EmomConfig; notes?: string }
  | { id: string; name: string; type: 'amrap'; config: AmrapConfig; notes?: string }
  | { id: string; name: string; type: 'reps'; config: RepsConfig; notes?: string }
  | { id: string; name: string; type: 'timed_hold'; config: TimedHoldConfig; notes?: string }
  | { id: string; name: string; type: 'cardio'; config: CardioConfig; notes?: string }
  | { id: string; name: string; type: 'rest'; config: RestConfig; notes?: string };

export type BlockConfigOverride = Partial<{ durationSec: number }>;

export type CircuitMember = { exerciseId: string; configOverride?: BlockConfigOverride };

/**
 * A workout block is either a single exercise, or a circuit: a round-robin group of exercises run
 * back-to-back for a number of rounds (A,B,C,A,B,C,...), with rest configurable both between
 * exercises within a round (0 for a true superset) and between rounds.
 */
export type WorkoutBlock =
  | { kind: 'exercise'; exerciseId: string; configOverride?: BlockConfigOverride }
  | {
      kind: 'circuit';
      /** Optional: lets a program week's overrides target this circuit's own rounds/rest by id. */
      id?: string;
      rounds: number;
      restBetweenExercisesSec?: number;
      restBetweenRoundsSec?: number;
      members: CircuitMember[];
    };

export type Workout = {
  id: string;
  name: string;
  blocks: WorkoutBlock[];
};

// --- Programs ---
// A periodized wrapper around workouts: each week points at a workout and may layer per-exercise
// config overrides on top of the base library definition for that week only.

export type ProgramOverride =
  | { kind: 'exercise'; exerciseId: string; config: Record<string, number | string> }
  | { kind: 'block'; blockId: string; config: Record<string, number | string> };
export type ProgramWeek = { week: number; day?: string; workoutId: string; notes?: string; overrides?: ProgramOverride[] };
export type Program = { id: string; name: string; weeks: ProgramWeek[] };

export type Library = {
  version: number;
  exercises: Exercise[];
  workouts: Workout[];
  programs: Program[];
};

// --- Sessions ---
// Only the entry shapes the current session runner can produce are modeled here.

export type TimedHoldSetLog = { holdSec: number; restTakenSec: number };
export type RepsSetLog = { reps: number; weightKg?: number; rpe?: number; restTakenSec: number };
export type EmomMinuteLog = { reps?: number };

export type SessionEntry =
  | { exercise: string; type: 'timed_hold'; sets: TimedHoldSetLog[] }
  | { exercise: string; type: 'reps'; sets: RepsSetLog[] }
  | { exercise: string; type: 'rest'; restTakenSec: number }
  | { exercise: string; type: 'hiit'; roundsCompleted: number }
  | { exercise: string; type: 'emom'; minutes: EmomMinuteLog[] }
  | { exercise: string; type: 'amrap'; roundsCompleted: number; extraReps?: number }
  | { exercise: string; type: 'cardio'; durationSec?: number; distanceMeters?: number };

export type Session = {
  version: number;
  id: string;
  workout: string | null;
  /** The program this session was started under (via a program week), if any. */
  program: string | null;
  /** Which week/day of `program` this was, if started via a program week — null for a plain workout. */
  programWeek: number | null;
  programDay: string | null;
  startedAt: string;
  endedAt: string | null;
  entries: SessionEntry[];
};
