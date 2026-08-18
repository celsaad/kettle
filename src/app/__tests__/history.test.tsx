import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips the same lint rule the
// other screen tests note.
import { changeLanguage } from 'i18next';
import { Alert } from 'react-native';

import HistoryScreen from '@/app/(tabs)/history';
import type { Session, SessionEntry } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { router } from '@/test-support/expo-router';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { pressAlertButton, renderScreen } from '@/test-support/render';

/**
 * History's card behaviour, which had no test until the list moved to a virtualised `FlatList` and
 * every row became a memoised component taking two callbacks instead of closing over its own session.
 *
 * That refactor is invisible when it works and total when it doesn't — a stale `expanded` prop or a
 * callback bound to the wrong row would still render a plausible screen. These pin the four things a
 * card does: expand, collapse, hand the right session to the delete confirm, and stay independent of
 * its neighbours.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

jest.mock('@/storage/export', () => ({
  exportSession: jest.fn().mockResolvedValue(undefined),
  exportSessions: jest.fn().mockResolvedValue(undefined),
}));

const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups' });
const dips = anExercise({ id: 'dips', name: 'Dips' });

const repsEntry = (exercise: string): SessionEntry => ({
  exercise,
  type: 'reps',
  sets: [{ reps: 8, restTakenSec: 60 }],
});

function aSession(id: string, workout: string, startedAt: string, entries: SessionEntry[]): Session {
  return {
    version: 1,
    id,
    workout,
    program: null,
    programWeek: null,
    programDay: null,
    startedAt,
    endedAt: '2026-07-29T10:00:00.000Z',
    entries,
  };
}

const push = aSession('push-1', 'push-day', '2026-07-29T09:00:00.000Z', [repsEntry('pull-ups')]);
const legs = aSession('legs-1', 'leg-day', '2026-07-27T09:00:00.000Z', [repsEntry('dips')]);

beforeEach(() => {
  useLibraryStore.setState({
    library: aLibrary({
      exercises: [pullUps, dips],
      workouts: [aWorkout({ id: 'push-day', name: 'Push day' }), aWorkout({ id: 'leg-day', name: 'Leg day' })],
    }),
    status: 'ready',
  });
  useSessionHistoryStore.setState({ sessions: [push, legs], errors: [], status: 'ready' });
});

it('lists every logged session', async () => {
  await renderScreen(<HistoryScreen />);

  expect(screen.getByText('Push day')).toBeTruthy();
  expect(screen.getByText('Leg day')).toBeTruthy();
});

// The card body is what `expanded` gates, and the exercise names only exist inside it.
it('shows a session-s exercises only once its card is expanded', async () => {
  await renderScreen(<HistoryScreen />);

  expect(screen.queryByText('Pull-ups')).toBeNull();

  await fireEvent.press(screen.getByText('Push day'));

  expect(screen.getByText('Pull-ups')).toBeTruthy();
});

it('collapses a card that was already open', async () => {
  await renderScreen(<HistoryScreen />);

  await fireEvent.press(screen.getByText('Push day'));
  await fireEvent.press(screen.getByText('Push day'));

  expect(screen.queryByText('Pull-ups')).toBeNull();
});

/**
 * The regression a per-row memo most easily introduces: rows now receive `expanded` as a prop rather
 * than each reading `expandedId` themselves, so a comparison that ignored it would leave the first
 * card open — or open both.
 */
it('opens one card at a time', async () => {
  await renderScreen(<HistoryScreen />);

  await fireEvent.press(screen.getByText('Push day'));
  await fireEvent.press(screen.getByText('Leg day'));

  expect(screen.getByText('Dips')).toBeTruthy();
  expect(screen.queryByText('Pull-ups')).toBeNull();
});

// `onDelete` is one shared callback for every card now, so the session it's given is the only thing
// saying which one the user meant.
it('deletes the session whose card was open, not another', async () => {
  const alert = jest.spyOn(Alert, 'alert');
  await renderScreen(<HistoryScreen />);

  await fireEvent.press(screen.getByText('Leg day'));
  await fireEvent.press(screen.getByText('Delete'));
  await pressAlertButton(alert, 'destructive');

  expect(useSessionHistoryStore.getState().sessions.map((session) => session.id)).toEqual(['push-1']);
});

it('narrows both the list and the stat tiles to what matched', async () => {
  await renderScreen(<HistoryScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText('Search workouts'), 'leg');

  expect(screen.queryByText('Push day')).toBeNull();
  expect(screen.getByText('Leg day')).toBeTruthy();
  expect(screen.getByText('1 of 2')).toBeTruthy();
});

// Before this, a search matching nothing left intact chrome over a blank body, which reads as a
// broken screen rather than as an answer.
it('says nothing matched instead of going blank', async () => {
  await renderScreen(<HistoryScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText('Search workouts'), 'zzz');

  expect(screen.getByText('Nothing matched')).toBeTruthy();
  expect(screen.getByText('0 of 2')).toBeTruthy();
});

// An empty log is what a new install *is*, and the tiles above already say so — a "nothing matched"
// card there would be answering a question nobody asked.
it('stays quiet when there is no history at all', async () => {
  useSessionHistoryStore.setState({ sessions: [], errors: [], status: 'ready' });

  await renderScreen(<HistoryScreen />);

  expect(screen.queryByText('Nothing matched')).toBeNull();
});

/**
 * The edit affordance (#56), and the one case where it must not appear.
 *
 * A session still being written to by the runner is in `sessions` — `startSession` puts it there — so
 * History lists it like any other. Offering Edit on it would offer something the store refuses, since
 * the runner writes through its own copy and would overwrite the correction on the next set.
 */
describe('editing a session', () => {
  it('opens the editor for the session whose card is open', async () => {
    await renderScreen(<HistoryScreen />);

    await fireEvent.press(screen.getByText('Leg day'));
    await fireEvent.press(screen.getByText('Edit'));

    expect(router.push).toHaveBeenCalledWith({ pathname: '/session-editor', params: { id: 'legs-1' } });
  });

  it('offers no Edit on a session that is still running', async () => {
    const running: Session = { ...push, id: 'running-1', endedAt: null };
    useSessionHistoryStore.setState({ sessions: [running], errors: [], status: 'ready' });
    await renderScreen(<HistoryScreen />);

    await fireEvent.press(screen.getByText('Push day'));

    // Delete stays: throwing away a session the runner is mid-way through is a coherent thing to want.
    expect(screen.getByText('Delete')).toBeTruthy();
    expect(screen.queryByText('Edit')).toBeNull();
  });
});

/**
 * The two stat rows.
 *
 * `THIS WEEK` (sessions, time, streak) moved here from the old Today tab, where it was a second,
 * smaller copy of the `ALL TIME` row directly below it — same `historyStats` aggregator, one tab over.
 * History owns the session log and the search that scopes it, so it owns the numbers too.
 *
 * The interesting part is what the search does to them. The all-time row narrows with the query, which
 * it always has; the this-week row cannot, because "this week" over "everything matching push" is not
 * a period and the numbers would be describing a set nobody asked about. So it hides outright rather
 * than sitting there unchanged and quietly lying.
 */
describe('the stat tiles', () => {
  it('shows this week above all time', async () => {
    await renderScreen(<HistoryScreen />);

    expect(screen.getByText('This week')).toBeTruthy();
    expect(screen.getByText('All time')).toBeTruthy();
    // One label each from the two rows, and one shared by both.
    expect(screen.getByText('streak')).toBeTruthy();
    expect(screen.getByText('sets')).toBeTruthy();
    expect(screen.getAllByText('sessions')).toHaveLength(2);
  });

  it('drops the this-week row while a search is narrowing the list', async () => {
    await renderScreen(<HistoryScreen />);

    await fireEvent.changeText(screen.getByPlaceholderText('Search workouts'), 'push');

    expect(screen.queryByText('This week')).toBeNull();
    expect(screen.queryByText('streak')).toBeNull();
    // The all-time row stays, because a filtered total is still a total — but it stops calling itself
    // "all time", which would be the same lie in the other direction.
    expect(screen.getByText('sets')).toBeTruthy();
    expect(screen.queryByText('All time')).toBeNull();
    expect(screen.getByText('1 of 2')).toBeTruthy();
  });

  it('brings it back when the search is cleared', async () => {
    await renderScreen(<HistoryScreen />);

    await fireEvent.changeText(screen.getByPlaceholderText('Search workouts'), 'push');
    await fireEvent.changeText(screen.getByPlaceholderText('Search workouts'), '');

    expect(screen.getByText('This week')).toBeTruthy();
    expect(screen.getByText('All time')).toBeTruthy();
  });

  /**
   * Driven in `pt` because an English-locale assertion cannot catch a hardcoded English string —
   * `t('history.thisWeekLabel')` and the literal it returns render identically. Both keys are new, and
   * a label typed straight into the JSX is exactly what this catches.
   */
  it('is translated', async () => {
    await changeLanguage('pt');

    await renderScreen(<HistoryScreen />);

    expect(screen.getByText('Esta semana')).toBeTruthy();
    expect(screen.getByText('Todo o período')).toBeTruthy();
    expect(screen.getByText('dias seguidos')).toBeTruthy();
  });
});
