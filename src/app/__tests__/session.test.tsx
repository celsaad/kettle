import { act, fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import SessionScreen, { ErrorBoundary } from '@/app/session';
import type { Exercise, Session, SessionEntry, Workout } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { router, setSearchParams } from '@/test-support/expo-router';
import { aLibrary, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The screen's routing decisions, not the runner's timing — `use-session-runner.test.tsx` owns that.
 * What's here is the part only the screen does: refusing to start an unrunnable workout, and picking
 * the sub-screen that matches the current step's kind.
 *
 * Step-kind dispatch is worth pinning because the four sub-screens take overlapping props and a
 * mis-wired branch renders a plausible-looking screen with the wrong controls on it — the sort of
 * thing that reads fine in review and only shows up mid-set.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

jest.mock('expo-keep-awake', () => ({ useKeepAwake: jest.fn() }));

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(() => Promise.resolve()),
  ImpactFeedbackStyle: { Medium: 'medium' },
}));

jest.mock('@/hooks/use-session-sounds', () => ({
  useSessionSounds: () => ({ playTick: jest.fn(), playExerciseChange: jest.fn(), playMilestone: jest.fn() }),
}));

jest.mock('@/hooks/safe-notifications', () => ({
  requestNotificationPermissions: jest.fn(() => Promise.resolve()),
  scheduleStepCompleteNotification: jest.fn(() => Promise.resolve('notif-id')),
  cancelNotification: jest.fn(),
}));

// The session store is stubbed rather than mocked at expo-file-system, so a rendered session logs
// into memory instead of writing files. Nothing here asserts on what was logged, apart from the
// boundary's abandon call — `session-history-store.test.ts` owns what that actually writes.
let mockSession: Session;
const mockAbandonActiveSession = jest.fn();
jest.mock('@/state/session-history-store', () => ({
  useSessionHistoryStore: (selector: (state: unknown) => unknown) =>
    selector({
      startSession: () => mockSession,
      logEntry: (current: Session, entry: SessionEntry) => ({ ...current, entries: [...current.entries, entry] }),
      replaceEntry: (current: Session, index: number, entry: SessionEntry) => ({
        ...current,
        entries: current.entries.map((existing, position) => (position === index ? entry : existing)),
      }),
      removeLastEntry: (current: Session) => current,
      completeSession: (current: Session) => current,
      abandonActiveSession: mockAbandonActiveSession,
    }),
}));

const exercises: Exercise[] = [
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, restSec: 90 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 3 } },
  { id: 'nothing', name: 'Nothing', type: 'reps', config: { sets: 0, targetRepsMin: 6, restSec: 90 } },
];

function setLibrary(workout: Workout) {
  useLibraryStore.setState({ library: aLibrary({ exercises, workouts: [workout] }), status: 'ready' });
}

const workoutOf = (exerciseId: string) => aWorkout({ id: 'w', name: 'Session', blocks: [{ kind: 'exercise', exerciseId }] });

/** Renders, then runs the 3-2-1 count-in out so the runner's first step is on screen. */
async function start(workout: Workout) {
  setLibrary(workout);
  await renderScreen(<SessionScreen />);
  // One act scope per second, not one 3000ms jump. The countdown schedules each timeout from an
  // effect keyed on the *previous* tick's state, and that effect doesn't run until the act scope
  // flushes — so a single 3000ms advance fires exactly one timer and leaves the count-in at 2.
  //
  // async act throughout: React 19 flushes effects inside the scope and each tick schedules more
  // work, so a sync act() nests scopes and React reports overlapping act calls.
  for (let second = 0; second < 3; second++) {
    await act(async () => {
      jest.advanceTimersByTime(1000);
    });
  }
}

beforeEach(() => {
  jest.useFakeTimers();
  jest.setSystemTime(new Date('2026-07-27T09:00:00Z'));
  setSearchParams({ workoutId: 'w' });
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
});

describe('before the first step', () => {
  it('renders nothing while the library is still loading', async () => {
    useLibraryStore.setState({ library: null, status: 'loading' });
    await renderScreen(<SessionScreen />);

    expect(screen.queryByText('GET READY')).toBeNull();
  });

  it('refuses to count in a workout with no runnable steps', async () => {
    // A 0-set exercise is reachable from the in-app editor, which bypasses the schema. Without this
    // guard the count-in runs and hands over to a runner with no steps — a blank screen with nothing
    // to tap. The check therefore has to happen before the countdown, not after it.
    setLibrary(aWorkout({ id: 'w', name: 'Session', blocks: [{ kind: 'exercise', exerciseId: 'nothing' }] }));
    await renderScreen(<SessionScreen />);

    expect(screen.getByText('Nothing to run')).toBeTruthy();
    expect(screen.queryByText('GET READY')).toBeNull();
  });

  // Driven in pt because an English assertion can't tell `t('session.nothingToRun.title')` from the
  // literal it replaced — which is how this screen (and its "Finish" control, below) shipped with
  // hardcoded English in an otherwise translated app.
  it('renders the empty state in the active locale', async () => {
    await changeLanguage('pt');
    setLibrary(aWorkout({ id: 'w', name: 'Session', blocks: [{ kind: 'exercise', exerciseId: 'nothing' }] }));
    await renderScreen(<SessionScreen />);

    expect(screen.getByText('Nada para executar')).toBeTruthy();
  });

  it('counts in before starting the timers', async () => {
    setLibrary(workoutOf('pullups'));
    await renderScreen(<SessionScreen />);

    expect(screen.getByText('GET READY')).toBeTruthy();
    expect(screen.getByText('Session')).toBeTruthy();
  });
});

describe('step-kind dispatch', () => {
  it('shows the reps screen for a reps step', async () => {
    await start(workoutOf('pullups'));

    expect(screen.getByText('REPS')).toBeTruthy();
    expect(screen.getByText('Pull-ups')).toBeTruthy();
    expect(screen.getByText('Set 1 of 3 · target 6')).toBeTruthy();
  });

  it('shows the hold screen for a timed-hold step', async () => {
    await start(workoutOf('lsit'));

    expect(screen.getByText('HOLD')).toBeTruthy();
    expect(screen.getByText('Set 1 of 3')).toBeTruthy();
    expect(screen.queryByText('REPS')).toBeNull();
  });

  it('shows the interval screen, labelled by variant, for a HIIT step', async () => {
    await start(workoutOf('burpees'));

    // The variant label is what separates the four interval flavours from each other; they share a
    // component, so getting the step kind right is only half of getting the screen right.
    expect(screen.getByText('HIIT')).toBeTruthy();
    expect(screen.getByText('Round 1 of 3')).toBeTruthy();
  });

  it('shows the rest screen once a set is logged', async () => {
    await start(workoutOf('pullups'));

    await fireEvent.press(screen.getByText('Log set → Rest'));

    expect(screen.getByText('REST')).toBeTruthy();
    expect(screen.queryByText('REPS')).toBeNull();
  });

  it('labels the finish control in the active locale', async () => {
    await changeLanguage('pt');
    await start(workoutOf('pullups'));

    expect(screen.getByText('Encerrar')).toBeTruthy();
  });
});

/**
 * The route's own boundary, rendered the way expo-router's `<Try>` hands it over. This is the one
 * screen where a render throw costs data, so it does more than apologise — everything below is about
 * not stranding the sets that were already flushed to the session file.
 */
describe('ErrorBoundary', () => {
  it('closes out the session the runner was writing to', async () => {
    await renderScreen(<ErrorBoundary error={new Error('boom')} retry={jest.fn(() => Promise.resolve())} />);

    expect(mockAbandonActiveSession).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Session interrupted')).toBeTruthy();
  });

  // dismissTo, not back: the runner is a modal, so leaving it *and* landing on a tab is one call.
  it('sends the user to the history tab, where the salvaged session is', async () => {
    await renderScreen(<ErrorBoundary error={new Error('boom')} retry={jest.fn(() => Promise.resolve())} />);

    await fireEvent.press(screen.getByLabelText('Go to History'));

    expect(router.dismissTo).toHaveBeenCalledWith('/history');
  });

  /**
   * Deliberately no retry, unlike the shared boundaries. Re-rendering this route restarts it from the
   * count-in, and mounting the runner again calls `startSession` — a second session file for one
   * workout, with the first one's sets stranded in it.
   */
  it('offers no retry', async () => {
    const retry = jest.fn(() => Promise.resolve());
    await renderScreen(<ErrorBoundary error={new Error('boom')} retry={retry} />);

    expect(screen.queryByLabelText('Try again')).toBeNull();
  });
});
