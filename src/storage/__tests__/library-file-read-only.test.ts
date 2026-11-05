const mockLibraryWrite = jest.fn();
const mockLibraryCreate = jest.fn();
// `mock`-prefixed because `jest.mock`'s factory is hoisted above every declaration in the file and
// may only close over names matching that prefix — see the house rule in AGENTS.md.
let mockLibraryText: string | null = null;

jest.mock('@/storage/paths', () => ({
  storagePaths: {
    libraryFile: {
      get exists() {
        return mockLibraryText !== null;
      },
      text: () => Promise.resolve(mockLibraryText),
      create: (...args: unknown[]) => mockLibraryCreate(...args),
      write: (text: string) => mockLibraryWrite(text),
    },
  },
}));

import { useLibraryStore } from '@/state/library-store';
import { loadLibrary } from '@/storage/library-file';

/**
 * **RN never writes the library** from the library flip (#220, #245). Native seeds a first launch's
 * file and repairs or quarantines and reseeds a corrupt one before RN starts, so what RN finds it only
 * reads, and anything it can't read is an error rather than something to heal. Healing it here would
 * make RN a second writer, racing native's own writes.
 */
beforeEach(() => {
  mockLibraryText = null;
  mockLibraryWrite.mockReset();
  mockLibraryCreate.mockReset();
  useLibraryStore.setState({ status: 'idle', library: null, error: null });
});

/** Every case ends here: whatever RN found, it touched nothing. */
function expectNothingWritten() {
  expect(mockLibraryWrite).not.toHaveBeenCalled();
  expect(mockLibraryCreate).not.toHaveBeenCalled();
}

it('reads a library that parses', async () => {
  mockLibraryText = 'version: 1\nexercises: []\nworkouts: [{ id: w, name: Push day, blocks: [] }]\nprograms: []\n';

  const result = await loadLibrary();

  expect(result.ok && result.library.workouts.map((workout) => workout.name)).toEqual(['Push day']);
  expectNothingWritten();
});

it('reports a missing file rather than seeding it', async () => {
  const result = await loadLibrary();

  expect(result).toMatchObject({ ok: false, error: expect.stringContaining('does not exist') });
  expectNothingWritten();
});

// Out of range, which RN used to clamp and save: native's repair now, before RN starts.
it('reports a library it would once have repaired, or reseeded, and leaves the file alone', async () => {
  for (const text of ['exercises: [unclosed', 'version: 1\nexercises:\n  - id: broken\nworkouts: []\nprograms: []\n']) {
    mockLibraryText = text;

    expect(await loadLibrary()).toMatchObject({ ok: false });
  }
  expectNothingWritten();
});

it('sends a first hydrate that finds no readable library to the error state', async () => {
  mockLibraryText = 'exercises: [unclosed';

  await useLibraryStore.getState().hydrate();

  expect(useLibraryStore.getState()).toMatchObject({
    status: 'error',
    library: null,
    error: expect.stringContaining('invalidYaml'),
  });
  expectNothingWritten();
});
