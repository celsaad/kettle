import type { Exercise, Workout } from '@/domain/types';
// Imported from session-steps, not use-session-runner: the latter pulls in expo-audio/haptics, which
// initialise native modules on import and fail under jest. That split is the whole point of the module.
import { buildSteps } from '@/hooks/session-steps';

/**
 * Assertions are on shape (kind / memberKey / setIndex), never on snapshots or display strings — the
 * step list is a structural contract, and the strings are due to move behind i18n.
 */

const exercises: Exercise[] = [
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, targetRepsMax: 10, restSec: 90 } },
  { id: 'pushups', name: 'Push-ups', type: 'reps', config: { sets: 2, targetRepsMin: 12, restSec: 45 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 4 } },
  { id: 'clean', name: 'Clean', type: 'emom', config: { intervalSec: 30, totalMinutes: 10, targetReps: 3 } },
  { id: 'everyminute', name: 'Every Minute', type: 'emom', config: { intervalSec: 60, totalMinutes: 5 } },
  { id: 'grinder', name: 'Grinder', type: 'amrap', config: { timeCapSec: 600 } },
  { id: 'row', name: 'Row', type: 'cardio', config: { distanceMeters: 2000 } },
  { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 120 } },
];

function workoutOf(...blocks: Workout['blocks']): Workout {
  return { id: 'w', name: 'W', blocks };
}

const single = (exerciseId: string): Workout['blocks'][number] => ({ kind: 'exercise', exerciseId });

describe('reps blocks', () => {
  it('interleaves rest between sets but not after the last one', () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    expect(steps.map((step) => step.kind)).toEqual(['reps', 'rest', 'reps', 'rest', 'reps']);
  });

  it('numbers sets from 1 and reports a stable total', () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    const reps = steps.filter((step) => step.kind === 'reps');
    expect(reps.map((step) => step.setIndex)).toEqual([1, 2, 3]);
    expect(reps.every((step) => step.setTotal === 3)).toBe(true);
  });

  it('carries the configured target weight onto each set', () => {
    const weighted: Exercise = {
      id: 'bench',
      name: 'Bench',
      type: 'reps',
      config: { sets: 2, targetRepsMin: 5, targetWeightKg: 60, restSec: 120 },
    };
    const steps = buildSteps(workoutOf(single('bench')), [...exercises, weighted]);
    const reps = steps.filter((step) => step.kind === 'reps');
    expect(reps.map((step) => step.targetWeightKg)).toEqual([60, 60]);
  });
});

describe('timed_hold blocks', () => {
  it('interleaves rest between holds', () => {
    const steps = buildSteps(workoutOf(single('lsit')), exercises);
    expect(steps.map((step) => step.kind)).toEqual(['hold', 'rest', 'hold', 'rest', 'hold']);
  });
});

describe('hiit blocks', () => {
  it('emits one interval per round with rest between, not after', () => {
    const steps = buildSteps(workoutOf(single('burpees')), exercises);
    expect(steps.map((step) => step.kind)).toEqual([
      'interval', 'rest', 'interval', 'rest', 'interval', 'rest', 'interval',
    ]);
    const work = steps.filter((step) => step.kind === 'interval');
    expect(work.every((step) => step.kind === 'interval' && step.targetSec === 40)).toBe(true);
  });
});

describe('emom blocks', () => {
  // The regression: the loop ran `totalMinutes` times at `intervalSec` each, so a 30s interval over
  // 10 minutes produced 10 intervals — 5 minutes of work labelled "of 10". Interval count has to come
  // from the total duration divided by the interval, not from the minute count.
  it('derives interval count from total duration, not from totalMinutes', () => {
    const steps = buildSteps(workoutOf(single('clean')), exercises);
    expect(steps).toHaveLength(20);
    expect(steps.every((step) => step.kind === 'interval' && step.targetSec === 30)).toBe(true);
  });

  it('covers the configured duration exactly', () => {
    const steps = buildSteps(workoutOf(single('clean')), exercises);
    const total = steps.reduce((sum, step) => sum + (step.kind === 'interval' ? step.targetSec : 0), 0);
    expect(total).toBe(10 * 60);
  });

  it('still emits one interval per minute for a literal every-minute emom', () => {
    const steps = buildSteps(workoutOf(single('everyminute')), exercises);
    expect(steps).toHaveLength(5);
    expect(steps.every((step) => step.kind === 'interval' && step.setTotal === 5)).toBe(true);
  });

  it('runs once rather than vanishing when the interval outlasts the block', () => {
    const oversized: Exercise = { id: 'odd', name: 'Odd', type: 'emom', config: { intervalSec: 90, totalMinutes: 1 } };
    expect(buildSteps(workoutOf(single('odd')), [...exercises, oversized])).toHaveLength(1);
  });
});

describe('single-shot blocks', () => {
  it('emits one step for amrap, cardio, and standalone rest', () => {
    expect(buildSteps(workoutOf(single('grinder')), exercises)).toHaveLength(1);
    expect(buildSteps(workoutOf(single('row')), exercises)).toHaveLength(1);
    expect(buildSteps(workoutOf(single('rest')), exercises)).toHaveLength(1);
  });

  it('counts up for cardio with no configured duration', () => {
    const steps = buildSteps(workoutOf(single('row')), exercises);
    expect(steps[0].kind === 'interval' && steps[0].countUp).toBe(true);
  });
});

describe('circuits', () => {
  const circuit = (): Workout['blocks'][number] => ({
    kind: 'circuit',
    rounds: 3,
    restBetweenExercisesSec: 15,
    restBetweenRoundsSec: 60,
    members: [{ exerciseId: 'pushups' }, { exerciseId: 'lsit' }],
  });

  it('visits members round-robin, once per round, ignoring their own set counts', () => {
    const steps = buildSteps(workoutOf(circuit()), exercises);
    const work = steps.filter((step) => step.kind !== 'rest');
    expect(work.map((step) => step.exerciseId)).toEqual([
      'pushups', 'lsit', 'pushups', 'lsit', 'pushups', 'lsit',
    ]);
  });

  it('gives each member a memberKey stable across rounds, so sets accumulate into one entry', () => {
    const steps = buildSteps(workoutOf(circuit()), exercises);
    const keysFor = (id: string) =>
      new Set(steps.filter((step) => step.kind !== 'rest' && step.exerciseId === id).map((step) => step.memberKey));
    expect(keysFor('pushups').size).toBe(1);
    expect(keysFor('lsit').size).toBe(1);
    expect([...keysFor('pushups')][0]).not.toBe([...keysFor('lsit')][0]);
  });

  it('rests between members and between rounds, but not after the final round', () => {
    const steps = buildSteps(workoutOf(circuit()), exercises);
    expect(steps.at(-1)?.kind).not.toBe('rest');
    const restLengths = steps.flatMap((step) => (step.kind === 'rest' ? [step.seconds] : []));
    expect(restLengths).toEqual([15, 60, 15, 60, 15]);
  });

  it('does not interleave a member exercise own per-set rest', () => {
    const steps = buildSteps(workoutOf(circuit()), exercises);
    // pushups configures restSec: 45; none of the circuit rests should be that.
    expect(steps.every((step) => step.kind !== 'rest' || step.seconds !== 45)).toBe(true);
  });
});

describe('degenerate workouts', () => {
  it('returns no steps for a workout with no blocks', () => {
    expect(buildSteps(workoutOf(), exercises)).toEqual([]);
  });

  it('returns no steps when a block references a missing exercise', () => {
    expect(buildSteps(workoutOf(single('nope')), exercises)).toEqual([]);
  });

  it('returns no steps when every exercise is configured to zero work', () => {
    const zeroed: Exercise = { id: 'zero', name: 'Zero', type: 'reps', config: { sets: 0, targetRepsMin: 5, restSec: 30 } };
    expect(buildSteps(workoutOf(single('zero')), [...exercises, zeroed])).toEqual([]);
  });
});
