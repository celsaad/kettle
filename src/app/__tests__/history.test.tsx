import { fireEvent, screen } from '@testing-library/react-native';
import { Alert } from 'react-native';

import HistoryScreen from '@/app/(tabs)/history';
import type { Session, SessionEntry } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
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
