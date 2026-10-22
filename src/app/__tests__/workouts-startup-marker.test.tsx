import { act, fireEvent, screen } from '@testing-library/react-native';

import WorkoutsScreen from '@/app/(tabs)/index';
import { reportHomeDrawn } from '@/hooks/startup-marker';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * Where the startup Macrobenchmark stops its clock (docs/native-migration-plan.md, M2). The library
 * loads after the first render, which draws nothing, so a marker on that render would time both builds
 * to a blank screen and make them look faster than they are.
 */
jest.mock('@/hooks/startup-marker', () => ({ reportHomeDrawn: jest.fn() }));
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const layout = { nativeEvent: { layout: { x: 0, y: 0, width: 400, height: 800 } } };
const loaded = {
  library: aLibrary({ exercises: [anExercise()], workouts: [aWorkout({ name: 'Bench day' })] }),
  status: 'ready' as const,
};

beforeEach(() => {
  jest.mocked(reportHomeDrawn).mockClear();
  useSessionHistoryStore.setState({ sessions: [], status: 'ready' });
});

it('reports Home drawn once its loaded content lays out', async () => {
  useLibraryStore.setState(loaded);
  await renderScreen(<WorkoutsScreen />);

  await fireEvent(screen.getByTestId('home-screen'), 'layout', layout);

  expect(reportHomeDrawn).toHaveBeenCalled();
});

it('does not report while the library is still loading', async () => {
  useLibraryStore.setState({ library: null, status: 'loading' });
  await renderScreen(<WorkoutsScreen />);

  // Layout isn't fired by the harness, so the marker's view being absent is what this rests on: a
  // marker moved above the bail-out would be on screen here, and laid out.
  expect(screen.queryByTestId('home-screen')).toBeNull();
  expect(reportHomeDrawn).not.toHaveBeenCalled();

  await act(async () => {
    useLibraryStore.setState(loaded);
  });
  await fireEvent(screen.getByTestId('home-screen'), 'layout', layout);

  expect(reportHomeDrawn).toHaveBeenCalled();
});
