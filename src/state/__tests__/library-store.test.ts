const mockSaveLibrary = jest.fn(() => Promise.resolve());
jest.mock('@/storage/library-file', () => ({
  loadLibrary: jest.fn(),
  saveLibrary: (...args: unknown[]) => mockSaveLibrary(...(args as [])),
}));

import type { Exercise, Library } from '@/domain/types';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';

/**
 * `setTargetWeightKg` only — the runner's one-tap adopt writes through it, which makes it the one
 * library action driven by a number the user never typed. The rest of the store is exercised through
 * the screen suites.
 */
const bench: Exercise = {
  id: 'bench',
  name: 'Bench Press',
  type: 'reps',
  config: { sets: 3, targetRepsMin: 5, targetWeightKg: 60, restSec: 120 },
};
const plank: Exercise = {
  id: 'plank',
  name: 'Plank',
  type: 'timed_hold',
  config: { sets: 3, holdSecMin: 45, restSec: 60 },
};

const library: Library = { version: 1, exercises: [bench, plank], workouts: [], programs: [] };

beforeEach(() => {
  useLibraryStore.setState({ status: 'ready', library, error: null });
});

it('sets the target load and persists the library', async () => {
  await useLibraryStore.getState().setTargetWeightKg('bench', 62.5);

  const saved = useLibraryStore.getState().library!;
  expect(saved.exercises.find((exercise) => exercise.id === 'bench')).toEqual({
    ...bench,
    config: { ...bench.config, targetWeightKg: 62.5 },
  });
  expect(mockSaveLibrary).toHaveBeenCalledWith(saved);
});

it('leaves every other exercise untouched', async () => {
  await useLibraryStore.getState().setTargetWeightKg('bench', 62.5);

  expect(useLibraryStore.getState().library!.exercises.find((exercise) => exercise.id === 'plank')).toEqual(plank);
});

/**
 * The reason this action exists instead of routing through the exercise form. The logged value is
 * already kilograms; a display round trip would show 100 kg as 220.5 lb and convert that back to
 * 100.02 — silently editing a weight the user only asked to copy. Driven in imperial, which is the
 * only setting where the drift is visible.
 */
it('stores kilograms unchanged whatever the display unit is', async () => {
  usePreferencesStore.setState((state) => ({ preferences: { ...state.preferences, unitSystem: 'imperial' } }));

  await useLibraryStore.getState().setTargetWeightKg('bench', 100);

  const saved = useLibraryStore.getState().library!.exercises.find((exercise) => exercise.id === 'bench');
  expect(saved).toMatchObject({ config: { targetWeightKg: 100 } });
});

// Only RepsConfig has a target weight. The caller is a set row that can't reach the other types, but
// a no-op is the right answer rather than writing a field the schema would refuse on export.
it('does nothing for an exercise with no target weight to set', async () => {
  await useLibraryStore.getState().setTargetWeightKg('plank', 60);

  expect(mockSaveLibrary).not.toHaveBeenCalled();
  expect(useLibraryStore.getState().library!.exercises).toEqual([bench, plank]);
});

it('does nothing for an id that is not in the library', async () => {
  await useLibraryStore.getState().setTargetWeightKg('deadlift', 100);

  expect(mockSaveLibrary).not.toHaveBeenCalled();
});
