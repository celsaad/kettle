# SDK 57 API notes

> **Reference, verified against `node_modules`, not against published docs.** Read this before using
> `expo-file-system`, `expo-notifications` or `expo-iap` — for two of the three, the docs site and
> most training data describe an older API that will typecheck-fail or crash at import. The general
> rule is in `AGENTS.md`: read the versioned docs at https://docs.expo.dev/versions/v57.0.0/ rather
> than recalling older Expo. This file is what that rule turned up the first time it was applied.
>
> Everything below was confirmed from the installed `.d.ts` files. If a version bumps, re-confirm the
> same way rather than trusting this page.

**Installed** (all via `pnpm expo install`, so versions are SDK-57-pinned): `js-yaml`, `zod`,
`zustand`, `expo-file-system`, `expo-keep-awake`, `expo-haptics`, `expo-notifications`,
`expo-sharing`. No `expo-document-picker` — the new `expo-file-system` API exposes
`File.pickFileAsync()` directly, so a separate picker dependency wasn't needed.

- `expo-notifications` and `expo-sharing` are registered in `app.json` `plugins` (the latter was
  auto-added by `expo install`).
- ✅ **API-version risk resolved:** SDK 57's `expo-file-system` is the class-based `File` / `Directory`
  / `Paths` API (confirmed from `node_modules/expo-file-system/build/*.d.ts`), not the legacy
  `readAsStringAsync`/`documentDirectory` functions. Key shapes used in `src/storage/`:
  `Paths.document`, `new Directory(...)` / `new File(...)`, sync `.exists`, sync `.create(options)`,
  async `.text()`, sync `.write(content)`, sync `.list()` (filter results with `instanceof File`).
- ✅ **expo-notifications API confirmed** from `node_modules/expo-notifications/build/*.d.ts`:
  `scheduleNotificationAsync({ content, trigger: { type: SchedulableTriggerInputTypes.TIME_INTERVAL,
  seconds, repeats } })`, `cancelScheduledNotificationAsync(id)`, `setNotificationHandler(...)`,
  `requestPermissionsAsync()`.
- ✅ **expo-iap API confirmed** from `node_modules/expo-iap/build/*.d.ts` (installed at 4.7.2 — the
  published docs site still shows 3.4 examples, and the `hyochan/expo-iap` repo is archived in favour
  of the OpenIAP monorepo, so the types are the only trustworthy source). Shapes used in
  `src/app/support.tsx`: `useIAP({ onPurchaseSuccess, onPurchaseError, onError })` returning
  `{ connected, products, fetchProducts, requestPurchase }`; `fetchProducts({ skus, type: 'in-app' })`;
  `requestPurchase({ request: { google: { skus } }, type: 'in-app' })` (`android` is deprecated in
  favour of `google`); the **module-level** `finishTransaction({ purchase, isConsumable })`, used
  instead of the hook's so the success handler doesn't close over a binding the hook hasn't produced
  yet; `ErrorCode.UserCancelled` (kebab-case values — the `E_`-prefixed codes are long gone);
  `Product.displayPrice` (the store's localized string; `price` is `number | null` on Android, hence
  never used for ordering) and `Purchase.purchaseState: 'pending' | 'purchased' | 'unknown'`.
- ✅ **`Directory.pickDirectoryAsync()` keeps its grant on Android and loses it on iOS.** This is the
  question the backup folder rests on, and the two platforms answer it differently. Read out of the
  installed native sources rather than from a device — **the pick / kill / relaunch test has not been
  run**, so treat the Android half as very strongly evidenced rather than as observed.

  - **Android persists.** `FilePickerContract.parseResult` calls
    `contentResolver.takePersistableUriPermission(uri, takeFlags)` on the `ACTION_OPEN_DOCUMENT_TREE`
    result, with `takeFlags` masked out of the result intent's own read/write grant. That is the
    platform mechanism for a grant that outlives the process and the reboot, not an approximation of
    one.
  - **iOS does not.** `ios/FilePickingUtils.swift` calls only `startAccessingSecurityScopedResource()`
    and never writes a security-scoped **bookmark**, so access dies with the app session. The bundled
    type doc says as much outright. This is why `isBackupFolderSupported` in `storage/backup.ts` is
    Android-only: a folder chosen on iOS would look set and quietly stop being written to.

- ⚠️ **A SAF `content://` URI is not a `file://` URI, and four of the differences fail silently.**
  Everything in `src/storage/` except `backup.ts` deals in `file://` and never meets these:

  1. **`new File(directory, 'name.yaml')` does not address a child.** `Paths.join` treats the tree URI
     as a URL and appends to its path, producing `…/tree/primary%3ADocs/name.yaml` — a document URI
     has to look like `…/tree/primary%3ADocs/document/primary%3ADocs%2Fname.yaml`. Children come from
     `directory.list()` or `directory.createFile(...)`, never from the constructor.
  2. **`Directory.create()` throws on a `content://` URI** by explicit check in
     `FileSystemDirectory.kt`, pointing at `createDirectory` instead. `ensureStorageReady`'s pattern
     doesn't transfer.
  3. **`createFile` on an existing name makes a duplicate, not an overwrite** — it goes through
     `DocumentsContract.createDocument`, which uniquifies (`kettle-library (1).yaml`). Every write has
     to be find-then-write against `list()`. `backup.test.ts` pins this one, because unguarded it
     turns a backup folder into one file per session.
  4. **`File.write()` does not truncate**, so overwriting with something shorter leaves the tail of
     the old content behind. `FileSystemFile.write` calls `outputStream(append = false)`, which for
     SAF is `contentResolver.openOutputStream(uri, "w")` — and `"w"` overwrites from offset zero
     without wiping. `FileMode` in the same package is where this is visible: `WRITE("w")` is
     "Write-only", `TRUNCATE("wt")` is "Write-only. **Wipes file contents before writing**". The
     `file://` half of the API is unaffected, because `JavaFile.outputStream(false)` is
     `FileOutputStream(file, false)`, which truncates the way everyone expects — which is exactly why
     nothing else in `src/storage/` has ever met this.

     The fix is `file.open(FileMode.Truncate)` + `handle.writeBytes(...)`;
     `FileSystemFileHandle.forContentURI` accepts `TRUNCATE` and maps it straight to
     `openFileDescriptor(uri, "wt")`. Delete-and-recreate also works and is worse — it opens a window
     with no file at all. `TextEncoder` is safe to encode with: it is a Hermes built-in (Expo's winter
     runtime polyfills only `TextDecoder`, and says so in a comment).

     Found in review, not on a device, and it is the most expensive of the four: it corrupts the one
     artefact the backup feature promises can be re-imported, silently, and only when the new content
     is *shorter* than the old — so a growing library never shows it.

  `File.name` does resolve correctly for these: `Paths.basename` decodes the pathname first, so a
  document URI ending `…%2Fkettle-library.yaml` answers `kettle-library.yaml`. That holds for
  providers whose document ids are paths (on-device storage, which is what this targets) and would not
  for one using opaque ids.

- ⚠️ **Web is not a persistence target.** `expo-file-system` has no web implementation. The storage
  layer detects this (`isFileStorageSupported` in `paths.ts`) and degrades gracefully: on web,
  `loadLibrary()` returns the seed library in-memory (no persistence, matching the old mock-data web
  experience) and `listSessions()`/all writes are no-ops instead of throwing. This was a real bug
  caught mid-implementation — `src/storage/paths.ts` originally constructed `Directory`/`File`
  instances at module-import time, which crashed `expo export --platform web` (and would have crashed
  the web runtime too); fixed by making path resolution lazy.


- ⚠️ **`Clipboard.getStringAsync()` resolves `''` for a denied paste as well as an empty clipboard,
  and the two cannot be told apart.** Confirmed from
  `node_modules/expo-clipboard/build/Clipboard.d.ts`, which says so outright: on iOS 16+ a paste is
  behind a system permission prompt, and a denial returns an empty string, with "no way to
  distinguish between an empty clipboard and denied permission". So the natural reading of that
  return — "the clipboard is empty" — is wrong on the platform where a user is most likely to hit it,
  and would tell someone their clipboard is empty while their YAML sits in it.

  Import's "paste from clipboard" note is worded as a disjunction for exactly this reason
  (`import.clipboardEmpty` names both possibilities). Anything that tightens it back to a single
  confident claim is reintroducing the bug. The **web** read half is different and does throw when
  denied, which is why `pasteFromClipboard`'s `catch` is the one branch that can still say
  "couldn't read" outright.

- ⚠️ **`Share.share()` rejects on cancel on web, but resolves on cancel on native.** React Native's
  `Share` reports dismissal as `{ action: 'dismissedAction' }`; `react-native-web`'s forwards
  straight to `window.navigator.share`
  (`node_modules/react-native-web/dist/exports/Share/index.js`), which rejects with an `AbortError`
  when the user backs out. So a `catch` that treats every rejection as a failure reports one for the
  ordinary cancel path on mobile web. Import filters `AbortError` for that reason. The same file is
  why sharing is feature-detected rather than attempted: with no `navigator.share`, it rejects
  outright with "Share is not supported in this browser" rather than degrading.
