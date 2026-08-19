import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips
// `import/no-named-as-default-member`, and that accepted-warning pile is meant to stop growing.
import { changeLanguage } from 'i18next';
import { Alert } from 'react-native';

import ExerciseEditorScreen from '@/app/exercise-editor';
import type { Library } from '@/domain/types';
import type { UnitSystem } from '@/domain/units';
import en from '@/i18n/locales/en.json';
import pt from '@/i18n/locales/pt.json';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { saveLibrary } from '@/storage/library-file';
import { setSearchParams } from '@/test-support/expo-router';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { pressAlertButton, renderScreen } from '@/test-support/render';

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

function setUnitSystem(unitSystem: UnitSystem) {
  // Merged rather than replaced: `preferences` now also carries the appearance choice, and dropping
  // it here would leave the theme provider without one.
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, unitSystem }, status: 'ready' }));
}

beforeEach(() => {
  setSearchParams({});
  useLibraryStore.setState({ library: aLibrary(), status: 'ready' });
  // No metric reset here: jest.setup-after-env.js does it globally, so the imperial cases below can't
  // leak into anything that runs after them.
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

/**
 * The screen half of the unit work — `domain/units.ts` and `exercise-form.ts` have their own tests, so
 * what's pinned here is the wiring those can't reach: that the editor reads the preference at all, and
 * that it hands `buildExercise` the *stored* kilograms so an untouched field survives a save.
 */
describe('weight units', () => {
  const benchPress = anExercise({
    id: 'bench-press',
    name: 'Bench Press',
    config: { sets: 5, targetRepsMin: 5, targetWeightKg: 100, restSec: 120 },
  });

  beforeEach(() => {
    setSearchParams({ id: 'bench-press' });
    useLibraryStore.setState({ library: aLibrary({ exercises: [benchPress] }) });
  });

  it('labels the weight field and fills it in the chosen unit', async () => {
    setUnitSystem('imperial');
    await renderScreen(<ExerciseEditorScreen />);

    expect(screen.getByText(/Weight \(lb\)/)).toBeTruthy();
    expect(configField(3).props.value).toBe('220.5');
  });

  // Driven in pt because an English-locale assertion can't tell `t('exerciseForm.field.weight')` apart
  // from a hardcoded "Weight" — they render identically.
  it('translates the label around the unit', async () => {
    setUnitSystem('imperial');
    await changeLanguage('pt');
    await renderScreen(<ExerciseEditorScreen />);

    expect(screen.getByText(/Peso \(lb\)/)).toBeTruthy();
  });

  it('stores a weight typed in pounds as kilograms', async () => {
    setUnitSystem('imperial');
    await renderScreen(<ExerciseEditorScreen />);

    await fireEvent.changeText(configField(3), '225');
    await fireEvent.press(screen.getByText('Save'));

    expect(persisted().exercises[0]).toMatchObject({ config: { targetWeightKg: 102.06 } });
  });

  /**
   * The bug this guards: 100 kg displays as 220.5 lb, and converting 220.5 lb back lands on 100.02.
   * Editing an unrelated field in pounds would then quietly rewrite the weight — and keep doing it,
   * a little further each time, on every subsequent save.
   */
  it('leaves an untouched weight exactly as stored when saving an unrelated change', async () => {
    setUnitSystem('imperial');
    await renderScreen(<ExerciseEditorScreen />);

    await fireEvent.changeText(configField(0), '6');
    await fireEvent.press(screen.getByText('Save'));

    expect(persisted().exercises[0]).toMatchObject({ config: { sets: 6, targetWeightKg: 100 } });
  });
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
    await pressAlertButton(alert, 'destructive');

    expect(persisted().exercises).toHaveLength(0);
  });
});

describe('the bundled drawing', () => {
  it('shows the art for a seeded exercise, described in the active language', async () => {
    // Driven in pt because an English assertion can't tell a translated string from a hardcoded one.
    await changeLanguage('pt');
    setSearchParams({ id: 'plank' });
    useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise({ id: 'plank', name: 'Prancha' })] }) });
    await renderScreen(<ExerciseEditorScreen />);

    expect(screen.getByLabelText(pt.exerciseArt.plank)).toBeTruthy();
  });

  it('shows nothing for an exercise the user wrote themselves', async () => {
    // Most of a real library is this case. An empty frame or a placeholder glyph on every one of
    // them would be worse than the silence.
    setSearchParams({ id: 'pull-ups' });
    useLibraryStore.setState({ library: aLibrary({ exercises: [anExercise()] }) });
    await renderScreen(<ExerciseEditorScreen />);

    expect(screen.queryByLabelText(en.exerciseArt.plank)).toBeNull();
    expect(screen.queryByRole('image')).toBeNull();
  });
});
