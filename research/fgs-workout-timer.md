# Research: a workout timer's foreground service on Android 14–16

> **Throwaway research note** for #293 (map #292, `docs/native-migration-plan.md` M6). Lives on the
> `research/fgs-workout-timer` branch only, never merged. Facts only, each with its source; the
> decisions belong to the cue and live-state tickets. Gathered 2026-11-04.

Context checked in the repo:

- `native/android/app/build.gradle.kts`: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`.
- **The ticket's premise about expo-audio is wrong for this app.** `app.json` configures expo-audio
  with `"enableBackgroundPlayback": false`, and expo-audio's config plugin
  (`node_modules/expo-audio/plugin/src/withAudio.ts`, `toggleService(PLAYBACK_SERVICE_NAME, enableBackgroundPlayback, …)`)
  *removes* `AudioControlsService` when that flag is false and adds neither `FOREGROUND_SERVICE`
  nor `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. The merged release manifest
  (`native/android/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`)
  confirms it: no `<service>` has a `foregroundServiceType`, and there is no `FOREGROUND_SERVICE*`
  permission. **Kettle ships no foreground service today**, so the runner's would be the first, and
  the first FGS-type declaration on the Play Console.
- The merged manifest already holds `WAKE_LOCK` (from Firebase/Play services via expo-notifications),
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` and `INTERNET`. No `SCHEDULE_EXACT_ALARM` /
  `USE_EXACT_ALARM`. The rest-day reminder uses `setAndAllowWhileIdle` (inexact, no permission),
  `native/android/app/src/main/java/com/casco/kettle/shell/reminder/RestDayReminder.kt:60`.

## 1. The FGS type and its Play declaration

Since Android 14 (targetSdk 34+) every FGS must declare a type in the manifest, hold `FOREGROUND_SERVICE`
plus the type's own permission, and meet the type's runtime prerequisites, or `startForeground()`
throws. ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[Android 14 behaviour changes](https://developer.android.com/about/versions/14/behavior-changes-14#fgs-types))

| Type | Manifest permission (protection level) | Runtime prerequisite | Documented purpose | Time limit |
|---|---|---|---|---|
| `health` | `FOREGROUND_SERVICE_HEALTH` (normal) | **At least one of:** declare `HIGH_SAMPLING_RATE_SENSORS` in the manifest; *or* be granted `BODY_SENSORS` (API ≤35), `READ_HEART_RATE`, `READ_SKIN_TEMPERATURE`, `READ_OXYGEN_SATURATION` or `ACTIVITY_RECOGNITION` | "Any long-running use cases to support apps in the fitness category such as exercise trackers." | none documented |
| `specialUse` | `FOREGROUND_SERVICE_SPECIAL_USE` (normal\|appop) | none; must add `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="…"/>`, reviewed in Play Console | "any valid foreground service use cases that aren't covered by the other foreground service types" | none documented |
| `mediaPlayback` | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (normal) | none | "Continue audio or video playback from the background." | none documented |
| `shortService` | none beyond `FOREGROUND_SERVICE` | none | "Quickly finish critical work that cannot be interrupted or postponed." | **~3 minutes**, then `onTimeout()` and an ANR if not stopped; no sticky support; can't start other FGSs |

Sources: [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)
(types, prerequisites, descriptions, shortService rules);
[Manifest.permission](https://developer.android.com/reference/android/Manifest.permission)
(protection levels: `FOREGROUND_SERVICE_HEALTH` normal|instant, `FOREGROUND_SERVICE_SPECIAL_USE`
normal|appop|instant, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` normal|instant, `HIGH_SAMPLING_RATE_SENSORS`
**normal**, `ACTIVITY_RECOGNITION` **dangerous**).

Facts that bear on fit:

- `health` is the only type whose documented purpose names the use case ("exercise trackers").
  ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types#health))
- Its prerequisite can be met without a runtime prompt by declaring `HIGH_SAMPLING_RATE_SENSORS`,
  which is a *normal* (install-time) permission. The alternative that matches a workout app on its
  face, `ACTIVITY_RECOGNITION`, is *dangerous* (a runtime "Physical activity" prompt).
  ([Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#HIGH_SAMPLING_RATE_SENSORS))
  Kettle reads no sensor, so either is declared purely to satisfy the prerequisite. The docs don't
  say whether that is acceptable, and no Play page addresses it.
- The `BODY_SENSORS` and sensor-based READ permissions are while-in-use and block creating a health FGS
  from the background; this "doesn't apply if it's a health service that needs different permissions,
  like `ACTIVITY_RECOGNITION`". ([bg-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions))
- Android 16 (targetSdk 36) moves `BODY_SENSORS` to the granular `android.permission.health.*`,
  including for `FOREGROUND_SERVICE_TYPE_HEALTH`, and mobile apps using them must declare a
  privacy-policy activity. Irrelevant unless Kettle chooses a sensor permission.
  ([Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16#health-fitness-permissions))
- `mediaPlayback` on Play: "Playback must be user-initiated; any automatic soundtracks that play when
  you open an app do not qualify." The runner plays short cues, not continuous media.
  ([Play: FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821))
- `specialUse` on Play: "In limited scenarios, if your use case meets the other characteristics
  required for foreground service usage … you may declare `TYPE_SPECIAL_USE`"; "All foreground service
  types are subject to review." ([Play: FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821))
- `shortService` cannot hold a workout: about 3 minutes, and extending it needs the app visible or an
  exemption. Android 17 also excludes it from background audio (see §3).
  ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types#short-service),
  [Android 17 background audio](https://developer.android.com/about/versions/17/changes/bg-audio))

**The Play Console declaration** (Policy > App content, required for targetSdk 34+, per type): a
description of the feature that uses it, the user impact if the system defers or interrupts it,
**a link to a video demonstrating each FGS feature**, and a use case picked from a list or typed in.
For `health` the video must "Show a user initiating a health data sync, such as counting steps or
recording an active workout." The page names no timer use case and no exemption for any type.
([Play: FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821),
[FGS types § Play enforcement](https://developer.android.com/develop/background-work/services/fgs/service-types#google-play-enforcement))
The Play policy-deadlines page lists one pending FGS change: geofencing removed as an approved use
case (deadline 2027-01-27), which doesn't touch Kettle.
([Play policy deadlines](https://support.google.com/googleplay/android-developer/answer/12253906))

**Coexistence:** types are declared per `<service>`, and one service may declare several with `|`.
`startForeground()` may pass a subset of the declared types and be called again later to add more.
(The docs' own example is a fitness running-tracker declaring `location|mediaPlayback`.) Nothing in
the docs forbids two services with different types in one app.
([Declare FGS](https://developer.android.com/develop/background-work/services/fgs/declare),
[Launch FGS](https://developer.android.com/develop/background-work/services/fgs/launch#promote-service))
Each type declared still needs its own Play declaration and video. As the context section shows,
expo-audio's `mediaPlayback` service isn't in this app's manifest, so the question doesn't arise.

## 2. Starting it

- Targeting Android 12+, an app **can't start an FGS while in the background** except for listed
  exemptions, or it gets `ForegroundServiceStartNotAllowedException`. The relevant exemptions: the app
  "transitions from a user-visible state, such as an activity"; "The user performs an action on a UI
  element related to your app", for example a notification; "Your app invokes an exact alarm to
  complete an action that the user requests"; "The user turns off battery optimizations for your
  app". ([bg-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#background-start-restriction-exemptions))
  The docs also say exact alarms "aren't affected by foreground service launch restrictions".
  ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule#system-resource))
- Sequence: `startForegroundService()`, then the service calls `startForeground()` with the types.
  An undeclared type throws `IllegalArgumentException`; a missing prerequisite throws
  `SecurityException`. ([Launch FGS](https://developer.android.com/develop/background-work/services/fgs/launch))
- **Resuming after backgrounding:** an FGS started while the runner is visible keeps running when the
  app goes to the background. The restriction is on *starting* one from the background. So "resume"
  only becomes a problem if the service was stopped while backgrounded, or restarted by the system
  after process death (§4). A notification action is an allowed restart path.
  ([bg-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start))
- `BOOT_COMPLETED` can't launch `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`
  or `microphone` FGSs when targeting 15+. `health` and `specialUse` aren't on the list. Kettle has no
  reason to start the runner at boot.
  ([Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed))
- **`POST_NOTIFICATIONS` denied:** "Apps don't need to request the `POST_NOTIFICATIONS` permission in
  order to launch a foreground service. However, apps must include a notification." If the user
  denies it, "they still see notices related to foreground services in the Task Manager but don't
  see them in the notification drawer." Media-session notifications are exempt.
  ([Notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions))
  So the service and its clock run either way, but the lock-screen/shade controls and any
  notification-based cues are invisible. That matters for whatever the cue ticket puts in the
  notification, and for the watch remote, which plans to live in the notification shade.
- Android 14+: users can swipe away an FGS notification set `setOngoing(true)`, except while the
  phone is locked or on "Clear all". Media notifications are exempt.
  ([Android 14 all-apps changes](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications))
- The FGS notification must be `PRIORITY_LOW` or higher.
  ([Launch FGS](https://developer.android.com/develop/background-work/services/fgs/launch#promote-service))

## 3. Doze, App Standby, OEM killers: is an in-process clock enough?

**Clock base.** `Handler`, `Thread.sleep`, `Object.wait` and `System.nanoTime` run on `uptimeMillis()`,
which "stops when the system enters deep sleep (CPU off, display dark…)". `elapsedRealtime()` includes
deep sleep and is "the recommend basis for general purpose interval timing". `AlarmManager` events
"occur even when the device is in deep sleep".
([SystemClock](https://developer.android.com/reference/android/os/SystemClock))
So a `Handler`/coroutine `delay` tick **does not fire on time while the CPU sleeps**, FGS or not. An
FGS keeps the process alive. It does not keep the CPU awake. What keeps the CPU awake is a
partial wake lock or an alarm. Computing the displayed time from `elapsedRealtime()` deltas stays
correct after wake-up, but a cue *scheduled* by a Handler fires late if the CPU slept through it.

**Doze** ignores wake locks and defers standard alarms. `setAndAllowWhileIdle`/`setExactAndAllowWhileIdle`
fire in Doze but "can[not] fire alarms more than once per nine minutes, per app" (the power-limits
table says "Limited to 7 per hour"). `setAlarmClock` alarms "continue to fire normally. The system exits
Doze shortly before those alarms fire."
([Doze](https://developer.android.com/training/monitoring-device-state/doze-standby#restrictions),
[Power limits](https://developer.android.com/topic/performance/power/power-details#device-state))
Doze needs the device unplugged, **stationary** and screen-off for a while
([Doze](https://developer.android.com/training/monitoring-device-state/doze-standby#understand_doze)),
so a phone on a bench mid-rest can qualify.

**But Doze doesn't revoke an FGS app's wake lock (AOSP).** In `PowerManagerService.setWakeLockDisabledStateLocked`,
while idle a partial wake lock is disabled only if the uid isn't allowlisted *and*
`procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE` (5). A process running an FGS sits at
`FOREGROUND_SERVICE` (4), so its partial wake lock is honoured in Doze.
([PowerManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/power/PowerManagerService.java),
[ProcessStateEnum.aidl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ProcessStateEnum.aidl):
`FOREGROUND_SERVICE = 4`, `BOUND_FOREGROUND_SERVICE = 5`.) The developer docs don't state this
explicitly; it is platform source, so OEM builds can differ.

**App Standby** doesn't treat an app as idle while it "has a process currently in the foreground, either
as an activity or foreground service". The docs add: "Don't start a foreground service just to prevent
the system from determining that your app is idle."
([Doze/App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby#understand_app_standby))
Under an FGS, alarm frequency is still limited by standby bucket. Network isn't restricted. On Android
16, jobs running alongside an FGS now count against job quota (irrelevant: the runner needs no jobs).
([Power limits](https://developer.android.com/topic/performance/power/power-details#app-state),
[Android 16 all-apps](https://developer.android.com/about/versions/16/behavior-changes-all#job-quota-opt))

**Samsung (One UI)**, from [dontkillmyapp.com/samsung](https://dontkillmyapp.com/samsung), not a
first-party source:
- Since Android 11 Samsung blocked wake locks held in FGSs by default ("Apps can no longer hold wake
  lock in foreground services").
- 07/2024 update: "Since One UI 6.0, foreground services of apps targeting Android 14 will be
  guaranteed to work as intended." Kettle targets 36; the S25+ ships One UI 7+.
- Samsung's own layers still exist: "Put unused apps to sleep" / Sleeping and Deep sleeping apps,
  a more aggressive Adaptive battery, a "Never sleeping apps" list, and "even apps removed from
  restricted lists may be re-added after firmware updates". "No known solution on dev end."
- The page's newest data point is One UI 6.0 / Android 14. Nothing is reported for One UI 7–8 on
  Android 15–16, so the S25+ has to be tested on the device.

**Exact alarms, if cues are backed by AlarmManager:**
- `SCHEDULE_EXACT_ALARM` "is denied by default" for most new installs targeting 13+ on Android 14.
  It is user-granted ("Alarms & reminders"), revocable, and revoking it stops the app and cancels its
  exact alarms. Check `canScheduleExactAlarms()`.
  ([Android 14 all-apps](https://developer.android.com/about/versions/14/behavior-changes-all#schedule-exact-alarms),
  [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule#using-schedule-exact-permission))
- `USE_EXACT_ALARM` is auto-granted and can't be revoked, but Play restricts it: "apps must only declare
  this permission if their core functionality supports the need for an exact alarm". Acceptable uses:
  "alarm or timer apps and calendar apps that show event notifications". Otherwise evaluate
  `SCHEDULE_EXACT_ALARM`, and a Play Console declaration is required.
  ([Play: Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241))
- **An exact alarm set with an `OnAlarmListener` (e.g. `setExact(…, listener, handler)`) needs no
  permission.** It is delivered in-process, so it only works while the process lives.
  ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule#exact-permission-declare))
- The docs' own guidance for in-lifetime timing: "For timing operations that are guaranteed to occur
  during the lifetime of your application, consider using the `Handler` class…", listed under "Use
  cases that might not require exact alarms".
  ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule))

**Android 17 (all apps, regardless of target) hardens background audio.** Playback, focus and volume
calls need "a visible activity or … a foreground service that is not of type `SHORT_SERVICE`". When
targeting 37, the FGS must also have while-in-use capability, which it gets by being started while the
app is visible or from a user action like a notification tap. That is waived for `USAGE_ALARM` streams
if the app holds the exact-alarm permission. Failures are **silent**: playback is muted with no
exception. ([Android 17 background audio](https://developer.android.com/about/versions/17/changes/bg-audio))
Android 15 (target 35+) already requires being the top app or running an FGS to request audio focus.
([Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-15#audio-focus))
So any type except `shortService`, **started from the visible runner**, keeps background cue sounds
legal now and after a target bump to 37.

## 4. Process death

- An FGS process ranks as a **visible** process: "extremely important and aren't killed unless doing so
  is required to keep all foreground processes running." The Service reference: "still theoretically
  possible … under extreme memory pressure … but in practice this should not be a concern."
  ([Process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle),
  [Service](https://developer.android.com/reference/android/app/Service#ProcessLifecycle))
  Android 17 adds RAM-based per-app memory limits, logged as `REASON_OTHER` with `"MemoryLimiter:AnonSwap"`.
  ([Android 17 all-apps](https://developer.android.com/about/versions/17/behavior-changes-all#app-memory-limits))
- **The user's Task Manager "Stop"** (Android 13+, any target) "removes your app from memory. … your
  entire app stops", removes the notification, sends **no callback**, and leaves alarms in place.
  Detect it next launch via `ApplicationExitInfo.REASON_USER_REQUESTED`. Test with
  `adb shell cmd activity stop-app <pkg>`.
  ([Handle user stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping))
- Restart semantics: `START_STICKY` leaves the service started and recreates it without the intent.
  `START_REDELIVER_INTENT` schedules a restart and re-delivers the last intent. `START_NOT_STICKY`
  doesn't recreate. ([Service](https://developer.android.com/reference/android/app/Service#START_STICKY))
  The only type documented as not supporting sticky is `shortService`. The docs **do not say**
  whether a system restart of a sticky `health`/`specialUse` service may call `startForeground()`
  from the background without hitting the start restriction. That is untested here; the
  process-death-mid-set device check should cover it.
- **Timeouts:** Android 15 added a 6-hours-in-24 limit for `dataSync` and `mediaProcessing` only.
  `shortService` has ~3 minutes. No timeout is documented for `health`, `specialUse` or
  `mediaPlayback`. ([Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-15#datasync-timeout),
  [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types))

## 5. Network and Data Safety

- None of the APIs above (FGS, notifications, `AlarmManager`, wake locks, `SoundPool`/`AudioTrack`)
  transmit anything. The FCM, high-priority-message and network rows on the pages above are
  alternatives Kettle doesn't use.
- Play's Data safety definition: "'Collect' means transmitting data from your app off a user's device";
  data "only processed locally on the user's device and not sent off device" isn't declared; "You do
  not need to declare collection or sharing unless data is actually collected and/or shared."
  ([Play: Data safety](https://support.google.com/googleplay/android-developer/answer/10787469))
  **The zero-data claim is unaffected.**
- What does change on Play: a new **FGS declaration** on the App content page, with a video, and
  under policy review. If chosen, a **`USE_EXACT_ALARM` declaration**. New install-time permissions
  shown on the listing's permission list (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_<TYPE>`, and for
  `health`, `HIGH_SAMPLING_RATE_SENSORS` or `ACTIVITY_RECOGNITION`). The permission list is built from
  "install-time permissions that an app declares in its manifest".
  ([Play: Data safety](https://support.google.com/googleplay/android-developer/answer/10787469))

## What this means for the decision

The facts leave these options open for the grilling tickets:

1. **FGS type.**
   - **`health`** is the only documented fit ("exercise trackers") and Play's health example names
     "recording an active workout". It costs a sensor-flavoured permission Kettle doesn't use:
     `HIGH_SAMPLING_RATE_SENSORS` (install-time, no prompt) or `ACTIVITY_RECOGNITION` (runtime prompt).
   - **`specialUse`** needs no extra permission but a free-text justification, and Play frames it as
     "limited scenarios" when no other type fits. One does fit.
   - **`mediaPlayback`** fails Play's "user-initiated playback" framing for a timer that only plays cues.
   - `shortService` is out.

   The facts lean to `health` with `HIGH_SAMPLING_RATE_SENSORS`. Whether declaring an unused sensor
   permission to meet the prerequisite is acceptable is undocumented either way.
2. **Clock and cues.**
   - (a) In-process clock on `elapsedRealtime()` with a partial wake lock held for the session.
     AOSP honours it in Doze for an FGS process; Samsung One UI 6+ promises the same for target-34+
     apps. No alarm permission is needed.
   - (b) (a) plus permission-free `OnAlarmListener` exact alarms as a wake-up for each cue.
   - (c) PendingIntent exact alarms or `setAlarmClock` as a cue backstop that survives process death.
     That needs `SCHEDULE_EXACT_ALARM` (prompt, revocable) or `USE_EXACT_ALARM` (Play declaration;
     "timer apps" are named, though Kettle's core function is arguably tracking, not timing).
3. **Notification denied.** The service runs regardless. Decide whether the runner asks for
   `POST_NOTIFICATIONS` at session start, and what degrades (shade controls, notification cues,
   the watch remote's surface) when it is refused.
4. **Process death and user Stop.**
   - `START_STICKY` vs `START_NOT_STICKY`, plus persisting live state so a restart, or the next
     launch after `REASON_USER_REQUESTED`, can resume or close out the session.
   - The background `startForeground()` after a sticky restart is undocumented, so it needs a device
     test.
5. **Start point.** Start the FGS from the visible runner, not lazily on backgrounding. That keeps
   the start legal, grants while-in-use capability for Android 17's audio hardening, and avoids
   `ForegroundServiceStartNotAllowedException`.
6. **Device verification on the S25+**: the dontkillmyapp data stops at One UI 6. The closed-testing
   script should include screen-off rests longer than a few minutes while stationary
   (`adb shell dumpsys deviceidle force-idle`), Task Manager Stop, and the device's "Sleeping apps"
   settings.
