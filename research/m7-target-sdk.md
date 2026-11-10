# targetSdk 36 → 37 for M7: what it changes for Kettle

> Research for #338 (M7 map #335), written 2026-11-09 against `feat/native-routing` at `34ba7a5`.
> Not a plan: a list of facts with a source per claim, and a verdict per item. Nothing was built.

## Answer

- **Kettle targets 36 today** and the latest stable level is **37 (Android 17)**. The move is small
  for Kettle: **no change to app code is required**. One toolchain change is: **compileSdk 37 needs
  AGP 9.1.1 or later**, and Robolectric 4.17 or later, because the unit tests run at the targetSdk. So
  the targetSdk PR can't land ahead of the AGP 9 unpin.
- **Two device checks**, both on the runner. Background audio hardening (cues while the screen is
  off), and large-screen orientation, which is already in effect at 36 and gets no opt-out at 37.
- **Play doesn't require 37 yet.** The current rule is 36 for new apps and updates from
  2026-08-31, and Kettle already meets it.
- **`lint.xml`'s `OldTargetApi` exception** exists only because of the RN pin. M7 can delete it, but
  it will come back on CI as soon as runners install API 38. That's a decision, set out below.

## Where today's level comes from

| Fact | Source |
|---|---|
| The shell has `compileSdk = 36`, `targetSdk = 36` and `minSdk = 24`, all hardcoded. | `native/android/app/build.gradle.kts:47,54-55` |
| `:benchmark` has `compileSdk = 36` and `targetSdk = 36` (`minSdk = 29`). It has to move with the app. | `native/android/benchmark/build.gradle.kts:10-15` |
| The RN app's level comes from React Native's version catalog: RN 0.86.3 has `minSdk = "24"`, `targetSdk = "36"` and `compileSdk = "36"`. Expo's autolinking plugin reads that catalog, and falls back to 35 when it has no value. | `node_modules/react-native/gradle/libs.versions.toml:3-5`; `node_modules/expo-modules-autolinking/.../ExpoRootProjectPlugin.kt:54-55` (Expo 57.0.22) |
| The toolchain is AGP `8.12.0`, Gradle `9.3.1`, Robolectric `4.16.1` and Kotlin `2.1.20`. | `native/android/gradle/libs.versions.toml:4,5,26`; `gradle/wrapper/gradle-wrapper.properties` |

So the shell's 36 is a copy of RN's, kept equal on purpose (commit `00e8d54`). Once the AAR is
gone, nothing ties the shell to it any more.

## Latest stable level and Play's deadline

- **Android 17, API 37, is stable.** Google announced it on 2026-06-16 ("Android 17 is here")
  ([blog](https://android-developers.googleblog.com/2026/06/Android-17.html)). Platform stability
  came with Beta 3, and Beta 4 was the last scheduled beta
  ([release notes](https://developer.android.com/about/versions/17/release-notes)). 37.1 and 37.2 are
  QPR minor SDKs on [the Android 17 page](https://developer.android.com/about/versions/17).
  `targetSdk` is an integer, so 37 is the value to set. No Android 18 preview is listed there.
- **Play** ([Play Console Help 11926878](https://support.google.com/googleplay/android-developer/answer/11926878),
  [target-sdk](https://developer.android.com/google/play/requirements/target-sdk)): "Starting August
  31 2026: New apps and app updates must target Android 16 (API level 36) or higher". Existing apps
  must target 35 or higher to stay available to new users on newer OS versions. Extensions run to
  2026-11-01. **Neither page mentions API 37 or a 2027 date.** Going by the yearly pattern, 37 would
  be due around August 2027, but that is an inference and not yet policy. At 36, Kettle already meets
  today's rule. Moving to 37 is the map's choice, not a requirement.

## Toolchain prerequisites (not behaviour changes, but they gate the PR)

| Item | Source | Verdict |
|---|---|---|
| **API 37.0 needs at least AGP 9.1.1** (and Android Studio Panda 3 Patch 1). Kettle is on AGP 8.12.0. AGP 9.1 needs Gradle at least 9.3.1, which the wrapper already has. | [About AGP: "Minimum versions of tools for Android API level"](https://developer.android.com/build/releases/about-agp) | **Code change.** compileSdk 37 lands with or after the AGP 9 unpin. For the KMP `:shared` module, the AGP 9 migration has its own JetBrains guide (linked from the same page). |
| **Robolectric 4.16 supports SDK 36 and goes no higher. 4.17 (10 Sep) "supports SDK 37".** Kettle's Robolectric tests run at the targetSdk (the comment at `app/build.gradle.kts:333`). | [Robolectric releases](https://github.com/robolectric/robolectric/releases), [4.17](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17) | **Code change.** Bump to 4.17 or later in the same PR, or the `:app` tests can't run at 37. 4.17 also fixes "dialog layout issues on SDK 37+". |
| The `:benchmark` module pins 36 as well. | `benchmark/build.gradle.kts` | **Code change.** Bump it alongside the app. |

## Behaviour changes, item by item

There are two kinds below. "Targeting 37" means changes gated on targetSdk, which the bump switches
on. "All apps on 17" means changes that already hit Kettle on an Android 17 phone at targetSdk 36.
The second kind isn't caused by the bump, but where it touches Kettle it is listed, because the same
device check covers it.

### Foreground service (`RunnerService.kt`, manifest `foregroundServiceType="health"`)

- The FGS changes page lists **nothing for API 37**. The latest entries are Android 16 (job quotas
  for all apps) and Android 15 (`dataSync`/`mediaProcessing` timeouts, `BOOT_COMPLETED` FGS limits)
  ([FGS changes](https://developer.android.com/develop/background-work/services/fgs/changes)).
  Neither behaviour-changes page for 17 mentions FGS types, starting a service or timeouts
  ([targeting 17](https://developer.android.com/about/versions/17/behavior-changes-17),
  [all apps](https://developer.android.com/about/versions/17/behavior-changes-all)).
- Kettle starts the service from the visible runner (`RunnerHost.kt:62`) and from the visible
  recovery prompt (`LaunchRecovery.kt:88`). It is `START_NOT_STICKY` (`RunnerService.kt:101,120`), so
  the system never restarts it from the background. That still matches #293's rule.
- **Verdict: no action.**

### Background audio hardening (`RunnerCues.kt`: `SoundPool` plus audio focus, played from the FGS)

- **All apps on 17:** background audio needs "a visible activity or ... a foreground service that is
  not of type `SHORT_SERVICE`". **Targeting 37** adds that a backgrounded app's FGS "has while-in-use
  (WIU) capabilities", and an FGS has them "if it is started in response to a user-initiated
  operation or while the app is visible to the user". On failure, playback "fail[s] silently" and
  focus returns `AUDIOFOCUS_REQUEST_FAILED`
  ([bg-audio](https://developer.android.com/about/versions/17/changes/bg-audio)).
- Kettle: both start paths are visible starts, so the service should hold WIU. If focus is refused,
  `play()` returns before `pool.play` (`RunnerCues.kt:87`). A failure would therefore be a **silent
  cue**, not a crash, and only a device can show it.
- The page gives a test hook: `adb shell cmd audio set-enable-hardening throw` turns enforcement on
  regardless of target, and makes failures loud. `dumpsys audio` / `logcat` then show `AudioHardening`
  lines (`level: full` means the FGS has no WIU, `level: partial` means there is no FGS).
- Open question: after a process death, the session resumes through the visible prompt (`LaunchRecovery.resume`),
  which counts as a visible start. Nothing restarts the service from the background, so no path
  should lack WIU. That's a reading of the docs, not a test result.
- **Verdict: device check.** Run `session-check.sh` on the `Medium_Phone_API_37.0` AVD with
  `set-enable-hardening throw`, screen off through the count-in and step ends, and the resume path
  after process death. It's worth a line in the session-check script or the PR template.

### Notifications and channels (`RunnerNotifications.kt`, `RestDayReminder.kt`)

- Neither 17 page changes channels, `POST_NOTIFICATIONS`, FGS notifications or importance. The one
  notification item in 17 is a size limit on **custom notification views**
  ([release notes, Beta 1](https://developer.android.com/about/versions/17/release-notes)). Kettle
  uses only standard `NotificationCompat` templates (`RunnerNotifications.kt:108-160`), with no
  `RemoteViews`.
- The RemoteViews memory limit for targeting 37 applies to **app widgets** only, and Kettle has none.
- The release notes list a fix for an Android 16 bug in which `setSilent(true)` notifications "may
  unexpectedly play alert sounds". The step-end alert uses `setSilent(silent)`, so this fix helps
  Kettle and asks nothing of it.
- **Verdict: no action.**

### Rest-day reminder alarm (`RestDayReminder.kt:59`)

- It uses `setAndAllowWhileIdle(RTC_WAKEUP, …)`, an **inexact** alarm, which needs no
  `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, and the manifest declares neither. Nothing on the 17 pages
  changes alarms for apps that target it. The only alarm item is a new, optional
  `setExactAndAllowWhileIdle(…, OnAlarmListener)` overload
  ([release notes, Beta 3](https://developer.android.com/about/versions/17/release-notes)).
- The background-audio waiver for exact alarms with `USAGE_ALARM` doesn't apply: cues are
  `USAGE_MEDIA` (`RunnerCues.kt:18`), and there is no exact-alarm permission.
- **Verdict: no action.**

### Edge-to-edge and insets (the View sheet `SheetStage.kt`, `SystemBars.kt`)

- At 37 there is no new edge-to-edge or insets change for either kind of change. Edge-to-edge was
  enforced at 35, and its opt-out stopped working at 36, both levels Kettle already targets.
- **Adjacent M7 finding, not about targetSdk:** on devices **below API 35**, the window goes edge to
  edge today only because Expo's `EdgeToEdgePackage` calls RN's `WindowUtilKt.updateEdgeToEdgeFeatureFlag`
  through the activity listeners (`ExpoActivityListeners.kt`, `node_modules/expo-modules-core/.../EdgeToEdgePackage.kt`,
  `android/gradle.properties: edgeToEdgeEnabled=true`). Removing RN removes that call. Without an
  `enableEdgeToEdge()` in `MainActivity`, API 24–34 phones would lose edge-to-edge, and the sheet's
  `statusBars().top` (`SheetStage.kt:90-92`) would measure a window that no longer draws under the bar.
  That belongs to the **removal PR's** hand-off, not this one.
- **Verdict: no action for the target bump.** The removal PR needs a code change plus a device check
  on an API ≤34 AVD.

### Predictive back (`enableOnBackInvokedCallback="true"`, `ModalSheet.kt`'s `OnBackPressedCallback`)

- The Android 16 changes (system animations by default, `onBackPressed` no longer called) took
  effect at 36 and are already live. Neither 17 page lists a back change.
  `MainActivity.invokeDefaultOnBackPressed` is RN's hook and goes with the removal.
- **Verdict: no action.**

### Large screens: orientation and resizability (`android:screenOrientation="portrait"`)

- **Targeting 37:** on displays with sw ≥ 600dp, `screenOrientation`, `resizeableActivity`,
  `min/maxAspectRatio` and `setRequestedOrientation` are ignored, "Android 17 removes the temporary
  developer opt-out" from 16, and the only exemptions are games and a user's own opt-in
  ([ff-restrictions-ignored](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored)).
- At 36 the rule already applies unless an app opts out. Kettle's manifest has no
  `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`, so **tablets and unfolded foldables already rotate
  Kettle today**. 37 only removes an opt-out Kettle never used. Phones (< 600dp) keep portrait.
- **Verdict: device check**, though not a new one. It's the same check 36 already called for: a
  sw ≥ 600dp AVD in landscape, covering the runner, the View sheet capped at `MAX_WIDTH_DP`, and the
  runner's state across a rotation. `configChanges` includes `orientation|screenSize`
  (`AndroidManifest.xml:82`), so a rotation doesn't recreate the activity.

### Configuration changes no longer restart activities

- From 17, keyboard, navigation, touchscreen, color mode and desk-mode `uiMode` changes no longer
  restart the activity by default, and `android:recreateOnConfigChanges` opts back in
  ([release notes, Beta 1](https://developer.android.com/about/versions/17/release-notes)). The docs
  found don't say whether this is target-gated.
- Kettle already handles `keyboard|keyboardHidden|uiMode` itself (`AndroidManifest.xml:82`), and
  nothing relies on a restart to reload resources.
- **Verdict: no action.**

### SAF backup folder, sharing and storage (`SafBackupFolder.kt`, `FileShare.kt`, `Clipboard.kt`)

- Neither 17 page changes SAF, `OPEN_DOCUMENT_TREE` or persisted grants.
- **All apps on 17** gives notice that **from Android 18** the system stops granting URI permissions
  automatically for `ACTION_SEND` and `ACTION_SEND_MULTIPLE`
  ([all apps](https://developer.android.com/about/versions/17/behavior-changes-all)). `FileShare.kt:76`
  already adds `FLAG_GRANT_READ_URI_PERMISSION`, and `Clipboard.kt:73` shares plain text with no URI.
- Storage permissions are capped at `maxSdkVersion=32`, which is unaffected.
- **Verdict: no action.**

### Network, privacy and security items gated on 37

| Change | Kettle | Verdict |
|---|---|---|
| `ACCESS_LOCAL_NETWORK` is required for LAN access | No LAN traffic in the shipped app. Metro in `.dev` uses `adb reverse` (loopback), and it goes away in M7. | No action |
| ECH, and Certificate Transparency on by default | Nothing in the app makes TLS calls itself. Billing goes through Play services IPC. | No action |
| Safer native DCL: `System.load()` files must be read-only | Kettle's code calls no `System.load`. RN/SoLoader's `.so`s go with the removal, so the bump should land **after** removal. | No action if it lands after removal |
| `static final` fields can't be changed by reflection; lock-free `MessageQueue` | No reflection on either in `native/android` (grep: no `setAccessible`/`getDeclaredField`/`MessageQueue`). Robolectric's looper shadows are its own concern, covered by 4.17. | No action |
| Password-field echo, SMS OTP, CP2, Bluetooth RFCOMM, NPU feature, `setContentCaptureEnabled` | Not used | No action |
| All apps on 17: app memory limits (`MemoryLimiter` in `ApplicationExitInfo`) | Kettle reads no exit reasons (no `ApplicationExitInfo` in `app/src/main`). A session killed this way is recovered from its state file like any other process death (#307). | No action. If an unexplained death turns up on 17, look for a `MemoryLimiter` exit. |

## `lint.xml`'s `OldTargetApi` exception (#148)

- What it covers today: lint's `OldTargetApi` flags a `targetSdk` older than the newest platform lint
  knows of, **including what the machine has installed**. That's why the check passed locally and
  failed on CI's newer image (commit `00e8d54`). The comment's stated reason is that the shell must
  not run ahead of the RN app it hosts, and that reason ends with the removal.
- At 37 the warning clears. It will **come back on any machine that installs API 38**, CI's image
  included, once Android 18 ships. That's expected around mid-2027 if the pattern holds, but it's an
  inference.
- **Verdict: code change (delete the exception) in the targetSdk PR**, keeping the zero-warning rule.
  Accept that a future CI image will fail `lint` once 38 is out, and treat that failure as the cue for
  the next bump. The alternative is to keep the ignore with a new reason ("we bump on our schedule,
  not the runner image's"), which trades the signal for a stable CI. This is the map's call. Either
  way, the current comment's reason is wrong after M7.

## Verdicts at a glance

| Area | Verdict |
|---|---|
| compileSdk/targetSdk 37 in `:app` and `:benchmark` | Code change. Gated on AGP ≥ 9.1.1 and Robolectric ≥ 4.17, so it lands with or after the AGP 9 unpin, and after removal. |
| FGS (`health`, starting, stickiness) | No action |
| Background audio (cues from the FGS) | Device check (`set-enable-hardening throw`, screen off, resume path) |
| Notification channels and alerts | No action |
| Rest-day reminder alarm | No action |
| Edge-to-edge / insets / View sheet | No action for the bump. The removal PR must add `enableEdgeToEdge()` (below API 35 only). |
| Predictive back | No action |
| Large-screen orientation | Device check (already owed at 36) |
| SAF / sharing / storage | No action |
| Network, security and other 37-gated items | No action |
| `OldTargetApi` exception | Code change (delete), or keep with a new reason. A decision for the map. |
| Play target-level policy | No requirement for 37 yet. 36 already meets the 2026-08-31 rule. |
