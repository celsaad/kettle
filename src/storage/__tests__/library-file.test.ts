import { changeLanguage } from 'i18next';

import { loadLibrary } from '@/storage/library-file';
import { seedLibraryFor } from '@/storage/seed-library';

/**
 * `loadLibrary` picks the seed in the language the UI is rendering in. Everything else about it is
 * file I/O; this covers the one decision it makes, through the storage-unsupported path so no files
 * are involved — which is also the web build's real behaviour, where the seed *is* the whole library.
 *
 * Mocked at our own boundary rather than at `expo-file-system`, per the house rule.
 */
jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: false,
  ensureStorageReady: jest.fn(),
  storagePaths: {},
}));

it('seeds in English by default', async () => {
  const result = await loadLibrary();

  expect(result.ok && result.library.exercises.find((exercise) => exercise.id === 'pushups')?.name).toBe('Push-ups');
});

it('seeds in the language the app is running in', async () => {
  // The harness resets the locale to `en` before every test, so this is the switch a Portuguese
  // device makes at startup — `@/i18n` sets `lng` from the device before anything hydrates.
  await changeLanguage('pt');
  const result = await loadLibrary();

  if (!result.ok) throw new Error(result.error);
  expect(result.library.exercises.find((exercise) => exercise.id === 'pushups')?.name).toBe('Flexões');
  expect(result.library).toEqual(seedLibraryFor('pt'));
});
