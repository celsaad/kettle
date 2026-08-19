/**
 * The reseed that recovers from an unparseable `exercises.yaml`, and the copy it now leaves beside it.
 *
 * `library-file.test.ts` covers the same module through the storage-*unsupported* path, which never
 * reaches any of this. Mocked at our own boundary (`@/storage/paths`) rather than at
 * `expo-file-system`, per the house rule, so the assertions read as what ended up on disk.
 */
const mockLibraryFile = {
  exists: true,
  text: jest.fn<Promise<string>, []>(),
  write: jest.fn(),
  create: jest.fn(),
};
const mockQuarantinedFile = {
  exists: false,
  write: jest.fn(),
  create: jest.fn(),
};
jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: true,
  ensureStorageReady: jest.fn(),
  storagePaths: {
    get libraryFile() {
      return mockLibraryFile;
    },
    get quarantinedLibraryFile() {
      return mockQuarantinedFile;
    },
  },
}));

import { loadLibrary } from '@/storage/library-file';
import { seedLibraryFor } from '@/storage/seed-library';

/**
 * A library that parsed fine before the repeat counts gained an upper bound, and does not now.
 *
 * This is the case that matters, and it is not hypothetical corruption: **the app tightening its own
 * schema is what turns a loadable file into an unparseable one**, so the release that adds a cap is
 * the release that runs this path — on exactly the users whose numbers the cap was added for.
 */
const pastTheNewCeiling = `version: 1
exercises:
  - id: pushups
    name: Push-ups
    type: reps
    config:
      sets: 20000
      target_reps_min: 10
      rest_sec: 60
workouts: []
programs: []
`;

beforeEach(() => {
  jest.spyOn(console, 'warn').mockImplementation(() => {});
  mockLibraryFile.text.mockReset().mockResolvedValue(pastTheNewCeiling);
  mockLibraryFile.write.mockReset();
  mockLibraryFile.create.mockReset();
  mockQuarantinedFile.write.mockReset();
  mockQuarantinedFile.create.mockReset();
  mockQuarantinedFile.exists = false;
});

afterEach(() => {
  jest.restoreAllMocks();
});

it('reseeds when the persisted library will not parse', async () => {
  const result = await loadLibrary();

  expect(result.ok && result.library).toEqual(seedLibraryFor('en'));
  expect(mockLibraryFile.write).toHaveBeenCalled();
});

/**
 * The half the reseed was missing. Overwriting is a recovery for the app and a total loss for the
 * user: that file is everything they ever authored, and the only notice was a `console.warn` that
 * nobody on a device can read. A copy beside it costs one write and makes the loss recoverable — fix
 * the line the warning named, import it back. Dropping the `quarantineLibrary` call fails this.
 */
it('sets the unparseable file aside before overwriting it', async () => {
  await loadLibrary();

  expect(mockQuarantinedFile.write).toHaveBeenCalledWith(pastTheNewCeiling);
});

/**
 * The quarantine must never become the thing that keeps the app on a blank screen: it is a
 * best-effort courtesy, and the reseed behind it is what actually unblocks the user.
 */
it('still reseeds when the file it cannot parse also cannot be set aside', async () => {
  mockQuarantinedFile.write.mockImplementation(() => {
    throw new Error('no space left on device');
  });

  const result = await loadLibrary();

  expect(result.ok && result.library).toEqual(seedLibraryFor('en'));
  expect(mockLibraryFile.write).toHaveBeenCalled();
});

it('leaves a library that parses completely alone', async () => {
  mockLibraryFile.text.mockResolvedValue(pastTheNewCeiling.replace('sets: 20000', 'sets: 3'));

  const result = await loadLibrary();

  expect(result.ok && result.library.exercises[0].id).toBe('pushups');
  expect(mockQuarantinedFile.write).not.toHaveBeenCalled();
  expect(mockLibraryFile.write).not.toHaveBeenCalled();
});
