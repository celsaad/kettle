# Research: an iOS file and time layer for `:shared`

> **Throwaway research note** for #402 (map #397, `docs/native-migration-plan.md` M8). Lives on the
> `research/ios-shared-file-layer` branch only and is never merged. Gathered 2026-11-13 against
> `master` at `ffdb231`. Every claim cites its source: the repo, the library source at the exact
> released tag, Maven Central's Gradle module metadata, Apple's developer documentation and man
> pages, or the Kotlin docs. Where a source was silent, this note says so.

## The answer in brief

- **Recommendation: a hand-written `expect`/`actual` file layer with no new dependency.** The JVM
  `actual` is today's `java.io` code, moved as it is, so the shipping Android write path doesn't
  change. The iOS `actual` uses `platform.posix` (`open`/`write`/`fsync`/`rename`) for the atomic
  write and `NSFileManager` for directories and listing. It is a small surface; the full shape is
  at the end of this note.
- **Neither library keeps the guarantee on iOS.** Both kotlinx-io and Okio do the rename with POSIX
  `rename(2)`, which is atomic. Neither one syncs a file on Apple targets: kotlinx-io's native file
  sink `flush()` is a no-op, and Okio's native `FileHandle.flush()` is `fflush`, not `fsync`. On the
  JVM, Okio's flush *does* call `fd.sync()`, so Okio would quietly give the two platforms different
  durability. Either library would still need an `actual` for `fsync`, and once you have that
  `actual`, the library adds nothing.
- **`NSData` atomic writes and `replaceItemAt` are not a better fit.** Apple documents the atomic
  write only as "an auxiliary file … then replace". It doesn't say where the auxiliary file goes,
  what it is called, or whether it is synced. Kettle's readers depend on the `.partial` name to skip
  a half-written file, and the runner's state file depends on it too, so an undocumented temporary
  name in a folder the user can see is a regression.
- **Dependencies:** Okio would cost **no new artifact**. snakeyaml-engine-kmp 5.0.0 already depends
  on `com.squareup.okio:okio:3.18.2`, and `okio-jvm:3.18.2` is already on `:app`'s release runtime
  classpath. kotlinx-io would be new (`kotlinx-io-core` + `kotlinx-io-bytestring` 0.9.2). Neither has
  a transitive dependency beyond the Kotlin stdlib, so neither phones home. The recommendation adds
  nothing either way.
- **Time:** after #358, `:shared`'s `commonMain` is already on `kotlin.time.Instant` and
  kotlinx-datetime 0.8. The **only** `java.time` left is in `jvmMain/storage/PreparedLibrary.kt`:
  the quarantine stamp formatter and `prepareLibrary`'s `now: () -> java.time.Instant`. Both port to
  common code with what already exists (`kotlin.time.Clock`, and `isoInstant` in `Tip.kt`). iOS
  needs no time `actual`.
- **Where the files live:** the same tree that Android keeps under `filesDir`, rooted at
  `Documents/exercise-tracker/` on iOS. That is where RN's `expo-file-system` `Paths.document` put it,
  and it is what the shipped `settings.filesInFilesApp` copy already promises in all three bundles.
  The runner's state files (`noBackupFilesDir/runner` on Android) go to
  `Library/Application Support/runner`, with `isExcludedFromBackup` set on that directory. No format
  change and no change to the layout under the root.
- **#368's test-only `actual` can grow into the production one** if it is written as part of this
  surface: `readTextOrNull` plus `listFileNames` over `String` paths, with `platform.posix` or
  `NSFileManager` underneath. It must not be written over Okio or JUnit. It also has costs #368
  doesn't name; see "#368" below.

## What is JVM-only today

Grep of `native/android/shared/src/{commonMain,jvmMain}` at `ffdb231`:

| File (`jvmMain`) | JVM API | What it does |
|---|---|---|
| `storage/StoredFileWrites.kt` | `java.io.File`, `FileOutputStream`, `IOException`, `java.nio.channels.FileChannel`, `StandardOpenOption` | `writeAtomically`, `writeSessionFile`, `writeLibraryFile` |
| `storage/StoredFileReads.kt` | `java.io.File`, `IOException` | `readSessionLog`, `readLibrary`, `readPreferences` |
| `storage/PreparedLibrary.kt` | `java.io.File`, `IOException`, `java.time.Instant`, `ZoneOffset`, `DateTimeFormatter` | `prepareLibrary` (seed, repair, quarantine, reseed) |
| `storage/Backup.kt` | `java.io.File`, `IOException` | `backUpNow` reads `exercises.yaml` and reads and writes `backup-targets.json` (`BackupFolder` is already an interface) |
| `domain/Normalization.jvm.kt` | `java.text.Normalizer` | the `actual` for `String.toNfd()`/`toNfc()`, already `expect`ed in common |

`commonMain` has no `java.*` import. Its time imports are `kotlin.time.Instant` and `kotlinx.datetime.*`
(`HistoryStats`, `HistoryViews`, `NextUp`, `RestDayReminder`, `ExerciseProgress`, …). It has no
`Clock.System` call: every clock is passed in by `:app`.

**`StoredFiles` itself is not in `:shared`.** It is an interface in
`app/src/main/java/com/casco/kettle/shell/files/LatestFiles.kt`, together with `StoragePaths`
(`filesDir/exercise-tracker/{sessions/, exercises.yaml, preferences.json, supporter.json,
backup-targets.json}`) and `DiskFiles`, which calls the `:shared` storage functions above. Screen
tests fake the interface (`FakeStoredFiles`). `:app` also uses `java.io.File` directly and will need
the same layer once the screens and runner go multiplatform:
- `runner/RunnerStateFile.kt` (writes through `writeAtomically`, `delete`)
- `runner/LaunchRecovery.kt` (`listFiles`, `readText`, `delete`, `writeSessionFile`)
- `support/SupporterFile.kt`
- `theme/PreferredScheme.kt`, `theme/SystemBars.kt` (read `preferences.json`)
- `export/FileShare.kt` (`cacheDir/exports`)

### How the atomic write works today

`writeAtomically(file, text)` in `StoredFileWrites.kt`:

1. `mkdirs` the parent.
2. Writes `<name>.partial` with a `FileOutputStream`, then `out.fd.sync()`.
3. `partial.renameTo(file)`, throwing `IOException(file.name)` on `false`.
4. Deletes the partial if anything before the rename failed.
5. Best effort (#333): `FileChannel.open(dir, READ).force(true)` on the parent directory, so the
   rename survives a power cut. Its failures are swallowed.

The `.partial` suffix is load-bearing. `readSessionLog` lists only names ending `.yaml`, and the
runner's state files end `.json`, which `RunnerStateFile.kt` notes is chosen so that a reader never
takes `writeAtomically`'s temporary for a state file.

## The options for iOS, against that protocol

### kotlinx-io 0.9.2

- Common API: `SystemFileSystem.exists`, `delete`, `createDirectories`, `atomicMove`, `source`, `sink`,
  `metadataOrNull`, `list` (`core/common/src/files/FileSystem.kt`, tag `0.9.2`).
- Apple `atomicMove` is `rename(source, destination)` (`core/apple/src/files/FileSystemApple.kt`).
- **No way to sync.** The native file sink's `flush()` is `= Unit` (`core/native/src/files/PathsNative.kt`
  line 148), and the JVM sink is a bare `FileOutputStream(...).asSink()` (`core/jvm/src/files/FileSystemJvm.kt`).
  There is no `FileHandle` and no `fsync` anywhere in the API. Steps 2 and 5 would need `actual`s anyway,
  and porting Android onto it would drop the `fd.sync()` that #333 relies on.
- A new dependency: `kotlinx-io-core` → `kotlinx-io-bytestring` → `kotlin-stdlib` only (Gradle module
  metadata, `kotlinx-io-core-iosarm64-0.9.2.module`). Nothing that transmits. Still pre-1.0.

### Okio 3.18.2

- `FileSystem.SYSTEM` exists on Apple targets (`okio/src/systemFileSystemMain`) and on the JVM.
- Apple `atomicMove` is `rename()` (`okio/src/unixMain/kotlin/okio/UnixPosixVariant.kt`, `variantMove`).
  JVM's is `File.renameTo` (`JvmSystemFileSystem.kt`) or `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`
  (`NioSystemFileSystem.kt`).
- **Durability differs by platform.** `FileHandle.flush()` is documented only as "Pushes all buffered
  bytes to their final destination" (`commonMain/okio/FileHandle.kt`). On the JVM it is
  `randomAccessFile.fd.sync()` (`JvmFileHandle.kt` line 74). On Apple it is
  `fflush(file)` (`UnixFileHandle.kt` line 82), which empties the stdio buffer into the kernel and is
  not `fsync`. Okio exposes no file descriptor, so iOS cannot add the sync itself. Okio gives no way
  to sync a directory on either platform. Okio would therefore leave the same two `actual`s to write
  as kotlinx-io.
- **Costs no new artifact.** `snakeyaml-engine-kmp-5.0.0.module` (and its `-iosarm64` variant)
  depends on `com.squareup.okio:okio:3.18.2` and `net.thauvin.erik.urlencoder:urlencoder-lib:1.6.0`,
  and `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` already lists
  `okio-jvm:3.18.2`. Okio's own metadata depends only on `kotlin-stdlib`. It is on `:shared`'s
  runtime classpath but not its compile classpath (snakeyaml declares it `implementation`), so using
  it would mean declaring it in the catalog at snakeyaml's version and moving it whenever snakeyaml
  does.

### Foundation directly: `NSData` atomic write, `NSFileManager.replaceItemAt`

- `NSData.WritingOptions.atomic`: "write data to an auxiliary file first and then replace the original
  file with the auxiliary file when the write completes"; equivalent to `atomically: true` (Apple
  docs). The page says nothing about where the auxiliary file goes, what it is named, or whether it
  is synced. Foundation on Darwin is closed source, so this note can't check.
- `FileManager.replaceItemAt(_:withItemAt:backupItemName:options:)` "replaces the contents of the item
  … in a manner that ensures no data loss occurs". It preserves the original's creation date and
  permissions, and works only on the same volume, using `itemReplacementDirectory` for a temporary
  location (Apple docs). It needs the new item written and synced first, so it can only replace the
  `rename` step, and `rename(2)` already gives that step's guarantee.
- For the Documents folder specifically, an undocumented temporary name is a visible risk: that
  folder is shown in the Files app (below), and if the name ever ends in `.yaml`, a crash could leave
  a file that the session log reads.

### POSIX through Kotlin/Native's platform libraries (recommended for iOS)

- `platform.posix` is a platform library "for all UNIX- and Windows-based targets, including Android
  and iOS". Platform libraries are "available by default" and need no extra dependency (Kotlin docs,
  *Platform libraries*). `platform.Foundation` is available the same way.
- `rename(2)` on iOS: "guarantees that an instance of new will always exist, even if the system should
  crash in the middle of the operation". It fails with `EXDEV` across file systems, which can't
  happen here because `.partial` is a sibling (Apple iOS man page).
- `fsync(2)` on iOS moves the data "to a permanent storage device", but the drive may write it late
  and out of order. "For applications that require tighter guarantees … F_FULLFSYNC fcntl" (Apple iOS
  man page). Plain `fsync` is what Android's `fd.sync()` gives, so it is the parity choice.
  `F_FULLFSYNC` is stronger and slower, and can be tried in the same `actual` without changing the
  surface.
- This reproduces steps 1–5 exactly, `.partial` name included:
  `open(O_WRONLY|O_CREAT|O_TRUNC)` → `write` loop → `fsync` → `close` → `rename`; then
  `open(dir, O_RDONLY)` → `fsync` → `close` as the best-effort directory sync, with failures
  swallowed as on the JVM. Listing, `mkdirs`, `exists` and `delete` are simplest through
  `NSFileManager` (`contentsOfDirectoryAtPath`, `createDirectoryAtPath:withIntermediateDirectories:`,
  `fileExistsAtPath`, `removeItemAtPath`) or through POSIX `opendir`/`mkdir`/`unlink`.
- Data protection: app files default to Class C, "Protected Until First User Authentication" (Apple
  Platform Security guide). Its key stays available after the device locks, so the runner can write
  a session and its state file with the screen locked, provided the phone was unlocked once since
  boot. Don't raise the protection class on these files.

## Time: what remains after #358

- Kotlin 2.3.0 made `kotlin.time.Clock` and `kotlin.time.Instant` stable (Kotlin docs, *What's new in
  2.3.0*). This repo is on Kotlin 2.4.21. kotlinx-datetime 0.7+ uses those stdlib types, and 0.8.x is
  the last line with `-0.6.x-compat` artifacts (kotlinx-datetime README).
- `libs.versions.toml` on `master`: `kotlinxDatetime = "0.8.0"`, `snakeyamlEngineKmp = "5.0.0"`,
  `kotlinxSerialization = "1.12.0"`, `kotlin = "2.4.21"`. Maven Central serves `-iosarm64` and
  `-iossimulatorarm64` artifacts for all three at exactly those versions. kotlinx-datetime's iOS
  artifact depends on `kotlinx-serialization-core` and the stdlib only.
- **What is left is two lines in `PreparedLibrary.kt`.**
  - `now: () -> java.time.Instant = Instant::now` becomes `now: () -> kotlin.time.Instant =
    Clock.System::now`. Its only production caller (`LibraryGate.kt`) uses the default, and
    `PrepareLibraryTest` passes a fixed instant.
  - The `STAMP` formatter, `yyyy-MM-dd'T'HH-mm-ss-SSS'Z'` in UTC, becomes
    `isoInstant(at.toEpochMilliseconds()).replace(':', '-').replace('.', '-')`. `isoInstant`
    (`commonMain/domain/Tip.kt`) is already `Date.toISOString` with milliseconds, so the result is the
    same string for any four-digit year and the quarantine name doesn't change.
    kotlinx-datetime's `byUnicodePattern` would also do it, but needs an opt-in for a non-constant
    pattern, and none is needed here.
- iOS needs nothing of its own for time. kotlinx-datetime reads the system's zone data by default
  (README), and `:app`'s callers already supply the clock.

## Where the files live on iOS

| Android (`StoragePaths`, `runnerStateDir`) | iOS | Why |
|---|---|---|
| `filesDir/exercise-tracker/` | `Documents/exercise-tracker/` | RN's `src/storage/paths.ts` (deleted in #309) rooted it at `new Directory(Paths.document, 'exercise-tracker')`, which is iOS's `Documents`. The shipped `settings.filesInFilesApp` string in all three bundles names "a folder called exercise-tracker" in the Files app. `Documents/` is "user-generated content … can be made available to the user through file sharing … backed up by iTunes and iCloud" (Apple, *File System Programming Guide*), and `UIFileSharingEnabled` needs the files to be in `Documents` (Apple Info.plist reference). |
| `…/sessions/<id>.yaml`, `exercises.yaml`, `preferences.json`, `exercises.invalid-<stamp>.yaml` | same names, same tree | No format or layout change. `supporter.json` and `backup-targets.json` are never created on iOS, because the tip jar and the SAF backup are out of the map's scope. |
| `noBackupFilesDir/runner/<id>.json` | `Library/Application Support/runner/<id>.json`, with `isExcludedFromBackup` set on `runner/` | Application Support holds "app data files … not user documents" and is backed up by default. Apple recommends excluding a group "by moving those files into a directory and updating the directory's `isExcludedFromBackup`", since "certain file operations can reset resource values" on a file. A rename-over is one of those operations, so the flag goes on the directory, not on each file. Apple calls the flag guidance and not a guarantee, so a restored device *may* still carry a state file, and `LaunchRecovery` already copes with one whose session file is missing. `Caches`/`tmp` would be wrong because the system purges them. |
| `cacheDir/exports/` (`FileShare.kt`) | `tmp/` or `Library/Caches/` | Purgeable, not backed up (Apple). An `:app`-shell concern; it isn't `:shared`'s. |

So the common code takes a **root directory** from the platform (Android: `filesDir`; iOS: the
`NSDocumentDirectory` URL's path) and builds everything under it exactly as `StoragePaths` does now.
The paths are the platform's to supply; the layout under them is common.

Two consequences of `Documents` being user-visible, neither a layout change:
- A `.partial` left by a crash shows in the Files app until the next write of that file replaces it.
  Readers already ignore it. Sweeping stale `*.partial` at launch is optional polish.
- With `LSSupportsOpeningDocumentsInPlace`, another app can edit `exercises.yaml` while Kettle is
  running. The rename keeps every read whole, either the old file or the new one, and `prepareLibrary`
  repairs at the next launch. `NSFileCoordinator` is the Apple mechanism for coordinating such
  writers (Apple docs). The docs also warn that a registered file presenter "may be terminated" in the
  background. **Not coordinating for TestFlight** keeps the protocol identical to Android's; it is a
  decision to name in the M8 file-layer ticket, not one this note makes.

## #368's test-only `actual`

- Every `:shared` test, the conformance runner included, is in `jvmTest`. `ConformanceTest.kt` uses
  JUnit Jupiter `@TestFactory`/`DynamicTest`, `Assumptions.abort` (how an unported suite reports as
  *skipped* by name), and `java.io.File`. To run on `iosSimulatorArm64`, that runner has to move to
  `commonTest` on `kotlin.test`, which has no dynamic tests and no assumptions. "Zero skipped" then
  has to be asserted (fail on a suite with no runner) rather than *reported* per case. #368 doesn't
  name this cost.
- Reading the corpus needs exactly two of the production primitives: read a file's text and list a
  directory. The simulator process sees the Mac's file system, and `simctl` passes environment
  variables prefixed `SIMCTL_CHILD_` to the child (`xcrun simctl help spawn`). That is how the corpus
  path reaches an iOS test in place of the JVM's `-Dkettle.conformanceDir`.
- **It can become the production layer** if #368 writes those two primitives as `internal expect`
  functions over `String` paths in a common source set, with the iOS `actual` over
  `platform.posix`/`NSFileManager`. Production then adds `writeAtomically`, `fileExists`,
  `deleteFile` and `createDirectories` to the same file. Written over Okio, or kept as an
  `iosTest`-only helper, it would be thrown away.
- Separately, #368's `jvmMain` stubs: the storage files are `jvmMain`-only today, so compiling
  `commonMain` for iOS needs **only** the `Normalization` `actual`. On iOS that is
  `NSString.decomposedStringWithCanonicalMapping`/`precomposedStringWithCanonicalMapping` ("normalizing
  … using the Unicode Normalization Form D/C", Apple docs). No storage stub is needed.

## Recommendation and the `expect`/`actual` surface

Move `StoredFileReads.kt`, `StoredFileWrites.kt`, `PreparedLibrary.kt` and `Backup.kt` from
`jvmMain` to `commonMain`. Their logic is already platform-free except for the calls below. Put the
platform behind one small file of `expect`s in `com.casco.kettle.storage`:

```kotlin
// commonMain — paths are plain strings; the layout under a root is common code.

/** JVM: `typealias` to java.io.IOException, so every `catch (IOException)` in :app keeps working. */
expect open class IOException(message: String?) : Exception

/** The file's text, or null where it is missing or can't be read (today's `readTextOrNull`). */
internal expect fun readTextOrNull(path: String): String?

/** Names of the regular files in [dir]; empty where [dir] doesn't exist. Unsorted: callers sort. */
internal expect fun listFileNames(dir: String): List<String>

internal expect fun fileExists(path: String): Boolean

/** True where the file is gone afterwards, an absent file included. */
internal expect fun deleteFile(path: String): Boolean

internal expect fun createDirectories(dir: String)

/**
 * [text] (UTF-8) into `<path>.partial`, synced, renamed over [path]; the partial removed on failure;
 * then the parent directory synced, best effort. Throws [IOException] when the disk refuses.
 */
expect fun writeAtomically(path: String, text: String)
```

- **`jvmMain` `actual`s** are the current bodies: `File.readText`, `listFiles`, `File.delete`, `mkdirs`,
  and `writeAtomically` with its `beforeRename`/`syncDirectory` test seams, which stay `internal` in
  `jvmMain`. The `SecurityException` catches stay on the JVM side. This is a move, not a rewrite, and
  `jvmTest`'s storage tests keep testing the code Android ships.
- **`iosMain` `actual`s**: `platform.posix` for `writeAtomically` (`open`/`write`/`fsync`/`close`/`rename`,
  then `fsync` of the directory fd, both best effort past the rename), `NSFileManager` or POSIX for the
  rest, and `IOException` as a plain `open class`. File protection stays at the default.
- **Platform roots, not in `:shared`'s `expect`s:** the shell hands common code a root
  (`filesDir` / `NSDocumentDirectory`) and a no-backup directory (`noBackupFilesDir` /
  `Application Support` with `isExcludedFromBackup`). `StoragePaths` becomes common code over the
  root, so the layout is written once.
- **Signatures:** the public storage functions take `String` paths in common code.
  `PreparedLibrary.Reseeded.quarantined` becomes a `String?` (its one reader, `LibraryGate.kt`, uses
  `.name`). `prepareLibrary`'s `now` becomes `() -> kotlin.time.Instant` with `Clock.System::now`. To
  keep `:app` untouched in that PR, add `jvmMain` overloads taking `File`; or change its few call
  sites (`LatestFiles.kt`, `LibraryGate.kt`, `SafBackupFolder.kt`, `RunnerStateFile.kt`,
  `SupporterFile.kt`, `FileShare.kt`, `LaunchRecovery.kt`) to pass `.path`.
- **`StoredFiles` stays an interface** and moves to common code with the screens in Phase 1, so
  `FakeStoredFiles` keeps working. Nothing here changes what it promises.
- **No dependency is added.** If a later need (streaming a large export, say) argues for a library,
  Okio is the one to pick: it is already on the classpath. Its `fsync` gap on Apple still means
  `writeAtomically` stays hand-written.

## Against the map and #368

- **The map is consistent with this note.** Nothing in #397's decisions is contradicted. The
  migration plan's "After M7's release, one PR adds `iosArm64`…" is already overtaken by the map's
  decision that #368 waits only on #361, and #361 is closed. That is for the map's plan amendment,
  not this ticket.
- **#368's acceptance understates its work.** "Runs every conformance suite with zero skipped" on
  the simulator requires porting `ConformanceTest` (and `LibraryJson`/`StepsJson`/`GoldenWriter`) from
  JUnit Jupiter's dynamic tests to `kotlin.test` in `commonTest`, and passing the corpus path through
  `SIMCTL_CHILD_`. It isn't only an added `actual`. Its "`jvmMain` stubs only where compiling needs
  them" comes down to the `Normalization` `actual` alone.
- **#368's "test-only `actual`" should be scoped as the first slice of this surface**
  (`readTextOrNull`, `listFileNames` over `String`), not as an `iosTest` helper. That way M8 adds to
  it instead of replacing it.

## Sources

- Repo at `ffdb231`: `native/android/shared/src/jvmMain/kotlin/com/casco/kettle/storage/*.kt`,
  `…/domain/Normalization.jvm.kt`, `…/commonMain/…/domain/Tip.kt`,
  `native/android/app/src/main/java/com/casco/kettle/shell/files/LatestFiles.kt`,
  `…/runner/RunnerStateFile.kt`, `…/runner/LaunchRecovery.kt`, `native/android/gradle/libs.versions.toml`,
  `native/android/shared/build.gradle.kts`, `…/jvmTest/…/conformance/ConformanceTest.kt`,
  `src/i18n/locales/{en,pt,ja}.json` (`settings.filesInFilesApp`), `git show 34ba7a5^:src/storage/paths.ts`;
  `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
- Okio source, tag `parent-3.18.2` (github.com/square/okio): `okio/src/unixMain/kotlin/okio/UnixFileHandle.kt`,
  `UnixPosixVariant.kt`, `okio/src/jvmMain/kotlin/okio/JvmFileHandle.kt`, `JvmSystemFileSystem.kt`,
  `NioSystemFileSystem.kt`, `okio/src/commonMain/kotlin/okio/FileHandle.kt`.
- kotlinx-io source, tag `0.9.2` (github.com/Kotlin/kotlinx-io): `core/common/src/files/FileSystem.kt`,
  `core/native/src/files/PathsNative.kt`, `core/native/src/files/FileSystemNative.kt`,
  `core/apple/src/files/FileSystemApple.kt`, `core/jvm/src/files/FileSystemJvm.kt`.
- Maven Central Gradle module metadata: `it.krzeminski:snakeyaml-engine-kmp{,-iosarm64}:5.0.0`,
  `com.squareup.okio:okio{,-iosarm64}:3.18.2`, `org.jetbrains.kotlinx:kotlinx-io-core{,-iosarm64}:0.9.2`,
  `org.jetbrains.kotlinx:kotlinx-datetime-iosarm64:0.8.0`, `net.thauvin.erik.urlencoder:urlencoder-lib:1.6.0`;
  POM presence of the `-iosarm64`/`-iossimulatorarm64` artifacts for datetime 0.8.0, snakeyaml-engine-kmp
  5.0.0 and serialization-json 1.12.0.
- Apple: `NSData.WritingOptions.atomic`; `FileManager.replaceItemAt(_:withItemAt:backupItemName:options:)`;
  `FileManager.url(for:in:appropriateFor:create:)` and `itemReplacementDirectory`;
  `URLResourceKey.isExcludedFromBackupKey`; *Optimizing your app's data for iCloud backup*;
  `NSFileCoordinator`; `FileProtectionType.completeUntilFirstUserAuthentication`;
  `NSString.decomposedStringWithCanonicalMapping`; Info.plist `UIFileSharingEnabled`,
  `LSSupportsOpeningDocumentsInPlace`; *File System Programming Guide — File System Basics*; iOS man
  pages `rename(2)`, `fsync(2)`; Apple Platform Security, *Data Protection classes*.
- Kotlin: *What's new in Kotlin 2.3.0* (stable `kotlin.time.Clock`/`Instant`); *Platform libraries*
  (Kotlin/Native `platform.posix`); kotlinx-datetime `README.md`.
- `xcrun simctl help spawn` (Xcode 27.2), for `SIMCTL_CHILD_`.
