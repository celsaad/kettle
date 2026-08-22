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
  mockPriorSessions = [];
}

const mockSetTargetWeightKg = jest.fn(() => Promise.resolve());
jest.mock('@/state/library-store', () => ({
  useLibraryStore: { getState: () => ({ setTargetWeightKg: mockSetTargetWeightKg }) },
}));

jest.mock('@/state/session-history-store', () => ({
  // Both call shapes, because the runner uses both: the hook form for actions, and `getState()` for
  // the one-off history snapshot it takes at session start without subscribing.
  useSessionHistoryStore: Object.assign((selector: (state: unknown) => unknown) => selector(mockStoreState()), {
    getState: () => mockStoreState(),
  }),
}));

/** The log the runner snapshots for "last time" and the live PR marker. Set per test. */
let mockPriorSessions: Session[] = [];

/**
 * Keeps the in-flight session in `sessions` as the runner writes to it, which is what the real store
 * does (`logEntry`/`replaceEntry` map over `sessions`). The stand-in used to leave `sessions` alone,
 * so no test here could have caught a history read that saw the session it was in the middle of.
 */
function syncLiveSession() {
  mockPriorSessions = mockPriorSessions.map((session) => (session.id === mockSession.id ? mockSession : session));
}

function mockStoreState() {
  return {
    sessions: mockPriorSessions,
    startSession: (workoutId: string | null) => {
      mockSession = { ...mockSession, workout: workoutId };
      mockPriorSessions = [mockSession, ...mockPriorSessions];
      return mockSession;
    },
    logEntry: (current: Session, entry: SessionEntry) => {
      mockSession = { ...current, entries: [...current.entries, entry] };
      syncLiveSession();
      return mockSession;
    },
    replaceEntry: (current: Session, index: number, entry: SessionEntry) => {
      mockSession = {
        ...current,
        entries: current.entries.map((existing, position) => (position === index ? entry : existing)),
      };
      syncLiveSession();
      return mockSession;
    },
    removeLastEntry: (current: Session) => {
      mockSession = { ...current, entries: current.entries.slice(0, -1) };
      syncLiveSession();
      return mockSession;
    },
    completeSession: (current: Session) => {
      mockCompleted = true;
      mockSession = { ...current, endedAt: new Date().toISOString() };
      syncLiveSession();
      return mockSession;
    },
  };
}

const exercises: Exercise[] = [
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 3 } },
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, targetWeightKg: 20, restSec: 90 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  // The three hold shapes, which end differently: `lsit` at its fixed 15s, `plank` at the top of its
  // range, and `deadhang` not at all.
  { id: 'plank', name: 'Plank', type: 'timed_hold', config: { sets: 2, holdSecMin: 15, holdSecMax: 25, restSec: 30 } },
  { id: 'deadhang', name: 'Dead Hang', type: 'timed_hold', config: { sets: 2, restSec: 30 } },
  { id: 'grinder', name: 'Grinder', type: 'amrap', config: { timeCapSec: 300 } },
  // The two EMOM shapes, which seed differently: `swings` prescribes a rep count per minute, `climb`
  // prescribes only the interval and so has nothing to seed from.
  { id: 'swings', name: 'KB Swings', type: 'emom', config: { intervalSec: 60, totalMinutes: 3, targetReps: 10 } },
  { id: 'climb', name: 'Rope Climb', type: 'emom', config: { intervalSec: 60, totalMinutes: 2 } },
  // The two cardio shapes, which end differently: `row` counts a configured 60s down, `walk` counts up
  // until the Done button ends it.
  { id: 'row', name: 'Row', type: 'cardio', config: { durationSec: 60 } },
  { id: 'walk', name: 'Walk', type: 'cardio', config: {} },
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

  it('logs the configured duration of a cardio step, not the time spent away', async () => {
    const { result } = await mount(workoutOf(single('row'), single('pullups'))); // row is 60s
    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:10:00Z')); // 600s later
      appStateHandlers.at(-1)?.('active');
    });

    expect(result.current.stepIndex).toBe(1);
    const cardio = mockSession.entries.find((entry) => entry.type === 'cardio');
    expect(cardio?.type === 'cardio' && cardio.durationSec).toBe(60);
  });

  it('still measures a count-up cardio by the clock, having no duration to clamp to', async () => {
    const { result } = await mount(workoutOf(single('walk')));
    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:05:00Z'));
      appStateHandlers.at(-1)?.('active');
    });
    await press(() => result.current.logInterval());

    const cardio = mockSession.entries.find((entry) => entry.type === 'cardio');
    expect(cardio?.type === 'cardio' && cardio.durationSec).toBe(300);
  });

  /**
   * The resume race. React Native hands JS a backlog of queued native calls in one batch, so a timer
   * callback that came due while backgrounded and the AppState event can both run before React
   * re-renders — and the refs holding the current step's clock are only re-seeded by that render. Both
   * therefore judge the *new* step against the *old* step's deadline and advance again.
   *
   * Driven inside one `act` scope, which is exactly that batch: two callbacks, no render between them.
   * Without the staleness guard this logged a second pull-up set at 0 reps — one nobody performed — and
   * skipped set 2 outright, landing on set 3.
   */
  it('does not advance twice when a queued tick lands in the same batch as the catch-up', async () => {
    const { result } = await mount(workoutOf(single('pullups'))); // [set1, rest 90, set2, rest 90, set3]
    await press(() => result.current.logSet());
    expect(result.current.step?.kind).toBe('rest');

    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:05:00Z')); // 300s away, the rest was 90s
      appStateHandlers.at(-1)?.('active');
      jest.advanceTimersByTime(1000); // the tick that came due while away
    });

    expect(result.current.stepIndex).toBe(2); // set 2, not skipped past
    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(1);
  });

  /**
   * The other ordering — which of the two runs first is not ours to choose.
   *
   * **This one passes without the guard as well, and is kept knowing that.** It cannot stage the batch:
   * `advanceTimersByTime` flushes React's pending render along with the timer, so by the time the
   * AppState handler runs the refs have been re-seeded and it is no longer stale. Only the reverse
   * ordering — a handler called directly, then a timer — leaves two callbacks with no render between.
   * So this holds the *other* half: that the guard doesn't suppress a catch-up that should still fire.
   */
  it('does not advance twice when the catch-up lands in the same batch as a queued tick', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet());

    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:05:00Z'));
      jest.advanceTimersByTime(1000); // the tick first this time
      appStateHandlers.at(-1)?.('active');
    });

    expect(result.current.stepIndex).toBe(2);
    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(1);
  });

  /**
   * The same race on the last step, where it lands differently: `advance()` returns from the completion
   * path without moving `stepIndexRef` — there is nowhere to move it to — so the guard above cannot see
   * that the session is over, and the second call re-committed the final round. A 3-round HIIT logged 4.
   */
  it('completes once when the last step ends in that same batch', async () => {
    const { result, onComplete } = await mount(workoutOf(single('burpees'))); // 3 rounds
    await press(() => result.current.logInterval());
    await press(() => result.current.skipRest());
    await press(() => result.current.logInterval());
    await press(() => result.current.skipRest());
    expect(result.current.stepIndex).toBe(4);

    await act(async () => {
      jest.setSystemTime(new Date('2026-07-27T09:05:00Z'));
      appStateHandlers.at(-1)?.('active');
      jest.advanceTimersByTime(1000);
    });

    const hiit = mockSession.entries.find((entry) => entry.type === 'hiit');
    expect(hiit?.type === 'hiit' && hiit.roundsCompleted).toBe(3);
    expect(onComplete).toHaveBeenCalledTimes(1);
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

  /**
   * The shipped bug: only `reps` steps seeded from their target, so every EMOM minute started at 0
   * while the screen showed the prescription right above the counter. `commitCurrentStep` writes
   * `reps || undefined`, so an untouched minute logged *nothing* — a whole EMOM could finish with an
   * entry that recorded no work at all.
   */
  it('seeds an EMOM minute from its target, and logs it without a tap', async () => {
    const { result } = await mount(workoutOf(single('swings')));
    expect(result.current.reps).toBe(10);

    await press(() => result.current.logInterval()); // minute 1, untouched
    expect(result.current.reps).toBe(10); // re-seeded for minute 2, not carried
    await press(() => result.current.setReps(8));
    await press(() => result.current.logInterval()); // minute 2, corrected down
    await press(() => result.current.logInterval()); // minute 3, back at the target

    const emom = mockSession.entries.find((entry) => entry.type === 'emom');
    expect(emom?.type === 'emom' && emom.minutes).toEqual([{ reps: 10 }, { reps: 8 }, { reps: 10 }]);
  });

  it('leaves an EMOM with no prescribed reps at zero, which logs the minute as unrecorded', async () => {
    const { result } = await mount(workoutOf(single('climb')));
    expect(result.current.reps).toBe(0);

    await press(() => result.current.logInterval());
    await press(() => result.current.logInterval());

    const emom = mockSession.entries.find((entry) => entry.type === 'emom');
    expect(emom?.type === 'emom' && emom.minutes).toEqual([{}, {}]);
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

/**
 * Committing a step twice has to mean the same as committing it once.
 *
 * The undo window is one level deep, and every one of these gets back onto a step from outside it —
 * two Prevs, or Finish from a stepped-back position. Before contributions were keyed by step index
 * the commit simply appended again there, so a set performed once was logged twice and an exercise
 * finished with more sets than its plan has. Each case below fails against that bug, verified by
 * reintroducing it.
 */
describe('redoing a step already committed', () => {
  it('rewrites the set rather than logging a second one for the same step', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setReps(5));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest()); // closes the undo window: rest adds nothing to take back
    await press(() => result.current.goPrev()); // back onto the rest step
    await press(() => result.current.goPrev()); // back onto set 1, whose commit stands
    expect(result.current.stepIndex).toBe(0);

    await press(() => result.current.setReps(7));
    await press(() => result.current.logSet());

    const reps = mockSession.entries.filter((entry) => entry.type === 'reps');
    expect(reps).toHaveLength(1);
    expect(reps[0].type === 'reps' && reps[0].sets).toEqual([{ reps: 7, weightKg: 20, rpe: 8, restTakenSec: 0 }]);
  });

  it('keeps the rest already recorded against a set that is redone', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet());
    await tick(40);
    await press(() => result.current.skipRest()); // 40s of rest, recorded against set 1
    await press(() => result.current.goPrev());
    await press(() => result.current.goPrev());
    await press(() => result.current.setReps(7));
    await press(() => result.current.logSet());

    // The rest was taken; only the set was redone. Re-seeding it to 0 would quietly rewrite history.
    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toEqual([{ reps: 7, weightKg: 20, rpe: 8, restTakenSec: 40 }]);
  });

  it('attributes the rest to the set it followed, not to whatever is on the end of the log', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.logSet()); // set 1
    await press(() => result.current.skipRest());
    await press(() => result.current.logSet()); // set 2
    await press(() => result.current.goPrev()); // undo set 2 (in the window)
    await press(() => result.current.goPrev()); // onto the rest step
    await press(() => result.current.goPrev()); // onto set 1, whose commit stands
    await press(() => result.current.logSet()); // redone in place, at position 0
    await tick(25);
    await press(() => result.current.skipRest());

    // Set 1's rest belongs to set 1. Reading "the last set logged" would have put it on set 2 once
    // the redo stopped being the newest thing in the member's log.
    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets.map((set) => set.restTakenSec)).toEqual([25]);
  });

  it('does not count a HIIT round twice', async () => {
    const { result } = await mount(workoutOf(single('burpees'))); // 3 rounds: work, rest, work, rest, work
    await press(() => result.current.logInterval()); // round 1
    await press(() => result.current.logInterval()); // its rest
    await press(() => result.current.goPrev());
    await press(() => result.current.goPrev());
    await press(() => result.current.logInterval()); // round 1 again — the same round, not another
    for (let i = 0; i < 4; i++) await press(() => result.current.logInterval());

    const hiit = mockSession.entries.filter((entry) => entry.type === 'hiit');
    expect(hiit).toHaveLength(1);
    expect(hiit[0]).toMatchObject({ roundsCompleted: 3 });
  });

  it('rewrites an EMOM minute rather than logging an extra one', async () => {
    const { result } = await mount(workoutOf(single('swings'))); // 3 minutes, 10 reps each
    await press(() => result.current.logInterval()); // minute 1
    await press(() => result.current.logInterval()); // minute 2
    await press(() => result.current.goPrev()); // undoes minute 2 (in the window)
    await press(() => result.current.goPrev()); // onto minute 1, whose commit stands
    await press(() => result.current.setReps(5));
    await press(() => result.current.logInterval());

    const emom = mockSession.entries.find((entry) => entry.type === 'emom');
    expect(emom?.type === 'emom' && emom.minutes).toEqual([{ reps: 5 }]);
  });

  /**
   * The one-shot kinds (amrap/cardio/standalone rest) were the worst off: with no accumulating log
   * behind them, a redo appended a whole second *entry* rather than a duplicate set — and one carrying
   * whatever the re-seeded screen held, so a 4-round amrap gained a 0-round twin.
   */
  it('rewrites a one-shot entry rather than logging a second one', async () => {
    const { result } = await mount(workoutOf(single('grinder'), single('pullups')));
    await press(() => result.current.setRoundsCompleted(4));
    await press(() => result.current.doneSet()); // amrap logged
    await press(() => result.current.logSet()); // pull-ups set 1
    await press(() => result.current.goPrev()); // undoes that set (in the window)
    await press(() => result.current.goPrev()); // onto the amrap, whose entry stands
    await press(() => result.current.setRoundsCompleted(6));
    await press(() => result.current.doneSet());

    const amrap = mockSession.entries.filter((entry) => entry.type === 'amrap');
    expect(amrap).toHaveLength(1);
    expect(amrap[0]).toMatchObject({ exercise: 'grinder', roundsCompleted: 6 });
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

  // Finish from a step whose commit already stands: the same double-log the redo cases above cover,
  // reached without redoing anything. Two Prevs put the step back on screen with its set still logged,
  // and Finish commits the step it lands on.
  it('does not log the set twice when Finish follows two Prevs', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setReps(5));
    await press(() => result.current.logSet());
    await press(() => result.current.skipRest());
    await press(() => result.current.goPrev());
    await press(() => result.current.goPrev());
    await press(() => result.current.finishSession());

    const reps = mockSession.entries.filter((entry) => entry.type === 'reps');
    expect(reps).toHaveLength(1);
    expect(reps[0].type === 'reps' && reps[0].sets).toHaveLength(1);
  });

  // The session is over after the first call, so the second has nothing to commit into it. Cheap to
  // hold, and it covers the double-tap as well as a Finish landing in the same batch as an auto-advance.
  it('logs nothing more when it is called a second time', async () => {
    const { result, onComplete } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.finishSession());
    await press(() => result.current.finishSession());

    const reps = mockSession.entries.find((entry) => entry.type === 'reps');
    expect(reps?.type === 'reps' && reps.sets).toHaveLength(1);
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

  /**
   * The shipped bug: the guard ref held *which step chimed last*, which silences a repeat inside a
   * step but also silences the next visit to one. Stepping back over anything that doesn't chime —
   * this rest — left the ref still naming the hold, so redoing that hold crossed its minimum in
   * silence. Reintroduce it by dropping the reset in the step-change block and this fails.
   */
  it('sounds again on a hold redone with Prev', async () => {
    const { result } = await mount(workoutOf(single('plank'))); // 15–25s, ends at 25
    await tick(15);
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);

    await tick(10); // the hold ends itself at 25 -> rest, which never chimes
    expect(result.current.step?.kind).toBe('rest');
    await press(() => result.current.goPrev()); // back onto the hold, from the top
    mockPlayMilestone.mockClear();

    await tick(15);
    expect(mockPlayMilestone).toHaveBeenCalledTimes(1);
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

/**
 * "Last time" on the set row, and the live marker beside it. Both read a snapshot of the log taken at
 * session start, and the snapshot is the whole design: the runner writes every set straight through
 * the store, so a live read would answer "last time" with the set finished two minutes ago.
 */
describe('last time on the set row', () => {
  const finished = (id: string, entries: Session['entries']): Session => ({
    version: 1,
    id,
    workout: 'w',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt: '2026-07-20T09:00:00.000Z',
    endedAt: '2026-07-20T10:00:00.000Z',
    entries,
  });

  it('shows nothing on a first-ever session', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    expect(result.current.previousSet).toBeNull();
  });

  it('shows the matching set from the last time the exercise was trained', async () => {
    mockPriorSessions = [
      finished('older', [
        {
          exercise: 'pullups',
          type: 'reps',
          sets: [
            { reps: 8, weightKg: 15, restTakenSec: 90 },
            { reps: 6, weightKg: 15, restTakenSec: 90 },
          ],
        },
      ]),
    ];

    const { result } = await mount(workoutOf(single('pullups')));

    expect(result.current.previousSet).toEqual({ kind: 'reps', reps: 8, weightKg: 15 });
  });

  /**
   * The trap the issue names: `persistMember` writes this session's entry to the store the moment a
   * set is logged, so a naive lookup answers "last time" with the set from two minutes ago.
   *
   * **What this actually pins is `previousSetFor`'s unfinished-session skip**, which is the defence
   * that carries the correctness. Verified by mutation, and the result was not what the first draft of
   * this comment claimed: reintroducing a live store read on its own leaves the test green, because
   * the in-flight session has no `ended_at` and is skipped either way. Only removing *both* the
   * snapshot and that skip fails it — reporting the 40 kg just logged instead of last week's 15.
   *
   * The snapshot's own job is the half no assertion here can see: reading the store live would
   * subscribe the runner to a store rewritten on every logged set, re-rendering the whole session
   * screen mid-workout. That's a performance contract, not a behavioural one.
   */
  it('does not change when a set is logged mid-session', async () => {
    mockPriorSessions = [
      finished('older', [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 8, weightKg: 15, restTakenSec: 90 }] }]),
    ];

    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setWeightKg(40));
    await press(result.current.logSet);
    // Past the trailing rest, onto set 2 of the same exercise.
    await press(result.current.skipRest);

    expect(result.current.step?.kind).toBe('reps');
    expect(result.current.previousSet).toEqual({ kind: 'reps', reps: 8, weightKg: 15 });
  });

  it('carries a hold set to the hold screen', async () => {
    mockPriorSessions = [
      finished('older', [{ exercise: 'lsit', type: 'timed_hold', sets: [{ holdSec: 22, restTakenSec: 60 }] }]),
    ];

    const { result } = await mount(workoutOf(single('lsit')));

    expect(result.current.previousSet).toEqual({ kind: 'hold', holdSec: 22 });
  });
});

describe('the live personal-best marker', () => {
  const benchLog = (weightKg: number): Session[] => [
    {
      version: 1,
      id: 'older',
      workout: 'w',
      program: null,
      programWeek: null,
      programDay: null,
      startedAt: '2026-07-20T09:00:00.000Z',
      endedAt: '2026-07-20T10:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 6, weightKg, restTakenSec: 90 }] }],
    },
  ];

  // A first-ever entry is not a record — same rule as sessionRecords, so the set row and the
  // completion screen can't disagree.
  it('stays quiet with nothing to beat', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setWeightKg(200));

    expect(result.current.beatsPersonalBest).toBe(false);
  });

  it('stays quiet on a tie', async () => {
    mockPriorSessions = benchLog(20);
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setWeightKg(20));

    expect(result.current.beatsPersonalBest).toBe(false);
  });

  it('marks a load above anything ever logged', async () => {
    mockPriorSessions = benchLog(20);
    const { result } = await mount(workoutOf(single('pullups')));
    await press(() => result.current.setWeightKg(22.5));

    expect(result.current.beatsPersonalBest).toBe(true);
  });

  /**
   * The bar rises with the session's own best, so three sets at a new top weight don't all claim a
   * PR — only the set that actually moved the number. Without that, adding 2.5 kg and doing your
   * three sets would light up three times.
   */
  it('does not re-mark the following set at the same new weight', async () => {
    mockPriorSessions = benchLog(20);
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.setWeightKg(25));
    expect(result.current.beatsPersonalBest).toBe(true);

    await press(result.current.logSet);
    await press(result.current.skipRest);

    // Load carries across sets, so set 2 is at 25 kg too — matched, not beaten.
    expect(result.current.weightKg).toBe(25);
    expect(result.current.beatsPersonalBest).toBe(false);
  });

  it('marks again once the load goes up mid-session', async () => {
    mockPriorSessions = benchLog(20);
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.setWeightKg(25));
    await press(result.current.logSet);
    await press(result.current.skipRest);
    await press(() => result.current.setWeightKg(27.5));

    expect(result.current.beatsPersonalBest).toBe(true);
  });
});

describe(`adopting last time's load`, () => {
  it('takes the load and writes it back as the new target', async () => {
    mockPriorSessions = [
      {
        version: 1,
        id: 'older',
        workout: 'w',
        program: null,
        programWeek: null,
        programDay: null,
        startedAt: '2026-07-20T09:00:00.000Z',
        endedAt: '2026-07-20T10:00:00.000Z',
        entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 6, weightKg: 32.5, restTakenSec: 90 }] }],
      },
    ];

    const { result } = await mount(workoutOf(single('pullups')));
    await press(result.current.adoptPreviousLoad);

    expect(result.current.weightKg).toBe(32.5);
    // Kilograms straight through, with no display round trip — see setTargetWeightKg.
    expect(mockSetTargetWeightKg).toHaveBeenCalledWith('pullups', 32.5);
  });

  it('does nothing when last time was bodyweight', async () => {
    mockPriorSessions = [
      {
        version: 1,
        id: 'older',
        workout: 'w',
        program: null,
        programWeek: null,
        programDay: null,
        startedAt: '2026-07-20T09:00:00.000Z',
        endedAt: '2026-07-20T10:00:00.000Z',
        entries: [{ exercise: 'dips', type: 'reps', sets: [{ reps: 10, restTakenSec: 0 }] }],
      },
    ];

    const { result } = await mount(workoutOf(single('dips')));
    await press(result.current.adoptPreviousLoad);

    expect(mockSetTargetWeightKg).not.toHaveBeenCalled();
  });
});

/**
 * Changing the set count mid-workout. The pure list surgery is covered in `build-steps.test.ts`; what
 * is here is everything that surgery can break in the runner around it — the undo buffer, the session
 * file, and the floor that keeps a logged set out of reach.
 */
describe('adding and dropping sets mid-session', () => {
  it('offers the controls on a plain reps step', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    expect(result.current.canAddSet).toBe(true);
    expect(result.current.canDropSet).toBe(true);
  });

  /**
   * Inside a circuit, `setIndex`/`setTotal` is the member's position in the block's *rounds*, and its
   * steps are interleaved with the other members'. "One more set" would mean "one more round of the
   * whole block", which this does not implement — so the control is not offered rather than doing
   * something the label doesn't describe.
   */
  it('offers nothing inside a circuit', async () => {
    const circuit: Workout['blocks'][number] = {
      kind: 'circuit',
      rounds: 2,
      members: [{ exerciseId: 'pullups' }, { exerciseId: 'dips' }],
    };
    const { result } = await mount(workoutOf(circuit));

    expect(result.current.canAddSet).toBe(false);
    expect(result.current.canDropSet).toBe(false);
  });

  it('adds a set to the exercise on screen', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    expect(result.current.step).toMatchObject({ setIndex: 1, setTotal: 3 });

    await press(result.current.addSet);

    expect(result.current.step).toMatchObject({ setIndex: 1, setTotal: 4 });
    expect(result.current.totalSteps).toBe(7);
  });

  it('drops one, and stops at the set in progress', async () => {
    const { result } = await mount(workoutOf(single('plank')));
    expect(result.current.step).toMatchObject({ setIndex: 1, setTotal: 2 });

    await press(result.current.dropSet);
    expect(result.current.step).toMatchObject({ setIndex: 1, setTotal: 1 });

    // Nothing left but the set being performed, so there is nothing further to give up.
    expect(result.current.canDropSet).toBe(false);
  });

  /**
   * The floor is what has reached the session file, plus the set in progress. Two sets logged out of
   * three means the third is the one you are on, and dropping it would retract work already written.
   */
  it('will not drop below what has already been logged', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(result.current.logSet);
    await press(result.current.skipRest);
    await press(result.current.logSet);
    await press(result.current.skipRest);

    expect(result.current.step).toMatchObject({ setIndex: 3, setTotal: 3 });
    expect(result.current.canDropSet).toBe(false);
  });

  /**
   * **The invariant-3 regression.** `lastCommit.resultingIndex` is an index into `steps`, so any edit
   * to the array invalidates it — and a stale one sends the next `goPrev()` to undo a commit that now
   * belongs to a different step. Removing the `lastCommitRef.current = null` in `mutateSteps` fails
   * this: the logged set is retracted from the session file by a Prev the user pressed to look back.
   */
  it('does not retract a logged set when Prev follows a mutation', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.setReps(9));
    await press(result.current.logSet);
    await press(result.current.addSet);
    await press(result.current.goPrev);

    expect(mockSession.entries).toHaveLength(1);
    expect(mockSession.entries[0]).toMatchObject({ exercise: 'pullups', type: 'reps' });
    const entry = mockSession.entries[0];
    if (entry.type !== 'reps') throw new Error('expected a reps entry');
    expect(entry.sets).toHaveLength(1);
    expect(entry.sets[0].reps).toBe(9);
  });

  // One entry per exercise however many sets it grew to — the added set goes through the same
  // replaceEntry path as every other, keyed by the memberKey the mutation deliberately preserves.
  it('logs an added set into the same entry, not a second one', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(result.current.addSet);
    for (let i = 0; i < 4; i++) {
      await press(result.current.logSet);
      if (result.current.step?.kind === 'rest') await press(result.current.skipRest);
    }

    expect(mockSession.entries).toHaveLength(1);
    const entry = mockSession.entries[0];
    if (entry.type !== 'reps') throw new Error('expected a reps entry');
    expect(entry.sets).toHaveLength(4);
  });

  // rest_sec: 0 emits no rest steps, so the added set gets none either — and the log button has to go
  // on saying so.
  it('keeps the log button honest after adding to a back-to-back exercise', async () => {
    const { result } = await mount(workoutOf(single('dips')));
    expect(result.current.restFollows).toBe(false);

    await press(result.current.addSet);

    expect(result.current.restFollows).toBe(false);
    expect(result.current.step).toMatchObject({ setTotal: 4 });
  });

  /**
   * The #53 interaction: "last time" matches on set index, and an added fourth set has no fourth set
   * to match. `previousSetFor` already falls back to the previous entry's last set, which is the right
   * answer — asserted here rather than left to be discovered.
   */
  it(`falls back to last time's final set for a set number that did not exist then`, async () => {
    mockPriorSessions = [
      {
        version: 1,
        id: 'older',
        workout: 'w',
        program: null,
        programWeek: null,
        programDay: null,
        startedAt: '2026-07-20T09:00:00.000Z',
        endedAt: '2026-07-20T10:00:00.000Z',
        entries: [
          {
            exercise: 'pullups',
            type: 'reps',
            sets: [
              { reps: 12, restTakenSec: 45 },
              { reps: 10, restTakenSec: 45 },
            ],
          },
        ],
      },
    ];

    const { result } = await mount(workoutOf(single('pullups')));
    await press(result.current.addSet);
    await press(result.current.logSet);
    await press(result.current.skipRest);
    await press(result.current.logSet);
    await press(result.current.skipRest);

    // Set 3 of 4 — a set number the previous entry, which had two, never reached.
    expect(result.current.step).toMatchObject({ setIndex: 3, setTotal: 4 });
    expect(result.current.previousSet).toEqual({ kind: 'reps', reps: 10, weightKg: undefined });
  });
});

/**
 * Swapping an exercise for what's left of the current one. The list surgery is covered in
 * `build-steps.test.ts`; what is here is the pair of invariants the issue names, which only exist at
 * this layer — every accumulating log in the runner is keyed by `memberKey`, and `entryIndexRef`
 * assumes entries are only ever appended at the end.
 */
describe('swapping an exercise mid-session', () => {
  const repsEntry = (index: number) => {
    const entry = mockSession.entries[index];
    if (entry?.type !== 'reps') throw new Error(`entry ${index} is not a reps entry`);
    return entry;
  };

  it('offers same-type candidates, minus the exercise on screen', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    expect(result.current.canSwapExercise).toBe(true);
    expect(result.current.swapCandidates.map((exercise) => exercise.id)).toEqual(['dips']);
  });

  it('offers holds a hold, never a reps exercise', async () => {
    const { result } = await mount(workoutOf(single('lsit')));

    expect(result.current.swapCandidates.map((exercise) => exercise.id)).toEqual(['plank', 'deadhang']);
  });

  it('offers nothing inside a circuit', async () => {
    const circuit: Workout['blocks'][number] = {
      kind: 'circuit',
      rounds: 2,
      members: [{ exerciseId: 'pullups' }, { exerciseId: 'dips' }],
    };
    const { result } = await mount(workoutOf(circuit));

    expect(result.current.canSwapExercise).toBe(false);
  });

  it('runs the substitute for what was left', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(result.current.logSet);
    await press(result.current.skipRest);
    expect(result.current.step).toMatchObject({ setIndex: 2, setTotal: 3 });

    await press(() => result.current.swapExercise('dips'));

    // Two of three sets were left, so the substitute gets two — starting from its own set 1.
    expect(result.current.step).toMatchObject({ exerciseId: 'dips', setIndex: 1, setTotal: 2 });
  });

  /**
   * **Invariants 1 and 2 together.** The sets done before the swap stay in the original exercise's
   * entry under its own member key, and the substitute appends a *second* entry at the end rather than
   * growing the first. Reusing the member key would have merged them — pull-up sets silently becoming
   * dip sets in the log.
   */
  it('keeps the work already done in its own entry and appends a new one', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.setReps(7));
    await press(result.current.logSet);
    await press(result.current.skipRest);
    await press(() => result.current.swapExercise('dips'));
    await press(() => result.current.setReps(11));
    await press(result.current.logSet);

    expect(mockSession.entries).toHaveLength(2);
    expect(repsEntry(0).exercise).toBe('pullups');
    expect(repsEntry(0).sets.map((set) => set.reps)).toEqual([7]);
    expect(repsEntry(1).exercise).toBe('dips');
    expect(repsEntry(1).sets.map((set) => set.reps)).toEqual([11]);
  });

  /**
   * Invariant 2 in its sharper form: `entryIndexRef` records where each member's entry sits and
   * assumes nothing ever moves. A third exercise logged after the swap must land at index 2 and leave
   * the first two exactly where they were.
   */
  it('does not disturb the index of an entry written before the swap', async () => {
    const { result } = await mount(workoutOf(single('pullups'), single('plank')));

    await press(result.current.logSet);
    await press(result.current.skipRest);
    await press(() => result.current.swapExercise('dips'));
    await press(result.current.logSet);
    await press(result.current.logSet);
    // Onto the plank block, which is its own member and must still append cleanly after the swap.
    await press(result.current.doneSet);

    expect(mockSession.entries.map((entry) => entry.exercise)).toEqual(['pullups', 'dips', 'plank']);
    expect(repsEntry(0).sets).toHaveLength(1);
    expect(repsEntry(1).sets).toHaveLength(2);
  });

  /**
   * The rule part 1 established, inherited here: `swapExercise` goes through `mutateSteps`, which
   * clears `lastCommitRef` before touching the array. Bypassing it makes this Prev retract the set
   * logged before the swap — verified against exactly that change.
   */
  it('does not retract a logged set when Prev follows a swap', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.setReps(9));
    await press(result.current.logSet);
    await press(() => result.current.swapExercise('dips'));
    await press(result.current.goPrev);

    expect(mockSession.entries).toHaveLength(1);
    expect(repsEntry(0).sets.map((set) => set.reps)).toEqual([9]);
  });

  // Swapping on set 1: the original never contributed anything, so it simply isn't in the session.
  it('leaves the original out of the log entirely when nothing was done under it', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.swapExercise('dips'));
    await press(result.current.logSet);

    expect(mockSession.entries.map((entry) => entry.exercise)).toEqual(['dips']);
  });

  // A second swap must not reissue the first substitute's key, for the same reason the first must not
  // reissue the original's.
  it('issues a fresh member key on every swap', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.swapExercise('dips'));
    await press(result.current.logSet);
    await press(() => result.current.swapExercise('pullups'));
    await press(result.current.logSet);

    expect(mockSession.entries.map((entry) => entry.exercise)).toEqual(['dips', 'pullups']);
  });
});

/**
 * An ad-hoc session: no pre-built workout, and a step list built as the user goes. The data model
 * always allowed it — `Session.workout` is `string | null` — but nothing had ever produced the case.
 */
describe('an ad-hoc session', () => {
  async function mountAdHoc(onComplete = jest.fn()) {
    const { result } = await renderHook(() => useSessionRunner(null, exercises, null, null, null, onComplete));
    return { result, onComplete };
  }

  it('creates a session file with no workout, and no steps to run', async () => {
    const { result } = await mountAdHoc();

    expect(mockSession.workout).toBeNull();
    expect(result.current.totalSteps).toBe(0);
    expect(result.current.step).toBeUndefined();
    expect(result.current.isAdHoc).toBe(true);
    // The stand-in is `formatSessionName`'s to render, not this layer's to assemble.
    expect(result.current.workoutName).toBeNull();
  });

  it('runs an exercise once one is added', async () => {
    const { result } = await mountAdHoc();

    await press(() => result.current.addExercise('pullups'));

    expect(result.current.step).toMatchObject({ exerciseId: 'pullups', setIndex: 1, setTotal: 3 });
    expect(result.current.blockTotal).toBe(1);
  });

  /**
   * The park, which is what makes an ad-hoc session usable: running out of steps waits for the next
   * decision instead of ending the session. Auto-completing would mean choosing everything up front.
   */
  it('parks rather than completing when the added exercise runs out', async () => {
    const { result, onComplete } = await mountAdHoc();

    await press(() => result.current.addExercise('dips'));
    // dips is three back-to-back sets, so three logs exhausts it.
    await press(result.current.logSet);
    await press(result.current.logSet);
    await press(result.current.logSet);

    expect(result.current.step).toBeUndefined();
    expect(onComplete).not.toHaveBeenCalled();
    expect(mockCompleted).toBe(false);
  });

  // Parking one past the end rather than clamping is what makes this free: the appended steps land
  // exactly where the index already points.
  it('picks straight up where it parked when another exercise is added', async () => {
    const { result } = await mountAdHoc();

    await press(() => result.current.addExercise('dips'));
    await press(result.current.logSet);
    await press(result.current.logSet);
    await press(result.current.logSet);
    await press(() => result.current.addExercise('pullups'));

    expect(result.current.step).toMatchObject({ exerciseId: 'pullups', setIndex: 1 });
    expect(result.current.blockTotal).toBe(2);
  });

  // Each added exercise is its own member, so each gets its own entry — the same rule swap relies on.
  it('logs each added exercise into its own entry, in order', async () => {
    const { result } = await mountAdHoc();

    await press(() => result.current.addExercise('dips'));
    // Adding queues behind the current work rather than jumping to it, so dips runs out first.
    await press(result.current.logSet);
    await press(() => result.current.addExercise('pullups'));
    await press(result.current.logSet);
    await press(result.current.logSet);
    expect(result.current.step).toMatchObject({ exerciseId: 'pullups' });

    await press(result.current.logSet);

    expect(mockSession.entries.map((entry) => entry.exercise)).toEqual(['dips', 'pullups']);
  });

  it('is finished by hand, committing the set in progress', async () => {
    const { result, onComplete } = await mountAdHoc();

    await press(() => result.current.addExercise('pullups'));
    await press(() => result.current.setReps(9));
    await press(result.current.finishSession);

    expect(mockCompleted).toBe(true);
    expect(onComplete).toHaveBeenCalled();
    const entry = mockSession.entries[0];
    if (entry?.type !== 'reps') throw new Error('expected a reps entry');
    expect(entry.sets.map((set) => set.reps)).toEqual([9]);
  });

  // Nothing added and nothing logged: still a real session file, just an empty one.
  it('can be finished having added nothing at all', async () => {
    const { result } = await mountAdHoc();

    await press(result.current.finishSession);

    expect(mockCompleted).toBe(true);
    expect(mockSession.entries).toEqual([]);
  });
});

/**
 * What the per-step reset keys off. It used to be `stepIndex` alone, which is wrong the moment the
 * step list can change *under* a stable index — both #54's swap and #55's add-exercise do exactly
 * that, and the seeded reps/load/timers were silently carried over from the step that used to be there.
 */
describe('re-seeding when the step changes without the index moving', () => {
  it('seeds reps from the exercise added to a parked ad-hoc session', async () => {
    const { result } = await renderHook(() => useSessionRunner(null, exercises, null, null, null, jest.fn()));

    await press(() => result.current.addExercise('pullups'));

    // pullups targets 6. Parking had zeroed reps, and the index does not move when the appended step
    // lands on it — so nothing re-seeded, and the first set logged 0.
    expect(result.current.reps).toBe(6);
  });

  it('seeds reps from the substitute after a swap', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    expect(result.current.reps).toBe(6);

    await press(() => result.current.swapExercise('dips'));

    // dips targets 8; carrying pullups' 6 over would log the wrong prescription as the default.
    expect(result.current.reps).toBe(8);
  });

  it('seeds the load from the substitute too', async () => {
    const { result } = await mount(workoutOf(single('pullups')));
    expect(result.current.weightKg).toBe(20);

    await press(() => result.current.swapExercise('dips'));

    // dips carries no target weight, so it is bodyweight rather than pullups' 20 kg.
    expect(result.current.weightKg).toBe(0);
  });

  // The other side of the same coin: adding a set must NOT re-seed, or a rep count dialled in mid-set
  // would snap back to the target the moment you decided to do one more.
  it('leaves the set in progress alone when the set count changes', async () => {
    const { result } = await mount(workoutOf(single('pullups')));

    await press(() => result.current.setReps(14));
    await press(result.current.addSet);

    expect(result.current.reps).toBe(14);
  });
});
