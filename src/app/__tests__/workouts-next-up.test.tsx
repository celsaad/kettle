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

/**
 * A program whose very first slot is a rest day. Shared by the two describes below because they pull
 * in opposite directions on it: the first-run card must stay away (its step one points at a Start
 * button that isn't there) and the starter-pack link must not, which is the whole reason the two are
 * gated differently.
 */
function setRestDayLibrary() {
  useLibraryStore.setState({
    library: aLibrary({
      exercises: [anExercise()],
      workouts: [aWorkout({ id: 'push-day', name: 'Push day' })],
      programs: [
        {
          id: 'base',
          name: 'Base',
          weeks: [
            { week: 1, day: 'Day 1', restDay: true },
            { week: 1, day: 'Day 2', workoutId: 'push-day' },
          ],
        },
      ],
    }),
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
    expect(screen.getByText('Your library is a file')).toBeTruthy();
  });

  it('is gone once a session has been logged', async () => {
    setSeededLibrary();
    useSessionHistoryStore.setState({ sessions: [aSession()], status: 'ready' });

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText('NEW HERE?')).toBeNull();
    // The screen itself is still fine — this is the card going away, not the tab.
    expect(screen.getByText('Start session')).toBeTruthy();
  });

  /**
   * Step 3 asserts the library is a file you own and can have an assistant write, and the card is
   * untappable by design — so the claim is only honest if the way to act on it is on screen. That is
   * the starter-pack link directly beneath, which is why this pair is pinned rather than left to the
   * two components' separate cases: they are gated independently, and nothing else would notice if
   * one of them stopped rendering next to the other.
   */
  it('puts the ownership claim above something that acts on it', async () => {
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Your library is a file')).toBeTruthy();
    expect(screen.getByText('Add a starter program')).toBeTruthy();
  });

  it('stays out of the empty state, which is its own single instruction', async () => {
    // Both conditions hold at once for a brand-new user who clears the seeded library: no sessions
    // and nothing to run. Step one would point at a workout that isn't there.
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText('NEW HERE?')).toBeNull();
    expect(screen.getByText('No workouts yet')).toBeTruthy();
  });

  /**
   * The gate is `queued`, not `nextUp`, and this is the case that tells them apart: a program whose
   * very first slot is a rest day, opened by someone who has never logged a session. Both branches
   * have something to show, so a `nextUp !== null` gate passes — and then step one reads "Start the
   * workout below" directly above a card that says today runs nothing and offers no Start button.
   *
   * Reachable rather than theoretical: `nextUpView` shows a leading rest slot until something is
   * logged (there is no anchor to count elapsed days from), so any imported program that starts on a
   * rest day lands a new user here on first launch.
   */
  it('stays out of a rest day, which has no workout to point at', async () => {
    setRestDayLibrary();

    await renderScreen(<WorkoutsScreen />);

    // The rest card is what's showing, and it has no Start button — so the guidance must not be here.
    expect(screen.getByText('Rest day')).toBeTruthy();
    expect(screen.queryByText('Start session')).toBeNull();
    expect(screen.queryByText('NEW HERE?')).toBeNull();
    expect(screen.queryByText('Start the workout below')).toBeNull();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('PRIMEIRA VEZ?')).toBeTruthy();
    expect(screen.getByText('Sua biblioteca é um arquivo')).toBeTruthy();
  });
});

/**
 * The way into the bundled starter packs, for someone who has logged nothing.
 *
 * It exists because the listing sells a library you own and can have an assistant write, and the app
 * reached that promise through a caption in the Library header and a row in Settings. What's pinned
 * here is mostly the *gate*, which is `sessions.length === 0 && all.length > 0` rather than the
 * `isFirstRun` next door, and both halves of that have a case below.
 */
describe('the starter-pack link', () => {
  it('is offered to someone who has never logged a session', async () => {
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Add a starter program')).toBeTruthy();
  });

  it('opens import', async () => {
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);
    await fireEvent.press(screen.getByText('Add a starter program'));

    expect(router.push).toHaveBeenCalledWith('/import');
  });

  it('is gone once a session has been logged', async () => {
    setSeededLibrary();
    useSessionHistoryStore.setState({ sessions: [aSession()], status: 'ready' });

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText('Add a starter program')).toBeNull();
  });

  /**
   * The case the `isFirstRun` gate gets wrong, and the reason this control has a gate of its own.
   * `isFirstRun` is `sessions.length === 0 && queued !== null`, and `queued` is null on a rest day —
   * so a brand-new user on a program that opens with one would see no first-run card and, having
   * workouts, no empty state either. Reusing that gate here leaves them nothing at all.
   *
   * Verified by reintroducing it: swap `showStarterPacks` for `isFirstRun` and this is the case that
   * fails.
   */
  it('is offered on a rest day, which the first-run card stays out of', async () => {
    setRestDayLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Rest day')).toBeTruthy();
    expect(screen.queryByText('NEW HERE?')).toBeNull();
    expect(screen.getByText('Add a starter program')).toBeTruthy();
  });

  /**
   * The other half of the gate. An empty library gets this offer from the list's own empty state, so
   * the header link stands down — `getAllByText` rather than `getByText` because the failure being
   * pinned is *two* invitations on one screen, which `getByText` would report as an ambiguous match
   * rather than as the duplication it is.
   */
  it('appears exactly once on an empty library, from the empty state', async () => {
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('No workouts yet')).toBeTruthy();
    expect(screen.getAllByText('Add a starter program')).toHaveLength(1);
  });

  /**
   * The same assertion as "opens import" above, against the other instance. Both come from one
   * component now, so this looks redundant — it is not: the two used to be copies, and the copy in
   * the empty state was the one nothing pressed. Retargeting it left the suite green.
   */
  it('opens import from the empty state too', async () => {
    setEmptyLibrary();

    await renderScreen(<WorkoutsScreen />);
    await fireEvent.press(screen.getByText('Add a starter program'));

    expect(router.push).toHaveBeenCalledWith('/import');
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    setSeededLibrary();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.getByText('Adicionar um programa inicial')).toBeTruthy();
  });
});

/**
 * What a long workout looks like on the card, now that it has no chips.
 *
 * It used to open with a wrapping row of block chips, capped first at eight and then at six as the
 * card kept overflowing; these cases pinned that cap. The chips are gone — on a device that row was
 * most of the reason the card filled the screen, and the summary line beneath it already made the same
 * claim in one line, for any length of workout. So what's pinned now is the opposite property: a
 * twelve-block session takes no more vertical room than a three-block one, and the summary is what
 * scales instead of the card.
 */
describe('a long workout', () => {
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

  /**
   * The regression that matters, and it fails against a card that lists its blocks: reintroducing the
   * chip row puts every one of these names in the tree. The movement names belong in the runner, on
   * the screen where you need to know them.
   */
  it('names no individual block on the card', async () => {
    setLongWorkout();

    await renderScreen(<WorkoutsScreen />);

    expect(screen.queryByText('Squat')).toBeNull();
    expect(screen.queryByText('Dip')).toBeNull();
    expect(screen.queryByText('Crunch')).toBeNull();
    // Nor the "+N more" chip that used to stand in for the tail.
    expect(screen.queryByText(/more/)).toBeNull();
  });

  // The one line that does have to scale with the workout, since it is now the only description of it.
  it('describes it in a single summary line instead', async () => {
    setLongWorkout();

    await renderScreen(<WorkoutsScreen />);

    // Twice, like the workout's name: the card and the list row below it describe the same workout
    // with the same one-liner, which is the point — it is the description that scales.
    expect(screen.getAllByText(/12 blocks/)).toHaveLength(2);
    expect(screen.getByText('Start session')).toBeTruthy();
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
