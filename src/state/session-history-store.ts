import { create } from 'zustand';

import type { Session, SessionEntry } from '@/domain/types';
import { usePreferencesStore } from '@/state/preferences-store';
import type { BackupFailure } from '@/storage/backup';
import { backUpNow, isBackupFolderSupported } from '@/storage/backup';
import {
  appendSessionEntry,
  createSession,
  deleteSession as deleteSessionFile,
  finalizeSession,
  listSessions,
  removeLastSessionEntry,
  removeSessionEntry,
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
   * Why the backup taken at the end of the last session didn't land, or `null` if it did (or if the
   * user has nominated no folder, which is nothing to report).
   *
   * Separate from `errors` on purpose: that list renders under a heading about *session files*, and a
   * folder whose grant was revoked is not one of those. The completion screen reads this, which is
   * the first moment after a workout where saying so costs nothing.
   */
  backupFailure: BackupFailure | null;
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
  /**
   * Corrects an already-logged entry on a **finished** session — the History editor's write path.
   *
   * Keyed by id rather than taking a `Session` like `replaceEntry` above, and the difference is not
   * cosmetic. The runner owns the session object it is writing to and needs the updated one back;
   * History owns no session, so handing it one to pass in would mean handing it something it could
   * hold past the next write. These read the current session out of state instead.
   */
  editEntry: (sessionId: string, index: number, entry: SessionEntry) => void;
  /** Drops one entry from a finished session — what `editEntry` can't express when an exercise's last set is removed. */
  removeEntry: (sessionId: string, index: number) => void;
};

/**
 * The session an id refers to, if it can be edited from outside the runner.
 *
 * **An in-flight session is refused.** While a session runs, the runner holds it in `sessionRef` and
 * writes through that copy; the same session is also in `sessions`, because `startSession` puts it
 * there. An edit from History would leave the runner holding a copy without it, and the runner's next
 * `logEntry` would spread that stale copy back over the file — the edit would vanish, one set later,
 * with nothing said. `exerciseHistory` and `previousSetFor` skip unfinished sessions for related
 * reasons; this is the same line drawn at the write side.
 */
function finishedSession(sessions: Session[], id: string): Session | undefined {
  const session = sessions.find((existing) => existing.id === id);
  return session?.endedAt ? session : undefined;
}

/** Keeps what was already reported alongside what a read just found, without duplicating either. */
function withRetained(existing: string[], incoming: string[]): string[] {
  return [...existing, ...incoming.filter((error) => !existing.includes(error))];
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
 * A session id: the start timestamp, plus four random characters.
 *
 * The id doubles as the filename, so a collision is not a duplicate row — it is one session's file
 * being overwritten by another's, silently, with no error anywhere. A bare ISO timestamp collides
 * whenever two sessions start in the same millisecond, and, more plausibly, whenever the device clock
 * moves backwards over a stretch that already has files in it. Neither is likely; both lose data
 * rather than reporting anything, which is what the suffix is for. The timestamp stays first so the
 * directory still sorts chronologically by name.
 */
function newSessionId(startedAt: string): string {
  const suffix = Math.random().toString(36).slice(2, 6).padEnd(4, '0');
  return `${startedAt.replace(/[:.]/g, '-')}-${suffix}`;
}

/**
 * Backs the log up now that a session has ended — **after the current tick**, not during it.
 *
 * Non-throwing was only half of "a backup must never interrupt a workout"; non-blocking is the other
 * half. `backUpNow` is entirely synchronous — `list()` on the tree, `textSync()` of the library,
 * serializing the whole log, and two SAF writes — and every one of those is content-provider IPC. The
 * copy actively recommends pointing this at a folder the user's sync client watches, which is exactly
 * how it lands on a cloud-backed provider where those calls take seconds. Run inline it would sit
 * between the Finish tap and the completion screen, because `completeSession` is called straight from
 * the runner's `finishSession`.
 *
 * `setTimeout(0)` yields to the event loop so React commits and paints first. Nothing waits on the
 * result: `CompletedSession` already subscribes to this store, so the warning line simply appears when
 * the answer arrives.
 *
 * Skipped entirely when the user has nominated no folder — someone who never opted in has nothing to
 * be told, and running it anyway would answer `noFolder` and light a warning on every completion
 * screen in the app.
 */
function backUpAfterSession(sessions: Session[], onResult: (failure: BackupFailure | null) => void): void {
  const { backupFolderUri } = usePreferencesStore.getState().preferences;
  if (!isBackupFolderSupported || !backupFolderUri) return;
  setTimeout(() => onResult(backUpNow(backupFolderUri, sessions)), 0);
}

export const useSessionHistoryStore = create<SessionHistoryState>((set, get) => ({
  status: 'idle',
  sessions: [],
  errors: [],
  activeSessionId: null,
  backupFailure: null,
  hydrate: async () => {
    set({ status: 'loading' });
    /*
      `listSessions` is non-throwing per file, but not per *call* — `ensureStorageReady()` and the
      directory `list()` both run before any of that and can throw on a storage layer that isn't
      there. Uncaught, the rejection went nowhere and left `status` on `loading` forever. That was
      survivable while this gated first paint, because the app simply never appeared; now that it
      doesn't, it is a Next Up card that never arrives and a History tab that stays empty in silence.

      `error` is a real terminal state rather than decoration: the screens treat it as "the read is
      over" exactly like `ready`, so an empty log stops being indistinguishable from an unread one.
    */
    let read: Awaited<ReturnType<typeof listSessions>>;
    try {
      read = await listSessions();
    } catch (error) {
      set((state) => ({ status: 'error', errors: withRetained(state.errors, [(error as Error).message]) }));
      return;
    }
    const { sessions, errors } = read;
    /*
      Merged rather than assigned, and that matters now that the app paints before this resolves
      (see _layout.tsx). A session started while the read was in flight is on disk but not in
      `sessions` — the directory was listed before its file existed — so a plain assignment would
      drop the session the runner is actively writing to out of state, mid-workout, while the runner
      kept appending to a copy nothing else could see.

      The in-flight session wins outright rather than being deduped by id: even if it *was* listed,
      the copy on disk is a snapshot from before the sets logged since, and the runner's is current.
    */
    set((state) => {
      // Every session already in memory wins, not just the active one. Anything here was created
      // during this run and so is at least as fresh as a read that started before it existed — which
      // covers the session the runner is mid-way through *and* one that was started and finished
      // inside the window, which an active-only rule dropped from state until the next launch.
      const heldIds = new Set(state.sessions.map((session) => session.id));
      const merged = [...state.sessions, ...sessions.filter((session) => !heldIds.has(session.id))];
      merged.sort((a, b) => b.startedAt.localeCompare(a.startedAt));
      // Unioned, not replaced: a write that failed during the window is the store's only record of a
      // full disk, and assigning the read's own errors over it threw that away.
      return { status: 'ready', sessions: merged, errors: withRetained(state.errors, errors) };
    });
  },
  startSession: (workoutId, programId, programWeek, programDay) => {
    // One clock read for both, so the id and the recorded start can't disagree.
    const startedAt = new Date().toISOString();
    const session = createSession(newSessionId(startedAt), workoutId, programId, programWeek, programDay, startedAt);
    set({ sessions: [session, ...get().sessions], errors: withWriteFailure(get().errors), activeSessionId: session.id });
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
    // Built before the `set` rather than inside it, because the backup has to carry the session that
    // just ended — reading `get().sessions` from within would archive the log as it was one set ago.
    const sessions = get().sessions.map((existing) => (existing.id === updated.id ? updated : existing));
    // Cleared rather than left: whatever the last session's backup did is not this one's answer, and a
    // stale warning under a fresh completion screen would be worse than a moment with none.
    set({ sessions, errors: withWriteFailure(get().errors), activeSessionId: null, backupFailure: null });
    backUpAfterSession(sessions, (backupFailure) => set({ backupFailure }));
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
  deleteSession: (id) => {
    // Called straight from an Alert button, which is an event handler: a throw here is caught by no
    // error boundary and takes the tab down. The file is what's being deleted, so a failure has to
    // leave the session in the list — dropping it from state anyway would show it gone until the next
    // launch brought it back.
    try {
      deleteSessionFile(id);
    } catch (err) {
      set({ errors: [...get().errors, `${id}: ${(err as Error).message}`] });
      return;
    }
    set({ sessions: get().sessions.filter((session) => session.id !== id), errors: withWriteFailure(get().errors) });
  },
  editEntry: (sessionId, index, entry) => {
    const session = finishedSession(get().sessions, sessionId);
    if (!session) return;
    const updated = replaceSessionEntry(session, index, entry);
    set({
      sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)),
      errors: withWriteFailure(get().errors),
    });
  },
  removeEntry: (sessionId, index) => {
    const session = finishedSession(get().sessions, sessionId);
    if (!session) return;
    const updated = removeSessionEntry(session, index);
    set({
      sessions: get().sessions.map((existing) => (existing.id === updated.id ? updated : existing)),
      errors: withWriteFailure(get().errors),
    });
  },
}));
