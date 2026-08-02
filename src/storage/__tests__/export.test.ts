const mockWrite = jest.fn();
const mockCreate = jest.fn();
const mockShareAsync = jest.fn();

jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: true,
  cacheFile: () => ({ uri: 'file:///cache/kettle-history.yaml', create: mockCreate, write: mockWrite }),
  sessionFile: (id: string) => ({ uri: `file:///sessions/${id}.yaml` }),
  storagePaths: { libraryFile: { uri: 'file:///exercises.yaml' } },
}));

jest.mock('expo-sharing', () => ({
  isAvailableAsync: () => Promise.resolve(true),
  shareAsync: (...args: unknown[]) => mockShareAsync(...args),
}));

import { load } from 'js-yaml';

import type { Session } from '@/domain/types';
import { exportSessions } from '@/storage/export';

/**
 * `expo-sharing` hands over one URI, so exporting a whole log means assembling a file that doesn't
 * otherwise exist. What's worth pinning is that the assembly reads from what it was given and lands
 * somewhere disposable — not the round-trip, which `yaml-mapping.test.ts` owns.
 */
function sessionAt(id: string, startedAt: string): Session {
  return {
    version: 1,
    id,
    workout: 'push',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt,
    endedAt: null,
    entries: [],
  };
}

const june = sessionAt('june', '2026-06-01T09:00:00.000Z');
const july = sessionAt('july', '2026-07-01T09:00:00.000Z');
const august = sessionAt('august', '2026-08-01T09:00:00.000Z');

function writtenArchive(): { sessions: { id: string }[] } {
  return load(mockWrite.mock.calls[0][0] as string) as { sessions: { id: string }[] };
}

it('writes every session it was handed into one shared file', async () => {
  await exportSessions([july, june]);

  expect(writtenArchive().sessions.map((session) => session.id)).toEqual(['june', 'july']);
  expect(mockShareAsync).toHaveBeenCalledWith('file:///cache/kettle-history.yaml', expect.anything());
});

// The store hands these over newest-first, which is right for History and wrong for a log someone
// reads top to bottom somewhere else.
it('orders the archive oldest-first regardless of the order it was given', async () => {
  await exportSessions([august, june, july]);

  expect(writtenArchive().sessions.map((session) => session.id)).toEqual(['june', 'july', 'august']);
});

// The caller's array is the store's own `sessions`, straight out of zustand — sorting it in place
// would reorder History underneath the user as a side effect of sharing.
it('leaves the caller-s array untouched', async () => {
  const sessions = [august, june, july];
  await exportSessions(sessions);

  expect(sessions.map((session) => session.id)).toEqual(['august', 'june', 'july']);
});

it('overwrites the previous export rather than failing on it', async () => {
  await exportSessions([june]);

  expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({ overwrite: true }));
});
