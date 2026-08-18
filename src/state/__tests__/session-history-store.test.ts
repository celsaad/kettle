const mockCreateSession = jest.fn();
const mockFinalizeSession = jest.fn();
const mockTakeWriteFailure = jest.fn<string | null, []>(() => null);
const mockDeleteSessionFile = jest.fn();
const mockBackUpNow = jest.fn();
let mockBackupSupported = true;
jest.mock('@/storage/backup', () => ({
  get isBackupFolderSupported() {
    return mockBackupSupported;
  },
  backUpNow: (...args: unknown[]) => mockBackUpNow(...args),
}));
jest.mock('@/storage/session-files', () => ({
  appendSessionEntry: (session: unknown) => session,
  createSession: (...args: unknown[]) => mockCreateSession(...args),
  deleteSession: (...args: unknown[]) => mockDeleteSessionFile(...args),
  takeWriteFailure: () => mockTakeWriteFailure(),
  finalizeSession: (...args: unknown[]) => mockFinalizeSession(...args),
  listSessions: jest.fn(),
  removeLastSessionEntry: (session: unknown) => session,
  replaceSessionEntry: (session: Session, index: number, entry: SessionEntry) => ({
    ...session,
    entries: session.entries.map((existing, position) => (position === index ? entry : existing)),
  }),
  removeSessionEntry: (session: Session, index: number) => ({
    ...session,
    entries: session.entries.filter((_, position) => position !== index),
  }),
}));

import type { Session, SessionEntry } from '@/domain/types';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * `activeSessionId`/`abandonActiveSession` (which exist for one caller: `session.tsx`'s error
 * boundary, running after React has thrown away the runner and the `Session` ref it held) and
 * `replaceEntry`'s effect on history state, which the runner's suite can't see because it mocks this
 * store out. The rest is exercised through that suite.
 */
function aSession(overrides: Partial<Session> = {}): Session {
  return {
    version: 1,
    id: 'session-1',
    workout: 'w',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt: '2026-07-29T09:00:00.000Z',
    endedAt: null,
    entries: [],
    ...overrides,
  };
}

function setBackupFolder(backupFolderUri: string | null) {
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      restDayReminder: false,
      backupFolderUri,
    },
  });
}

beforeEach(() => {
  useSessionHistoryStore.setState({
    status: 'ready',
    sessions: [],
    errors: [],
    activeSessionId: null,
    backupFailure: null,
  });
  mockCreateSession.mockReset().mockImplementation((id: string) => aSession({ id }));
  mockFinalizeSession.mockReset().mockImplementation((session: Session, endedAt: string) => ({ ...session, endedAt }));
  mockTakeWriteFailure.mockReset().mockReturnValue(null);
  mockDeleteSessionFile.mockReset();
  mockBackUpNow.mockReset().mockReturnValue(null);
  mockBackupSupported = true;
  setBackupFolder(null);
});

/**
 * A session write can't throw — `writeSession` catches, so that a full disk mid-workout can't take the
 * runner down between two sets. This is the half that stops it being silent: the failure has to reach
 * the store's `errors`, which History renders.
 */
describe('a flush that failed', () => {
  it('is recorded without disturbing the logged entry', () => {
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);
    mockTakeWriteFailure.mockReturnValue('session-1: no space left on device');

    useSessionHistoryStore
      .getState()
      .logEntry(session, { exercise: 'pullups', type: 'reps', sets: [{ reps: 6, restTakenSec: 0 }] });

    // The session stays in history and the call returns normally — the workout keeps running, which is
    // the whole point. (That the *entry* survives the failed write is pinned in the session-files
    // suite; `appendSessionEntry` is mocked to identity here.)
    expect(useSessionHistoryStore.getState().sessions).toHaveLength(1);
    expect(useSessionHistoryStore.getState().errors).toEqual(['session-1: no space left on device']);
  });

  it('is recorded once, not once per set', () => {
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);
    mockTakeWriteFailure.mockReturnValue('session-1: no space left on device');
    const entry: SessionEntry = { exercise: 'pullups', type: 'reps', sets: [{ reps: 6, restTakenSec: 0 }] };

    useSessionHistoryStore.getState().logEntry(session, entry);
    useSessionHistoryStore.getState().logEntry(session, entry);
    useSessionHistoryStore.getState().logEntry(session, entry);

    // A disk that's full stays full. One line per set would bury every other error under the same one.
    expect(useSessionHistoryStore.getState().errors).toHaveLength(1);
  });
});

describe('deleteSession', () => {
  it('keeps the session listed when its file would not delete', () => {
    useSessionHistoryStore.setState({ sessions: [aSession()], errors: [] });
    mockDeleteSessionFile.mockImplementation(() => {
      throw new Error('permission denied');
    });

    useSessionHistoryStore.getState().deleteSession('session-1');

    // Dropping it from state anyway would show it gone until the next launch brought it back — and the
    // throw itself, from an Alert button, is caught by no error boundary at all.
    expect(useSessionHistoryStore.getState().sessions).toHaveLength(1);
    expect(useSessionHistoryStore.getState().errors).toEqual(['session-1: permission denied']);
  });
});

describe('activeSessionId', () => {
  it('names the session the runner is writing to', () => {
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);

    expect(useSessionHistoryStore.getState().activeSessionId).toBe(session.id);
  });

  it('is cleared once the session finishes normally', () => {
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);

    useSessionHistoryStore.getState().completeSession(session);

    expect(useSessionHistoryStore.getState().activeSessionId).toBeNull();
  });
});

/**
 * The backup that rides along with a finished session.
 *
 * This is the path the brief's "a failed backup must never interrupt a workout" is about:
 * `completeSession` is reached from the runner's `finishSession`, which is an event handler, so a
 * throw here would take the session down on the screen of someone who has just finished training.
 * `backUpNow` is non-throwing by construction (its own suite pins that); what these pin is that the
 * store calls it with the right log, and steps over the result rather than acting on it.
 */
describe('backing up when a session finishes', () => {
  const FOLDER = 'content://tree/primary%3ADocuments%2FKettle';

  // The backup is deliberately deferred off the tick that finishes the session — see
  // `backUpAfterSession` — so these have to drive the clock to see it happen at all.
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  /**
   * The deferral itself, and the reason for it: `completeSession` is called straight from the
   * runner's `finishSession`, and `backUpNow` is synchronous SAF IO against a folder that may be
   * backed by a cloud provider. Run inline it sits between the Finish tap and the completion screen.
   */
  it('does not run the backup on the tick that finishes the session', () => {
    setBackupFolder(FOLDER);
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);

    useSessionHistoryStore.getState().completeSession(session);

    expect(mockBackUpNow).not.toHaveBeenCalled();

    jest.runAllTimers();
    expect(mockBackUpNow).toHaveBeenCalled();
  });

  it('archives the session that just ended, not the log as it was one set ago', () => {
    setBackupFolder(FOLDER);
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);

    useSessionHistoryStore.getState().completeSession(session);
    jest.runAllTimers();

    const [folderUri, sessions] = mockBackUpNow.mock.calls[0] as [string, Session[]];
    expect(folderUri).toBe(FOLDER);
    // The finished session, with its `ended_at` — reading `get().sessions` from inside the `set` gave
    // the un-finalized copy, which is the bug this pins.
    expect(sessions).toHaveLength(1);
    expect(sessions[0].endedAt).not.toBeNull();
  });

  // Someone who never opted in has nothing to be told, and running it anyway would answer `noFolder`
  // and light a warning on every completion screen in the app.
  it('does not back up at all when no folder has been chosen', () => {
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);

    useSessionHistoryStore.getState().completeSession(session);

    expect(mockBackUpNow).not.toHaveBeenCalled();
    expect(useSessionHistoryStore.getState().backupFailure).toBeNull();
  });

  it('records a failure for the completion screen instead of raising it', () => {
    setBackupFolder(FOLDER);
    mockBackUpNow.mockReturnValue({ kind: 'unreachable' });
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);

    expect(() => useSessionHistoryStore.getState().completeSession(session)).not.toThrow();
    jest.runAllTimers();

    expect(useSessionHistoryStore.getState().backupFailure).toEqual({ kind: 'unreachable' });
    // The session itself is untouched by a backup that didn't land — that's the whole point.
    expect(useSessionHistoryStore.getState().sessions[0].endedAt).not.toBeNull();
  });

  // Cleared as the session finishes rather than when the backup answers, so the completion screen
  // never shows the *previous* session's warning while this one's backup is still in flight.
  it('clears a previous failure immediately, not only once the backup lands', () => {
    setBackupFolder(FOLDER);
    useSessionHistoryStore.setState({ backupFailure: { kind: 'unreachable' } });
    const session = useSessionHistoryStore.getState().startSession('w', null, null, null);

    useSessionHistoryStore.getState().completeSession(session);

    expect(useSessionHistoryStore.getState().backupFailure).toBeNull();

    jest.runAllTimers();
    expect(useSessionHistoryStore.getState().backupFailure).toBeNull();
  });
});

describe('replaceEntry', () => {
  // The runner rewrites an exercise's entry on every set, so history has to show the grown version
  // rather than the one-set entry it first appended — a live History tab is reading this array.
  it('swaps the entry in history state, leaving its neighbours alone', () => {
    const first: SessionEntry = { exercise: 'pullups', type: 'reps', sets: [{ reps: 6, restTakenSec: 0 }] };
    const other: SessionEntry = { exercise: 'lsit', type: 'timed_hold', sets: [{ holdSec: 20, restTakenSec: 0 }] };
    const session = aSession({ entries: [first, other] });
    useSessionHistoryStore.setState({ sessions: [session] });

    const grown: SessionEntry = {
      exercise: 'pullups',
      type: 'reps',
      sets: [
        { reps: 6, restTakenSec: 45 },
        { reps: 5, restTakenSec: 0 },
      ],
    };
    const updated = useSessionHistoryStore.getState().replaceEntry(session, 0, grown);

    expect(updated.entries).toEqual([grown, other]);
    expect(useSessionHistoryStore.getState().sessions[0].entries).toEqual([grown, other]);
  });
});

describe('abandonActiveSession', () => {
  /**
   * The point of the whole mechanism: the sets are already flushed to the file, but a session with no
   * `ended_at` counts as zero minutes in every stat tile and is skipped outright by `exerciseHistory`
   * — logged work that exists on disk and appears nowhere in the app.
   */
  it('writes ended_at for the session that was in flight', () => {
    useSessionHistoryStore.getState().startSession('w', null, null, null);

    useSessionHistoryStore.getState().abandonActiveSession();

    expect(mockFinalizeSession).toHaveBeenCalledTimes(1);
    expect(useSessionHistoryStore.getState().sessions[0].endedAt).not.toBeNull();
    expect(useSessionHistoryStore.getState().activeSessionId).toBeNull();
  });

  // The boundary can mount more than once for one crash, and finalizing twice would move ended_at.
  it('does nothing on a second call', () => {
    useSessionHistoryStore.getState().startSession('w', null, null, null);

    useSessionHistoryStore.getState().abandonActiveSession();
    useSessionHistoryStore.getState().abandonActiveSession();

    expect(mockFinalizeSession).toHaveBeenCalledTimes(1);
  });

  /**
   * Why the id is tracked at all, rather than the boundary finalizing the newest session without an
   * `ended_at`: a session killed by an app kill (rather than a render throw) leaves exactly that
   * shape behind forever, and stamping it now would invent an end time days after the fact.
   */
  it('leaves an older unfinished session alone when nothing is in flight', () => {
    useSessionHistoryStore.setState({ sessions: [aSession({ id: 'crashed-last-week' })], activeSessionId: null });

    useSessionHistoryStore.getState().abandonActiveSession();

    expect(mockFinalizeSession).not.toHaveBeenCalled();
    expect(useSessionHistoryStore.getState().sessions[0].endedAt).toBeNull();
  });

  // A stale id (its session gone from history) must still clear, or the next crash finalizes nothing
  // while reporting that it did.
  it('clears the id when the session it names is gone', () => {
    useSessionHistoryStore.setState({ sessions: [], activeSessionId: 'vanished' });

    useSessionHistoryStore.getState().abandonActiveSession();

    expect(mockFinalizeSession).not.toHaveBeenCalled();
    expect(useSessionHistoryStore.getState().activeSessionId).toBeNull();
  });
});

/**
 * The History editor's write path (#56). These two are the only actions keyed by session *id* rather
 * than handed a `Session`, and the reason is the in-flight guard below: History has no session object
 * of its own, so letting it pass one in would let it pass a stale one.
 */
describe('editEntry', () => {
  const pullUps: SessionEntry = { exercise: 'pull-ups', type: 'reps', sets: [{ reps: 8, restTakenSec: 60 }] };
  const dips: SessionEntry = { exercise: 'dips', type: 'reps', sets: [{ reps: 10, restTakenSec: 60 }] };
  const finished = (entries: SessionEntry[]) => aSession({ endedAt: '2026-07-29T10:00:00.000Z', entries });

  it('rewrites the entry at the given index and leaves its neighbours alone', () => {
    useSessionHistoryStore.setState({ sessions: [finished([pullUps, dips])] });

    useSessionHistoryStore
      .getState()
      .editEntry('session-1', 1, { exercise: 'dips', type: 'reps', sets: [{ reps: 12, restTakenSec: 60 }] });

    const entries = useSessionHistoryStore.getState().sessions[0].entries;
    expect(entries[0]).toEqual(pullUps);
    expect(entries[1]).toEqual({ exercise: 'dips', type: 'reps', sets: [{ reps: 12, restTakenSec: 60 }] });
  });

  /**
   * The index is into `session.entries`, and `historySessionsView` filters `rest` entries out of what
   * History shows — so a card's position on that screen is not this number. Passing a view index would
   * rewrite whichever entry happened to sit at that offset, which here is the *rest* entry rather than
   * the dips. Reintroduce the bug by having the editor index the filtered list and this fails.
   */
  it('indexes the raw entry list, which is not History’s rest-filtered view of it', () => {
    const rest: SessionEntry = { exercise: 'rest', type: 'rest', restTakenSec: 120 };
    useSessionHistoryStore.setState({ sessions: [finished([pullUps, rest, dips])] });

    // Dips is at raw index 2, but at index 1 of what History renders.
    useSessionHistoryStore
      .getState()
      .editEntry('session-1', 2, { exercise: 'dips', type: 'reps', sets: [{ reps: 12, restTakenSec: 60 }] });

    const entries = useSessionHistoryStore.getState().sessions[0].entries;
    expect(entries[1]).toEqual(rest);
    expect(entries[2]).toEqual({ exercise: 'dips', type: 'reps', sets: [{ reps: 12, restTakenSec: 60 }] });
  });

  /**
   * The runner holds an in-flight session in a ref and writes through that copy, and the same session
   * is in `sessions` because `startSession` put it there. An edit accepted here would be overwritten
   * by the runner's next `logEntry` — the correction would vanish one set later, silently. Remove the
   * `endedAt` check in `finishedSession` and this fails.
   */
  it('refuses a session that is still running', () => {
    useSessionHistoryStore.setState({ sessions: [aSession({ endedAt: null, entries: [pullUps] })] });

    useSessionHistoryStore
      .getState()
      .editEntry('session-1', 0, { exercise: 'pull-ups', type: 'reps', sets: [{ reps: 99, restTakenSec: 60 }] });

    expect(useSessionHistoryStore.getState().sessions[0].entries[0]).toEqual(pullUps);
  });

  it('does nothing when the session is gone', () => {
    useSessionHistoryStore.setState({ sessions: [] });

    useSessionHistoryStore.getState().editEntry('vanished', 0, pullUps);

    expect(useSessionHistoryStore.getState().sessions).toEqual([]);
  });

  it('surfaces a flush failure the same way the runner’s writes do', () => {
    useSessionHistoryStore.setState({ sessions: [finished([pullUps])] });
    mockTakeWriteFailure.mockReturnValue('session-1: no space left on device');

    useSessionHistoryStore.getState().editEntry('session-1', 0, dips);

    expect(useSessionHistoryStore.getState().errors).toEqual(['session-1: no space left on device']);
  });
});

describe('removeEntry', () => {
  const pullUps: SessionEntry = { exercise: 'pull-ups', type: 'reps', sets: [{ reps: 8, restTakenSec: 60 }] };
  const dips: SessionEntry = { exercise: 'dips', type: 'reps', sets: [{ reps: 10, restTakenSec: 60 }] };
  const rows: SessionEntry = { exercise: 'rows', type: 'reps', sets: [{ reps: 12, restTakenSec: 60 }] };
  const finished = (entries: SessionEntry[]) => aSession({ endedAt: '2026-07-29T10:00:00.000Z', entries });

  it('drops the entry at the given index', () => {
    useSessionHistoryStore.setState({ sessions: [finished([pullUps, dips, rows])] });

    useSessionHistoryStore.getState().removeEntry('session-1', 1);

    expect(useSessionHistoryStore.getState().sessions[0].entries).toEqual([pullUps, rows]);
  });

  /**
   * Every removal shifts the indices after it down by one. The editor removes back to front for that
   * reason; this pins what the store does under that order, so a future caller doing it front to back
   * has something to fail against.
   */
  it('removes the intended two when called back to front', () => {
    useSessionHistoryStore.setState({ sessions: [finished([pullUps, dips, rows])] });

    useSessionHistoryStore.getState().removeEntry('session-1', 2);
    useSessionHistoryStore.getState().removeEntry('session-1', 0);

    expect(useSessionHistoryStore.getState().sessions[0].entries).toEqual([dips]);
  });

  it('refuses a session that is still running', () => {
    useSessionHistoryStore.setState({ sessions: [aSession({ endedAt: null, entries: [pullUps, dips] })] });

    useSessionHistoryStore.getState().removeEntry('session-1', 0);

    expect(useSessionHistoryStore.getState().sessions[0].entries).toEqual([pullUps, dips]);
  });
});
