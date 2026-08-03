import { act, renderHook } from '@testing-library/react-native';
import { AppState } from 'react-native';

import type { Exercise, Session, SessionEntry, Workout } from '@/domain/types';
import { useSessionRunner } from '@/hooks/use-session-runner';

/**
 * The runner is the app's highest-risk file — wall-clock timing, an append-only write path, and a
 * one-level undo. These tests exercise it through the real hook with Jest's modern fake timers, which
 * mock `Date.now()` and `setInterval` from the same virtual clock; that's why the wall-clock design
 * needs no clock injection to be testable.
 *
 * Deliberately no StrictMode: the hook's advance()/goPrev() are not idempotent by design (they append
 * to a session), so a double-invoking wrapper would report failures that don't exist in the app.
 */

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(() => Promise.resolve()),
  ImpactFeedbackStyle: { Medium: 'medium' },
}));

// `mock`-prefixed names are the only out-of-scope variables jest.mock factories may close over, since
// those factories are hoisted above these declarations.
const mockPlayTick = jest.fn();
const mockPlayExerciseChange = jest.fn();
const mockPlayMilestone = jest.fn();
jest.mock('@/hooks/use-session-sounds', () => ({
  useSessionSounds: () => ({
    playTick: mockPlayTick,
    playExerciseChange: mockPlayExerciseChange,
    playMilestone: mockPlayMilestone,
  }),
}));

// Typed parameters, not inferred from a zero-arg factory — the assertion below reads the third
// argument (the delay in seconds), which an inferred `[]` tuple makes inaccessible.
const mockScheduleNotification = jest.fn((_title: string, _body: string, _seconds: number) => Promise.resolve('notif-id'));
const mockCancelNotification = jest.fn();
jest.mock('@/hooks/safe-notifications', () => ({
  requestNotificationPermissions: jest.fn(() => Promise.resolve()),
  scheduleStepCompleteNotification: (title: string, body: string, seconds: number) =>
    mockScheduleNotification(title, body, seconds),
  cancelNotification: (id: string) => mockCancelNotification(id),
}));

/**
 * An in-memory stand-in for the zustand store. Mocked at our own boundary rather than at
 * expo-file-system: the store's contract is small, stable and ours, and this keeps the assertions
 * about *what got logged* rather than about file writes.
 */
let mockSession: Session;
let mockCompleted = false;

function resetStore() {
  mockSession = {
    version: 1,
    id: 'test-session',
    workout: 'w',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt: new Date().toISOString(),
    endedAt: null,
    entries: [],
  };
  mockCompleted = false;
}

jest.mock('@/state/session-history-store', () => ({
  useSessionHistoryStore: (selector: (state: unknown) => unknown) =>
    selector({
      startSession: () => mockSession,
      logEntry: (current: Session, entry: SessionEntry) => {
        mockSession = { ...current, entries: [...current.entries, entry] };
        return mockSession;
      },
      replaceEntry: (current: Session, index: number, entry: SessionEntry) => {
        mockSession = {
          ...current,
          entries: current.entries.map((existing, position) => (position === index ? entry : existing)),
        };
        return mockSession;
      },
      removeLastEntry: (current: Session) => {
        mockSession = { ...current, entries: current.entries.slice(0, -1) };
        return mockSession;
      },
      completeSession: (current: Session) => {
        mockCompleted = true;
        mockSession = { ...current, endedAt: new Date().toISOString() };
        return mockSession;
      },
    }),
}));

const exercises: Exercise[] = [
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 3 } },
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, targetWeightKg: 20, restSec: 90 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  // The three hold shapes, which end differently: `lsit` at its fixed 15s, `plank` at the top of its
  // range, and `deadhang` not at all.
  { id: 'plank', name: 'Plank', type: 'timed_hold', config: { sets: 2, holdSecMin: 15, holdSecMax: 25, restSec: 30 } },
  { id: 'deadhang', name: 'Dead Hang', type: 'timed_hold', config: { sets: 2, restSec: 30 } },
  { id: 'grinder', name: 'Grinder', type: 'amrap', config: { timeCapSec: 300 } },
  // rest_sec: 0 — back-to-back sets, how half a hand-rolled superset is written.
  { id: 'dips', name: 'Dips', type: 'reps', config: { sets: 3, targetRepsMin: 8, restSec: 0 } },
];

const workoutOf = (...blocks: Workout['blocks']): Workout => ({ id: 'w', name: 'W', blocks });
const single = (exerciseId: string): Workout['blocks'][number] => ({ kind: 'exercise', exerciseId });

// renderHook returns a Promise in RNTL 14 (React 19 made rendering async-aware), so this awaits it —
// without that, destructuring yields undefined and every assertion fails on `result.current`.
async function mount(workout: Workout, onComplete = jest.fn()) {
  const { result, rerender, unmount } = await renderHook(() =>
    useSessionRunner(workout, exercises, null, null, null, onComplete),
  );
  return { result, rerender, unmount, onComplete };
}

/**
 * Drives the virtual clock, which fake timers keep coherent with Date.now().
 *
 * async act throughout: React 19 flushes effects inside the act scope, and advancing timers schedules
 * more work, so a sync act() here nests scopes and React reports overlapping act calls.
 */
async function tick(seconds: number) {
  await act(async () => {
    jest.advanceTimersByTime(seconds * 1000);
  });
}

/** Runs a hook action inside its own act scope, for the same reason. */
async function press(action: () => void) {
  await act(async () => {
    action();
  });
}

/**
 * Captured AppState handlers, so the backgrounding test can drive a foreground return. Installed for
 * every test rather than only the one that needs it: a spy installed and restored inside a single test
 * left `addEventListener` returning undefined for later tests, and the hook's effect cleanup then threw
 * on `subscription.remove()` — which surfaced as opaque AggregateErrors in tests that passed alone.
 * Mock teardown itself is global now (`clearMocks`/`restoreMocks` in the jest config).
 */
let appStateHandlers: ((state: string) => void)[] = [];

beforeEach(() => {
  jest.useFakeTimers();
  jest.setSystemTime(new Date('2026-07-27T09:00:00Z'));
  resetStore();

  appStateHandlers = [];
  jest.spyOn(AppState, 'addEventListener').mockImplementation(((_: string, handler: (state: string) => void) => {
    appStateHandlers.push(handler);
    return { remove: jest.fn() };
  }) as never);
});

describe('countdown-type first step', () => {
  // The shipped regression: `useState(stepIndex)` seeded the reset sentinel to the same value as the
  // initial index, so the per-step reset never ran for step 0. restTargetSecRef stayed at its
  // useRef(0) default, the ticking effect saw remaining <= 0 immediately, and the first step of every
  // HIIT/EMOM/AMRAP workout was skipped. Asserted on the very first render, before any timer runs.
  it('seeds the countdown target on the first render', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    expect(result.current.restTargetSec).toBe(40);
    expect(result.current.restRemainingSec).toBe(40);
    expect(result.current.stepIndex).toBe(0);
  });

  it('does not auto-advance off the first step immediately', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    await tick(1);
    expect(result.current.stepIndex).toBe(0);
    expect(result.current.restRemainingSec).toBe(39);
  });
});

describe('countdown timing', () => {
  it('advances only once the full target has elapsed', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    await tick(39);
    expect(result.current.stepIndex).toBe(0);
    await tick(1);
    expect(result.current.stepIndex).toBe(1);
  });

  it('excludes paused time from the elapsed count', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    await tick(10);
    await press(() => result.current.setPaused());
    await tick(30); // wall clock moves, the timer must not
    expect(result.current.restRemainingSec).toBe(30);
    await press(() => result.current.setPaused());
    await tick(5);
    expect(result.current.restRemainingSec).toBe(25);
    expect(result.current.stepIndex).toBe(0);
  });
});

describe('foreground catch-up', () => {
  // §7.1: JS timers are throttled or suspended in the background, so returning to the app must
  // recompute from wall-clock timestamps and catch up a countdown that fully elapsed while away.
  it('advances exactly once when the countdown elapsed while backgrounded', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:02:00Z')); // 120s later, target was 40s
      appStateHandlers.at(-1)?.('active');
    });

    expect(result.current.stepIndex).toBe(1);
  });

  /**
   * The same catch-up for a hold, which is where it matters most: a hold is run with the phone on the
   * floor and the screen asleep, so ending while nothing is ticking is its *normal* case. Without it
   * the clock resumes mid-hold and the set runs long — which is the overrun the auto-end exists to
   * remove, reintroduced through the back door.
   */
  it('advances a hold whose end passed while backgrounded, logging the target rather than the overrun', async () => {
    const { result } = await mount(workoutOf(single('plank'))); // 15–25s
    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:01:00Z')); // 60s later, well past the 25s end
      appStateHandlers.at(-1)?.('active');
    });

    expect(result.current.stepIndex).toBe(1);
    const hold = mockSession.entries.find((entry) => entry.type === 'timed_hold');
    expect(hold?.type === 'timed_hold' && hold.sets[0].holdSec).toBe(25);
  });

  it('leaves a targetless hold running however long it was backgrounded', async () => {
    const { result } = await mount(workoutOf(single('deadhang')));
    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:05:00Z'));
      appStateHandlers.at(-1)?.('active');
    });

    expect(result.current.stepIndex).toBe(0);
    expect(result.current.holdElapsedSec).toBe(300);
  });
});

describe('a hold that ends itself', () => {
  it('advances at the top of the range, not at the minimum', async () => {
    const { result } = await mount(workoutOf(single('plank'))); // 15–25s
    await tick(20);
    expect(result.current.stepIndex).toBe(0);

    await tick(5);
    expect(result.current.step?.kind).toBe('rest');
  });

  it('advances at a fixed target', async () => {
    const { result } = await mount(workoutOf(single('lsit'))); // 15s
    await tick(14);
    expect(result.current.stepIndex).toBe(0);

    await tick(1);
    expect(result.current.step?.kind).toBe('rest');
  });

  // The whole point of the escape hatch: omitting the target is the only way to write "hold as long
  // as you can", and it has to survive well past any number the runner might have inferred.
  it('never advances a hold with no target', async () => {
    const { result } = await mount(workoutOf(single('deadhang')));
    await tick(600);

    expect(result.current.stepIndex).toBe(0);
    expect(result.current.holdElapsedSec).toBe(600);
  });

  it('logs the same set an auto-end and a tapped Done both produce', async () => {
    const { result } = await mount(workoutOf(single('lsit')));
    await tick(15); // auto-ends set 1
    await press(() => result.current.skipRest());
    await tick(10);
    await press(() => result.current.doneSet()); // set 2, ended early by hand

    const hold = mockSession.entries.find((entry) => entry.type === 'timed_hold');
    expect(hold?.type === 'timed_hold' && hold.sets.map((set) => set.holdSec)).toEqual([15, 10]);
  });

  // The 3-2-1 into the end, which is the part that earns its keep in a hold: it's how you know to
  // prepare the dismount without looking up.
  it('ticks the last three seconds before it ends', async () => {
    await mount(workoutOf(single('plank'))); // ends at 25s
    await tick(21);
    expect(mockPlayTick).not.toHaveBeenCalled();

    await tick(3); // 22s, 23s, 24s
    expect(mockPlayTick).toHaveBeenCalledTimes(3);
  });

  it('does not tick through a hold with no end to count into', async () => {
    await mount(workoutOf(single('deadhang')));
    await tick(120);

    expect(mockPlayTick).not.toHaveBeenCalled();
  });
});

/**
 * §7.2's promise — "a mid-workout crash loses at most the in-progress set" — is only true if each set
 * reaches the session as it happens. It didn't: sets accumulated in memory until the whole exercise
 * finished, so a crash three sets into a four-set exercise wrote none of them. These assert on the
 * session *between* sets, which is the only place the difference shows.
 */
describe('write-through persistence', () => {
  it('writes the first set before the exercise is anywhere near finished', async () => {
    const { result } = await mount(workoutOf(single('pullups'))); // 3 sets
    await press(() => result.current.setReps(7));
    await press(() => result.current.logSet());

    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(1);
    expect(reps?.type === 'reps' && reps.sets[0].reps).toBe(7);
  });

  it('grows that same entry set by set instead of appending one per set', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet());

    const reps = mockSession.entries.filter((entry) => entry.type === 'reps');
    expect(reps).toHaveLength(1);
    expect(reps[0].type === 'reps' && reps[0].sets).toHaveLength(2);
  });

  it('records the rest taken after a set that is already on disk', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet());
    await tick(45); // the rest between sets 1 and 2
    await press(() => result.current.skipRest());

    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets[0].restTakenSec).toBe(45);
  });

  it('writes a hiit round as it completes, and counts up in place', async () => {
    const { result } = await mount(workoutOf(single('burpees'))); // 3 rounds
    await press(() => result.current.logInterval());
    expect(mockSession.entries.filter((entry) => entry.type === 'hiit')).toMatchObject([{ roundsCompleted: 1 }]);

    await press(() => result.current.skipRest());
    await press(() => result.current.logInterval());
    expect(mockSession.entries.filter((entry) => entry.type === 'hiit')).toMatchObject([{ roundsCompleted: 2 }]);
  });

  it('keeps each circuit member in its own entry while both are still going', async () => {
    const circuit = workoutOf({
      kind: 'circuit',
      rounds: 2,
      restBetweenExercisesSec: 15,
      restBetweenRoundsSec: 60,
      members: [{ exerciseId: 'pullups' }, { exerciseId: 'lsit' }],
    });
    const { result } = await mount(circuit);

    // Round 1 of 2: pull-ups, circuit rest, L-sit. Both members are mid-workout, and interleaved —
    // which is what makes "rewrite the member's entry" need to find the right one rather than the last.
    await press(() => result.current.doneSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.doneSet());

    expect(mockSession.entries).toHaveLength(2);
    expect(mockSession.entries[0]).toMatchObject({ exercise: 'pullups', type: 'reps' });
    expect(mockSession.entries[1]).toMatchObject({ exercise: 'lsit', type: 'timed_hold' });

    // Round 2's pull-up set has to land back in entry 0, not in the L-sit entry or a third one.
    await press(() => result.current.skipRest());
    await press(() => result.current.doneSet());
    expect(mockSession.entries).toHaveLength(2);
    expect(mockSession.entries[0].type === 'reps' && mockSession.entries[0].sets).toHaveLength(2);
    expect(mockSession.entries[1].type === 'timed_hold' && mockSession.entries[1].sets).toHaveLength(1);
  });
});

describe('logging', () => {
  it('flushes one hiit entry with the rounds actually completed', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    // 3 rounds with interleaved rest: work, rest, work, rest, work.
    for (let i = 0; i < 5; i++) await press(() => result.current.logInterval());

    const hiit = mockSession.entries.filter((entry) => entry.type === 'hiit');
    expect(hiit).toHaveLength(1);
    expect(hiit[0]).toMatchObject({ exercise: 'burpees', roundsCompleted: 3 });
  });

  it('records reps, weight and rpe on each logged set', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setReps(8));
    await press(() => result.current.setWeightKg(22.5));
    await press(() => result.current.setRpe(9));
    await press(() => result.current.logSet()); // set 1 -> rest
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet()); // set 2 -> rest
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet()); // set 3, last -> flush

    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(3);
    expect(reps?.type === 'reps' && reps.sets[0]).toMatchObject({ reps: 8, weightKg: 22.5, rpe: 9 });
  });

  it('seeds reps from the target and load from config, and carries load between sets', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    expect(result.current.reps).toBe(6);
    expect(result.current.weightKg).toBe(20);

    await press(() => result.current.setWeightKg(30));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());

    // Reps re-seed from the target each set; load carries what was actually lifted.
    expect(result.current.reps).toBe(6);
    expect(result.current.weightKg).toBe(30);
  });

  it('logs bodyweight as an absent weight rather than a zero load', async () => {
    const { result } = await mount(workoutOf(single('lsit'), single('pullups')));
    await press(() => result.current.doneSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.doneSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.doneSet()); // leaves the hold member, flushing it

    await press(() => result.current.setWeightKg(0));
    await press(() => result.current.logSet());
    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    if (reps?.type === 'reps') expect(reps.sets[0].weightKg).toBeUndefined();
  });

  it('completes the session once the last step is done', async () => {
    const { result, onComplete } = await mount(workoutOf(single('grinder')));
    await press(() => result.current.logInterval());
    expect(mockCompleted).toBe(true);
    expect(onComplete).toHaveBeenCalledTimes(1);
  });
});

describe('circuits', () => {
  it('groups each member into one entry across rounds', async () => {
    const circuit = workoutOf({
      kind: 'circuit',
      rounds: 2,
      restBetweenExercisesSec: 15,
      restBetweenRoundsSec: 60,
      members: [{ exerciseId: 'pullups' }, { exerciseId: 'lsit' }],
    });
    const { result } = await mount(circuit);

    // Exactly one advance per step. Looping on `result.current.step` instead would never terminate:
    // the final advance completes the session but leaves the index (and so `step`) where it is, and
    // each extra call re-commits the last step.
    const total = result.current.totalSteps;
    for (let i = 0; i < total; i++) {
      await press(() => result.current.doneSet());
    }

    const reps = mockSession.entries.filter((entry) => entry.type === 'reps');
    const holds = mockSession.entries.filter((entry) => entry.type === 'timed_hold');
    expect(reps).toHaveLength(1);
    expect(holds).toHaveLength(1);
    expect(reps[0].type === 'reps' && reps[0].sets).toHaveLength(2);
    expect(holds[0].type === 'timed_hold' && holds[0].sets).toHaveLength(2);
  });
});

describe('restFollows', () => {
  // The reps button names what's next ("Log set → Rest"). That used to be unconditionally true,
  // because a rest step was emitted between sets even at rest_sec: 0. Now that zero-length rest is
  // skipped, the promise has to be checked against the actual step list or the button lies on
  // exactly the back-to-back sets this was fixed for.
  it('is true between sets that have real rest', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    expect(result.current.restFollows).toBe(true);
  });

  it('is false between back-to-back sets', async () => {
    const { result } = await mount(workoutOf(single('dips')));
    expect(result.current.restFollows).toBe(false);
    await press(result.current.logSet);
    expect(result.current.restFollows).toBe(false);
  });

  it('is false on the last step of the workout', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(result.current.logSet); // set 1 -> rest
    await press(result.current.skipRest); // rest -> set 2
    await press(result.current.logSet); // set 2 -> rest
    await press(result.current.skipRest); // rest -> set 3, the final step
    expect(result.current.restFollows).toBe(false);
  });
});

describe('goPrev undo', () => {
  it('retracts the entry a first set had just created', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet()); // set 1 is on disk immediately, in its own entry
    expect(mockSession.entries).toHaveLength(1);

    await press(() => result.current.goPrev());
    // Nothing of that set is left behind: its entry was the whole of what it wrote.
    expect(mockSession.entries).toHaveLength(0);
    expect(result.current.stepIndex).toBe(0);

    // Redo, then finish the exercise: the popped set must not resurface as a duplicate.
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet());
    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(3);
  });

  it('shrinks a written entry back by one set rather than retracting the whole thing', async () => {
    const { result } = await mount(workoutOf(single('pullups'), single('grinder')));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet()); // last set of the exercise
    const written = mockSession.entries.filter((entry) => entry.type === 'reps');
    expect(written).toHaveLength(1);
    expect(written[0].type === 'reps' && written[0].sets).toHaveLength(3);

    await press(() => result.current.goPrev());
    // Only the undone set goes: the two before it were never at risk, having been written as they happened.
    const afterUndo = mockSession.entries.filter((entry) => entry.type === 'reps');
    expect(afterUndo).toHaveLength(1);
    expect(afterUndo[0].type === 'reps' && afterUndo[0].sets).toHaveLength(2);

    await press(() => result.current.logSet()); // redo the final set
    const reps = mockSession.entries.filter((entry) => entry.type === 'reps');
    expect(reps).toHaveLength(1);
    expect(reps[0].type === 'reps' && reps[0].sets).toHaveLength(3);
  });

  it('undoes only one level: a second goPrev just moves the index', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.goPrev()); // undoes the rest step (which only overwrote a field)
    await press(() => result.current.goPrev()); // no further commit to reverse
    await press(() => result.current.goPrev());
    expect(result.current.stepIndex).toBe(0);
  });

  it('does not go below the first step', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.goPrev());
    expect(result.current.stepIndex).toBe(0);
  });
});

describe('finishSession', () => {
  it('commits the in-progress set rather than discarding it', async () => {
    const { result, onComplete } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setReps(4));
    await press(() => result.current.finishSession());

    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(1);
    expect(reps?.type === 'reps' && reps.sets[0].reps).toBe(4);
    expect(mockCompleted).toBe(true);
    expect(onComplete).toHaveBeenCalledTimes(1);
  });
});

describe('milestone chime', () => {
  // Both triggers are thresholds that stay true for the rest of the step, so the once-per-step guard
  // is the whole feature — without it the 1Hz tick would chime every second to the end of the interval.
  it('sounds once at the halfway point of a hiit work interval', async () => {
    const { result } = await mount(workoutOf(single('burpees'))); // 40s work
    await tick(19);
    expect(mockPlayMilestone).not.toHaveBeenCalled();

    await tick(1); // 20s elapsed = halfway
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);

    await tick(10); // still past halfway, must not repeat
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);
    expect(result.current.stepIndex).toBe(0);
  });

  it('sounds again on the next interval, having reset per step', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    await tick(40); // finish work interval 1 -> rest
    await press(() => result.current.skipRest());
    mockPlayMilestone.mockClear();

    await tick(20); // halfway through work interval 2
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);
  });

  it('sounds once when a range hold crosses its minimum', async () => {
    const { result } = await mount(workoutOf(single('plank'))); // 15–25s, ends at 25
    await tick(14);
    expect(mockPlayMilestone).not.toHaveBeenCalled();

    await tick(1);
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);

    // Still inside the range, where a repeat would be most annoying — the once-per-step guard.
    await tick(5);
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);
    expect(result.current.holdElapsedSec).toBe(20);
  });

  // The minimum is only worth marking when there's something left to run after it. On a fixed target
  // the mark and the auto-advance land in the same second, and the chime firing into the step change
  // is two cues in one breath rather than two pieces of information.
  it('stays silent on a fixed-target hold, whose minimum is its end', async () => {
    await mount(workoutOf(single('lsit'))); // holdSecMin 15, no max
    await tick(15);

    expect(mockPlayMilestone).not.toHaveBeenCalled();
  });

  it('stays silent through a hold with no target at all', async () => {
    await mount(workoutOf(single('deadhang')));
    await tick(120);

    expect(mockPlayMilestone).not.toHaveBeenCalled();
  });

  it('stays silent through a rest countdown', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    await tick(40);
    expect(result.current.step?.kind).toBe('rest');
    mockPlayMilestone.mockClear();

    await tick(19); // the 20s rest, all the way out
    expect(mockPlayMilestone).not.toHaveBeenCalled();
  });
});

describe('addRestSeconds', () => {
  it('extends the countdown', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    await tick(10);
    await press(() => result.current.addRestSeconds(30));
    expect(result.current.restTargetSec).toBe(70);
    expect(result.current.restRemainingSec).toBe(60);
  });

  // The notification is scheduled for a fixed delay, so extending the rest has to reschedule it or the
  // "back to work" cue fires early. The effect reads the target from a ref no dependency tracks, so
  // restTargetSec is in its deps purely as the change signal.
  it('reschedules the background notification for the new end time', async () => {
    const { result } = await mount(workoutOf(single('burpees')));
    mockScheduleNotification.mockClear();

    await tick(10);
    await press(() => result.current.addRestSeconds(30));

    expect(mockScheduleNotification).toHaveBeenCalled();
    const seconds = mockScheduleNotification.mock.calls.at(-1)?.[2];
    expect(seconds).toBe(60);
  });
});
