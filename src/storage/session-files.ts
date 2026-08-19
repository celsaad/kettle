import { File } from 'expo-file-system';

import { parseSessionYaml, serializeSessionYaml } from '@/domain/yaml-mapping';
import type { Session, SessionEntry } from '@/domain/types';
import { ensureStorageReady, isFileStorageSupported, sessionFile, storagePaths } from '@/storage/paths';

export type ListSessionsResult = { sessions: Session[]; errors: string[] };

/**
 * How many session files `listSessions` reads at once. Enough to hide the per-file bridge latency,
 * small enough that a long training history doesn't open hundreds of handles or hold every file's
 * text in memory at the same moment — both of which land during startup.
 */
const ReadConcurrency = 16;

/**
 * Reads every session file. One malformed file produces an error entry, not a crash.
 * On web (unsupported by expo-file-system) this degrades to an empty, non-persisted history.
 */
export async function listSessions(): Promise<ListSessionsResult> {
  if (!isFileStorageSupported) return { sessions: [], errors: [] };

  ensureStorageReady();

  const files = storagePaths.sessionsDir
    .list()
    .filter((entry): entry is File => entry instanceof File && entry.name.endsWith('.yaml'));

  const sessions: Session[] = [];
  const errors: string[] = [];

  /*
    Read in parallel rather than one `await` at a time. Each `file.text()` is a bridge round-trip,
    and serialised they add up in the user's own training history: a log of several hundred sessions
    paid for every one of those latencies end to end. Parsing stays serial below — it's CPU-bound and
    the JS thread is single — so this only overlaps the part that was waiting.

    `allSettled`, not `all`: this function's contract is that one bad file costs that file and not the
    history, and a *read* that failed was the hole in it — a plain `await` in the old loop threw
    straight out of `hydrate`, which has no catch, leaving the store stuck on `loading` forever. That
    used to mean the app never painted at all, which at least said something was wrong; now that
    history no longer gates first paint it would be a History tab that stayed empty with no reason
    given. `Promise.all` would have made it worse still, losing every other file to one unreadable one.

    In **chunks** rather than all at once, and the chunk is what keeps this a latency win instead of a
    trade. Firing every read together on a log of several hundred files opens that many handles at
    once and holds every file's text in memory simultaneously — at startup, on the device least able
    to afford it. A window of ${ReadConcurrency} recovers nearly all of the overlap (the round-trips
    are latency-bound, not throughput-bound) with a bounded footprint, and each chunk is parsed and
    released before the next is read.
  */
  for (let start = 0; start < files.length; start += ReadConcurrency) {
    const chunk = files.slice(start, start + ReadConcurrency);
    const reads = await Promise.allSettled(chunk.map((file) => file.text()));

    chunk.forEach((file, index) => {
      const read = reads[index];
      if (read.status === 'rejected') {
        errors.push(`${file.name} (unreadable): ${(read.reason as Error).message}`);
        return;
      }
      const result = parseSessionYaml(read.value);
      if (result.ok) sessions.push(result.data);
      else errors.push(`${file.name} (${result.error.kind}): ${result.error.detail}`);
    });
  }

  sessions.sort((a, b) => b.startedAt.localeCompare(a.startedAt));
  return { sessions, errors };
}

/**
 * Set by `writeSession` when a flush fails, read and cleared by `takeWriteFailure()`.
 *
 * Module state rather than a return value on purpose: every session write funnels through
 * `writeSession`, and threading a success flag back out would change the signature of all five
 * exported writers plus the store actions plus the runner's `sessionRef.current = logEntry(...)`
 * chain — a wide change to the timer-critical path to report something none of those callers can act
 * on mid-set anyway.
 */
let writeFailure: string | null = null;

/** Reads and clears the last flush failure. The store calls this after each write to surface it. */
export function takeWriteFailure(): string | null {
  const failure = writeFailure;
  writeFailure = null;
  return failure;
}

/**
 * The single choke point for every session write, and deliberately **non-throwing**.
 *
 * These writes are synchronous and happen inside the runner's `advance()` — an event handler or an
 * interval tick, neither of which any error boundary covers. So a full disk mid-workout used to take
 * the runner down between two sets: the set was done, the app was gone, and what had already been
 * flushed was all that survived. A workout has to outlive the disk that's recording it — losing the
 * log is bad, losing the session in progress is worse — so a failure is recorded and stepped over,
 * and `takeWriteFailure` is what stops that from being silent.
 */
function writeSession(session: Session): void {
  if (!isFileStorageSupported) return;
  try {
    ensureStorageReady();
    const file = sessionFile(session.id);
    if (!file.exists) file.create({ intermediates: true, overwrite: true });
    file.write(serializeSessionYaml(session));
  } catch (err) {
    writeFailure = `${session.id}: ${(err as Error).message}`;
  }
}

/** Creates and immediately flushes a new session file. Call at session start (§7.2: never hold a live session only in memory). */
export function createSession(
  id: string,
  workout: string | null,
  program: string | null,
  programWeek: number | null,
  programDay: string | null,
  startedAt: string,
): Session {
  const session: Session = {
    version: 1,
    id,
    workout,
    program,
    programWeek,
    programDay,
    startedAt,
    endedAt: null,
    entries: [],
  };
  writeSession(session);
  return session;
}

/** Appends one logged entry and flushes to disk. A mid-workout crash then loses at most the current in-progress set. */
export function appendSessionEntry(session: Session, entry: SessionEntry): Session {
  const updated: Session = { ...session, entries: [...session.entries, entry] };
  writeSession(updated);
  return updated;
}

/**
 * Rewrites one already-appended entry in place and flushes to disk.
 *
 * The runner appends an exercise's entry when its first set lands and then replaces that same entry
 * as each later set is added, which is what keeps the on-disk session at most one in-progress set
 * behind (§7.2). Out-of-range indices are left alone rather than appended: the caller tracks the index
 * it was given at append time, so an index that no longer exists means the caller's bookkeeping is
 * wrong, and inventing a duplicate entry would hide that instead of showing it.
 */
export function replaceSessionEntry(session: Session, index: number, entry: SessionEntry): Session {
  const updated: Session = {
    ...session,
    entries: session.entries.map((existing, position) => (position === index ? entry : existing)),
  };
  writeSession(updated);
  return updated;
}

/**
 * Removes the most recently appended entry and flushes to disk. Mirrors `appendSessionEntry` but in
 * reverse — used by `goPrev()` to un-flush a set/round/entry that was just committed, when the user
 * steps back to redo it. A full rewrite here is exactly as cheap as the append it undoes (§5.2 note 3
 * is about not rewriting *other* sessions' files, not this session's own).
 */
export function removeLastSessionEntry(session: Session): Session {
  const updated: Session = { ...session, entries: session.entries.slice(0, -1) };
  writeSession(updated);
  return updated;
}

/**
 * Removes one entry at an arbitrary index and flushes to disk — what correcting a mis-logged session
 * needs when a whole exercise comes out, as opposed to `removeLastSessionEntry`'s undo of the write
 * that just happened.
 *
 * Out-of-range indices are left alone, matching `replaceSessionEntry`: the caller is working from a
 * session it read, so an index that doesn't exist means its bookkeeping is wrong, and silently
 * deleting nothing is easier to notice than silently deleting the wrong entry.
 */
export function removeSessionEntry(session: Session, index: number): Session {
  const updated: Session = { ...session, entries: session.entries.filter((_, position) => position !== index) };
  writeSession(updated);
  return updated;
}

/**
 * Deletes a session's file. Idempotent: a missing file already satisfies the caller's intent, and
 * `File.delete()` throws on a nonexistent path, so the `exists` check is load-bearing rather than
 * defensive.
 */
export function deleteSession(id: string): void {
  if (!isFileStorageSupported) return;
  ensureStorageReady();
  const file = sessionFile(id);
  if (file.exists) file.delete();
}

export function finalizeSession(session: Session, endedAt: string): Session {
  const updated: Session = { ...session, endedAt };
  writeSession(updated);
  return updated;
}
