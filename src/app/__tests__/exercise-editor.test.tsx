import { fireEvent, screen } from '@testing-library/react-native';
import { Alert } from 'react-native';

import ExerciseEditorScreen from '@/app/exercise-editor';
import type { Library } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { saveLibrary } from '@/storage/library-file';
import { setSearchParams } from '@/test-support/expo-router';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The editor's wiring, not its validation — `validateConfig` has its own unit tests in
 * `domain/__tests__/exercise-form.test.ts` and isn't re-tested here. What's covered is what only
 * exists once the form is mounted: that a validation failure reaches the screen and blocks the write,
 * that the form's strings become numbers on the way into the library, and the delete guard.
 */
jest.mock('@/storage/library-file', () => ({
  loadLibrary: jest.fn(),
  saveLibrary: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-router', () => require('@/test-support/expo-router'));

const savedLibrary = saveLibrary as jest.MockedFunction<typeof saveLibrary>;

function persisted(): Library {
  expect(savedLibrary).toHaveBeenCalled();
  return savedLibrary.mock.calls.at(-1)![0];
}

/**
 * Every config field renders with a placeholder of "0", so they're addressed positionally, in
 * `CONFIG_FIELDS[type]` order. For 'reps' that is: sets, targetRepsMin, targetRepsMax,
 * targetWeightKg, restSec.
 */
function configField(index: number) {
  return screen.getAllByPlaceholderText('0')[index];
}

beforeEach(() => {
  setSearchParams({});
  useLibraryStore.setState({ library: aLibrary(), status: 'ready' });
});

it('refuses a blank name', async () => {
  await renderScreen(<ExerciseEditorScreen />);

  await fireEvent.press(screen.getByText('Save'));

  expect(screen.getByText('Name is required.')).toBeTruthy();
  expect(savedLibrary).not.toHaveBeenCalled();
});

it('surfaces a config error and writes nothing', async () => {
  await renderScreen(<ExerciseEditorScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText('e.g. Front Lever'), 'Pull-ups');
  await fireEvent.changeText(configField(0), '0');
  await fireEvent.press(screen.getByText('Save'));

  // In-app forms write straight to the store and never pass through the zod schema, so this guard is
  // the only thing standing between a typo and a 0-set exercise — which builds zero runnable steps
  // and lands the user on session.tsx's "Nothing to run".
  expect(screen.getByText('Sets must be at least 1.')).toBeTruthy();
  expect(savedLibrary).not.toHaveBeenCalled();
});

it('stores the config as numbers, not the strings the form holds', async () => {
  await renderScreen(<ExerciseEditorScreen />);

  await fireEvent.changeText(screen.getByPlaceholderText('e.g. Front Lever'), 'Pull-ups');
  await fireEvent.changeText(configField(0), '4');
  await fireEvent.changeText(configField(1), '6');
  await fireEvent.changeText(configField(4), '90');
  await fireEvent.press(screen.getByText('Save'));

  // The form holds every field as a string. Persisting those verbatim would put `sets: "4"` into the
  // library — and from there into the YAML the user exports and hand-edits, where it stops matching
  // the documented format and the schema rejects it on the way back in.
  expect(persisted().exercises[0]).toEqual({
    id: 'pull-ups',
    name: 'Pull-ups',
    type: 'reps',
    config: { sets: 4, targetRepsMin: 6, targetRepsMax: undefined, targetWeightKg: undefined, restSec: 90 },
    notes: undefined,
  });
});

it('locks the type of an exercise that already exists', async () => {
  // Each type keys a different config shape. Switching type on an existing exercise would leave the
  // form's values under keys the new type doesn't read, silently zeroing every field.
  setSearchParams({ id: 'pull-ups' });
  useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }) });
  await renderScreen(<ExerciseEditorScreen />);

  await fireEvent.press(screen.getByText('HIIT'));
  await fireEvent.press(screen.getByText('Save'));

  expect(persisted().exercises[0].type).toBe('reps');
});

describe('deleting', () => {
  let alert: jest.SpyInstance<ReturnType<typeof Alert.alert>, Parameters<typeof Alert.alert>>;

  beforeEach(() => {
    setSearchParams({ id: 'pull-ups' });
    alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
  });

  it('refuses to delete an exercise a workout uses as a plain block', async () => {
    useLibraryStore.setState({
      library: aLibrary({
        exercises: [anExercise()],
        workouts: [aWorkout({ name: 'Push day', blocks: [{ kind: 'exercise', exerciseId: 'pull-ups' }] })],
      }),
    });
    await renderScreen(<ExerciseEditorScreen />);

    await fireEvent.press(screen.getByText('Delete exercise'));

    expect(alert).toHaveBeenCalledWith('Exercise in use', expect.stringContaining('Push day'));
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('also refuses when the only use is inside a circuit', async () => {
    // The branch a click-through misses: circuit membership is a second, differently-shaped
    // reference, and missing it would let a delete orphan the circuit rather than the workout.
    useLibraryStore.setState({
      library: aLibrary({
        exercises: [anExercise()],
        workouts: [
          aWorkout({
            name: 'Push day',
            blocks: [{ kind: 'circuit', rounds: 3, members: [{ exerciseId: 'pull-ups' }, { exerciseId: 'dips' }] }],
          }),
        ],
      }),
    });
    await renderScreen(<ExerciseEditorScreen />);

    await fireEvent.press(screen.getByText('Delete exercise'));

    expect(alert).toHaveBeenCalledWith('Exercise in use', expect.stringContaining('Push day'));
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('deletes an unreferenced exercise once confirmed', async () => {
    useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }) });
    await renderScreen(<ExerciseEditorScreen />);

    await fireEvent.press(screen.getByText('Delete exercise'));
    const buttons = alert.mock.calls[0][2]!;
    await buttons.find((button) => button.style === 'destructive')!.onPress!();

    expect(persisted().exercises).toHaveLength(0);
  });
});
