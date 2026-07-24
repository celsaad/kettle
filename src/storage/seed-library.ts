import type { Library } from '@/domain/types';

/**
 * Written to exercises.yaml the first time the app launches and no library file exists yet, so
 * the app never opens empty. Mirrors what was previously hardcoded mock data, and demonstrates
 * ranges, notes, a circuit block, and a periodized program.
 */
export const seedLibrary: Library = {
  version: 1,
  exercises: [
    { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 4 } },
    {
      id: 'bench-press',
      name: 'Bench Press',
      type: 'reps',
      config: { sets: 5, targetRepsMin: 5, targetWeightKg: 60, restSec: 120 },
    },
    {
      id: 'l-sit',
      name: 'L-Sit Hold',
      type: 'timed_hold',
      config: { sets: 4, holdSecMin: 15, holdSecMax: 25, restSec: 60 },
      notes: 'Escalate toward 25s before adding load.',
    },
    {
      id: 'pullups',
      name: 'Pull-ups',
      type: 'reps',
      config: { sets: 4, targetRepsMin: 6, targetRepsMax: 10, restSec: 90 },
      notes: 'Beat last session within the range before adding a set.',
    },
    {
      id: 'pushups',
      name: 'Push-ups',
      type: 'reps',
      config: { sets: 3, targetRepsMin: 12, targetRepsMax: 20, restSec: 45 },
      notes: 'Stop 2 reps shy of failure.',
    },
    { id: 'row-erg', name: 'Row Erg', type: 'cardio', config: { distanceMeters: 2000 } },
    { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 90 } },
  ],
  workouts: [
    {
      id: 'calisthenics-a',
      name: 'Calisthenics A',
      blocks: [
        { kind: 'exercise', exerciseId: 'l-sit' },
        { kind: 'exercise', exerciseId: 'rest' },
        { kind: 'exercise', exerciseId: 'pullups' },
        { kind: 'exercise', exerciseId: 'rest', configOverride: { durationSec: 120 } },
      ],
    },
    {
      id: 'finisher-circuit',
      name: 'Finisher Circuit',
      blocks: [
        {
          kind: 'circuit',
          id: 'finisher-rounds',
          rounds: 3,
          restBetweenExercisesSec: 15,
          restBetweenRoundsSec: 60,
          // Each member here contributes one visit per round (not its own `sets`) — the circuit's
          // `rounds` is what repeats pushups/l-sit/pullups, not their individual set counts.
          members: [{ exerciseId: 'pushups' }, { exerciseId: 'l-sit' }, { exerciseId: 'pullups' }],
        },
      ],
    },
  ],
  programs: [
    {
      id: 'pull-progression',
      name: '6-Week Pull Progression',
      weeks: [
        { week: 1, workoutId: 'calisthenics-a', notes: 'Baseline — log where you land in each range.' },
        {
          week: 3,
          workoutId: 'calisthenics-a',
          notes: 'Add a 5th set once 2 clean sessions hit the top of the range.',
          overrides: [{ kind: 'exercise', exerciseId: 'pullups', config: { sets: 5 } }],
        },
        {
          week: 6,
          workoutId: 'finisher-circuit',
          notes: 'Deload — cut the finisher circuit down to 2 rounds.',
          overrides: [{ kind: 'block', blockId: 'finisher-rounds', config: { rounds: 2 } }],
        },
      ],
    },
  ],
};
