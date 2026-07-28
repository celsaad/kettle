import { fireEvent, screen } from '@testing-library/react-native';
import i18next from 'i18next';

import ImportScreen from '@/app/import';
import type { Library } from '@/domain/types';
import { serializeLibraryYaml } from '@/domain/yaml-mapping';
import { useLibraryStore } from '@/state/library-store';
import { saveLibrary } from '@/storage/library-file';
import { router } from '@/test-support/expo-router';
import { aLibrary, anExercise, aProgram, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * Import is the one flow a browser check can't drive at all — it opens the OS file picker — and it's
 * the only path by which arbitrary outside data reaches the library. Every rejection here exists to
 * stop a bad file from landing, so what's tested is that each one surfaces its reason and leaves the
 * library alone.
 */
const mockPickFile = jest.fn();
jest.mock('expo-file-system', () => ({
  File: { pickFileAsync: (...args: unknown[]) => mockPickFile(...args) },
}));

jest.mock('@/storage/library-file', () => ({
  loadLibrary: jest.fn(),
  saveLibrary: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-router', () => require('@/test-support/expo-router'));

const savedLibrary = saveLibrary as jest.MockedFunction<typeof saveLibrary>;

/** Stands in for the picked file. Serialising a real Library keeps the fixture and the schema in step. */
function picks(library: Library, name = 'exercises.yaml') {
  mockPickFile.mockResolvedValue({
    canceled: false,
    result: { name, size: 512, text: () => Promise.resolve(serializeLibraryYaml(library)) },
  });
}

function picksText(text: string) {
  mockPickFile.mockResolvedValue({
    canceled: false,
    result: { name: 'exercises.yaml', size: 512, text: () => Promise.resolve(text) },
  });
}

const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups' });
const dips = anExercise({ id: 'dips', name: 'Dips' });
const pushDay = aWorkout({ id: 'push-day', name: 'Push day', blocks: [{ kind: 'exercise', exerciseId: 'pull-ups' }] });

const current = aLibrary({ exercises: [pullUps], workouts: [pushDay] });

beforeEach(() => {
  useLibraryStore.setState({ library: current, status: 'ready' });
});

// Driving the picker is navigation, not an assertion, so it goes through `t` — that's what lets the
// pt cases below reuse it rather than hardcoding the Portuguese label of a button they don't test.
async function chooseFile() {
  await renderScreen(<ImportScreen />);
  await fireEvent.press(screen.getByText(i18next.t('import.chooseFile')));
}

it('does nothing when the picker is dismissed', async () => {
  mockPickFile.mockResolvedValue({ canceled: true });
  await chooseFile();

  // Specifically no error: a cancel is a normal outcome, and reporting it as a failure would train
  // the user to ignore the error line that does matter.
  expect(screen.queryByText('No changes — the imported file matches your current library.')).toBeNull();
  expect(screen.getByText('Choose exercises.yaml')).toBeTruthy();
});

it('surfaces a YAML syntax error', async () => {
  picksText('exercises: [\n  - id: broken');
  await chooseFile();

  expect(screen.getByText(/^Invalid YAML:/)).toBeTruthy();
  expect(savedLibrary).not.toHaveBeenCalled();
});

it('surfaces a schema error rather than importing a partial library', async () => {
  picksText('version: 1\nexercises: []\nworkouts: []\n');
  await chooseFile();

  // `programs` is missing. The file parses as YAML perfectly well, so the only thing standing
  // between it and the store is the schema.
  expect(screen.getByText(/programs/)).toBeTruthy();
  expect(savedLibrary).not.toHaveBeenCalled();
});

it('refuses a file whose workout points at an exercise nobody has', async () => {
  // The merge-level check, which the schema can't make: each file is internally valid, and only the
  // *combination* is broken. Importing it would leave "Push day" resolving a block to nothing, which
  // surfaces much later as a workout that silently runs one exercise short.
  picks(
    aLibrary({
      workouts: [aWorkout({ id: 'leg-day', name: 'Leg day', blocks: [{ kind: 'exercise', exerciseId: 'squats' }] })],
    }),
  );
  await chooseFile();

  expect(screen.getByText('Workout "leg-day" references unknown exercise "squats"')).toBeTruthy();
  expect(savedLibrary).not.toHaveBeenCalled();
});

it('previews what would change, and only writes once confirmed', async () => {
  picks(aLibrary({ exercises: [dips] }), 'more.yaml');
  await chooseFile();

  expect(screen.getByText('more.yaml')).toBeTruthy();
  expect(screen.getByText('dips')).toBeTruthy();
  expect(screen.getByText('new exercise')).toBeTruthy();
  // The preview alone must not touch the library — that's the entire point of having one.
  expect(savedLibrary).not.toHaveBeenCalled();

  await fireEvent.press(screen.getByText('Merge & import'));

  expect(savedLibrary.mock.calls.at(-1)![0].exercises.map((exercise) => exercise.id)).toEqual(['pull-ups', 'dips']);
  expect(router.back).toHaveBeenCalled();
});

/**
 * A program-only file used to preview as "0 new, 0 updated" and then merge its programs regardless:
 * the summary counted exercises and workouts and simply dropped the third collection. Silent for the
 * exact class of file a program author would be importing, and the one thing the preview exists to
 * prevent (§6 — updates have to be visible before they overwrite local tweaks).
 */
it('counts and lists programs, not just exercises and workouts', async () => {
  picks(aLibrary({ programs: [aProgram({ id: 'base-6', name: 'Base 6' })] }));
  await chooseFile();

  expect(screen.getByText('base-6')).toBeTruthy();
  expect(screen.getByText('new program')).toBeTruthy();
  // The only non-zero tile, so `getByText` doubles as an assertion that nothing else claims a count.
  expect(screen.getByText('1')).toBeTruthy();
  expect(screen.queryByText('No changes — the imported file matches your current library.')).toBeNull();

  await fireEvent.press(screen.getByText('Merge & import'));

  expect(savedLibrary.mock.calls.at(-1)![0].programs.map((program) => program.id)).toEqual(['base-6']);
});

it('counts an existing program as an update', async () => {
  const current6 = aProgram({ id: 'base-6', name: 'Base 6' });
  useLibraryStore.setState({ library: { ...current, programs: [current6] }, status: 'ready' });
  picks(aLibrary({ programs: [aProgram({ id: 'base-6', name: 'Base 6 v2' })] }));
  await chooseFile();

  expect(screen.getByText('updated program')).toBeTruthy();
});

// An English-locale assertion can't tell `t('import.newProgram')` apart from the literal it returns,
// so the summary and the refusal reasons are only genuinely proven translated from here.
describe('in Portuguese', () => {
  beforeEach(async () => {
    await i18next.changeLanguage('pt');
  });

  it('translates the change summary', async () => {
    picks(aLibrary({ programs: [aProgram({ id: 'base-6', name: 'Base 6' })] }));
    await chooseFile();

    expect(screen.getByText('novo programa')).toBeTruthy();
  });

  it('translates a refusal, leaving the ids in it alone', async () => {
    picks(
      aLibrary({
        workouts: [aWorkout({ id: 'leg-day', name: 'Leg day', blocks: [{ kind: 'exercise', exerciseId: 'squats' }] })],
      }),
    );
    await chooseFile();

    expect(screen.getByText('O treino "leg-day" referencia o exercício desconhecido "squats"')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
  });
});

it('says so when the file adds and changes nothing', async () => {
  picks(aLibrary());
  await chooseFile();

  expect(screen.getByText('No changes — the imported file matches your current library.')).toBeTruthy();
});

it('counts a re-imported identical file as updates, not as no changes', async () => {
  picks(current);
  await chooseFile();

  // Documenting real behaviour rather than asserting a wish: `mergeById` classifies by id, not by
  // value, so every same-id item counts as "updated" even when byte-identical. Harmless — the merge
  // is a whole-object replace, so re-importing your own export is a no-op in effect — but it means
  // the "no changes" line is reachable only by an empty file, which is worth knowing before someone
  // "fixes" this test by loosening it.
  expect(screen.queryByText('No changes — the imported file matches your current library.')).toBeNull();
  expect(screen.getByText('updated exercise')).toBeTruthy();
});

it('surfaces a read failure instead of failing silently', async () => {
  mockPickFile.mockResolvedValue({
    canceled: false,
    result: { name: 'exercises.yaml', size: 512, text: () => Promise.reject(new Error('Permission denied')) },
  });
  await chooseFile();

  // Framed by a sentence of ours: the platform's own message says what went wrong but not which step
  // it went wrong in, and it's the only part of the line that can't be translated.
  expect(screen.getByText("Couldn't read that file: Permission denied")).toBeTruthy();
  // And the picker is usable again — the `finally` that clears `busy` is what makes a failed import
  // retryable rather than a dead screen.
  expect(screen.getByText('Choose exercises.yaml')).toBeTruthy();
});
