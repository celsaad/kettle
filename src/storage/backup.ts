/**
 * Backups into a folder the user nominated.
 *
 * The user picks a directory once — theirs, wherever their sync client watches — and Kettle writes
 * its two artefacts there. It is the user's own filesystem, so nothing here transmits and nothing
 * here needs a Data Safety declaration; see the decision log for why that beat a cloud SDK.
 *
 * The `content://` URIs this deals in behave nothing like the `file://` ones the rest of
 * `src/storage/` uses, and every difference fails quietly rather than loudly. All five are recorded
 * in `docs/sdk-57-api-notes.md`; the two this file works around on every single write are that
 * `createFile` *uniquifies* rather than overwrites, and that `write` does not truncate. A third —
 * that the truncating handle never closed its descriptor — is patched in the package instead.
 */
import { Directory, File, FileMode } from 'expo-file-system';
import { Platform } from 'react-native';
import { z } from 'zod';

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
 * iOS only, and the answer to the same question `isBackupFolderSupported` refuses.
 *
 * iOS can't keep a folder the user picks, but it doesn't need to: `UIFileSharingEnabled` and
 * `LSSupportsOpeningDocumentsInPlace` (both in `app.json`'s `ios.infoPlist`) publish the app's own
 * Documents folder to the Files app, where the user's iCloud Drive, Dropbox or Working Copy can
 * already reach it. The user doesn't nominate a folder, they just have one — and Kettle keeps
 * writing exactly where it always wrote, so there is no second copy to keep in sync and nothing here
 * to run.
 *
 * That means this flag governs *copy*, not behaviour: it says whether Settings may tell the user
 * their files are reachable. `app-config.test.ts` asserts both keys are still in `app.json`, because
 * removing one would leave this sentence on screen and false.
 */
export const isDocumentsFolderShared = Platform.OS === 'ios' && isFileStorageSupported;

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
 * What the last backup wrote, as file name → document URI, so the next one can find those documents
 * where their names don't identify them (see `writeChild`).
 *
 * Not keyed by folder: a URI only counts while the folder's own `list()` still returns it, so one left
 * over from a folder the user has since swapped out can never match, and there is nothing to expire.
 */
type BackupTargets = Record<string, string>;
const backupTargetsSchema = z.record(z.string(), z.string());

/**
 * Never throws, and a missing or unreadable record reads as "nothing remembered". The record is a
 * convenience — losing it costs one more pair of files on a provider that needs it, never the backup.
 */
function loadTargets(): BackupTargets {
  try {
    const file = storagePaths.backupTargetsFile;
    if (!file.exists) return {};
    const parsed = backupTargetsSchema.safeParse(JSON.parse(file.textSync()));
    return parsed.success ? parsed.data : {};
  } catch {
    return {};
  }
}

/** Never throws, for the same reason: failing to remember costs a duplicate next time, not this backup. */
function saveTargets(targets: BackupTargets): void {
  try {
    const file = storagePaths.backupTargetsFile;
    if (!file.exists) file.create({ intermediates: true, overwrite: true });
    file.write(JSON.stringify(targets));
  } catch {
    // Swallowed on purpose — see above.
  }
}

/**
 * Finds the child this backup wrote last time, or makes it, and writes into it.
 *
 * Load-bearing, not defensive: `Directory.createFile` goes through `DocumentsContract.createDocument`,
 * which *uniquifies* a name that already exists rather than overwriting it. Creating unconditionally
 * would leave `kettle-history (1).yaml`, `(2)`, `(3)` beside the original — one file per session, in
 * the folder whose whole job is to hold one good copy.
 *
 * Found by name first and by last time's URI second, because neither works on every provider.
 * `File.name` is only the last segment of the document URI: the display name where document ids are
 * paths (on-device storage), an opaque id where they aren't. Google Drive's are `acc=1;doc=encoded=…`,
 * so the name never matched there, and a device showed a fresh `(1)`, `(2)`, `(3)` pair after every
 * backup. The remembered URI works anywhere, but only while `list()` still returns it — a backup the
 * user deleted or moved out of the folder is made again rather than chased to wherever it went. Name
 * first keeps on-device folders behaving exactly as they did before the URI existed.
 *
 * What the URI can't see is a rename. On Drive, a backup the user renamed is still the document
 * Kettle made, so it goes on receiving backups under its new name.
 */
function writeChild(
  folder: Directory,
  children: (Directory | File)[],
  targets: BackupTargets,
  name: string,
  content: string,
): void {
  const existing =
    children.find((entry): entry is File => entry instanceof File && entry.name === name) ??
    children.find((entry): entry is File => entry instanceof File && entry.uri === targets[name]);
  // `new File(folder, name)` is not the alternative: `Paths.join` appends to the tree URI's path and
  // produces something that is not a document URI at all. Children come from `list()` or `createFile`.
  const file = existing ?? folder.createFile(name, 'application/x-yaml');
  // Before the write, not after it: a write that throws has still left a document in the folder, and
  // the next backup has to find that one rather than make another.
  targets[name] = file.uri;

  // **Not `file.write(content)`**, and this is the SAF difference that costs data rather than just
  // failing. On Android `write` opens the document with mode `"w"`, which overwrites from offset zero
  // and *does not truncate*; `FileMode` in the same package spells out the distinction, since `"wt"`
  // is the one documented as "Wipes file contents before writing". Every `file://` path in
  // `src/storage/` gets truncation for free from `FileOutputStream(file, false)`, so nothing here had
  // met this before.
  //
  // Left as `write`, any backup shorter than the last one — a deleted exercise, a shortened note, a
  // deleted session — leaves the tail of the previous version welded onto the end of the new one. The
  // result is a `kettle-library.yaml` that either won't parse or, worse, parses into something wrong,
  // in the one artefact this feature promises can be re-imported.
  //
  // Truncate-and-write rather than delete-and-recreate: deleting first opens a window where the
  // backup doesn't exist at all, which is the wrong trade for the file whose job is to be the copy
  // that survives.
  //
  // `close()` is what tells the provider the write is finished, and on every 57.x release it didn't:
  // the handle kept only the channel and leaked the `ParcelFileDescriptor` underneath it, which is
  // how a backup could land as a file with nothing in it. That is fixed by the `expo-file-system`
  // patch under `patches/`, not here — JS has no way to reach the descriptor.
  const handle = file.open(FileMode.Truncate);
  try {
    handle.writeBytes(new TextEncoder().encode(content));
  } finally {
    handle.close();
  }
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
    // Filled in by each write and saved even when one throws, since a document created before the
    // failure still has to be found next time.
    const targets = loadTargets();

    try {
      // The library is copied byte for byte rather than re-serialized. It is the same artefact
      // `exportLibrary` shares — the file itself — so a backup and an export can't disagree.
      if (storagePaths.libraryFile.exists) {
        writeChild(folder, children, targets, LIBRARY_BACKUP_NAME, storagePaths.libraryFile.textSync());
      }

      // Oldest-first, matching `exportSessions`: this is an archive read top to bottom in some other
      // app, so it runs forward in time.
      // oxlint-disable-next-line unicorn/no-array-sort
      const chronological = [...sessions].sort((a, b) => a.startedAt.localeCompare(b.startedAt));
      writeChild(
        folder,
        children,
        targets,
        HISTORY_BACKUP_NAME,
        serializeSessionArchiveYaml(chronological, new Date().toISOString()),
      );
    } finally {
      saveTargets(targets);
    }

    return null;
  } catch (error) {
    // `?? String(error)` because a native module can reject with something that has no `message` —
    // and "Couldn't write the backup: undefined" is a worse answer than the raw object's own text.
    return { kind: 'writeFailed', detail: (error as Error)?.message ?? String(error) };
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
    // A tree URI's document id is `<volume>:<path>`, and that colon is the split point. Matched
    // explicitly rather than with `lastIndexOf(':')`, which always finds the scheme's own colon and so
    // would answer `//provider/tree/abc` for a provider using an opaque id — mangled rather than
    // recognisable. No volume marker means there is no path to show, so the raw URI is the honest
    // answer: ugly, but it is at least the thing the user picked.
    const afterVolume = /:([^:]*)$/.exec(decoded.slice(decoded.indexOf('/tree/')));
    return afterVolume?.[1].replace(/^\/+|\/+$/g, '') || decoded;
  } catch {
    return folderUri;
  }
}
