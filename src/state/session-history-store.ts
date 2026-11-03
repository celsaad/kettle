import { create } from 'zustand';

import type { Session, SessionEntry } from '@/domain/types';
import { reportSessionFinished } from '@/hooks/brownfield-module';
import { loggedSessions } from '@/state/selectors/logged-sessions';
import {
  appendSessionEntry,
  createSession,
  finalizeSession,
  listSessions,
  removeLastSessionEntry,
  replaceSessionEntry,
  takeWriteFailure,
} from '@/storage/session-files';

type SessionHistoryState = {
  status: 'idle' | 'loading' | 'ready' | 'error';
  sessions: Session[];
  errors: string[];
  /**
   * The session the runner is currently writing to, or null between sessions.
   *
   * Exists for the crash path: when the runner's render throws, React unmounts it and every ref it
   * held — including the `Session` it was appending to — so the session's own `ended_at` never gets
   * written and nothing left in the tree knows which file was in flight. `session.tsx`'s
   * `ErrorBoundary` reads this to finish that file off. Tracked as an id rather than the object so
   * there's only ever one copy of the session itself, in `sessions`.
   */
  activeSessionId: string | null;
  /**
   * Whether the backup the shell took at the end of the last session didn't land: false until it
   * answers, and false where no folder is set, which native doesn't answer, being nothing to report.
   *
   * Separate from `errors` on purpose: that list renders under a heading about *session files*, and a
   * folder whose grant was revoked is not one of those. The completion screen reads this, which is
   * the first moment after a workout where saying so costs nothing.
   */
  backupFailed: boolean;
  /** The shell's `backupResult`, the answer to the `sessionFinished` that `completeSession` sends. */
  backupAnswered: (failed: boolean) => void;
  hydrate: () => Promise<void>;
  /**
   * Reads the log again after another writer changed it (the native shell's `filesChanged`). Never
   * passes through `loading`, which the root layout answers by unmounting the app, runner and all.
   *
   * The in-flight session keeps its in-memory copy: the runner is its only writer and writes through
   * its own copy, so the one on disk is never newer, and is older when a write failed. Errors are kept
   * too, since a failed write is reported once and the listing can't know it; the cost is that a
   * listing error outlives a fix until the next launch.
   */
  reload: () => Promise<void>;
  /** Creates and flushes a new session file immediately (§7.2: never hold a live session only in memory). */
  startSession: (
    workoutId: string | null,
    programId: string | null,
    programWeek: number | null,
    programDay: string | null,
  ) => Session;
  /** Appends one logged entry, flushes to disk, and updates history state. Returns the updated session. */
  logEntry: (session: Session, entry: SessionEntry) => Session;
  /**
   * Rewrites an already-logged entry in place, flushes to disk, and updates history state. The runner
   * uses this to grow an exercise's entry set by set without appending a second one. Returns the
   * updated session.
   */
  replaceEntry: (session: Session, index: number, entry: SessionEntry) => Session;
  /** Removes the most recently appended entry, flushes to disk, and updates history state. Used to un-flush a logged entry when the user steps back. Returns the updated session. */
  removeLastEntry: (session: Session) => Session;
  /** Writes `ended_at` and updates history state. Returns the updated session. */
  completeSession: (session: Session) => Session;
  /**
   * Finishes off the in-flight session after the runner died, so it stops being a session that never
   * ended. Idempotent and a no-op when nothing is in flight, since the error boundary that calls it
   * can mount more than once.
   */
  abandonActiveSession: () => void;
};

/** `listed`, with the in-flight session as it is in memory rather than as it was on disk. */
function keepingActive(listed: Session[], { sessions, activeSessionId }: SessionHistoryState): Session[] {
  const active = sessions.find((session) => session.id === activeSessionId);
  if (!active) return listed;
  return [active, ...listed.filter((session) => session.id !== active.id)];
}

/**
 * Whether any session but the running one changed since `before`. Read with the id as it is now, so a
 * session finished in the meantime counts: its `ended_at` isn't something memory keeps for it.
 */
function changedBesidesActive(before: Session[], { sessions, activeSessionId }: SessionHistoryState): boolean {
  const others = (list: Session[]) => list.filter((session) => session.id !== activeSessionId);
  const then = others(before);
  const now = others(sessions);
  return then.length !== now.length || then.some((session, index) => session !== now[index]);
}

/**
 * Merges any flush failure from the write that just happened into the store's own `errors`, which
 * History renders. The writes themselves can't throw (see `writeSession`) — this is what keeps a disk
 * that stopped accepting the session from being invisible rather than fatal.
 */
function withWriteFailure(errors: string[]): string[] {
  const failure = takeWriteFailure();
  // Deduped: a disk that's full stays full, and one line per set would bury every other error.
  return failure && !errors.includes(failure) ? [...errors, failure] : errors;
}

/**
 * The log every training view reads: `loggedSessions`, which hides zero work. The default way in, so
 * a new reader gets the filter without having to know it exists; raw `state.sessions` is for the
 * readers that hand over the user's own files (export, backup). `select` narrows what the caller
 * subscribes to, so a reader that only needs a count re-renders only when the count changes.
 */
export function useLoggedSessions(): Session[];
export function useLoggedSessions<T>(select: (sessions: Session[]) => T): T;
export function useLoggedSessions<T>(select?: (sessions: Session[]) => T): T | Session[] {
  return useSessionHistoryStore((state) => {
    const sessions = loggedSessions(state.sessions);
    return select ? select(sessions) : sessions;
  });
}

export const useSessionHistoryStore = create<SessionHistoryState>((set, get) => ({
  status: 'idle',
  sessions: [],
  errors: [],
  activeSessionId: null,
  backupFailed: false,
  backupAnswered: (failed) => set({ backupFailed: failed }),
  hydrate: async () => {
    set({ status: 'loading' });
    await get().reload();
  },
  reload: async () => {
    const before = get().sessions;
    const { sessions, errors } = await listSessions();
    // RN wrote while the listing was out, so the listing may hold a file from before that write: a
    // finished session unfinished. Writes are synchronous, so the next listing has them. The running
    // session's sets don't count: memory wins for it anyway, and they land often enough to keep a slow
    // listing restarting for a whole workout.
    if (changedBesidesActive(before, get())) return get().reload();
    set({ status: 'ready', sessions: keepingActive(sessions, get()), errors: [...new Set([...get().errors, ...errors])] });
  },
  startSession: (workoutId, programId, programWeek, programDay) => {
    const id = new Date().toISOString().replace(/[:.]/g, '-');
    const session = createSession(id, workoutId, programId, programWeek, programDay, new Date().toISOString());
    set({ sessions: [session, ...get().sessions], errors: withWriteFailure(get().errors), activeSessionId: id });
    return session;
  },
  logEntry: (session, entry) => {
    const updated = appendSessionEntry(session, entry);
    set({
      sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)),
      errors: withWriteFailure(get().errors),
    });
    return updated;
  },
  replaceEntry: (session, index, entry) => {
    const updated = replaceSessionEntry(session, index, entry);
    set({
      sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)),
      errors: withWriteFailure(get().errors),
    });
    return updated;
  },
  removeLastEntry: (session) => {
    const updated = removeLastSessionEntry(session);
    set({
      sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)),
      errors: withWriteFailure(get().errors),
    });
    return updated;
  },
  completeSession: (session) => {
    const updated = finalizeSession(session, new Date().toISOString());
    const sessions = get().sessions.map((existing) => (existing.id === updated.id ? updated : existing));
    // Cleared rather than left: whatever the last session's backup did is not this one's answer, and a
    // stale warning under a fresh completion screen would be worse than a moment with none.
    set({ sessions, errors: withWriteFailure(get().errors), activeSessionId: null, backupFailed: false });
    // After the write, since native backs up the log from disk (#220). It runs the backup off its main
    // thread and answers with `backupResult`, so nothing here waits on a folder a cloud provider backs.
    reportSessionFinished();
    return updated;
  },
  abandonActiveSession: () => {
    const { activeSessionId, sessions } = get();
    if (!activeSessionId) return;
    const active = sessions.find((session) => session.id === activeSessionId);
    // Clear the id even when the session is missing (nothing to write, but it isn't in flight either),
    // so a stale id can't make a later crash stamp the wrong file.
    if (!active) {
      set({ activeSessionId: null });
      return;
    }
    const updated = finalizeSession(active, new Date().toISOString());
    set({
      sessions: sessions.map((existing) => (existing.id === updated.id ? updated : existing)),
      errors: withWriteFailure(get().errors),
      activeSessionId: null,
    });
  },
}));
