import * as Sharing from 'expo-sharing';

import type { Session } from '@/domain/types';
import { serializeSessionArchiveYaml } from '@/domain/yaml-mapping';
import { cacheFile, sessionFile, storagePaths } from '@/storage/paths';

/** Where the assembled history lands before it's shared. Overwritten each time; see `cacheFile`. */
const HISTORY_FILENAME = 'kettle-history.yaml';

async function share(uri: string, dialogTitle: string): Promise<void> {
  const available = await Sharing.isAvailableAsync();
  if (!available) throw new Error('Sharing is not available on this device');
  await Sharing.shareAsync(uri, { mimeType: 'application/x-yaml', dialogTitle });
}

// Both are `async` for a specific reason, not style: resolving `.uri` constructs an expo-file-system
// File/Directory, which has no web implementation and throws *synchronously* — before `share()`'s own
// async body is ever entered. Without `async` here that throw escapes past the returned promise
// entirely, so the `.catch()` every caller already has never runs and it surfaces as an unhandled
// error. `async` turns it into a rejection those handlers can actually see.
export async function exportLibrary(): Promise<void> {
  return share(storagePaths.libraryFile.uri, 'Export exercises.yaml');
}

export async function exportSession(sessionId: string): Promise<void> {
  return share(sessionFile(sessionId).uri, `Export session ${sessionId}`);
}

/**
 * Shares the whole log as one file. Takes the sessions rather than reading `sessionsDir` because the
 * store already holds every one of them in memory — re-reading and re-parsing the directory here
 * would pay `listSessions()`'s O(all sessions ever logged) cost a second time for data that is
 * already sitting one import away, and would disagree with what History is showing if it did.
 *
 * Ordered oldest-first, the opposite of the store (and of History, which leads with what you just
 * did). This is an archive being read top to bottom in some other app, so it runs forward in time.
 */
export async function exportSessions(sessions: Session[]): Promise<void> {
  // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
  // oxlint-disable-next-line unicorn/no-array-sort
  const chronological = [...sessions].sort((a, b) => a.startedAt.localeCompare(b.startedAt));
  const file = cacheFile(HISTORY_FILENAME);
  file.create({ intermediates: true, overwrite: true });
  file.write(serializeSessionArchiveYaml(chronological, new Date().toISOString()));
  return share(file.uri, 'Export history');
}
