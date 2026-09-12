import type { Exercise, Workout } from '@/domain/types';
import { upcomingOutline, type UpcomingItem } from '@/hooks/session-outline';
// From session-steps, not use-session-runner — see the note atop build-steps.test.ts.
import {
  addSetForMember,
  buildSteps,
  buildStepsForExercise,
  swapExerciseForMember,
  type RunnerStep,
} from '@/hooks/session-steps';

/**
 * Every fixture is real `buildSteps` output rather than a hand-built step array, so these follow the
 * expansion if it changes instead of pinning a copy of it. Assertions go through `shape`, which keeps
 * the fields the sheet renders and drops the step objects themselves.
 */

const exercises: Exercise[] = [
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, targetRepsMax: 10, restSec: 90 } },
  { id: 'pushups', name: 'Push-ups', type: 'reps', config: { sets: 2, targetRepsMin: 12, restSec: 45 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 4 } },
  { id: 'everyminute', name: 'Every Minute', type: 'emom', config: { intervalSec: 60, totalMinutes: 5 } },
  // Twenty 30s intervals in a 10-minute block: the case where an EMOM's intervals and minutes differ.
  { id: 'halfminute', name: 'Half Minute', type: 'emom', config: { intervalSec: 30, totalMinutes: 10 } },
  { id: 'grinder', name: 'Grinder', type: 'amrap', config: { timeCapSec: 600 } },
  { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 120 } },
  // The shape of a circuit-heavy calisthenics library: every member one 40s interval or hold, with
  // the circuit's own rounds doing all the repeating.
  { id: 'arm-circles', name: 'Arm circles', type: 'hiit', config: { workSec: 40, restSec: 0, rounds: 1 } },
  { id: 'inch-worms', name: 'Inch worms', type: 'hiit', config: { workSec: 40, restSec: 0, rounds: 1 } },
  { id: 'pike', name: 'Pike push-ups', type: 'hiit', config: { workSec: 40, restSec: 0, rounds: 1 } },
  { id: 'hollow', name: 'Hollow hold', type: 'timed_hold', config: { sets: 1, holdSecMin: 40, restSec: 0 } },
  { id: 'dead-bug', name: 'Dead bug', type: 'hiit', config: { workSec: 40, restSec: 0, rounds: 1 } },
  { id: 'side-plank-left', name: 'Side plank (left)', type: 'timed_hold', config: { sets: 1, holdSecMin: 40, restSec: 0 } },
  {
    id: 'side-plank-right',
    name: 'Side plank (right)',
    type: 'timed_hold',
    config: { sets: 1, holdSecMin: 40, restSec: 0 },
  },
];

const byId = (id: string) => exercises.find((exercise) => exercise.id === id)!;

function workoutOf(...blocks: Workout['blocks']): Workout {
  return { id: 'w', name: 'W', blocks };
}

const single = (exerciseId: string): Workout['blocks'][number] => ({ kind: 'exercise', exerciseId });

const circuit = (
  rounds: number,
  memberIds: string[],
  rest: { betweenExercises?: number; betweenRounds?: number } = {},
): Workout['blocks'][number] => ({
  kind: 'circuit',
  rounds,
  restBetweenExercisesSec: rest.betweenExercises,
  restBetweenRoundsSec: rest.betweenRounds,
  members: memberIds.map((exerciseId) => ({ exerciseId })),
});

/** A circuit's work step at a position — the only sane way to point at "round 2, member 1". */
function workAt(steps: RunnerStep[], blockIndex: number, round: number, member: number): number {
  const index = steps.findIndex(
    (step) =>
      step.kind !== 'rest' &&
      step.blockIndex === blockIndex &&
      step.circuit?.round === round &&
      step.circuit.member === member,
  );
  if (index < 0) throw new Error(`no work step at block ${blockIndex}, round ${round}, member ${member}`);
  return index;
}

/** The circuit rest that follows the work at a position (rest takes the position of the work before it). */
function restAfter(steps: RunnerStep[], blockIndex: number, round: number, member: number): number {
  const index = steps.findIndex(
    (step) =>
      step.kind === 'rest' &&
      step.blockIndex === blockIndex &&
      step.circuit?.round === round &&
      step.circuit.member === member,
  );
  if (index < 0) throw new Error(`no rest after block ${blockIndex}, round ${round}, member ${member}`);
  return index;
}

function shape(items: UpcomingItem[]) {
  return items.map((item) => {
    switch (item.kind) {
      case 'exercise':
        return { kind: item.kind, name: item.step.exerciseName, left: item.left, unit: item.unit, started: item.started };
      case 'rest':
        return { kind: item.kind, seconds: item.seconds };
      case 'round':
        return {
          kind: item.kind,
          round: item.round,
          rounds: item.rounds,
          members: item.members.map((member) => member.step.exerciseName),
        };
      case 'moreRounds':
        return { kind: item.kind, count: item.count, names: item.names };
      case 'circuit':
        return { kind: item.kind, rounds: item.rounds, names: item.names };
    }
  });
}

const outline = (steps: RunnerStep[], stepIndex: number) => shape(upcomingOutline(steps, stepIndex));

describe('single exercises', () => {
  it('counts the sets left in the exercise on screen, and marks it started', () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    expect(outline(steps, 0)).toEqual([{ kind: 'exercise', name: 'Pull-ups', left: 2, unit: 'set', started: true }]);
  });

  it('counts a hold in sets too', () => {
    const steps = buildSteps(workoutOf(single('lsit')), exercises);
    expect(outline(steps, 0)).toEqual([{ kind: 'exercise', name: 'L-Sit', left: 2, unit: 'set', started: true }]);
  });

  it('still counts it as started while resting between its sets', () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    expect(steps[1].kind).toBe('rest');
    expect(outline(steps, 1)).toEqual([{ kind: 'exercise', name: 'Pull-ups', left: 2, unit: 'set', started: true }]);
  });

  it('lists a standalone Rest block with its own duration, and never an inter-set rest', () => {
    const steps = buildSteps(
      workoutOf(
        single('pullups'),
        { kind: 'exercise', exerciseId: 'rest', configOverride: { durationSec: 60 } },
        single('pushups'),
      ),
      exercises,
    );
    expect(outline(steps, 0)).toEqual([
      { kind: 'exercise', name: 'Pull-ups', left: 2, unit: 'set', started: true },
      { kind: 'rest', seconds: 60 },
      { kind: 'exercise', name: 'Push-ups', left: 2, unit: 'set', started: false },
    ]);
  });

  it('lists the next exercise as not started while resting in the block before it', () => {
    const steps = buildSteps(workoutOf(single('pullups'), single('rest'), single('pushups')), exercises);
    const restBlock = steps.findIndex((step) => step.kind === 'rest' && step.standalone);
    expect(outline(steps, restBlock)).toEqual([
      { kind: 'exercise', name: 'Push-ups', left: 2, unit: 'set', started: false },
    ]);
  });

  it('counts HIIT in rounds, from wherever it is', () => {
    const steps = buildSteps(workoutOf(single('burpees')), exercises);
    // interval, rest, interval, … — index 2 is the second interval.
    expect(steps[2].kind === 'interval' && steps[2].setIndex).toBe(2);
    expect(outline(steps, 2)).toEqual([{ kind: 'exercise', name: 'Burpees', left: 2, unit: 'round', started: true }]);
  });

  it('counts EMOM in minutes and gives one-shot work no unit', () => {
    const steps = buildSteps(workoutOf(single('pullups'), single('everyminute'), single('grinder')), exercises);
    expect(outline(steps, 0)).toEqual([
      { kind: 'exercise', name: 'Pull-ups', left: 2, unit: 'set', started: true },
      { kind: 'exercise', name: 'Every Minute', left: 5, unit: 'minute', started: false },
      { kind: 'exercise', name: 'Grinder', left: 1, unit: null, started: false },
    ]);
  });

  /**
   * An EMOM expands to one step per *interval*, so its count is in minutes only when the interval is
   * one. The interval screen draws the same line ("Minute 3 of 10" against "Interval 3 of 20").
   */
  it('counts an EMOM in intervals when its interval is not a minute', () => {
    const steps = buildSteps(workoutOf(single('pullups'), single('halfminute')), exercises);
    expect(outline(steps, 0)).toEqual([
      { kind: 'exercise', name: 'Pull-ups', left: 2, unit: 'set', started: true },
      { kind: 'exercise', name: 'Half Minute', left: 20, unit: 'interval', started: false },
    ]);
  });
});

describe('the circuit you are in', () => {
  const threeRounds = buildSteps(
    workoutOf(circuit(3, ['pullups', 'pushups', 'lsit'], { betweenExercises: 15, betweenRounds: 60 })),
    exercises,
  );
  const inRoundTwo = [
    { kind: 'round', round: 2, rounds: 3, members: ['Push-ups', 'L-Sit'] },
    { kind: 'moreRounds', count: 1, names: ['Pull-ups', 'Push-ups', 'L-Sit'] },
  ];

  it('spells out the rest of this round, then collapses the rounds after it', () => {
    expect(outline(threeRounds, workAt(threeRounds, 0, 2, 1))).toEqual(inRoundTwo);
  });

  it('reads the same from the rest between two members', () => {
    expect(outline(threeRounds, restAfter(threeRounds, 0, 2, 1))).toEqual(inRoundTwo);
  });

  it('spells out the next round in full from the rest between rounds', () => {
    // The rest between rounds takes the position of the last member of the round it follows.
    expect(outline(threeRounds, restAfter(threeRounds, 0, 1, 3))).toEqual([
      { kind: 'round', round: 2, rounds: 3, members: ['Pull-ups', 'Push-ups', 'L-Sit'] },
      { kind: 'moreRounds', count: 1, names: ['Pull-ups', 'Push-ups', 'L-Sit'] },
    ]);
  });

  it('spells out the next round from the last member of a round, with no rest in between', () => {
    const steps = buildSteps(workoutOf(circuit(3, ['pullups', 'pushups', 'lsit'])), exercises);
    expect(outline(steps, workAt(steps, 0, 1, 3))).toEqual([
      { kind: 'round', round: 2, rounds: 3, members: ['Pull-ups', 'Push-ups', 'L-Sit'] },
      { kind: 'moreRounds', count: 1, names: ['Pull-ups', 'Push-ups', 'L-Sit'] },
    ]);
  });

  it('has nothing after the last member of the last round', () => {
    expect(outline(threeRounds, workAt(threeRounds, 0, 3, 3))).toEqual([]);
  });

  it('gives a single-round circuit no "more rounds"', () => {
    const steps = buildSteps(workoutOf(circuit(1, ['pullups', 'pushups', 'lsit'])), exercises);
    expect(outline(steps, workAt(steps, 0, 1, 1))).toEqual([
      { kind: 'round', round: 1, rounds: 1, members: ['Push-ups', 'L-Sit'] },
    ]);
  });

  /**
   * A HIIT member runs its own full round count on every visit, so each visit is several steps. Counted
   * by steps rather than read off `circuit.round`, two more rounds of a 4-round HIIT read as eight.
   */
  describe('with a HIIT member, which is several steps per visit', () => {
    const steps = buildSteps(workoutOf(circuit(3, ['burpees', 'pullups'])), exercises);
    const expected = [
      { kind: 'round', round: 1, rounds: 3, members: ['Pull-ups'] },
      { kind: 'moreRounds', count: 2, names: ['Burpees', 'Pull-ups'] },
    ];

    it('counts rounds, not steps, and lists the member once', () => {
      expect(outline(steps, 0)).toEqual(expected);
    });

    it('leaves the rest of the visit in progress to the NOW row', () => {
      // Burpees' second interval of round 1: its remaining intervals are not "the next member".
      const secondInterval = steps.findIndex(
        (step) => step.kind === 'interval' && step.memberKey === steps[0].memberKey && step.setIndex === 2,
      );
      expect(secondInterval).toBeGreaterThan(0);
      expect(outline(steps, secondInterval)).toEqual(expected);
    });
  });
});

describe('circuits you have not reached', () => {
  it('collapses to one item with its rounds and members, even when it is next', () => {
    const steps = buildSteps(workoutOf(single('pushups'), circuit(3, ['burpees', 'pullups'])), exercises);
    expect(outline(steps, 0)).toEqual([
      { kind: 'exercise', name: 'Push-ups', left: 1, unit: 'set', started: true },
      { kind: 'circuit', rounds: 3, names: ['Burpees', 'Pull-ups'] },
    ]);
  });

  it('lists only the members that resolved to an exercise', () => {
    const steps = buildSteps(workoutOf(single('pushups'), circuit(2, ['pullups', 'ghost', 'lsit'])), exercises);
    expect(outline(steps, 0).at(-1)).toEqual({ kind: 'circuit', rounds: 2, names: ['Pull-ups', 'L-Sit'] });
  });
});

describe('a circuit-heavy workout', () => {
  // Warm-up ×1, rest, a 3-round series, rest, a 1-round finisher — circuits all the way down.
  const steps = buildSteps(
    workoutOf(
      circuit(1, ['arm-circles', 'inch-worms'], { betweenExercises: 20 }),
      single('rest'),
      circuit(3, ['pike', 'hollow', 'dead-bug'], { betweenExercises: 20, betweenRounds: 60 }),
      single('rest'),
      circuit(1, ['side-plank-left', 'side-plank-right'], { betweenExercises: 20 }),
    ),
    exercises,
  );

  it('spells out only the circuit you are in', () => {
    expect(outline(steps, 0)).toEqual([
      { kind: 'round', round: 1, rounds: 1, members: ['Inch worms'] },
      { kind: 'rest', seconds: 120 },
      { kind: 'circuit', rounds: 3, names: ['Pike push-ups', 'Hollow hold', 'Dead bug'] },
      { kind: 'rest', seconds: 120 },
      { kind: 'circuit', rounds: 1, names: ['Side plank (left)', 'Side plank (right)'] },
    ]);
  });

  it('reads right from a rest in the middle of the series', () => {
    expect(outline(steps, restAfter(steps, 2, 2, 2))).toEqual([
      { kind: 'round', round: 2, rounds: 3, members: ['Dead bug'] },
      { kind: 'moreRounds', count: 1, names: ['Pike push-ups', 'Hollow hold', 'Dead bug'] },
      { kind: 'rest', seconds: 120 },
      { kind: 'circuit', rounds: 1, names: ['Side plank (left)', 'Side plank (right)'] },
    ]);
  });
});

describe('a step list changed mid-session', () => {
  it('lists a substitute under its own name, with the sets it took over', () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    // Swapped from the second set onward: set 1 of Pull-ups is still on screen.
    const swapped = swapExerciseForMember(steps, '0', 2, byId('pushups'), '0:swap:1');
    expect(outline(swapped, 0)).toEqual([{ kind: 'exercise', name: 'Push-ups', left: 2, unit: 'set', started: false }]);
    expect(outline(swapped, 2)).toEqual([{ kind: 'exercise', name: 'Push-ups', left: 1, unit: 'set', started: true }]);
  });

  it('counts an added set', () => {
    const steps = addSetForMember(buildSteps(workoutOf(single('pullups')), exercises), '0');
    expect(outline(steps, 0)).toEqual([{ kind: 'exercise', name: 'Pull-ups', left: 3, unit: 'set', started: true }]);
  });

  it('lists an ad-hoc queue in the order it was added', () => {
    const steps = [
      ...buildStepsForExercise(byId('pullups'), 0, 'adhoc:0'),
      ...buildStepsForExercise(byId('pushups'), 1, 'adhoc:1'),
    ];
    expect(outline(steps, 0)).toEqual([
      { kind: 'exercise', name: 'Pull-ups', left: 2, unit: 'set', started: true },
      { kind: 'exercise', name: 'Push-ups', left: 2, unit: 'set', started: false },
    ]);
  });

  it('ends where a truncated list ends, counting only the rounds that are in it', () => {
    const steps = buildSteps(
      workoutOf(circuit(3, ['pullups', 'pushups', 'lsit'], { betweenExercises: 15, betweenRounds: 60 })),
      exercises,
    );
    // Cut partway through round 2, the way MaxStepsPerWorkout cuts a list.
    const cut = steps.slice(0, workAt(steps, 0, 2, 2));
    expect(outline(cut, 0)).toEqual([
      { kind: 'round', round: 1, rounds: 3, members: ['Push-ups', 'L-Sit'] },
      { kind: 'moreRounds', count: 1, names: ['Pull-ups'] },
    ]);
  });
});

describe('nothing left', () => {
  const steps = buildSteps(workoutOf(single('pullups'), single('pushups')), exercises);

  it('is empty on the last step', () => {
    expect(upcomingOutline(steps, steps.length - 1)).toEqual([]);
  });

  it('is empty, not a throw, past the end — how a parked ad-hoc session looks', () => {
    expect(upcomingOutline(steps, steps.length)).toEqual([]);
    expect(upcomingOutline(steps, steps.length + 5)).toEqual([]);
  });

  it('is empty for an empty list', () => {
    expect(upcomingOutline([], 0)).toEqual([]);
  });
});
