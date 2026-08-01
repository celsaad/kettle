import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';

import TodayScreen from '@/app/(tabs)/index';
import type { Session } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { router } from '@/test-support/expo-router';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * Today's empty state.
 *
 * The screen used to `return null` whenever `nextUpView` came back null, and the only way it does
 * that is a library with no workouts — reachable by deleting the seeded programs and then the seeded
 * workouts, which is a normal reaction to example data. The result was a blank home tab: no wordmark,
 * no settings button, nothing to say why. Every other tab kept working, so it read as a crash.
 *
 * What's pinned here is that the chrome survives an empty library and that there's a way out of it.
 * The hydration guard (`library === null`) still returns null on purpose and is a different case.
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

    await renderScreen(<TodayScreen />);

    // The wordmark is the cheapest proof the screen rendered at all: it sits above everything the
    // empty library affects, so it was the first casualty of the blank-screen bug.
    expect(screen.getByText('Kettle')).toBeTruthy();
    expect(screen.getByText('Today')).toBeTruthy();
    expect(screen.getByText('Nothing to run yet')).toBeTruthy();
    expect(screen.queryByText('Start session')).toBeNull();
  });

  it('offers a way out, so the empty state is not a dead end', async () => {
    setEmptyLibrary();

    await renderScreen(<TodayScreen />);
    await fireEvent.press(screen.getByText('New workout'));

    expect(router.push).toHaveBeenCalledWith('/workout-editor');
  });

  it('is translated', async () => {
    // Driven in pt because an English assertion cannot tell `t('today.emptyTitle')` from a hardcoded
    // literal — both render identically.
    await changeLanguage('pt');
    setEmptyLibrary();

    await renderScreen(<TodayScreen />);

    expect(screen.getByText('Nada para fazer ainda')).toBeTruthy();
    expect(screen.getByText('Novo treino')).toBeTruthy();
  });
});

it('still suggests a workout when the library has one', async () => {
  setSeededLibrary();

  await renderScreen(<TodayScreen />);

  expect(screen.getByText('Push day')).toBeTruthy();
  expect(screen.getByText('Start session')).toBeTruthy();
  expect(screen.queryByText('Nothing to run yet')).toBeNull();
});

/**
 * The first-run guidance. Who sees it is derived from history rather than persisted: "has never
 * finished a session" is what being new actually means, and it costs no storage — which is also why
 * it behaves the same on web, where nothing can be persisted at all.
 */
describe('first-run guidance', () => {
  it('is shown to someone who has never logged a session', async () => {
    setSeededLibrary();

    await renderScreen(<TodayScreen />);

    expect(screen.getByText('NEW HERE?')).toBeTruthy();
    expect(screen.getByText('Start the workout below')).toBeTruthy();
    expect(screen.getByText('Make it yours in Build')).toBeTruthy();
    expect(screen.getByText('Plan weeks in Programs')).toBeTruthy();
  });

  it('is gone once a session has been logged', async () => {
    setSeededLibrary();
    useSessionHistoryStore.setState({ sessions: [aSession()], status: 'ready' });

    await renderScreen(<TodayScreen />);

    expect(screen.queryByText('NEW HERE?')).toBeNull();
    // The screen itself is still fine — this is the card going away, not the tab.
    expect(screen.getByText('Start session')).toBeTruthy();
  });

  it('stays out of the empty state, which is its own single instruction', async () => {
    // Both conditions hold at once for a brand-new user who clears the seeded library: no sessions
    // and nothing to run. Step one would point at a workout that isn't there.
    setEmptyLibrary();

    await renderScreen(<TodayScreen />);

    expect(screen.queryByText('NEW HERE?')).toBeNull();
    expect(screen.getByText('Nothing to run yet')).toBeTruthy();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    setSeededLibrary();

    await renderScreen(<TodayScreen />);

    expect(screen.getByText('PRIMEIRA VEZ?')).toBeTruthy();
    expect(screen.getByText('Planeje semanas em Programas')).toBeTruthy();
  });
});
