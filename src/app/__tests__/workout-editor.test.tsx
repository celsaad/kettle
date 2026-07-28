import { fireEvent, screen } from '@testing-library/react-native';
import { Alert } from 'react-native';

import WorkoutEditorScreen from '@/app/workout-editor';
import type { Library, Workout } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { saveLibrary } from '@/storage/library-file';
import { router, setSearchParams } from '@/test-support/expo-router';
import { aLibrary, anExercise, aProgram, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The editor's validation, its circuit-assembly state, and its delete guard.
 *
 * These are the branches the browser check reaches worst. Two of them it cannot reach at all: both
 * delete paths go through `Alert.alert`, which react-native-web implements as a literal no-op, so
 * clicking "Delete workout" in a browser proves only that nothing crashed.
 *
 * Note every `fireEvent` is awaited. In RNTL 14 it returns a Promise, like `render` — skipping the
 * await doesn't fail loudly, it just leaves the assertion looking at the pre-press tree, and the only
 * hint is an "overlapping act() calls" warning from some later test.
 */
jest.mock('@/storage/library-file', () => ({
  loadLibrary: jest.fn(),
  saveLibrary: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-router', () => require('@/test-support/expo-router'));

const savedLibrary = saveLibrary as jest.MockedFunction<typeof saveLibrary>;

/** What the store actually persisted, which is the only durable evidence a save happened. */
function persisted(): Library {
  expect(savedLibrary).toHaveBeenCalled();
  return savedLibrary.mock.calls.at(-1)![0];
}

function persistedWorkout(id: string): Workout | undefined {
  return persisted().workouts.find((workout) => workout.id === id);
}

const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups' });
const dips = anExercise({ id: 'dips', name: 'Dips' });

beforeEach(() => {
  setSearchParams({});
  useLibraryStore.setState({ library: aLibrary({ exercises: [pullUps, dips] }), status: 'ready' });
});

describe('saving', () => {
  it('refuses a blank name instead of writing an unnamed workout', async () => {
    await renderScreen(<WorkoutEditorScreen />);

    await fireEvent.press(screen.getByText('Save'));

    expect(screen.getByText('Name is required.')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
    expect(router.back).not.toHaveBeenCalled();
  });

  it('refuses a name that yields no id, rather than saving one that cannot be addressed', async () => {
    await renderScreen(<WorkoutEditorScreen />);

    // A name of only emoji slugifies to an empty string. Saving anyway would produce a workout with
    // id '' — unreachable by every `find(w => w.id === id)` in the app, including this editor's own.
    // (That non-Latin names hit this guard at all is a logged bug; the guard's job is to fail loudly
    // rather than write the broken record, and that is what's pinned here.)
    await fireEvent.changeText(screen.getByPlaceholderText('e.g. Push day'), '🏋️🏋️');
    await fireEvent.press(screen.getByText('Save'));

    expect(screen.getByText('Could not derive an id from that name.')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('derives the id from the name and trims it', async () => {
    await renderScreen(<WorkoutEditorScreen />);

    await fireEvent.changeText(screen.getByPlaceholderText('e.g. Push day'), '  Push day  ');
    await fireEvent.press(screen.getByText('Save'));

    expect(persistedWorkout('push-day')?.name).toBe('Push day');
    expect(router.back).toHaveBeenCalled();
  });

  it('keeps an existing id when the workout is renamed', async () => {
    // The invariant behind this: program weeks reference workouts by id. Re-deriving the id on every
    // save would silently orphan every program pointing at this workout the moment it's renamed.
    setSearchParams({ id: 'push-day' });
    useLibraryStore.setState({
      library: aLibrary({ exercises: [pullUps], workouts: [aWorkout({ id: 'push-day', name: 'Push day' })] }),
    });
    await renderScreen(<WorkoutEditorScreen />);

    await fireEvent.changeText(screen.getByDisplayValue('Push day'), 'Upper body');
    await fireEvent.press(screen.getByText('Save'));

    expect(persistedWorkout('push-day')?.name).toBe('Upper body');
    expect(persisted().workouts).toHaveLength(1);
  });
});

describe('blocks', () => {
  it('adds a picked exercise as a block and closes the picker', async () => {
    await renderScreen(<WorkoutEditorScreen />);

    await fireEvent.press(screen.getByText('+ Add block'));
    await fireEvent.press(screen.getByText('Pull-ups'));

    expect(screen.getByText('1 block')).toBeTruthy();
    // One occurrence, not two: the row is present and the picker listing it is gone.
    expect(screen.getAllByText('Pull-ups')).toHaveLength(1);
  });

  it('removes a block', async () => {
    await renderScreen(<WorkoutEditorScreen />);
    await fireEvent.press(screen.getByText('+ Add block'));
    await fireEvent.press(screen.getByText('Pull-ups'));

    await fireEvent.press(screen.getByLabelText('Remove Pull-ups'));

    expect(screen.getByText('0 blocks')).toBeTruthy();
  });
});

describe('circuits', () => {
  async function buildCircuit() {
    await fireEvent.press(screen.getByText('Pull-ups'));
    await fireEvent.press(screen.getByText('Dips'));
    await fireEvent.press(screen.getByText('Add circuit (2)'));
  }

  beforeEach(async () => {
    await renderScreen(<WorkoutEditorScreen />);
    await fireEvent.press(screen.getByText('+ New circuit'));
  });

  it('will not build a circuit from a single exercise', async () => {
    await fireEvent.press(screen.getByText('Pull-ups'));
    await fireEvent.press(screen.getByText('Add circuit (1)'));

    // A one-member circuit is just an exercise block with extra rest settings; the button stays
    // disabled and `addCircuit` returns early, so nothing is added.
    expect(screen.getByText('0 blocks')).toBeTruthy();
  });

  it('builds a circuit from two, then clears the selection', async () => {
    await buildCircuit();

    expect(screen.getByText('1 block')).toBeTruthy();
    expect(screen.getByText('Circuit')).toBeTruthy();
    // The picker closed and took the selection with it — reopening must not resurrect the previous
    // members, which would silently add them to the *next* circuit too.
    await fireEvent.press(screen.getByText('+ New circuit'));
    expect(screen.getByText('Add circuit (0)')).toBeTruthy();
  });

  it('deselects a member that is tapped twice', async () => {
    await fireEvent.press(screen.getByText('Pull-ups'));
    await fireEvent.press(screen.getByText('Dips'));
    await fireEvent.press(screen.getByText('Pull-ups'));

    expect(screen.getByText('Add circuit (1)')).toBeTruthy();
  });

  it('does not let rounds fall below one', async () => {
    await buildCircuit();

    expect(screen.getByText('3 rounds · 15s / 60s rest')).toBeTruthy();

    // Four presses of "−" from the default 3 would reach -1 without the Math.max clamp, and a
    // circuit of -1 rounds builds zero steps — a workout that starts and immediately ends.
    const decrement = screen.getByText('−');
    for (let press = 0; press < 4; press++) await fireEvent.press(decrement);

    // Read off the summary line rather than the stepper's own numeral: the members are numbered 1
    // and 2, so a bare `getByText('1')` is ambiguous and would match whichever came first.
    expect(screen.getByText('1 round · 15s / 60s rest')).toBeTruthy();
  });

  it('removes a member without removing the circuit', async () => {
    await buildCircuit();

    await fireEvent.press(screen.getByLabelText('Remove Dips from circuit'));

    expect(screen.getByText('Circuit')).toBeTruthy();
    expect(screen.getByText('1 block')).toBeTruthy();
    expect(screen.queryByText('Dips')).toBeNull();
  });
});

describe('deleting', () => {
  const inProgram = aProgram({ id: 'base', name: 'Base', weeks: [{ week: 1, workoutId: 'push-day' }] });
  let alert: jest.SpyInstance<ReturnType<typeof Alert.alert>, Parameters<typeof Alert.alert>>;

  beforeEach(() => {
    setSearchParams({ id: 'push-day' });
    alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  });

  it('refuses to delete a workout a program still points at', async () => {
    useLibraryStore.setState({
      library: aLibrary({ workouts: [aWorkout({ id: 'push-day', name: 'Push day' })], programs: [inProgram] }),
    });
    await renderScreen(<WorkoutEditorScreen />);

    await fireEvent.press(screen.getByText('Delete workout'));

    // Two arguments, not three: this is the dead-end alert, with no destructive button attached. The
    // program would otherwise be left resolving week 1 to a workout that no longer exists.
    expect(alert).toHaveBeenCalledWith('Workout in use', expect.stringContaining('Base'));
    expect(alert.mock.calls[0]).toHaveLength(2);
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('deletes an unreferenced workout once confirmed', async () => {
    useLibraryStore.setState({
      library: aLibrary({ workouts: [aWorkout({ id: 'push-day', name: 'Push day' })], programs: [] }),
    });
    await renderScreen(<WorkoutEditorScreen />);

    await fireEvent.press(screen.getByText('Delete workout'));

    // Nothing is written on the prompt alone — only the confirming button does the work, so the
    // press is driven through the button the alert was handed.
    expect(savedLibrary).not.toHaveBeenCalled();
    const buttons = alert.mock.calls[0][2]!;
    await buttons.find((button) => button.style === 'destructive')!.onPress!();

    expect(persisted().workouts).toHaveLength(0);
    expect(router.back).toHaveBeenCalled();
  });
});
