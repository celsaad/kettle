import { act, fireEvent, screen } from '@testing-library/react-native';
import { Alert } from 'react-native';
import { changeLanguage } from 'i18next';

import SessionScreen, { ErrorBoundary } from '@/app/session';
import type { Exercise, Session, SessionEntry, Workout } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { router, setSearchParams } from '@/test-support/expo-router';
import { aLibrary, aWorkout } from '@/test-support/library';
import { pressAlertButton, renderScreen } from '@/test-support/render';

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
/** The log the completion screen judges the finished session against — see the PR test below. */
let mockSessions: Session[] = [];
const mockAbandonActiveSession = jest.fn();
jest.mock('@/state/session-history-store', () => ({
  // Both call shapes: the hook form for actions and the completion screen's `sessions`, and
  // `getState()` for the history snapshot the runner takes at session start without subscribing.
  useSessionHistoryStore: Object.assign((selector: (state: unknown) => unknown) => selector(mockStoreState()), {
    getState: () => mockStoreState(),
  }),
}));

function mockStoreState() {
  return {
    sessions: mockSessions,
    startSession: () => mockSession,
    logEntry: (current: Session, entry: SessionEntry) => ({ ...current, entries: [...current.entries, entry] }),
    replaceEntry: (current: Session, index: number, entry: SessionEntry) => ({
      ...current,
      entries: current.entries.map((existing, position) => (position === index ? entry : existing)),
    }),
    removeLastEntry: (current: Session) => current,
    completeSession: (current: Session) => current,
    abandonActiveSession: mockAbandonActiveSession,
  };
}

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
  mockSessions = [];
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

  /**
   * A rest week resolves to no workout, exactly like a broken reference does — so without its own
   * branch this screen returns null and renders a blank page. Reachable by deep link, by the back
   * stack, or by editing a week into a rest day while a link to it is still live.
   */
  it('explains a rest week rather than rendering a blank screen', async () => {
    useLibraryStore.setState({
      library: aLibrary({
        exercises,
        workouts: [workoutOf('pullups')],
        programs: [
          {
            id: 'p',
            name: 'P',
            weeks: [
              { week: 1, day: 'Day 1', workoutId: 'w' },
              { week: 1, day: 'Day 2', restDay: true },
            ],
          },
        ],
      }),
      status: 'ready',
    });
    setSearchParams({ programId: 'p', week: '1', day: 'Day 2' });
    await renderScreen(<SessionScreen />);

    expect(screen.getByText('Rest day')).toBeTruthy();
    expect(screen.queryByText('GET READY')).toBeNull();
    // Not the generic error: the program is fine, it just runs nothing today.
    expect(screen.queryByText('Nothing to run')).toBeNull();
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
 * The second breadcrumb line, which exists because a circuit's steps are interleaved: the exercise
 * name says what you're doing and the header dots say which block, but between them nothing said
 * that two more rounds of this were coming. The round is on screen; the place in the round-robin is
 * carried by the dots, so it's the accessibility label that has both to assert against.
 */
describe('the circuit crumb', () => {
  const circuitWorkout = aWorkout({
    id: 'w',
    name: 'Session',
    blocks: [
      {
        kind: 'circuit',
        rounds: 2,
        restBetweenExercisesSec: 15,
        members: [{ exerciseId: 'pullups' }, { exerciseId: 'lsit' }],
      },
    ],
  });

  it('names the round and the place in the round-robin', async () => {
    await start(circuitWorkout);

    expect(screen.getByText('CIRCUIT · ROUND 1 OF 2')).toBeTruthy();
    expect(screen.getByLabelText('Circuit, round 1 of 2, exercise 1 of 2')).toBeTruthy();
  });

  it('follows the round-robin across members and rounds', async () => {
    await start(circuitWorkout);

    // Through the between-exercises rest, which keeps the position of the work it followed rather
    // than jumping ahead to what's next.
    await fireEvent.press(screen.getByText('Log set → Rest'));
    expect(screen.getByLabelText('Circuit, round 1 of 2, exercise 1 of 2')).toBeTruthy();

    await fireEvent.press(screen.getByText('Skip rest →'));
    expect(screen.getByLabelText('Circuit, round 1 of 2, exercise 2 of 2')).toBeTruthy();

    // No between-rounds rest configured, so the hold hands straight over to round 2.
    await fireEvent.press(screen.getByText('Done set →'));
    expect(screen.getByText('CIRCUIT · ROUND 2 OF 2')).toBeTruthy();
    expect(screen.getByLabelText('Circuit, round 2 of 2, exercise 1 of 2')).toBeTruthy();
  });

  // The crumb reserves the header control's width with an invisible copy of its label, so the row
  // below lines up with the row above. Two "Finish" strings in the tree is the cost, and this is what
  // stops the real control getting lost among them.
  it('keeps the finish control uniquely addressable beside the crumb spacer', async () => {
    await start(circuitWorkout);

    expect(screen.getByLabelText('Finish session?')).toBeTruthy();
  });

  it('shows nothing outside a circuit', async () => {
    await start(workoutOf('pullups'));

    expect(screen.queryByText(/CIRCUIT/)).toBeNull();
  });

  it('renders in the active locale', async () => {
    await changeLanguage('pt');
    await start(circuitWorkout);

    expect(screen.getByText('CIRCUITO · RODADA 1 DE 2')).toBeTruthy();
  });
});

/**
 * The hand-off the completion screen depends on: the runner passes the session it just finished
 * writing, because nothing else can. React unmounts the runner and the ref holding that `Session` on
 * the same tick, and `completeSession` has already cleared `activeSessionId` by then — so a screen
 * that read it back off the store would find nothing to compare.
 */
describe('finishing a session', () => {
  const finish = async () => {
    const alert = jest.spyOn(Alert, 'alert');
    await fireEvent.press(screen.getByLabelText('Finish session?'));
    await pressAlertButton(alert, 'destructive');
  };

  it('lands on the completion screen', async () => {
    await start(workoutOf('pullups'));

    await finish();

    expect(screen.getByText('Workout complete')).toBeTruthy();
  });

  it('reports what the finished session beat', async () => {
    // Pull-ups are bodyweight (the runner seeds load from targetWeightKg, which this exercise has
    // none of), so the record they compete for is reps — 6 seeded from the target, against 4 logged
    // a week ago.
    mockSessions = [
      {
        version: 1,
        id: 'sess-earlier',
        workout: 'w',
        program: null,
        programWeek: null,
        programDay: null,
        startedAt: '2026-07-20T09:00:00.000Z',
        endedAt: '2026-07-20T10:00:00.000Z',
        entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 4, restTakenSec: 90 }] }],
      },
    ];
    await start(workoutOf('pullups'));

    await finish();

    expect(screen.getByText('PR')).toBeTruthy();
    expect(screen.getByText('Pull-ups')).toBeTruthy();
    expect(screen.getByText('Most reps ever · 6 reps')).toBeTruthy();
  });

  // The common case, and the one that must stay exactly as it was: nothing beaten, nothing added.
  it('says nothing about records when none were set', async () => {
    await start(workoutOf('pullups'));

    await finish();

    expect(screen.getByText('Workout complete')).toBeTruthy();
    expect(screen.queryByText('PR')).toBeNull();
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

/**
 * An ad-hoc session, started with `adhoc=1` and no workout. An empty step list is its *starting*
 * state rather than the "Nothing to run" error a pre-built workout with no blocks gets.
 */
describe('an ad-hoc session', () => {
  const startAdHoc = async () => {
    setSearchParams({ adhoc: '1' });
    setLibrary(workoutOf('pullups'));
    await renderScreen(<SessionScreen />);
    for (let second = 0; second < 3; second++) {
      await act(async () => {
        jest.advanceTimersByTime(1000);
      });
    }
  };

  it('counts in under the stand-in name rather than refusing to start', async () => {
    setSearchParams({ adhoc: '1' });
    setLibrary(workoutOf('pullups'));
    await renderScreen(<SessionScreen />);

    expect(screen.getByText('GET READY')).toBeTruthy();
    expect(screen.getByText('Ad-hoc session')).toBeTruthy();
    expect(screen.queryByText('Nothing to run')).toBeNull();
  });

  it('waits on the add-exercise state with nothing queued', async () => {
    await startAdHoc();

    expect(screen.getByText('Nothing queued')).toBeTruthy();
    expect(screen.getByText('Add exercise')).toBeTruthy();
  });

  it('runs an exercise picked from the library', async () => {
    await startAdHoc();

    await fireEvent.press(screen.getByText('Add exercise'));
    await fireEvent.press(screen.getByText('Pull-ups'));

    expect(screen.getByText('REPS')).toBeTruthy();
    expect(screen.getByText('Set 1 of 3 · target 6')).toBeTruthy();
  });

  // Driven in pt, since an English assertion can't tell `t('session.adhoc.title')` from the literal.
  it('renders the add-exercise state in the active locale', async () => {
    await changeLanguage('pt');
    await startAdHoc();

    expect(screen.getByText('Nada na fila')).toBeTruthy();
    expect(screen.getByText('Adicionar exercício')).toBeTruthy();
  });
});
