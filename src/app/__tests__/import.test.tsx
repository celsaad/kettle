import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage, t } from 'i18next';
import { AccessibilityInfo } from 'react-native';

import ImportScreen from '@/app/import';
import type { Exercise, Library } from '@/domain/types';
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

const mockSetString = jest.fn<Promise<boolean>, [string]>();
jest.mock('expo-clipboard', () => ({ setStringAsync: (...args: [string]) => mockSetString(...args) }));

const savedLibrary = saveLibrary as jest.MockedFunction<typeof saveLibrary>;

beforeEach(() => {
  mockSetString.mockResolvedValue(true);
});

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
  await fireEvent.press(screen.getByText(t('import.chooseFile')));
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
  // Deliberately *not* closed: the write is the one step with nothing to show for it, so the screen
  // stays to say what landed. Closing is now the Done button's job, covered below.
  expect(router.back).not.toHaveBeenCalled();
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
    await changeLanguage('pt');
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

/**
 * The paste box exists because an assistant's YAML is text in a chat window, and the picker can only
 * take a file. It shares every rejection with the picker by construction — both call the same
 * `review` — so what's worth pinning here is that it reaches that shared path at all, and that it
 * can't reach the merge on an empty box.
 */
describe('pasting', () => {
  async function paste(text: string) {
    await renderScreen(<ImportScreen />);
    await fireEvent.press(screen.getByText(t('import.pasteToggle')));
    await fireEvent.changeText(screen.getByPlaceholderText(t('import.pastePlaceholder')), text);
    await fireEvent.press(screen.getByText(t('import.reviewPaste')));
  }

  it('previews a pasted library and merges it on confirm', async () => {
    await paste(serializeLibraryYaml(aLibrary({ exercises: [dips] })));

    expect(screen.getByText('Pasted YAML')).toBeTruthy();
    expect(screen.getByText('dips')).toBeTruthy();
    expect(screen.getByText('new exercise')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
    // The picker was never touched, which is the point — this path has no file and no I/O at all.
    expect(mockPickFile).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByText('Merge & import'));

    expect(savedLibrary.mock.calls.at(-1)![0].exercises.map((exercise) => exercise.id)).toEqual(['pull-ups', 'dips']);
  });

  it('counts the lines it is about to merge', async () => {
    // Trailing whitespace is trimmed before counting, so the label describes the YAML rather than
    // however many blank lines a chat window happened to append.
    await paste(`${serializeLibraryYaml(aLibrary({ exercises: [dips] }))}\n\n`);

    const lines = serializeLibraryYaml(aLibrary({ exercises: [dips] }))
      .trim()
      .split('\n').length;
    expect(screen.getByText(`${lines} lines pasted`)).toBeTruthy();
  });

  it('refuses malformed YAML the same way the picker does, keeping the text to fix', async () => {
    await paste('exercises: [\n  - id: broken');

    expect(screen.getByText(/^Invalid YAML:/)).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
    // Still on the paste box with the text in it — a refusal the user can act on, not a reset.
    expect(screen.getByPlaceholderText(t('import.pastePlaceholder')).props.value).toBe('exercises: [\n  - id: broken');
  });

  it('refuses a merge-level break, which the schema alone would let through', async () => {
    await paste(
      serializeLibraryYaml(
        aLibrary({
          workouts: [aWorkout({ id: 'leg-day', name: 'Leg day', blocks: [{ kind: 'exercise', exerciseId: 'squats' }] })],
        }),
      ),
    );

    expect(screen.getByText('Workout "leg-day" references unknown exercise "squats"')).toBeTruthy();
    expect(savedLibrary).not.toHaveBeenCalled();
  });

  it('does nothing on an empty box', async () => {
    await paste('   \n  ');

    // No preview *and no error*. Unguarded, whitespace reaches js-yaml and comes back "expected a
    // document, but the input is empty" — an accusatory red line on the way to typing, before the
    // user has done anything wrong. Asserting only the absence of a preview passes either way, so
    // the rejection is what this pins.
    expect(screen.queryByText(/^Invalid YAML:/)).toBeNull();
    expect(screen.queryByText('Pasted YAML')).toBeNull();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    await paste(serializeLibraryYaml(aLibrary({ exercises: [dips] })));

    expect(screen.getByText('YAML colado')).toBeTruthy();
    expect(screen.getByText('novo exercício')).toBeTruthy();
  });
});

/**
 * The other half of the paste path. An assistant emits the YAML, the importer refuses it, and the
 * refusal already names the offending ids — so the loop closes only if that sentence can get back to
 * whoever wrote it without being retyped from a screenshot.
 */
describe('the repair loop', () => {
  /** The merge-level refusal, which is the one worth handing back: it names both ends of the break. */
  async function refusedForUnknownExercise() {
    picks(
      aLibrary({
        workouts: [aWorkout({ id: 'leg-day', name: 'Leg day', blocks: [{ kind: 'exercise', exerciseId: 'squats' }] })],
      }),
    );
    await chooseFile();
  }

  it('copies the refusal, framed as something to act on', async () => {
    await refusedForUnknownExercise();
    await fireEvent.press(screen.getByText('Copy error'));

    // The ids survive verbatim — they're what the fix is addressed to.
    expect(mockSetString).toHaveBeenCalledWith(
      'Kettle refused this exercises.yaml on import. Please fix the YAML and send back the corrected file.\n\n' +
        'Workout "leg-day" references unknown exercise "squats"',
    );
  });

  it('confirms the copy on the button and to a screen reader', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility');
    await refusedForUnknownExercise();
    await fireEvent.press(screen.getByText('Copy error'));

    // The button's own text is the confirmation, so it carries no accessibilityLabel to drift from
    // it — which is exactly why the announcement has to be explicit: focus doesn't move, so nothing
    // would be re-read.
    expect(screen.getByText('Copied')).toBeTruthy();
    expect(announce).toHaveBeenCalledWith('Error copied to the clipboard');
  });

  it('offers nothing to copy when the failure was not about the content', async () => {
    mockPickFile.mockResolvedValue({
      canceled: false,
      result: { name: 'exercises.yaml', size: 512, text: () => Promise.reject(new Error('Permission denied')) },
    });
    await chooseFile();

    // A read failure is the platform's, not the YAML's. Offering to send it to an assistant would
    // imply the assistant could do something about a denied file handle.
    expect(screen.getByText("Couldn't read that file: Permission denied")).toBeTruthy();
    expect(screen.queryByText('Copy error')).toBeNull();
  });

  it('says so when the clipboard refuses, without losing the error', async () => {
    // Reachable on web, where the clipboard is permission-gated. The refusal is still the thing worth
    // reading, so a failed copy must not overwrite it.
    mockSetString.mockRejectedValue(new Error('NotAllowedError'));
    await refusedForUnknownExercise();
    await fireEvent.press(screen.getByText('Copy error'));

    expect(screen.getByText("Couldn't reach the clipboard.")).toBeTruthy();
    expect(screen.getByText('Workout "leg-day" references unknown exercise "squats"')).toBeTruthy();
    expect(screen.queryByText('Copied')).toBeNull();
  });

  it('does not claim a copy the clipboard reported it did not make', async () => {
    // The other failure shape: `setStringAsync` resolves false rather than throwing. Claiming
    // "Copied" over that would send the user back to a chat window to paste nothing.
    mockSetString.mockResolvedValue(false);
    await refusedForUnknownExercise();
    await fireEvent.press(screen.getByText('Copy error'));

    expect(screen.getByText("Couldn't reach the clipboard.")).toBeTruthy();
    expect(screen.queryByText('Copied')).toBeNull();
  });

  it('is translated, prompt included', async () => {
    await changeLanguage('pt');
    await refusedForUnknownExercise();
    await fireEvent.press(screen.getByText('Copiar erro'));

    expect(mockSetString.mock.calls.at(-1)![0]).toBe(
      'O Kettle recusou este exercises.yaml na importação. Corrija o YAML e devolva o arquivo corrigido.\n\n' +
        'O treino "leg-day" referencia o exercício desconhecido "squats"',
    );
  });
});

/**
 * §12.5, and the reason §6 asks for updates to be surfaced clearly: an id that already exists is
 * replaced wholesale, so the preview is the only place a local tweak can be seen before it's gone.
 */
describe('the update diff', () => {
  it('names what an updated exercise is about to lose', async () => {
    const fourSets = anExercise({ config: { sets: 4, targetRepsMin: 8, restSec: 90 } } as Partial<Exercise>);
    const threeSets = anExercise({ config: { sets: 3, targetRepsMin: 8, restSec: 90 } } as Partial<Exercise>);
    useLibraryStore.setState({ library: { ...current, exercises: [threeSets] }, status: 'ready' });
    picks(aLibrary({ exercises: [fourSets] }));
    await chooseFile();

    expect(screen.getByText(/Sets: 3 → 4/)).toBeTruthy();
  });

  it('says so when an updated id is byte-identical rather than showing a blank indent', async () => {
    // `mergeById` classifies by id, not by value, so re-importing your own export lands every item
    // here. Without this line that's a wall of updates with no explanation attached.
    picks(current);
    await chooseFile();

    // One per updated row — the exercise and the workout — so every row on screen accounts for itself.
    expect(screen.getAllByText('no field changes — replaced with an identical definition')).toHaveLength(2);
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    picks(current);
    await chooseFile();

    expect(screen.getAllByText('nenhum campo alterado — substituído por uma definição idêntica')).toHaveLength(2);
  });
});

/**
 * A real hand-authored library runs to hundreds of ids, and re-importing one classifies *every* id as
 * an update — so the preview's length is set by the file, not by how much actually changed. These pin
 * the two things that keeps from costing: a wall of rows to read, and a Merge button pushed off the
 * bottom of a screen that gives no sign it's down there.
 */
describe('a preview with more changes than fit', () => {
  const many = Array.from({ length: 30 }, (_, i) => anExercise({ id: `ex-${i}`, name: `Exercise ${i}` }));

  it('shows the first few and offers the rest by count', async () => {
    picks(aLibrary({ exercises: many }));
    await chooseFile();

    expect(screen.getByText('ex-7')).toBeTruthy();
    expect(screen.queryByText('ex-8')).toBeNull();
    // The count is of the whole list, not of what's hidden — it answers "how long is this" rather
    // than making the reader add two numbers to find out.
    expect(screen.getByText('Show all 30 changes')).toBeTruthy();
  });

  it('reveals the rest on request, and can be collapsed again', async () => {
    picks(aLibrary({ exercises: many }));
    await chooseFile();
    await fireEvent.press(screen.getByText('Show all 30 changes'));

    expect(screen.getByText('ex-29')).toBeTruthy();

    await fireEvent.press(screen.getByText('Show fewer'));
    expect(screen.queryByText('ex-29')).toBeNull();
  });

  it('leaves the actions reachable without scrolling past the list', async () => {
    picks(aLibrary({ exercises: many }));
    await chooseFile();

    // The real claim is a layout one, which a test tree can't measure. What it can prove is the
    // structure the claim rests on: no ScrollView stands between Merge and the root, so no length of
    // list can push it out of view. Moving the button back inside the scroll fails this.
    const ancestors: string[] = [];
    for (let node = screen.getByText('Merge & import').parent; node; node = node.parent) {
      const { type } = node;
      const named = type as { displayName?: string; name?: string };
      ancestors.push(typeof type === 'string' ? type : (named.displayName ?? named.name ?? ''));
    }

    expect(ancestors.filter((name) => name.includes('ScrollView'))).toHaveLength(0);
  });

  it('carries the expansion into what landed, rather than re-collapsing under the reader', async () => {
    picks(aLibrary({ exercises: many }));
    await chooseFile();
    await fireEvent.press(screen.getByText('Show all 30 changes'));
    await fireEvent.press(screen.getByText('Merge & import'));

    expect(screen.getByText('ex-29')).toBeTruthy();
  });

  it('offers no toggle when the list is short enough to read', async () => {
    picks(aLibrary({ exercises: [dips] }));
    await chooseFile();

    expect(screen.queryByText(/Show all/)).toBeNull();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    picks(aLibrary({ exercises: many }));
    await chooseFile();

    expect(screen.getByText('Mostrar todas as 30 alterações')).toBeTruthy();
  });
});

/** The outbound half: what gets sent to an assistant before any YAML exists to import. */
describe('the assistant brief', () => {
  it('copies the schema and the library’s own ids', async () => {
    await renderScreen(<ImportScreen />);
    await fireEvent.press(screen.getByText('Copy the format for an assistant'));

    const copied = mockSetString.mock.calls.at(-1)![0];
    expect(copied).toContain('pull-ups (reps)');
    expect(copied).toContain('push-day — Push day');
    expect(copied).toContain('"$schema"');
    expect(screen.getByText('Copied')).toBeTruthy();
  });

  it('confirms only the button that was pressed', async () => {
    picksText('exercises: [\n  - id: broken');
    await chooseFile();
    await fireEvent.press(screen.getByText('Copy error'));

    // Both copy buttons are on screen at once here. A shared "copied" flag would light up the wrong
    // one — the brief's label must still read as un-copied.
    expect(screen.getByText('Copied')).toBeTruthy();
    expect(screen.getByText('Copy the format for an assistant')).toBeTruthy();
  });

  it('is not offered before the library has loaded', async () => {
    // Its entire value is the ids in it; offered against no library it would hand an assistant a
    // confident list of nothing to reference.
    useLibraryStore.setState({ library: null, status: 'loading' });
    await renderScreen(<ImportScreen />);

    expect(screen.queryByText('Copy the format for an assistant')).toBeNull();
  });
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

/**
 * What the screen says once the write has landed.
 *
 * The modal used to close the instant the merge succeeded, which is the one moment in the flow with
 * nothing to show for it: a few ids folded into a library of dozens is invisible by nature, so
 * "it closed" was the only evidence anything had happened — indistinguishable from a button that
 * did nothing.
 */
describe('after a successful merge', () => {
  it('reports what landed instead of vanishing', async () => {
    picks(aLibrary({ exercises: [dips], programs: [aProgram({ id: 'base-6', name: 'Base 6' })] }));
    await chooseFile();
    await fireEvent.press(screen.getByText('Merge & import'));

    expect(screen.getByText('Imported')).toBeTruthy();
    expect(screen.getByText('2 new items · 0 updated')).toBeTruthy();
    // The ids that landed are still named, so the confirmation is specific rather than a bare "OK".
    expect(screen.getByText('dips')).toBeTruthy();
    expect(screen.getByText('base-6')).toBeTruthy();
  });

  it('announces the result, which a screen reader would otherwise miss', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility');
    picks(aLibrary({ exercises: [dips] }));
    await chooseFile();
    await fireEvent.press(screen.getByText('Merge & import'));

    // Focus doesn't move and the heading swaps in below it, so nothing re-reads on its own.
    expect(announce).toHaveBeenCalledWith('Imported. 1 new, 0 updated.');
  });

  it('closes on Done, and offers nothing that implies the import could be undone', async () => {
    picks(aLibrary({ exercises: [dips] }));
    await chooseFile();
    await fireEvent.press(screen.getByText('Merge & import'));

    // Cancel would be a lie at this point — the library is already on disk.
    expect(screen.queryByText('Cancel')).toBeNull();
    expect(screen.queryByText('Merge & import')).toBeNull();

    await fireEvent.press(screen.getByText('Done'));

    expect(router.back).toHaveBeenCalled();
  });

  it('counts an update separately from a new item', async () => {
    // `pull-ups` is already in the library, so this is one of each — the case where the two plural
    // fragments have to disagree.
    picks(aLibrary({ exercises: [anExercise({ id: 'pull-ups', name: 'Pull-ups (renamed)' }), dips] }));
    await chooseFile();
    await fireEvent.press(screen.getByText('Merge & import'));

    expect(screen.getByText('1 new item · 1 updated')).toBeTruthy();
  });

  it('is translated', async () => {
    await changeLanguage('pt');
    picks(aLibrary({ exercises: [dips] }));
    await chooseFile();
    await fireEvent.press(screen.getByText('Mesclar e importar'));

    expect(screen.getByText('Importado')).toBeTruthy();
    // "0 atualizado", singular: pt's CLDR rule is `i = 0,1`, so zero takes the singular form where
    // English takes the plural. Precisely what i18next's `count` handles and a `=== 1` ternary
    // would get wrong in one of the two shipped languages.
    expect(screen.getByText('1 item novo · 0 atualizado')).toBeTruthy();
  });
});
