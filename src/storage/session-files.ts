import { File } from 'expo-file-system';

import { parseSessionYaml, serializeSessionYaml } from '@/domain/yaml-mapping';
import type { Session, SessionEntry } from '@/domain/types';
import { ensureStorageReady, isFileStorageSupported, sessionFile, storagePaths } from '@/storage/paths';

export type ListSessionsResult = { sessions: Session[]; errors: string[] };

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

  for (const file of files) {
    const text = await file.text();
    const result = parseSessionYaml(text);
    if (result.ok) sessions.push(result.data);
    else errors.push(`${file.name}: ${result.error}`);
  }

  sessions.sort((a, b) => b.startedAt.localeCompare(a.startedAt));
  return { sessions, errors };
}

function writeSession(session: Session): void {
  if (!isFileStorageSupported) return;
  ensureStorageReady();
  const file = sessionFile(session.id);
  if (!file.exists) file.create({ intermediates: true, overwrite: true });
  file.write(serializeSessionYaml(session));
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
