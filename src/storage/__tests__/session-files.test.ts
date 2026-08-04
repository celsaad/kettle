const mockWrite = jest.fn();
const mockCreate = jest.fn();

jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: true,
  ensureStorageReady: jest.fn(),
  sessionFile: () => ({ exists: true, create: mockCreate, write: mockWrite }),
  storagePaths: {},
}));

import type { SessionEntry } from '@/domain/types';
import { appendSessionEntry, finalizeSession, removeSessionEntry, takeWriteFailure } from '@/storage/session-files';

/**
 * The one guarantee this file exists for: **a session write can't abort a workout.**
 *
 * These writes are synchronous and happen inside the runner's `advance()` — an event handler or an
 * interval tick, neither of which an error boundary covers. So a throw here used to end the session
 * between two sets: the set was done, the app was gone, and only what had already been flushed
 * survived. Losing the log is bad; losing the workout in progress is worse.
 */
const session = {
  version: 1,
  id: 'session-1',
  workout: 'push',
  program: null,
  programWeek: null,
  programDay: null,
  startedAt: '2026-07-29T09:00:00.000Z',
  endedAt: null,
  entries: [] as SessionEntry[],
};

const entry: SessionEntry = { exercise: 'pullups', type: 'reps', sets: [{ reps: 6, restTakenSec: 0 }] };

beforeEach(() => {
  mockWrite.mockReset();
  mockCreate.mockReset();
  takeWriteFailure();
});

it('returns the updated session even when the disk refuses the write', () => {
  mockWrite.mockImplementation(() => {
    throw new Error('no space left on device');
  });

  const updated = appendSessionEntry(session, entry);

  // The set is still logged in the returned session, which is what the runner keeps going from.
  expect(updated.entries).toEqual([entry]);
});

it('reports the failure once, then forgets it', () => {
  mockWrite.mockImplementation(() => {
    throw new Error('no space left on device');
  });
  appendSessionEntry(session, entry);

  expect(takeWriteFailure()).toBe('session-1: no space left on device');
  // Cleared on read, so the next successful write doesn't re-report a failure that already surfaced.
  expect(takeWriteFailure()).toBeNull();
});

it('reports nothing when the write succeeds', () => {
  finalizeSession(session, '2026-07-29T10:00:00.000Z');

  expect(mockWrite).toHaveBeenCalled();
  expect(takeWriteFailure()).toBeNull();
});

/**
 * Removing an entry at an arbitrary index — the session editor's path (#56), as opposed to
 * `removeLastSessionEntry`'s undo of the write that just happened.
 */
describe('removeSessionEntry', () => {
  const dips: SessionEntry = { exercise: 'dips', type: 'reps', sets: [{ reps: 10, restTakenSec: 0 }] };
  const rows: SessionEntry = { exercise: 'rows', type: 'reps', sets: [{ reps: 12, restTakenSec: 0 }] };
  const three = { ...session, entries: [entry, dips, rows] };

  it('drops the one at the index and keeps the order of the rest', () => {
    expect(removeSessionEntry(three, 1).entries).toEqual([entry, rows]);
    expect(mockWrite).toHaveBeenCalled();
  });

  /**
   * Matches `replaceSessionEntry`: the caller works from a session it read, so an index that doesn't
   * exist means its bookkeeping is wrong. Deleting nothing is easier to notice than deleting the
   * wrong entry, and far easier than deleting one silently.
   */
  it('leaves an out-of-range index alone rather than guessing', () => {
    expect(removeSessionEntry(three, 7).entries).toEqual([entry, dips, rows]);
    expect(removeSessionEntry(three, -1).entries).toEqual([entry, dips, rows]);
  });

  it('returns the updated session even when the disk refuses the write', () => {
    mockWrite.mockImplementation(() => {
      throw new Error('no space left on device');
    });

    expect(removeSessionEntry(three, 0).entries).toEqual([dips, rows]);
    expect(takeWriteFailure()).toBe('session-1: no space left on device');
  });
});
