import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import WorkoutsScreen from '@/app/(tabs)/index';
import type { Session } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { router } from '@/test-support/expo-router';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The next-up half of the Workouts tab: the card that says what to run, and the empty state behind it.
 * The list underneath it is `workouts-list.test.tsx`; the two were separate tabs and separate suites
 * before the merge, and stayed separate suites because they fail for entirely different reasons.
 *
 * The empty state is the older story here. The screen used to `return null` whenever `nextUpView`
 * came back null, and the only way it does that is a library with no workouts — reachable by deleting
 * the seeded programs and then the seeded workouts, which is a normal reaction to example data. The
 * result was a blank home tab: no title, no settings button, nothing to say why. Every other tab kept
 * working, so it read as a crash.
 *
 * What's pinned is that the chrome survives an empty library and that there's a way out of it. The
 * hydration guard (`library === null`) still returns null on purpose and is a different case.
 *
 * **Watch for names that now appear twice.** The card and the list render the same library, so a
 * queued workout's name is in the tree twice — assertions about the *card* have to say something only
 * the card says (its `NEXT UP` label, its `Start session` button) rather than a workout name.
 */
jest.mock('@/storage/library-file', () => ({
  loadLibrary: jest.fn(),
  saveLibrary: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-router', () => require('@/test-support/expo-router'));

beforeEach(() => {
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
});

/** An exercise but no workouts: nothing for `nextUpView` to suggest, which is the null path. */
function setEmptyLibrary() {
  useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }), status: 'ready' });
}

/** The seeded shape a first run actually has: something to run, nothing logged yet. */
function setSeededLibrary() {
  useLibraryStore.setState({
    library: aLibrary({ exercises: [anExercise()], workouts: [aWorkout({ name: 'Push day' })] }),
    status: 'ready',
  });
}

function aSession(overrides: Partial<Session> = {}): Session {
  return {
    version: 1,
    id: 'session-1',
    workout: 'push-day',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt: '2026-07-29T09:00:00.000Z',
    endedAt: '2026-07-29T09:40:00.000Z',
    entries: [],
    ...overrides,
  };
}

describe('with no workouts', () => {
  it('keeps the screen chrome rather than rendering nothing', async () => {
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);

    // The title and the gear are the cheapest proof the screen rendered at all: they sit above
    // everything the empty library affects, so they were the first casualties of the blank-screen bug.
    // The gear matters twice over — this screen is the app's only route to Settings, so losing it
    // strands the user rather than just looking broken.
    expect(screen.getByText('Workouts')).toBeTruthy();
    expect(screen.getByLabelText('Settings')).toBeTruthy();
    expect(screen.getByText('No workouts yet')).toBeTruthy();
    expect(screen.queryByText('Start session')).toBeNull();
    // No card at all rather than a card saying the library is empty — the list's own empty state
    // above already says it, and two of them is worse than either.
    expect(screen.queryByText('NEXT UP')).toBeNull();
  });

  it('offers a way out, so the empty state is not a dead end', async () => {
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);
    // The FAB is a bare "+", so its accessible name is the only thing that identifies it.
    await fireEvent.press(screen.getByLabelText('New workout'));

    expect(router.push).toHaveBeenCalledWith('/workout-editor');
  });

  it('is translated', async () => {
    // Driven in pt because an English assertion cannot tell `t('build.emptyTitle')` from a hardcoded
    // literal — both render identically.
    await changeLanguage('pt');
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Nenhum treino ainda')).toBeTruthy();
    expect(screen.getByLabelText('Novo treino')).toBeTruthy();
  });
});

it('still suggests a workout when the library has one', async () => {
  setSeededLibrary();

  await renderScreen(<WorkoutsScreen />);

  // Twice: once on the card, once in the list below it. Asserting a single match here would fail for
  // the wrong reason the moment the card and the list agree, which is the normal case.
  expect(screen.getAllByText('Push day')).toHaveLength(2);
  expect(screen.getByText('Start session')).toBeTruthy();
  expect(screen.queryByText('No workouts yet')).toBeNull();
});

/**
 * The first-run guidance. Who sees it is derived from history rather than persisted: "has never
 * finished a session" is what being new actually means, and it costs no storage — which is also why
 * it behaves the same on web, where nothing can be persisted at all.
 */
describe('first-run guidance', () => {
  it('is shown to someone who has never logged a session', async () => {
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('NEW HERE?')).toBeTruthy();
    expect(screen.getByText('Start the workout below')).toBeTruthy();
    expect(screen.getByText('Make it yours')).toBeTruthy();
    expect(screen.getByText('Plan weeks in Programs')).toBeTruthy();
  });

  it('is gone once a session has been logged', async () => {
    setSeededLibrary();
    useSessionHistoryStore.setState({ sessions: [aSession()], status: 'ready' });

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText('NEW HERE?')).toBeNull();
    // The screen itself is still fine — this is the card going away, not the tab.
    expect(screen.getByText('Start session')).toBeTruthy();
  });

  it('stays out of the empty state, which is its own single instruction', async () => {
    // Both conditions hold at once for a brand-new user who clears the seeded library: no sessions
    // and nothing to run. Step one would point at a workout that isn't there.
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText('NEW HERE?')).toBeNull();
    expect(screen.getByText('No workouts yet')).toBeTruthy();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('PRIMEIRA VEZ?')).toBeTruthy();
    expect(screen.getByText('Planeje semanas em Programas')).toBeTruthy();
  });
});

/**
 * The block chips on the Next-up card.
 *
 * A real workout produces around twenty of them — one per block plus one per circuit member — and
 * they wrap, so an uncapped row pushed `Start session` below the fold and behind the tab bar. A new
 * user then had to scroll to reach the app's primary action on the screen that opens first.
 *
 * The cap tightened from eight to six when this card became the header of the workout list rather
 * than a screen of its own: it now has to end near the fold or the list it introduces never shares a
 * screen with it.
 *
 * What's pinned is the cap and the summary, not the exact number that fits: `blockChips` still
 * returns the whole list (`selectors-dst-chips.test.ts` owns that), and the slice is the card's.
 */
describe('a long workout’s chips', () => {
  /** Twelve blocks, which is an ordinary session rather than a contrived one. */
  function setLongWorkout() {
    const names = ['Squat', 'Bench', 'Row', 'Press', 'Curl', 'Dip', 'Lunge', 'Plank', 'Fly', 'Pulldown', 'Calf', 'Crunch'];
    const exercises = names.map((name) => anExercise({ id: name.toLowerCase(), name }));
    useLibraryStore.setState({
      library: aLibrary({
        exercises,
        workouts: [
          aWorkout({
            name: 'Long day',
            blocks: exercises.map((exercise) => ({ kind: 'exercise', exerciseId: exercise.id })),
          }),
        ],
      }),
      status: 'ready',
    });
  }

  it('shows the first six and summarises the rest', async () => {
    setLongWorkout();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Squat')).toBeTruthy();
    expect(screen.getByText('Dip')).toBeTruthy();
    // The seventh onwards are folded into the summary rather than rendered.
    expect(screen.queryByText('Lunge')).toBeNull();
    expect(screen.queryByText('Crunch')).toBeNull();
    expect(screen.getByText('+6 more')).toBeTruthy();
  });

  /**
   * The regression this pins. Removing the cap fails it: `Start session` is still in the tree either
   * way, so the assertion has to be about the chips that pushed it down rather than about the button.
   */
  it('keeps Start session above a row that would otherwise fill the card', async () => {
    setLongWorkout();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Start session')).toBeTruthy();
    expect(screen.queryByText('Crunch')).toBeNull();
  });

  it('leaves a short workout alone, with no summary chip', async () => {
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText(/more/)).toBeNull();
  });

  /**
   * Driven in `pt` because an English assertion cannot tell `t('today.moreBlocks')` from a hardcoded
   * literal — and a `+N more` built by hand is exactly the shape that gets hardcoded. It also proves
   * the count runs through i18next's `count` rather than a `=== 1` ternary, since the plural form is
   * what resolves the string at all.
   */
  it('is translated', async () => {
    await changeLanguage('pt');
    setLongWorkout();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('+6 a mais')).toBeTruthy();
  });
});

/**
 * What keeps the card from growing without limit, now that it is the header of a list rather than a
 * screen of its own.
 *
 * Everything capped here is capped with `numberOfLines` rather than a `maxHeight` on the card: a fixed
 * height clips its own contents at large accessibility text sizes, the same reason every touch target
 * in this codebase uses `minHeight`. So the assertions read the prop, which is the mechanism — there
 * is no rendered height to measure in this environment, and a snapshot of one would prove nothing.
 */
describe('the card’s height caps', () => {
  /** A note far longer than any card should render, which is what a real program week can carry. */
  const longNote =
    'Deload week: keep every working set at RPE 7 or below, add a set only if the last one moved fast, ' +
    'and stop entirely if the bar speed drops. Sleep is the priority this week.';

  function setProgramWithNote() {
    useLibraryStore.setState({
      library: aLibrary({
        exercises: [anExercise()],
        workouts: [aWorkout({ id: 'push-day', name: 'Push day' })],
        programs: [{ id: 'base', name: 'Base', weeks: [{ week: 1, workoutId: 'push-day', notes: longNote }] }],
      }),
      status: 'ready',
    });
  }

  it('caps a week’s note, which is unbounded user text', async () => {
    setProgramWithNote();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText(longNote).props.numberOfLines).toBe(2);
  });

  it('caps the workout name, which is also the user’s to make as long as they like', async () => {
    setProgramWithNote();

    await renderScreen(<WorkoutsScreen />);

    // The card's copy, not the list row's — the row has no cap and needs none, since it is one line of
    // a scrolling list rather than the thing standing between the reader and the list.
    const onCard = screen.getAllByText('Push day').find((node) => node.props.numberOfLines === 2);
    expect(onCard).toBeTruthy();
  });

  it('caps a rest day’s note the same way', async () => {
    useLibraryStore.setState({
      library: aLibrary({
        exercises: [anExercise()],
        workouts: [aWorkout({ id: 'push-day', name: 'Push day' })],
        programs: [
          {
            id: 'base',
            name: 'Base',
            weeks: [
              { week: 1, day: 'Day 1', workoutId: 'push-day' },
              { week: 1, day: 'Day 2', restDay: true, notes: longNote },
            ],
          },
        ],
      }),
      status: 'ready',
    });
    const startedAt = new Date().toISOString();
    useSessionHistoryStore.setState({
      sessions: [aSession({ startedAt, endedAt: startedAt, program: 'base', programWeek: 1, programDay: 'Day 1' })],
      status: 'ready',
    });

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText(longNote).props.numberOfLines).toBe(2);
  });
});

/**
 * The way into a session with no pre-built workout. It sits outside the Next-up card's conditional so
 * both branches carry it — the empty-library case being the one where it earns its place, since with
 * no workouts at all it is the only way to train.
 */
describe('starting an empty session', () => {
  it('offers the entry point alongside a queued workout', async () => {
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Start an empty session')).toBeTruthy();
  });

  it('offers it with no workouts to run at all', async () => {
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('No workouts yet')).toBeTruthy();
    expect(screen.getByText('Start an empty session')).toBeTruthy();
  });

  it('starts the session with the ad-hoc flag rather than a workout id', async () => {
    setSeededLibrary();
    await renderScreen(<WorkoutsScreen />);

    await fireEvent.press(screen.getByText('Start an empty session'));

    expect(router.push).toHaveBeenCalledWith({ pathname: '/session', params: { adhoc: '1' } });
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Começar uma sessão vazia')).toBeTruthy();
  });
});

/**
 * The rest-day card.
 *
 * The program says today runs nothing, so the card must not offer a Start button — that is the whole
 * claim of the feature, and the one thing a regression here would silently undo. The card's own
 * "train anyway" link and the "start an empty session" button below it are what keep it from being a
 * dead end.
 *
 * `startedAt` is built from the real clock rather than a fixed date: the screen calls `nextUpView`
 * with its default `now`, and the rule that decides rest-versus-workout counts calendar days between
 * the two. The arithmetic itself is pinned with an injected clock in `selectors.test.ts`.
 */
describe('a scheduled rest day', () => {
  /** A three-slot week whose middle slot is rest, with the first slot logged today. */
  function setRestDayProgram() {
    useLibraryStore.setState({
      library: aLibrary({
        exercises: [anExercise()],
        workouts: [aWorkout({ id: 'push-day', name: 'Push day' }), aWorkout({ id: 'pull-day', name: 'Pull day' })],
        programs: [
          {
            id: 'base',
            name: 'Base',
            weeks: [
              { week: 1, day: 'Day 1', workoutId: 'push-day' },
              { week: 1, day: 'Day 2', restDay: true, notes: 'Walk, nothing heavy.' },
              { week: 1, day: 'Day 3', workoutId: 'pull-day' },
            ],
          },
        ],
      }),
      status: 'ready',
    });
    const startedAt = new Date().toISOString();
    useSessionHistoryStore.setState({
      sessions: [aSession({ startedAt, endedAt: startedAt, program: 'base', programWeek: 1, programDay: 'Day 1' })],
      status: 'ready',
    });
  }

  it('replaces the workout card and offers nothing to start', async () => {
    setRestDayProgram();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Rest day')).toBeTruthy();
    expect(screen.getByText('REST DAY · Week 1 · Day 2')).toBeTruthy();
    expect(screen.queryByText('Start session')).toBeNull();
    // Not "Pull day is absent" — it is in the workout list below, as it should be. The claim is that
    // the *card* isn't the workout one, and `NEXT UP` is the only thing that says so.
    expect(screen.queryByText('NEXT UP')).toBeNull();
  });

  it("shows the week's own note, which is the user's own text", async () => {
    setRestDayProgram();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Walk, nothing heavy.')).toBeTruthy();
  });

  it('does not follow the rest card with the empty-library state', async () => {
    setRestDayProgram();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText('No workouts yet')).toBeNull();
  });

  it('jumps to the next slot that runs something when asked', async () => {
    setRestDayProgram();

    await renderScreen(<WorkoutsScreen />);
    await fireEvent.press(screen.getByText('Train anyway'));

    expect(router.push).toHaveBeenCalledWith({
      pathname: '/session',
      params: { programId: 'base', week: '1', day: 'Day 3' },
    });
  });

  it('leaves the empty session available, so a rest day is never a dead end', async () => {
    setRestDayProgram();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Start an empty session')).toBeTruthy();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    setRestDayProgram();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Dia de descanso')).toBeTruthy();
    expect(screen.getByText('Treinar mesmo assim')).toBeTruthy();
    // User data stays in the language it was written in, translated screen or not.
    expect(screen.getByText('Walk, nothing heavy.')).toBeTruthy();
  });
});
