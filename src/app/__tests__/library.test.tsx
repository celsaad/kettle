import { fireEvent, screen } from '@testing-library/react-native';

import LibraryScreen from '@/app/(tabs)/library';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * Library is the only list that can be narrowed two ways, and the one branch worth its own test is the
 * second: quoting an empty query back at someone who narrowed by *filter* explains nothing, so the
 * no-match card has to notice which of the two emptied the list.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

jest.mock('@/storage/export', () => ({
  exportLibrary: jest.fn().mockResolvedValue(undefined),
  exportSession: jest.fn().mockResolvedValue(undefined),
  exportSessions: jest.fn().mockResolvedValue(undefined),
}));

const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups', type: 'reps' });
const dips = anExercise({ id: 'dips', name: 'Dips', type: 'reps' });

beforeEach(() => {
  useLibraryStore.setState({ library: aLibrary({ exercises: [pullUps, dips] }), status: 'ready' });
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      restDayReminder: false,
      sessionSounds: true,
      backupFolderUri: null,
    },
  });
});

it('narrows the list to matching names', async () => {
  await renderScreen(<LibraryScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ exercises?$/), 'dip');

  expect(screen.getByText('Dips')).toBeTruthy();
  expect(screen.queryByText('Pull-ups')).toBeNull();
});

it('quotes the query back when a search matched nothing', async () => {
  await renderScreen(<LibraryScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText(/^Search [0-9]+ exercises?$/), 'zzz');

  expect(screen.getByText('Nothing matched')).toBeTruthy();
  expect(screen.getByText(/No results for "zzz"/)).toBeTruthy();
});

// Nothing was typed, so there is no query to quote — pointing at the filters is the only useful thing
// the card can say.
it('points at the filters when a filter emptied the list instead', async () => {
  await renderScreen(<LibraryScreen />);

  await fireEvent.press(screen.getByText('Hold'));

  expect(screen.getByText('Nothing matched')).toBeTruthy();
  expect(screen.getByText(/fits those filters/)).toBeTruthy();
});
