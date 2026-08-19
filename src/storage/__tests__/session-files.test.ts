const mockWrite = jest.fn();
const mockCreate = jest.fn();
const mockList = jest.fn<unknown[], []>(() => []);

jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: true,
  ensureStorageReady: jest.fn(),
  sessionFile: () => ({ exists: true, create: mockCreate, write: mockWrite }),
  storagePaths: { sessionsDir: { list: () => mockList() } },
}));

// The stand-in lives in its own module because `jest.mock`'s factory is hoisted above every
// declaration here — a `class` beside it would still be in its temporal dead zone when the factory
// runs. Same reason, and same fix, as the shared `expo-router` stand-in.
jest.mock('expo-file-system', () => require('@/test-support/expo-file-system'));

import type { SessionEntry } from '@/domain/types';
import { File as MockFile } from '@/test-support/expo-file-system';
import {
  appendSessionEntry,
  finalizeSession,
  listSessions,
  removeSessionEntry,
  takeWriteFailure,
} from '@/storage/session-files';

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
  mockList.mockReset().mockReturnValue([]);
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

/**
 * The docstring on `listSessions` has always promised that "one malformed file produces an error
 * entry, not a crash" — and that held for a file that wouldn't *parse* but never for one that wouldn't
 * *read*. A bare `await file.text()` threw straight out of `hydrate`, which has no catch, leaving the
 * store on `loading` forever. That was survivable while history gated first paint, because the app
 * simply never appeared; now that it doesn't, it would be a History tab that stayed empty in silence.
 */
describe('listSessions', () => {
  const yamlFor = (id: string, startedAt: string) =>
    `version: 1
id: ${id}
workout: push
started_at: '${startedAt}'
ended_at: null
entries: []
`;

  it('reads every session file and returns them newest first', async () => {
    mockList.mockReturnValue([
      new MockFile('a.yaml', yamlFor('a', '2026-07-01T09:00:00.000Z')),
      new MockFile('b.yaml', yamlFor('b', '2026-07-29T09:00:00.000Z')),
    ]);

    const { sessions, errors } = await listSessions();

    expect(sessions.map((listed) => listed.id)).toEqual(['b', 'a']);
    expect(errors).toEqual([]);
  });

  it('keeps the readable sessions when one file will not read at all', async () => {
    mockList.mockReturnValue([
      new MockFile('good.yaml', yamlFor('good', '2026-07-01T09:00:00.000Z')),
      new MockFile('gone.yaml', new Error('ENOENT')),
      new MockFile('later.yaml', yamlFor('later', '2026-07-29T09:00:00.000Z')),
    ]);

    const { sessions, errors } = await listSessions();

    // Every other file survives: one unreadable session must not cost the whole history.
    expect(sessions.map((listed) => listed.id)).toEqual(['later', 'good']);
    expect(errors).toEqual(['gone.yaml (unreadable): ENOENT']);
  });

  it('still reports a file that reads but will not parse', async () => {
    mockList.mockReturnValue([
      new MockFile('good.yaml', yamlFor('good', '2026-07-01T09:00:00.000Z')),
      new MockFile('bad.yaml', 'entries: [unclosed'),
    ]);

    const { sessions, errors } = await listSessions();

    expect(sessions.map((listed) => listed.id)).toEqual(['good']);
    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain('bad.yaml');
  });

  it('ignores directory entries that are not files', async () => {
    mockList.mockReturnValue([{ name: 'nested.yaml' }, new MockFile('a.yaml', yamlFor('a', '2026-07-01T09:00:00.000Z'))]);

    const { sessions } = await listSessions();

    expect(sessions.map((listed) => listed.id)).toEqual(['a']);
  });
});
