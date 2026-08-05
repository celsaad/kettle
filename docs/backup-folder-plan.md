# Backup folder plan

> **Executed; kept for its rationale, not as a backlog.** The approach for letting the user nominate
> a folder Kettle writes its files into, agreed before any code was written. Why a user-picked SAF
> folder beat a cloud SDK is in [`decisions.md`](decisions.md); the API facts it rests on are in
> [`sdk-57-api-notes.md`](sdk-57-api-notes.md). The one section still live is "Still to confirm on a
> device".

## The problem

A lost phone loses the whole training log. "Export anytime" is a manual ritual nobody performs. This
is the highest-severity gap before promoting out of closed testing.

## The shape

The user picks a directory **once**. Kettle writes its two artefacts there on session finish, plus on
demand from Settings. It is the user's own filesystem — no SDK, no network call, nothing to declare
on the Data Safety form, so the zero-data-collected claim is untouched.

Deliberately **not** in scope: a cloud backend, an auto-backup SDK, scheduling, a background task,
incremental sync, conflict resolution, or restoring a session log (see "What the copy may not
promise").

## What was verified, and what wasn't

`Directory.pickDirectoryAsync(initialUri?)` exists in SDK 57
(`node_modules/expo-file-system/build/Directory.d.ts:16`). The open question was whether the returned
SAF URI carries **persistable** permission that survives a relaunch.

**Answer: yes on Android, no on iOS** — read out of the installed native sources, which is the
standard `sdk-57-api-notes.md` already sets. It is **not** the device relaunch test, which could not
be run here (see "Still to confirm on a device").

- **Android persists.** `FilePickerContract.parseResult`
  (`android/src/main/java/expo/modules/filesystem/FilePickerContract.kt:48`) calls
  `contentResolver.takePersistableUriPermission(uri, takeFlags)` on the URI returned from
  `ACTION_OPEN_DOCUMENT_TREE`, with `takeFlags` masked from the result intent's own read/write grant
  flags. That is exactly the platform mechanism for a grant that outlives the process and the reboot.
- **iOS does not.** `ios/FilePickingUtils.swift:44` calls only
  `startAccessingSecurityScopedResource()` and never writes a security-scoped **bookmark**, so access
  dies with the app session. The bundled type doc says so outright: *"On iOS, the selected directory
  grants temporary read and write access for the current app session only. After the app restarts,
  you must prompt the user again."*

iOS being session-only would reduce this feature to a one-shot export with extra steps on that
platform — but Kettle ships to Google Play only for now, so it does not change the design today. It
does mean **this feature is Android-only** and has to be built so an iOS build degrades rather than
lies. Recorded because it will be re-discovered as a bug otherwise.

### Three SAF traps found in the same pass

These are properties of `content://` URIs that the `file://` code in `src/storage/` has never had to
deal with, and each one silently does the wrong thing rather than failing loudly:

1. **`new File(directory, 'name.yaml')` does not work.** `Paths.join` treats the tree URI as a URL and
   appends to its path, producing `content://…/tree/primary%3ADocs/kettle-library.yaml` — not a
   document URI, which has to look like `…/tree/primary%3ADocs/document/primary%3ADocs%2Fname`. Child
   files must come from `directory.list()` or `directory.createFile(...)`, never from the constructor.
2. **`Directory.create()` throws on a `content://` URI**, by explicit check
   (`FileSystemDirectory.kt:55`), with a message pointing at `createDirectory` instead. So
   `ensureStorageReady`'s pattern cannot be reused here.
3. **`createFile` on a name that already exists makes a duplicate**, not an overwrite —
   `DocumentFile.createFile` delegates to the provider, which uniquifies (`kettle-library (1).yaml`).
   So each write is find-then-write: `list()`, match on `name`, and only `createFile` when there is no
   match. Without this a weekly user accumulates one file per session and the "backup" is 200 files.

### One honest limitation about *which* folders work

SAF writes land in whatever the picked `DocumentsProvider` allows. The realistic targets are
**on-device folders** — `Documents`, an SD card, or the local folder a Syncthing / Dropbox /
OneDrive / Nextcloud client keeps in sync. The Google Drive app's own provider is not a dependable
write target. The UI copy must therefore say "a folder on this device" and let the user point it at
whatever their sync client watches, rather than naming Drive.

### Still to confirm on a device

Flagged in the PR rather than assumed. None of these change the design; each changes a detail:

- The relaunch test itself — pick, kill, relaunch, write.
- Whether `createFile('kettle-library.yaml', 'application/x-yaml')` keeps that exact name, or whether
  the provider appends an extension of its own.
- What `directory.exists` returns after the user revokes the grant in system settings, which is the
  path the "folder unreachable" message depends on.
- **That the write truncates.** Back up a large library, delete half of it, back up again, and read
  `kettle-library.yaml` back — it must end where the new content ends, with no tail of the old one.
  This was a real bug (`File.write` opens SAF documents `"w"`, which does not truncate; see trap 4 in
  the API notes), caught in review and fixed with `FileMode.Truncate`. The unit test models the
  platform's behaviour rather than observing it, so this is the one that confirms the model was right.

## Design

### Where the folder lives

`backupFolderUri: string | null` in `Preferences`, persisted to `preferences.json` via
`preferences-store` alongside `themePreference` and `unitSystem`. It is app-owned state and must never
go into the hand-editable YAML library, which is a file users export and share.

In `preferencesSchema` it is `z.string().nullable().default(null)` — **defaulted, not required**, for
the reason that file already documents twice: a required key fails `safeParse` on every
`preferences.json` written before it existed, and `loadPreferences` answers a failed parse with
`null`, which would silently reset the user's unit and theme choices too.

### What gets written

The same two artefacts the existing exports already produce, from the same code paths:

| File | Content | Source |
| --- | --- | --- |
| `kettle-library.yaml` | the library, verbatim | the bytes of `storagePaths.libraryFile` |
| `kettle-history.yaml` | the whole log as one document | `serializeSessionArchiveYaml`, oldest-first, as `exportSessions` builds it |

Both names are prefixed because the destination is a folder of the user's own files, not an app
sandbox.

### The choke point

A new `src/storage/backup.ts`, following `writeSession`'s pattern exactly:

- One **non-throwing** `runBackup(sessions)`. Guarded by `isFileStorageSupported` first, so web is a
  no-op rather than a crash.
- Failures are recorded to module state and read-and-cleared by `takeBackupFailure()`, mirroring
  `takeWriteFailure()`.
- Called from `completeSession` in `session-history-store`, which is reached from the runner's
  `finishSession` — an event handler no error boundary covers. **A failed backup must never interrupt
  a workout**: the failure is stepped over and surfaced afterwards, never mid-set.
- Called again, directly, from the "Back up now" button in Settings, where the result *is* rendered
  immediately because the user just asked for it.

### Settings

The existing **"Backups and sync"** section is where this goes, and its current copy — which says the
files are local and export/import is the mechanism — is replaced. The section gains:

- A row naming the chosen folder, or inviting the user to choose one. Opens the system picker.
- A "Back up now" row, disabled with a reason when no folder is set.
- A row to forget the folder, so the choice is reversible without a reinstall.
- Copy stating what is written, when, and what it can and cannot restore.

### What the copy may not promise

`decisions.md` records that **the session log is export-only — nothing imports a session back**. A
backed-up `kettle-history.yaml` therefore preserves the data in a readable form but **cannot be
reloaded into a fresh install**. The library can be re-imported; the log cannot.

So no UI string may say "restore". Session-restore is named as a follow-up in the PR body, not built
here — it is a larger job, and it is not a launch blocker as long as the copy is truthful.

## Accessibility, i18n, tests

House rules, listed so they are not retrofitted: `accessibilityRole` and `accessibilityState` on every
new row, 44px targets via `minHeight`, every new string in **both** `en.json` and `pt.json`, and the
folder path treated as user data — rendered verbatim, never translated. Tests cover the non-throwing
choke point (no folder, unreachable folder, write refused) and the Settings rows, with the
translation assertion driven in `pt`.

## Also lands with it

- A `decisions.md` entry for why a user-picked SAF folder beat a cloud SDK or platform auto-backup.
- A write-up under `## Unreleased` in `CHANGELOG.md`.
- The SAF findings above folded into `sdk-57-api-notes.md`.
