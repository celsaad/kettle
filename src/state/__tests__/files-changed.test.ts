const mockLibraryWrite = jest.fn();
const mockQuarantineWrite = jest.fn();
let mockLibraryText = '';
// The library is read through the real `loadLibrary`, down to the file, because the reseed of a
// corrupt one is part of what a re-read has to do. The other two files are mocked at their module.
jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: true,
  ensureStorageReady: jest.fn(),
  storagePaths: {
    libraryFile: {
      get exists() {
        return true;
      },
      text: () => Promise.resolve(mockLibraryText),
      create: jest.fn(),
      write: (text: string) => mockLibraryWrite(text),
    },
  },
  quarantineFile: () => ({ exists: false, create: jest.fn(), write: (text: string) => mockQuarantineWrite(text) }),
}));

const mockListSessions = jest.fn();
jest.mock('@/storage/session-files', () => ({
  listSessions: () => mockListSessions(),
  takeWriteFailure: () => null,
}));
jest.mock('@/storage/backup', () => ({ isBackupFolderSupported: false, backUpNow: jest.fn() }));

const mockLoadPreferences = jest.fn();
jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: () => mockLoadPreferences(),
  savePreferences: jest.fn(),
}));

import type { Library, Session } from '@/domain/types';
import { onFilesChanged } from '@/state/files-changed';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { seedLibraryFor } from '@/storage/seed-library';

/**
 * The shell tells RN which file it has just written, and the store that reads that file reads it
 * again (docs/native-migration-plan.md, M3). Each case starts from a store already showing what was
 * on disk before, and checks it shows what is there now.
 */
const before: Library = { version: 1, exercises: [], workouts: [], programs: [] };

const aSession = (id: string): Session => ({
  version: 1,
  id,
  workout: 'w',
  program: null,
  programWeek: null,
  programDay: null,
  startedAt: '2026-10-26T09:00:00.000Z',
  endedAt: '2026-10-26T09:30:00.000Z',
  entries: [],
});

beforeEach(() => {
  mockLibraryText = '';
  mockLibraryWrite.mockReset();
  mockQuarantineWrite.mockReset();
  mockListSessions.mockReset();
  mockLoadPreferences.mockReset();
  // In beforeEach rather than after the assertions, so a failed one can't leave a spy behind.
  jest.restoreAllMocks();
  useLibraryStore.setState({ status: 'ready', library: before, error: null });
  useSessionHistoryStore.setState({ status: 'ready', sessions: [], errors: [], activeSessionId: null });
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      restDayReminder: false,
      sessionSounds: true,
      backupFolderUri: null,
    },
  });
});

it('re-reads the library', async () => {
  mockLibraryText = `version: 1
exercises:
  - id: bench
    name: Bench
    type: reps
    config: { sets: 3, target_reps_min: 5, rest_sec: 60 }
workouts: []
programs: []
`;

  await onFilesChanged('library');

  expect(useLibraryStore.getState().library?.exercises.map((exercise) => exercise.name)).toEqual(['Bench']);
});

// The same recovery a cold start takes: the bad file is moved aside, never overwritten, and the app
// carries on with the seed rather than an error screen.
it('reseeds from a corrupt library, keeping the bad file aside', async () => {
  mockLibraryText = 'exercises: [unclosed';

  await onFilesChanged('library');

  expect(mockQuarantineWrite).toHaveBeenCalledWith('exercises: [unclosed');
  expect(useLibraryStore.getState()).toMatchObject({ status: 'ready', library: seedLibraryFor('en') });
});

it('re-reads the session log', async () => {
  mockListSessions.mockResolvedValue({ sessions: [aSession('edited')], errors: [] });

  await onFilesChanged('sessions');

  expect(useSessionHistoryStore.getState().sessions.map((session) => session.id)).toEqual(['edited']);
});

it('re-reads the preferences', async () => {
  mockLoadPreferences.mockResolvedValue({
    unitSystem: 'imperial',
    themePreference: 'dark',
    restDayReminder: true,
    sessionSounds: false,
    backupFolderUri: null,
  });

  await onFilesChanged('preferences');

  expect(usePreferencesStore.getState().preferences).toMatchObject({ unitSystem: 'imperial', themePreference: 'dark' });
});

// The root layout renders nothing while any of the three is loading, so a re-read that passed through
// it would unmount the app, a running workout included.
it.each(['library', 'sessions', 'preferences'])('keeps the app mounted while it re-reads %s', async (kind) => {
  mockLibraryText = 'version: 1\nexercises: []\nworkouts: []\nprograms: []\n';
  mockListSessions.mockResolvedValue({ sessions: [], errors: [] });
  mockLoadPreferences.mockResolvedValue(null);
  const statuses: string[] = [];
  const record = (state: { status: string }) => statuses.push(state.status);
  const unsubscribes = [
    useLibraryStore.subscribe(record),
    useSessionHistoryStore.subscribe(record),
    usePreferencesStore.subscribe(record),
  ];

  await onFilesChanged(kind);
  unsubscribes.forEach((unsubscribe) => unsubscribe());

  expect(statuses.length).toBeGreaterThan(0);
  expect(statuses).not.toContain('loading');
});

// A read that throws leaves the store as it was rather than taking the listener down with it.
it('keeps what it had when the re-read throws', async () => {
  jest.spyOn(console, 'warn').mockImplementation(() => {});
  mockListSessions.mockRejectedValue(new Error('EIO'));
  useSessionHistoryStore.setState({ sessions: [aSession('kept')] });

  await expect(onFilesChanged('sessions')).resolves.toBeUndefined();

  expect(useSessionHistoryStore.getState().sessions.map((session) => session.id)).toEqual(['kept']);
});

// A shell newer than this JS may name a file it doesn't know. The message arrives in the native
// module's listener, where a throw has nowhere useful to go.
it('ignores a kind it does not know, reading nothing', async () => {
  await expect(onFilesChanged('tips')).resolves.toBeUndefined();
  await expect(onFilesChanged(undefined)).resolves.toBeUndefined();

  expect(mockListSessions).not.toHaveBeenCalled();
  expect(mockLoadPreferences).not.toHaveBeenCalled();
  expect(mockLibraryWrite).not.toHaveBeenCalled();
  expect(useLibraryStore.getState().library).toBe(before);
});
