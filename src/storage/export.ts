import * as Sharing from 'expo-sharing';
import { t } from 'i18next';

import type { Session } from '@/domain/types';
import { serializeSessionArchiveYaml } from '@/domain/yaml-mapping';
import { cacheFile, sessionFile, storagePaths } from '@/storage/paths';

/** Where the assembled history lands before it's shared. Overwritten each time; see `cacheFile`. */
const HISTORY_FILENAME = 'kettle-history.yaml';

/**
 * Both platforms' options go in every call, since each reads only its own: `mimeType` and
 * `dialogTitle` are Android's (`dialogTitle` is web's too), `UTI` is iOS's.
 *
 * `public.plain-text` rather than a type of our own. iOS picks the type from the extension when no
 * UTI is given, no system type claims `.yaml`, and the app list in the sheet is built from that — so
 * an unclaimed extension quietly narrows it. Declaring a `public.yaml` of our own is an Info.plist
 * exercise that buys an icon; saying "it's text", which it is, buys every text-capable app.
 *
 * The title is translated because Android shows it in the chooser. `t` is called here rather than
 * passed in by the four call sites, matching `library-file.ts`; the session id in it is user data and
 * is interpolated, never translated.
 */
async function share(uri: string, dialogTitle: string): Promise<void> {
  const available = await Sharing.isAvailableAsync();
  if (!available) throw new Error('Sharing is not available on this device');
  await Sharing.shareAsync(uri, { mimeType: 'application/x-yaml', UTI: 'public.plain-text', dialogTitle });
}

// Both are `async` for a specific reason, not style: resolving `.uri` constructs an expo-file-system
// File/Directory, which has no web implementation and throws *synchronously* — before `share()`'s own
// async body is ever entered. Without `async` here that throw escapes past the returned promise
// entirely, so the `.catch()` every caller already has never runs and it surfaces as an unhandled
// error. `async` turns it into a rejection those handlers can actually see.
export async function exportLibrary(): Promise<void> {
  return share(storagePaths.libraryFile.uri, t('settings.shareLibraryTitle'));
}

export async function exportSession(sessionId: string): Promise<void> {
  return share(sessionFile(sessionId).uri, t('settings.shareSessionTitle', { id: sessionId }));
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
  return share(file.uri, t('settings.shareHistoryTitle'));
}
