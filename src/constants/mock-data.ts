/**
 * Static placeholder data standing in for the real exercises.yaml / sessions store described in
 * the product plan. No file I/O, no persistence — this is UI-scoped mock data only.
 */

export type ExerciseType = 'hiit' | 'emom' | 'amrap' | 'reps' | 'timed_hold' | 'cardio' | 'rest';

export type Exercise =
  | { id: string; name: string; type: 'hiit'; config: { workSec: number; restSec: number; rounds: number } }
  | {
      id: string;
      name: string;
      type: 'reps';
      config: { sets: number; targetReps: number; targetWeightKg?: number; restSec: number };
    }
  | { id: string; name: string; type: 'timed_hold'; config: { sets: number; holdSec: number; restSec: number } }
  | { id: string; name: string; type: 'cardio'; config: { distanceMeters: number } }
  | { id: string; name: string; type: 'rest'; config: { durationSec: number } };

export const exercises: Exercise[] = [
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 4 } },
  {
    id: 'bench-press',
    name: 'Bench Press',
    type: 'reps',
    config: { sets: 5, targetReps: 5, targetWeightKg: 60, restSec: 120 },
  },
  { id: 'l-sit', name: 'L-Sit Hold', type: 'timed_hold', config: { sets: 4, holdSec: 20, restSec: 60 } },
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 4, targetReps: 8, restSec: 90 } },
  { id: 'row-erg', name: 'Row Erg', type: 'cardio', config: { distanceMeters: 2000 } },
];

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

export const restExercise: Exercise = { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 90 } };

export const workouts: Workout[] = [
  {
    id: 'calisthenics-a',
    name: 'Calisthenics A',
    blocks: [
      { exerciseId: 'l-sit' },
      { exerciseId: 'rest' },
      { exerciseId: 'pullups' },
      { exerciseId: 'rest', configOverride: { durationSec: 120 } },
    ],
  },
];

export function findExercise(id: string): Exercise {
  const exercise = id === 'rest' ? restExercise : exercises.find((candidate) => candidate.id === id);
  if (!exercise) throw new Error(`Unknown exercise id: ${id}`);
  return exercise;
}

export const nextWorkout = {
  workout: workouts[0],
  blockChips: ['L-Sit', 'Rest', 'Pull-ups', 'Rest'],
  summary: '4 blocks · mixed hold + reps · ~28 min',
};

export const recentSessions = [
  { id: 's1', workoutName: 'Calisthenics A', dateLabel: 'Mon', durationLabel: '35 min', setsLabel: '7 sets' },
  { id: 's2', workoutName: 'Push Day', dateLabel: 'Sat', durationLabel: '41 min', setsLabel: '15 sets' },
];

export const historyStats = { sessions: 9, hours: 5.2, sets: 86 };

export const historySessions = [
  {
    id: 'h1',
    day: 22,
    month: 'JUL',
    workoutName: 'Calisthenics A',
    durationLabel: '35 min',
    setsLabel: '7 sets',
    mixed: true,
    entries: [
      { exerciseName: 'L-Sit', summary: '20s · 18s · 15s' },
      { exerciseName: 'Pull-ups', summary: '8 · 7 · 5 reps' },
    ],
  },
  {
    id: 'h2',
    day: 20,
    month: 'JUL',
    workoutName: 'Push Day',
    durationLabel: '41 min',
    setsLabel: '15 sets',
    mixed: false,
    entries: [],
  },
  {
    id: 'h3',
    day: 18,
    month: 'JUL',
    workoutName: 'Calisthenics A',
    durationLabel: '33 min',
    setsLabel: '7 sets',
    mixed: true,
    entries: [],
  },
];
