const mockCreateSession = jest.fn();
const mockFinalizeSession = jest.fn();
jest.mock('@/storage/session-files', () => ({
  appendSessionEntry: (session: unknown) => session,
  createSession: (...args: unknown[]) => mockCreateSession(...args),
  deleteSession: jest.fn(),
  finalizeSession: (...args: unknown[]) => mockFinalizeSession(...args),
  listSessions: jest.fn(),
  removeLastSessionEntry: (session: unknown) => session,
  replaceSessionEntry: (session: Session, index: number, entry: SessionEntry) => ({
    ...session,
    entries: session.entries.map((existing, position) => (position === index ? entry : existing)),
  }),
}));

import type { Session, SessionEntry } from '@/domain/types';
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

beforeEach(() => {
  useSessionHistoryStore.setState({ status: 'ready', sessions: [], errors: [], activeSessionId: null });
  mockCreateSession.mockReset().mockImplementation((id: string) => aSession({ id }));
  mockFinalizeSession.mockReset().mockImplementation((session: Session, endedAt: string) => ({ ...session, endedAt }));
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
