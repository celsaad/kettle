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
  scheduleRestCompleteNotification: (title: string, body: string, seconds: number) =>
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
  { id: 'grinder', name: 'Grinder', type: 'amrap', config: { timeCapSec: 300 } },
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

describe('goPrev undo', () => {
  it('pops a pending set that had not been flushed yet', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet()); // set 1 buffered, member unchanged -> nothing written
    expect(mockSession.entries).toHaveLength(0);

    await press(() => result.current.goPrev());
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

  it('removes an already-flushed entry and restores its earlier sets to the buffer', async () => {
    const { result } = await mount(workoutOf(single('pullups'), single('grinder')));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet()); // last set: leaves the member, flushes all 3
    expect(mockSession.entries.filter((entry) => entry.type === 'reps')).toHaveLength(1);

    await press(() => result.current.goPrev());
    // The flushed entry is retracted, not left behind as stale data.
    expect(mockSession.entries.filter((entry) => entry.type === 'reps')).toHaveLength(0);

    await press(() => result.current.logSet()); // redo the final set
    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(3);
  });

  it('undoes only one level: a second goPrev just moves the index', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.goPrev()); // undoes the rest step (nothing buffered)
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

  it('sounds once when a hold reaches its target', async () => {
    const { result } = await mount(workoutOf(single('lsit'))); // holdSecMin 15, counts up
    await tick(14);
    expect(mockPlayMilestone).not.toHaveBeenCalled();

    await tick(1);
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);

    // A hold doesn't auto-advance, so it keeps ticking past the target — exactly where a repeat
    // would be most annoying.
    await tick(15);
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);
    expect(result.current.holdElapsedSec).toBe(30);
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
