const mockCreateSession = jest.fn();
const mockFinalizeSession = jest.fn();
const mockTakeWriteFailure = jest.fn<string | null, []>(() => null);
const mockListSessions = jest.fn();
jest.mock('@/storage/session-files', () => ({
  appendSessionEntry: (session: unknown) => session,
  createSession: (...args: unknown[]) => mockCreateSession(...args),
  takeWriteFailure: () => mockTakeWriteFailure(),
  finalizeSession: (...args: unknown[]) => mockFinalizeSession(...args),
  listSessions: () => mockListSessions(),
  removeLastSessionEntry: (session: unknown) => session,
  replaceSessionEntry: (session: Session, index: number, entry: SessionEntry) => ({
    ...session,
    entries: session.entries.map((existing, position) => (position === index ? entry : existing)),
  }),
}));

import type { Session, SessionEntry } from '@/domain/types';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * `activeSessionId`/`abandonActiveSession` (which existed for one caller: RN's runner's error
 * boundary, running after React had thrown away the runner and the `Session` ref it held) and
 * `replaceEntry`'s effect on history state. RN's runner went in the flip release (#308), and these go
 * with the store when routing moves native (#309).
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

beforeEach(() => {
  useSessionHistoryStore.setState({
    status: 'ready',
    sessions: [],
    errors: [],
    activeSessionId: null,
  });
  mockCreateSession.mockReset().mockImplementation((id: string) => aSession({ id }));
  mockFinalizeSession.mockReset().mockImplementation((session: Session, endedAt: string) => ({ ...session, endedAt }));
  mockTakeWriteFailure.mockReset().mockReturnValue(null);
  mockListSessions.mockReset();
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

/**
 * The native shell's `filesChanged`: another writer changed the log, and History has to show it. A
 * workout may be running while it arrives.
 */
describe('reload', () => {
  const pullups: SessionEntry = { exercise: 'pullups', type: 'reps', sets: [{ reps: 6, restTakenSec: 0 }] };

  // The root layout renders nothing while a store is loading, which would unmount the runner mid-set.
  it('never passes through loading', async () => {
    mockListSessions.mockResolvedValue({ sessions: [], errors: [] });
    const statuses: string[] = [];
    const unsubscribe = useSessionHistoryStore.subscribe((state) => statuses.push(state.status));

    await useSessionHistoryStore.getState().reload();
    unsubscribe();

    expect(statuses).not.toContain('loading');
    expect(useSessionHistoryStore.getState().status).toBe('ready');
  });

  // The listing is async, so the runner can log a set while it is out; and a failed flush leaves the
  // file behind memory. Either way the disk's copy of the running session is the stale one.
  it("keeps the running session's sets when the copy on disk is behind", async () => {
    const running = useSessionHistoryStore.getState().startSession('w', null, null, null);
    const logged = { ...running, entries: [pullups] };
    useSessionHistoryStore.setState({ sessions: [logged] });
    const finished = aSession({ id: 'earlier', endedAt: '2026-07-28T10:00:00.000Z' });
    mockListSessions.mockResolvedValue({ sessions: [{ ...logged, entries: [] }, finished], errors: [] });

    await useSessionHistoryStore.getState().reload();

    expect(useSessionHistoryStore.getState().sessions).toEqual([logged, finished]);
  });

  // A full disk mid-workout is only ever said once, when the write fails; the listing can't know it.
  it('keeps a failed flush on record alongside what the listing found', async () => {
    useSessionHistoryStore.setState({ errors: ['session-1: no space left on device'] });
    mockListSessions.mockResolvedValue({ sessions: [], errors: ['bad.yaml (parse): unexpected end'] });

    await useSessionHistoryStore.getState().reload();

    expect(useSessionHistoryStore.getState().errors).toEqual([
      'session-1: no space left on device',
      'bad.yaml (parse): unexpected end',
    ]);
  });

  // Once finished it is no longer the running session, so memory stops winning for it.
  it('reads again when the running session finished while the listing was out', async () => {
    const running = useSessionHistoryStore.getState().startSession('w', null, null, null);
    mockListSessions
      .mockImplementationOnce(async () => {
        useSessionHistoryStore.getState().completeSession(running);
        return { sessions: [running], errors: [] };
      })
      .mockImplementation(async () => ({ sessions: [{ ...running, endedAt: 'finished' }], errors: [] }));

    await useSessionHistoryStore.getState().reload();

    expect(mockListSessions).toHaveBeenCalledTimes(2);
    expect(useSessionHistoryStore.getState().sessions[0].endedAt).toBe('finished');
  });

  // The runner writes on every set, so reading again for its writes could chase it for a whole slow
  // listing. Its session is kept from memory anyway, so its writes need no second read.
  it("doesn't read again for the running session's own sets", async () => {
    const running = useSessionHistoryStore.getState().startSession('w', null, null, null);
    mockListSessions.mockImplementation(async () => {
      useSessionHistoryStore.setState({ sessions: [{ ...running, entries: [pullups] }] });
      return { sessions: [running], errors: [] };
    });

    await useSessionHistoryStore.getState().reload();

    expect(mockListSessions).toHaveBeenCalledTimes(1);
    expect(useSessionHistoryStore.getState().sessions[0].entries).toEqual([pullups]);
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
