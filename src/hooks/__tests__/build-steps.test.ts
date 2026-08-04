import type { Exercise, Workout } from '@/domain/types';
// Imported from session-steps, not use-session-runner: the latter pulls in expo-audio/haptics, which
// initialise native modules on import and fail under jest. That split is the whole point of the module.
import {
  addSetForMember,
  buildSteps,
  dropLastSetForMember,
  setStepsForMember,
  swapExerciseForMember,
} from '@/hooks/session-steps';

/**
 * Assertions are on shape (kind / memberKey / setIndex), never on snapshots or display strings — the
 * step list is a structural contract, and the strings are due to move behind i18n.
 */

const exercises: Exercise[] = [
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, targetRepsMax: 10, restSec: 90 } },
  { id: 'pushups', name: 'Push-ups', type: 'reps', config: { sets: 2, targetRepsMin: 12, restSec: 45 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  { id: 'plank', name: 'Plank', type: 'timed_hold', config: { sets: 2, holdSecMin: 15, holdSecMax: 25, restSec: 30 } },
  { id: 'deadhang', name: 'Dead Hang', type: 'timed_hold', config: { sets: 2, restSec: 30 } },
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 4 } },
  { id: 'clean', name: 'Clean', type: 'emom', config: { intervalSec: 30, totalMinutes: 10, targetReps: 3 } },
  { id: 'everyminute', name: 'Every Minute', type: 'emom', config: { intervalSec: 60, totalMinutes: 5 } },
  { id: 'grinder', name: 'Grinder', type: 'amrap', config: { timeCapSec: 600 } },
  { id: 'row', name: 'Row', type: 'cardio', config: { distanceMeters: 2000 } },
  { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 120 } },
  // rest_sec: 0 — how an author writes back-to-back sets (half of a hand-rolled superset).
  { id: 'nonstop-reps', name: 'Nonstop Reps', type: 'reps', config: { sets: 3, targetRepsMin: 8, restSec: 0 } },
  { id: 'nonstop-hold', name: 'Nonstop Hold', type: 'timed_hold', config: { sets: 3, holdSecMin: 20, restSec: 0 } },
  { id: 'nonstop-hiit', name: 'Nonstop HIIT', type: 'hiit', config: { workSec: 30, restSec: 0, rounds: 3 } },
  { id: 'norest', name: 'No Rest', type: 'rest', config: { durationSec: 0 } },
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

  /**
   * Where each of the three config shapes ends. The end is the *top* of the range rather than the
   * bottom: scaled to the minimum, a range hold would stop at the first second the range describes,
   * making the range itself unreachable.
   */
  describe('holdEndSec', () => {
    const firstHold = (exerciseId: string) => {
      const step = buildSteps(workoutOf(single(exerciseId)), exercises)[0];
      return step.kind === 'hold' ? step : null;
    };

    it('is the maximum when the target is a range', () => {
      expect(firstHold('plank')?.holdEndSec).toBe(25);
      expect(firstHold('plank')?.holdTargetSec).toBe(15);
    });

    it('is the minimum when that is the only target', () => {
      expect(firstHold('lsit')?.holdEndSec).toBe(15);
    });

    it('is absent when there is no target, which is what makes a max-effort hold possible', () => {
      expect(firstHold('deadhang')?.holdEndSec).toBeUndefined();
      expect(firstHold('deadhang')?.holdTargetSec).toBeUndefined();
    });

    /**
     * A 0 end would auto-advance on the first tick and skip the hold outright. Nothing validates a
     * program week's override config — the override schema types it as a free record of numbers and
     * the in-app override editor doesn't call validateConfig — so this arrives from a real path, and
     * degrading to a hold you end by hand beats one that vanishes mid-workout.
     */
    it('is absent for a 0 target, which an unvalidated program override can still write', () => {
      const zeroHold: Exercise = {
        id: 'zero',
        name: 'Zero',
        type: 'timed_hold',
        config: { sets: 1, holdSecMin: 0, restSec: 30 },
      };
      const steps = buildSteps(workoutOf(single('zero')), [...exercises, zeroHold]);
      expect(steps[0].kind === 'hold' && steps[0].holdEndSec).toBeUndefined();
      // Still a runnable step, not a skipped one.
      expect(steps[0].kind).toBe('hold');
    });
  });
});

describe('hiit blocks', () => {
  it('emits one interval per round with rest between, not after', () => {
    const steps = buildSteps(workoutOf(single('burpees')), exercises);
    expect(steps.map((step) => step.kind)).toEqual(['interval', 'rest', 'interval', 'rest', 'interval', 'rest', 'interval']);
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

/**
 * The regression: inter-set rest was emitted unconditionally, so `rest_sec: 0` produced a real
 * zero-second rest step. That isn't a no-op — the runner shows the rest screen and `remaining <= 0`
 * fires on the next tick, so back-to-back sets got a flash of rest UI, the completion chime and a
 * scheduled "Rest complete" notification between every one of them.
 */
describe('zero-length rest', () => {
  it('omits the rest step between reps sets', () => {
    const steps = buildSteps(workoutOf(single('nonstop-reps')), exercises);
    expect(steps.map((step) => step.kind)).toEqual(['reps', 'reps', 'reps']);
  });

  it('omits the rest step between holds', () => {
    const steps = buildSteps(workoutOf(single('nonstop-hold')), exercises);
    expect(steps.map((step) => step.kind)).toEqual(['hold', 'hold', 'hold']);
  });

  it('omits the rest step between hiit rounds', () => {
    const steps = buildSteps(workoutOf(single('nonstop-hiit')), exercises);
    expect(steps.map((step) => step.kind)).toEqual(['interval', 'interval', 'interval']);
  });

  it('still numbers the sets it does emit', () => {
    const steps = buildSteps(workoutOf(single('nonstop-reps')), exercises);
    expect(steps.map((step) => (step.kind === 'reps' ? step.setIndex : null))).toEqual([1, 2, 3]);
  });

  // Exempt: a standalone Rest block is its own logged session entry, and the schema permits
  // `duration_sec: 0`. Dropping it would lose the entry, not just a pause.
  it('keeps a standalone rest block of zero seconds', () => {
    const steps = buildSteps(workoutOf(single('norest')), exercises);
    expect(steps.map((step) => step.kind)).toEqual(['rest']);
    expect(steps[0].kind === 'rest' && steps[0].standalone).toBe(true);
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
    expect(work.map((step) => step.exerciseId)).toEqual(['pushups', 'lsit', 'pushups', 'lsit', 'pushups', 'lsit']);
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

  // A member is visited once per round, so its own `sets` is meaningless here — but reporting the
  // literal 1-of-1 of that single visit left the runner saying "Set 1 of 1" on every round, with no
  // sense of progress through the circuit. The round position is the honest number: a member visited
  // once per round across 3 rounds is doing 3 sets of that exercise.
  it('numbers a member visit by its round, not by the visit being a single set', () => {
    const steps = buildSteps(workoutOf(circuit()), exercises);
    const positions = steps.flatMap((step) =>
      step.kind !== 'rest' && step.exerciseId === 'pushups' ? [[step.setIndex, step.setTotal]] : [],
    );
    expect(positions).toEqual([
      [1, 3],
      [2, 3],
      [3, 3],
    ]);
  });

  // Two rounds against lsit's own `sets: 3`, so the total can only have come from the circuit.
  it('takes the total from the circuit rounds, not from the member own sets', () => {
    const pair: Workout['blocks'][number] = {
      kind: 'circuit',
      rounds: 2,
      members: [{ exerciseId: 'lsit' }, { exerciseId: 'pushups' }],
    };
    const holds = buildSteps(workoutOf(pair), exercises).filter((step) => step.kind === 'hold');
    expect(holds.map((step) => step.setIndex)).toEqual([1, 2]);
    expect(holds.every((step) => step.setTotal === 2)).toBe(true);
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

describe('adding a set mid-session', () => {
  it('appends one more set of that member and renumbers the total', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    const after = addSetForMember(before, '0');

    const sets = after.filter((step) => step.kind === 'reps');
    expect(sets.map((step) => step.setIndex)).toEqual([1, 2, 3, 4]);
    expect(sets.every((step) => step.setTotal === 4)).toBe(true);
  });

  it('leads the new set with a rest, matching the ones already between sets', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    const after = addSetForMember(before, '0');

    expect(after.map((step) => step.kind)).toEqual(['reps', 'rest', 'reps', 'rest', 'reps', 'rest', 'reps']);
    const rests = after.filter((step) => step.kind === 'rest');
    expect(rests.every((step) => step.kind === 'rest' && step.seconds === 90)).toBe(true);
  });

  /**
   * The back-to-back case, which falls out of cloning the member's own rest rather than rebuilding one
   * from config: `rest_sec: 0` emits no rest steps at all, so there is nothing to clone. A rebuilt
   * 0-second rest would put a flash of rest UI and a completion chime between every superset set.
   */
  it('adds no rest to an exercise that has none between its sets', () => {
    const before = buildSteps(workoutOf(single('nonstop-reps')), exercises);
    const after = addSetForMember(before, '0');

    expect(after.map((step) => step.kind)).toEqual(['reps', 'reps', 'reps', 'reps']);
  });

  it('works on holds as well as reps', () => {
    const before = buildSteps(workoutOf(single('lsit')), exercises);
    const after = addSetForMember(before, '0');

    const holds = after.filter((step) => step.kind === 'hold');
    expect(holds.map((step) => step.setIndex)).toEqual([1, 2, 3, 4]);
    expect(holds.every((step) => step.setTotal === 4)).toBe(true);
  });

  // memberKey is the primary key for every accumulating log in the runner (memberSetsRef,
  // entryIndexRef and the rest). Reissuing one for work already logged is a data bug, not a display one.
  it('keeps the member key of the exercise it is extending', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    const after = addSetForMember(before, '0');

    expect(after.every((step) => step.memberKey === '0')).toBe(true);
  });

  it('leaves every other block alone', () => {
    const before = buildSteps(workoutOf(single('pullups'), single('pushups')), exercises);
    const after = addSetForMember(before, '0');

    // Kind first, then member: TypeScript infers a type predicate from a simple `filter` callback but
    // not from a compound one, so the narrowing that gives `setIndex` its type is lost in a single
    // `&&` pass.
    const pushups = after.filter((step) => step.kind === 'reps').filter((step) => step.memberKey === '1');
    expect(pushups.map((step) => step.setIndex)).toEqual([1, 2]);
    expect(pushups.every((step) => step.setTotal === 2)).toBe(true);
  });

  it('does nothing for a member that has no sets in the list', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    expect(addSetForMember(before, 'nope')).toEqual(before);
  });
});

describe('dropping a set mid-session', () => {
  it('removes the last set and the rest that led into it, renumbering the total', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    const after = dropLastSetForMember(before, '0');

    expect(after.map((step) => step.kind)).toEqual(['reps', 'rest', 'reps']);
    const sets = after.filter((step) => step.kind === 'reps');
    expect(sets.map((step) => step.setIndex)).toEqual([1, 2]);
    expect(sets.every((step) => step.setTotal === 2)).toBe(true);
  });

  it('removes only the set when there was no rest before it', () => {
    const before = buildSteps(workoutOf(single('nonstop-reps')), exercises);
    const after = dropLastSetForMember(before, '0');

    expect(after.map((step) => step.kind)).toEqual(['reps', 'reps']);
  });

  // The runner holds the real floor (what has been logged, plus the set in progress). This is the
  // floor below which the list itself stops making sense.
  it('never drops a member to zero sets', () => {
    const before = buildSteps(workoutOf(single('pushups')), exercises);
    const once = dropLastSetForMember(before, '0');
    const twice = dropLastSetForMember(once, '0');

    expect(once.filter((step) => step.kind === 'reps')).toHaveLength(1);
    expect(twice).toEqual(once);
  });

  it('leaves the rest of the workout untouched', () => {
    const before = buildSteps(workoutOf(single('pullups'), single('pushups')), exercises);
    const after = dropLastSetForMember(before, '1');

    const pullups = after.filter((step) => step.kind === 'reps').filter((step) => step.memberKey === '0');
    expect(pullups.map((step) => step.setIndex)).toEqual([1, 2, 3]);
    expect(pullups.every((step) => step.setTotal === 3)).toBe(true);
  });

  it('round-trips: adding then dropping returns the list it started from', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    expect(dropLastSetForMember(addSetForMember(before, '0'), '0')).toEqual(before);
  });
});

describe('setStepsForMember', () => {
  it(`counts a member's own work steps, not its rests`, () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    expect(setStepsForMember(steps, '0')).toBe(3);
  });

  it('is zero for a member that is not in the list', () => {
    expect(setStepsForMember(buildSteps(workoutOf(single('pullups')), exercises), 'nope')).toBe(0);
  });
});

describe('swapping an exercise mid-session', () => {
  const pushupsExercise = exercises.find((exercise) => exercise.id === 'pushups')!;
  const nonstop = exercises.find((exercise) => exercise.id === 'nonstop-reps')!;

  it('replaces the rest of the member and leaves what came before it alone', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    // Index 2 is set 2 of pullups: set 1 and its rest are behind us.
    const after = swapExerciseForMember(before, '0', 2, pushupsExercise, '0~swap1');

    expect(after.slice(0, 2)).toEqual(before.slice(0, 2));
    expect(after[0]).toMatchObject({ exerciseId: 'pullups', memberKey: '0' });
    expect(after.slice(2).every((step) => step.exerciseId === 'pushups')).toBe(true);
  });

  it('gives the substitute the remaining count, not its own configured sets', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    // Two of the three pullup sets are still ahead; pushups is a 2-set exercise anyway, so this also
    // pins that the count comes from what is left rather than coinciding with it.
    const after = swapExerciseForMember(before, '0', 2, pushupsExercise, '0~swap1');

    // Kind first: TypeScript infers a type predicate from a simple `filter` callback but not a
    // compound one, so `&&` here would lose the narrowing that gives `setIndex` its type.
    const swapped = after.filter((step) => step.kind === 'reps').filter((step) => step.memberKey === '0~swap1');
    expect(swapped).toHaveLength(2);
    expect(swapped.map((step) => step.setIndex)).toEqual([1, 2]);
    expect(swapped.every((step) => step.setTotal === 2)).toBe(true);
  });

  it('grows the substitute when more sets remain than it prescribes', () => {
    // Four sets of pullups, swapped on set 1: four remain, pushups prescribes two.
    const before = addSetForMember(buildSteps(workoutOf(single('pullups')), exercises), '0');
    const after = swapExerciseForMember(before, '0', 0, pushupsExercise, '0~swap1');

    const swapped = after.filter((step) => step.kind === 'reps').filter((step) => step.memberKey === '0~swap1');
    expect(swapped).toHaveLength(4);
    expect(swapped.map((step) => step.setIndex)).toEqual([1, 2, 3, 4]);
  });

  // The whole point of the substitution: a different exercise, with its own prescription.
  it('takes targets and rest from the substitute', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    const after = swapExerciseForMember(before, '0', 0, pushupsExercise, '0~swap1');

    const first = after[0];
    if (first.kind !== 'reps') throw new Error('expected a reps step');
    expect(first).toMatchObject({ exerciseName: 'Push-ups', targetReps: 12 });
    const rest = after.find((step) => step.kind === 'rest');
    expect(rest).toMatchObject({ seconds: 45 });
  });

  /**
   * Invariant 1: every accumulating log in the runner is keyed by `memberKey`. Reusing the original
   * would make the substitute's sets grow the *replaced* exercise's session entry.
   */
  it('gives the substitute a member key of its own', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    const after = swapExerciseForMember(before, '0', 2, pushupsExercise, '0~swap1');

    expect(after.some((step) => step.memberKey === '0')).toBe(true);
    expect(after.some((step) => step.memberKey === '0~swap1')).toBe(true);
    expect(after.filter((step) => step.memberKey === '0~swap1').every((step) => step.exerciseId === 'pushups')).toBe(true);
  });

  it('leaves the blocks around it untouched', () => {
    const before = buildSteps(workoutOf(single('pullups'), single('plank')), exercises);
    const after = swapExerciseForMember(before, '0', 0, pushupsExercise, '0~swap1');

    const planks = after.filter((step) => step.memberKey === '1');
    expect(planks).toEqual(before.filter((step) => step.memberKey === '1'));
  });

  // Same rule as adding a set: a substitute authored rest_sec: 0 emits no rest steps.
  it('adds no rest for a back-to-back substitute', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    const after = swapExerciseForMember(before, '0', 0, nonstop, '0~swap1');

    expect(after.map((step) => step.kind)).toEqual(['reps', 'reps', 'reps']);
  });

  it('does nothing when the member has nothing left to replace', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    expect(swapExerciseForMember(before, '0', before.length, pushupsExercise, '0~swap1')).toEqual(before);
  });

  it('does nothing for a member that is not in the list', () => {
    const before = buildSteps(workoutOf(single('pullups')), exercises);
    expect(swapExerciseForMember(before, 'nope', 0, pushupsExercise, '0~swap1')).toEqual(before);
  });
});
