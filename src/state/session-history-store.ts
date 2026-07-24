import { create } from 'zustand';

import type { Session, SessionEntry } from '@/domain/types';
import { appendSessionEntry, createSession, finalizeSession, listSessions } from '@/storage/session-files';

type SessionHistoryState = {
  status: 'idle' | 'loading' | 'ready' | 'error';
  sessions: Session[];
  errors: string[];
  hydrate: () => Promise<void>;
  /** Creates and flushes a new session file immediately (§7.2: never hold a live session only in memory). */
  startSession: (workoutId: string | null, programId: string | null) => Session;
  /** Appends one logged entry, flushes to disk, and updates history state. Returns the updated session. */
  logEntry: (session: Session, entry: SessionEntry) => Session;
  /** Writes `ended_at` and updates history state. Returns the updated session. */
  completeSession: (session: Session) => Session;
};

export const useSessionHistoryStore = create<SessionHistoryState>((set, get) => ({
  status: 'idle',
  sessions: [],
  errors: [],
  hydrate: async () => {
    set({ status: 'loading' });
    const { sessions, errors } = await listSessions();
    set({ status: 'ready', sessions, errors });
  },
  startSession: (workoutId, programId) => {
    const id = new Date().toISOString().replace(/[:.]/g, '-');
    const session = createSession(id, workoutId, programId, new Date().toISOString());
    set({ sessions: [session, ...get().sessions] });
    return session;
  },
  logEntry: (session, entry) => {
    const updated = appendSessionEntry(session, entry);
    set({ sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)) });
    return updated;
  },
  completeSession: (session) => {
    const updated = finalizeSession(session, new Date().toISOString());
    set({ sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)) });
    return updated;
  },
}));
