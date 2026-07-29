import { create } from 'zustand';

import type { Session, SessionEntry } from '@/domain/types';
import {
  appendSessionEntry,
  createSession,
  deleteSession as deleteSessionFile,
  finalizeSession,
  listSessions,
  removeLastSessionEntry,
  replaceSessionEntry,
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
  hydrate: () => Promise<void>;
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
  /** Deletes a session's file and drops it from history state. Takes an id rather than a Session since, unlike the other actions, there is no updated session to return. */
  deleteSession: (id: string) => void;
};

export const useSessionHistoryStore = create<SessionHistoryState>((set, get) => ({
  status: 'idle',
  sessions: [],
  errors: [],
  activeSessionId: null,
  hydrate: async () => {
    set({ status: 'loading' });
    const { sessions, errors } = await listSessions();
    set({ status: 'ready', sessions, errors });
  },
  startSession: (workoutId, programId, programWeek, programDay) => {
    const id = new Date().toISOString().replace(/[:.]/g, '-');
    const session = createSession(id, workoutId, programId, programWeek, programDay, new Date().toISOString());
    set({ sessions: [session, ...get().sessions], activeSessionId: id });
    return session;
  },
  logEntry: (session, entry) => {
    const updated = appendSessionEntry(session, entry);
    set({ sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)) });
    return updated;
  },
  replaceEntry: (session, index, entry) => {
    const updated = replaceSessionEntry(session, index, entry);
    set({ sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)) });
    return updated;
  },
  removeLastEntry: (session) => {
    const updated = removeLastSessionEntry(session);
    set({ sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)) });
    return updated;
  },
  completeSession: (session) => {
    const updated = finalizeSession(session, new Date().toISOString());
    set({
      sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)),
      activeSessionId: null,
    });
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
      activeSessionId: null,
    });
  },
  deleteSession: (id) => {
    deleteSessionFile(id);
    set({ sessions: get().sessions.filter((session) => session.id !== id) });
  },
}));
