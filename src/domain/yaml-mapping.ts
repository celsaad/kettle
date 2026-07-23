import { dump, load } from 'js-yaml';
import type { z } from 'zod';

import {
  rawExerciseSchema,
  rawLibrarySchema,
  rawSessionEntrySchema,
  rawSessionSchema,
  rawWorkoutSchema,
} from '@/domain/schema';
import type { Exercise, Library, Session, SessionEntry, Workout, WorkoutBlock } from '@/domain/types';

type RawExercise = z.infer<typeof rawExerciseSchema>;
type RawWorkout = z.infer<typeof rawWorkoutSchema>;
type RawWorkoutBlock = RawWorkout['blocks'][number];
type RawLibrary = z.infer<typeof rawLibrarySchema>;
type RawSessionEntry = z.infer<typeof rawSessionEntrySchema>;
type RawSession = z.infer<typeof rawSessionSchema>;

export type ParseResult<T> = { ok: true; data: T } | { ok: false; error: string };

function formatZodError(error: z.ZodError): string {
  return error.issues.map((issue) => `${issue.path.join('.') || '(root)'}: ${issue.message}`).join('; ');
}

// --- Exercise / Workout / Library ---

function exerciseToDomain(raw: RawExercise): Exercise {
  switch (raw.type) {
    case 'hiit':
      return {
        id: raw.id,
        name: raw.name,
        type: 'hiit',
        config: { workSec: raw.config.work_sec, restSec: raw.config.rest_sec, rounds: raw.config.rounds },
      };
    case 'emom':
      return {
        id: raw.id,
        name: raw.name,
        type: 'emom',
        config: {
          intervalSec: raw.config.interval_sec,
          totalMinutes: raw.config.total_minutes,
          targetReps: raw.config.target_reps,
        },
      };
    case 'amrap':
      return { id: raw.id, name: raw.name, type: 'amrap', config: { timeCapSec: raw.config.time_cap_sec } };
    case 'reps':
      return {
        id: raw.id,
        name: raw.name,
        type: 'reps',
        config: {
          sets: raw.config.sets,
          targetReps: raw.config.target_reps,
          targetWeightKg: raw.config.target_weight,
          restSec: raw.config.rest_sec,
        },
      };
    case 'timed_hold':
      return {
        id: raw.id,
        name: raw.name,
        type: 'timed_hold',
        config: { sets: raw.config.sets, holdSec: raw.config.hold_sec, restSec: raw.config.rest_sec },
      };
    case 'cardio':
      return {
        id: raw.id,
        name: raw.name,
        type: 'cardio',
        config: { durationSec: raw.config.duration_sec, distanceMeters: raw.config.distance_meters },
      };
    case 'rest':
      return { id: raw.id, name: raw.name, type: 'rest', config: { durationSec: raw.config.duration_sec } };
  }
}

function exerciseToRaw(exercise: Exercise): RawExercise {
  switch (exercise.type) {
    case 'hiit':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'hiit',
        config: { work_sec: exercise.config.workSec, rest_sec: exercise.config.restSec, rounds: exercise.config.rounds },
      };
    case 'emom':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'emom',
        config: {
          interval_sec: exercise.config.intervalSec,
          total_minutes: exercise.config.totalMinutes,
          target_reps: exercise.config.targetReps,
        },
      };
    case 'amrap':
      return { id: exercise.id, name: exercise.name, type: 'amrap', config: { time_cap_sec: exercise.config.timeCapSec } };
    case 'reps':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'reps',
        config: {
          sets: exercise.config.sets,
          target_reps: exercise.config.targetReps,
          target_weight: exercise.config.targetWeightKg,
          rest_sec: exercise.config.restSec,
        },
      };
    case 'timed_hold':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'timed_hold',
        config: { sets: exercise.config.sets, hold_sec: exercise.config.holdSec, rest_sec: exercise.config.restSec },
      };
    case 'cardio':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'cardio',
        config: { duration_sec: exercise.config.durationSec, distance_meters: exercise.config.distanceMeters },
      };
    case 'rest':
      return { id: exercise.id, name: exercise.name, type: 'rest', config: { duration_sec: exercise.config.durationSec } };
  }
}

function workoutBlockToDomain(raw: RawWorkoutBlock): WorkoutBlock {
  return {
    exerciseId: raw.exercise,
    configOverride: raw.config ? { durationSec: raw.config.duration_sec } : undefined,
  };
}

function workoutBlockToRaw(block: WorkoutBlock): RawWorkoutBlock {
  return {
    exercise: block.exerciseId,
    config: block.configOverride ? { duration_sec: block.configOverride.durationSec } : undefined,
  };
}

function workoutToDomain(raw: RawWorkout): Workout {
  return { id: raw.id, name: raw.name, blocks: raw.blocks.map(workoutBlockToDomain) };
}

function workoutToRaw(workout: Workout): RawWorkout {
  return { id: workout.id, name: workout.name, blocks: workout.blocks.map(workoutBlockToRaw) };
}

function libraryToDomain(raw: RawLibrary): Library {
  return { version: raw.version, exercises: raw.exercises.map(exerciseToDomain), workouts: raw.workouts.map(workoutToDomain) };
}

function libraryToRaw(library: Library): RawLibrary {
  return { version: library.version, exercises: library.exercises.map(exerciseToRaw), workouts: library.workouts.map(workoutToRaw) };
}

export function parseLibraryYaml(text: string): ParseResult<Library> {
  let parsed: unknown;
  try {
    parsed = load(text);
  } catch (error) {
    return { ok: false, error: `Invalid YAML: ${(error as Error).message}` };
  }
  const result = rawLibrarySchema.safeParse(parsed);
  if (!result.success) return { ok: false, error: formatZodError(result.error) };
  return { ok: true, data: libraryToDomain(result.data) };
}

export function serializeLibraryYaml(library: Library): string {
  return dump(libraryToRaw(library), { noRefs: true, sortKeys: false });
}

// --- Sessions ---

function sessionEntryToDomain(raw: RawSessionEntry): SessionEntry {
  if (raw.type === 'timed_hold') {
    return {
      exercise: raw.exercise,
      type: 'timed_hold',
      sets: raw.sets.map((set) => ({ holdSec: set.hold_sec, restTakenSec: set.rest_taken_sec })),
    };
  }
  if (raw.type === 'reps') {
    return {
      exercise: raw.exercise,
      type: 'reps',
      sets: raw.sets.map((set) => ({ reps: set.reps, weightKg: set.weight, rpe: set.rpe, restTakenSec: set.rest_taken_sec })),
    };
  }
  return { exercise: raw.exercise, type: 'rest', restTakenSec: raw.rest_taken_sec };
}

function sessionEntryToRaw(entry: SessionEntry): RawSessionEntry {
  if (entry.type === 'timed_hold') {
    return {
      exercise: entry.exercise,
      type: 'timed_hold',
      sets: entry.sets.map((set) => ({ hold_sec: set.holdSec, rest_taken_sec: set.restTakenSec })),
    };
  }
  if (entry.type === 'reps') {
    return {
      exercise: entry.exercise,
      type: 'reps',
      sets: entry.sets.map((set) => ({ reps: set.reps, weight: set.weightKg, rpe: set.rpe, rest_taken_sec: set.restTakenSec })),
    };
  }
  return { exercise: entry.exercise, type: 'rest', rest_taken_sec: entry.restTakenSec };
}

function sessionToDomain(raw: RawSession): Session {
  return {
    version: raw.version,
    id: raw.id,
    workout: raw.workout,
    startedAt: raw.started_at,
    endedAt: raw.ended_at,
    entries: raw.entries.map(sessionEntryToDomain),
  };
}

function sessionToRaw(session: Session): RawSession {
  return {
    version: session.version,
    id: session.id,
    workout: session.workout,
    started_at: session.startedAt,
    ended_at: session.endedAt,
    entries: session.entries.map(sessionEntryToRaw),
  };
}

export function parseSessionYaml(text: string): ParseResult<Session> {
  let parsed: unknown;
  try {
    parsed = load(text);
  } catch (error) {
    return { ok: false, error: `Invalid YAML: ${(error as Error).message}` };
  }
  const result = rawSessionSchema.safeParse(parsed);
  if (!result.success) return { ok: false, error: formatZodError(result.error) };
  return { ok: true, data: sessionToDomain(result.data) };
}

export function serializeSessionYaml(session: Session): string {
  return dump(sessionToRaw(session), { noRefs: true, sortKeys: false });
}
