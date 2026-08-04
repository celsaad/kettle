/**
 * Backups into a folder the user nominated.
 *
 * The user picks a directory once — theirs, wherever their sync client watches — and Kettle writes
 * its two artefacts there. It is the user's own filesystem, so nothing here transmits and nothing
 * here needs a Data Safety declaration; see the decision log for why that beat a cloud SDK.
 *
 * The `content://` URIs this deals in behave nothing like the `file://` ones the rest of
 * `src/storage/` uses, and each difference fails quietly rather than loudly. The three that matter
 * are recorded in `docs/sdk-57-api-notes.md`; the one this file has to work around on every write is
 * that `createFile` *uniquifies* rather than overwrites.
 */
import { Directory, File } from 'expo-file-system';
import { Platform } from 'react-native';

import type { Session } from '@/domain/types';
import { serializeSessionArchiveYaml } from '@/domain/yaml-mapping';
import { isFileStorageSupported, storagePaths } from '@/storage/paths';

/** Prefixed because the destination is a folder of the user's own files, not an app sandbox. */
export const LIBRARY_BACKUP_NAME = 'kettle-library.yaml';
export const HISTORY_BACKUP_NAME = 'kettle-history.yaml';

/**
 * Android only.
 *
 * Android's picker takes a *persistable* URI permission (`takePersistableUriPermission` on the
 * `ACTION_OPEN_DOCUMENT_TREE` result), so a folder chosen once keeps working. iOS only calls
 * `startAccessingSecurityScopedResource` and stores no bookmark, so the grant dies with the app
 * session — a folder chosen there would silently stop working at the next launch, which is worse than
 * not offering it. Web has no filesystem at all.
 */
export const isBackupFolderSupported = Platform.OS === 'android' && isFileStorageSupported;

/**
 * Why a backup didn't happen. A code rather than a message because the caller renders it — the one
 * place a raw platform string reaches the screen is `writeFailed`, which carries the reason the OS
 * gave and has no better phrasing available.
 */
export type BackupFailure =
  | { kind: 'unsupported' }
  | { kind: 'noFolder' }
  | { kind: 'unreachable' }
  | { kind: 'writeFailed'; detail: string };

/**
 * Finds the child by name, or makes it.
 *
 * Load-bearing, not defensive: `Directory.createFile` goes through `DocumentsContract.createDocument`,
 * which *uniquifies* a name that already exists rather than overwriting it. Creating unconditionally
 * would leave `kettle-history (1).yaml`, `(2)`, `(3)` beside the original — one file per session, in
 * the folder whose whole job is to hold one good copy.
 *
 * Matching is on the display name that `File.name` derives from the document URI. That is exact for
 * the providers this actually targets (on-device storage), and would fail for a provider using opaque
 * document ids — which would cost duplicates, not data.
 */
function writeChild(folder: Directory, children: (Directory | File)[], name: string, content: string): void {
  const existing = children.find((entry): entry is File => entry instanceof File && entry.name === name);
  // `new File(folder, name)` is not the alternative: `Paths.join` appends to the tree URI's path and
  // produces something that is not a document URI at all. Children come from `list()` or `createFile`.
  const file = existing ?? folder.createFile(name, 'application/x-yaml');
  file.write(content);
}

/**
 * Writes both artefacts into the chosen folder, and answers with what went wrong or `null`.
 *
 * **Never throws.** One of its two callers is `completeSession`, reached from the runner's
 * `finishSession` — an event handler no error boundary covers, exactly like the `writeSession` path
 * next door. A workout has to outlive a backup that couldn't be written, so a failure is returned and
 * stepped over rather than raised, and both callers surface it *after* the session rather than during
 * it. It returns the failure instead of parking it in module state the way `writeSession` has to,
 * because unlike that one it has two call sites and both can read a return value.
 */
export function backUpNow(folderUri: string | null, sessions: Session[]): BackupFailure | null {
  if (!isBackupFolderSupported) return { kind: 'unsupported' };
  if (!folderUri) return { kind: 'noFolder' };

  try {
    const folder = new Directory(folderUri);
    // Covers both halves of "the folder went away": deleted, and the grant revoked in system
    // settings. `Directory.exists` answers false rather than throwing for either.
    if (!folder.exists) return { kind: 'unreachable' };

    // Listed once and passed to both writes: `list()` is O(files in the user's folder), and that
    // folder is theirs, so it may hold a great deal more than ours.
    const children = folder.list();

    // The library is copied byte for byte rather than re-serialized. It is the same artefact
    // `exportLibrary` shares — the file itself — so a backup and an export can't disagree.
    if (storagePaths.libraryFile.exists) {
      writeChild(folder, children, LIBRARY_BACKUP_NAME, storagePaths.libraryFile.textSync());
    }

    // Oldest-first, matching `exportSessions`: this is an archive read top to bottom in some other
    // app, so it runs forward in time.
    // oxlint-disable-next-line unicorn/no-array-sort
    const chronological = [...sessions].sort((a, b) => a.startedAt.localeCompare(b.startedAt));
    writeChild(folder, children, HISTORY_BACKUP_NAME, serializeSessionArchiveYaml(chronological, new Date().toISOString()));

    return null;
  } catch (error) {
    return { kind: 'writeFailed', detail: (error as Error).message };
  }
}

/**
 * Opens the system folder picker and returns the chosen URI, or `null` if the user backed out.
 *
 * Cancelling is not a failure and must not read as one — it is the ordinary way to close a picker
 * you opened by accident — so it is separated from a real error by the code the native module
 * attaches (`PickerCancelledException` infers `ERR_PICKER_CANCELLED`). Anything else is rethrown for
 * the caller to show.
 */
export async function pickBackupFolder(): Promise<string | null> {
  const directory = await Directory.pickDirectoryAsync().catch((error: unknown) => {
    if ((error as { code?: string }).code === 'ERR_PICKER_CANCELLED') return null;
    throw error;
  });
  return directory?.uri ?? null;
}

/**
 * The last path segment of a SAF tree URI, decoded — `primary:Documents/Kettle` becomes
 * `Documents/Kettle`. Purely to give the user something recognisable to look at; nothing reads it
 * back, and it is user data, so it renders verbatim and is never translated.
 */
export function backupFolderLabel(folderUri: string): string {
  try {
    const decoded = decodeURIComponent(folderUri);
    // Everything after the volume marker. A tree URI's document id is `<volume>:<path>`, so the colon
    // is the split point; a provider that doesn't follow that shape falls through to the raw URI,
    // which is ugly but honest.
    const afterVolume = decoded.slice(decoded.lastIndexOf(':') + 1).replace(/^\/+|\/+$/g, '');
    return afterVolume || decoded;
  } catch {
    return folderUri;
  }
}
