import type { Library } from '@/domain/types';

/**
 * Written to exercises.yaml the first time the app launches and no library file exists yet, so
 * the app never opens empty. Mirrors what was previously hardcoded mock data.
 */
export const seedLibrary: Library = {
  version: 1,
  exercises: [
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
    { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 90 } },
  ],
  workouts: [
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
  ],
};
