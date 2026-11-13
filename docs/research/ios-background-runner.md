# Background timing and cues for the iOS runner — research

> **Research findings for #398** (map #397), written November 2026 against iOS/iPadOS 27 docs and
> Kotlin 2.4. Not a plan and never merged: it lives on the `research/ios-background-runner` branch.
> It feeds the prototype (#399) and, through it, the iOS runner port. Every claim cites the source
> that owns it; where a source says nothing, this file says so rather than filling the gap.

## The question

What is the iOS equivalent of everything `RunnerService.kt` owns on Android — the clock, the
count-in, every write and every cue — given that iOS has no foreground service, and that timer
reliability is the make-or-break issue (AGENTS.md)?

## The answer in one paragraph

**iOS has no legitimate way for a plain timer app to keep running while locked**, so the iOS host
cannot be a `RunnerService`. The engine's clock survives that unchanged (it is already wall time
through a `ClockAnchor`, and catches up on any late wake), but **one engine rule does not**: a step
that ends during a catch-up starts the *next* step "fresh at now", which on Android is milliseconds
late and on iOS is "whenever the user next opens the app". So the recommendation is: (1) the host
runs the engine only while the app is in the foreground and recomputes from wall time on every
return; (2) the engine learns to **replay** a gap — each self-ending step ends at its own moment and
the next starts there, up to the first step only the user can end; (3) the host **pre-schedules one
time-sensitive local notification per self-ending step** on that same timeline, with the app's own
`.wav` cues as their sounds; (4) in-app cues use `.playback` + `.mixWithOthers`. Background audio is
**not** recommended: Apple's own guidance for workout apps contradicts using it as a keep-alive. An
`HKWorkoutSession` on iPhone (iOS 26+) is the one candidate for a true keep-alive, undocumented on
that point for iPhone, and worth one experiment in the prototype — not a dependency.

## What `RunnerService` owns, and its iOS fate

| Android (`RunnerService.kt` and friends) | iOS equivalent | Carries over? |
|---|---|---|
| `health` FGS + partial wake lock keep the process and CPU alive through lock and Doze | Nothing equivalent for a timer. Apps are suspended in the background except under a few modes (below) | **No** — the central gap |
| `ClockAnchor` on `elapsedRealtime()` | `ClockAnchor` on `clock_gettime(CLOCK_MONOTONIC)`, which on Darwin keeps counting through sleep | Yes, one line |
| `RunnerLoop` on a `HandlerThread`; `nextTimeUpdateAt` wakes | A serial `dispatch_queue` + `dispatch_after`, re-armed on every foreground | Yes, while foreground |
| `RunnerCues` (`SoundPool`, `USAGE_MEDIA`, no focus, skip in a call, respect DnD) | `AVAudioPlayer`s on an `AVAudioSession` `.playback` + `.mixWithOthers` | Foreground only; DnD has no public read |
| `StepAlerts`: post an alert when a step ends and the runner isn't visible | Pre-scheduled `UNNotificationRequest`s, `.timeSensitive`, custom sound | Yes, but **scheduled ahead**, not posted at the moment |
| Ongoing notification in the shade | Nothing in scope (a Live Activity is the analogue — out of scope) | No |
| Session survives a Recents swipe (#297) | An App Switcher swipe terminates the process | **No** — falls to the recovery path |
| Runner-state file in `noBackupFilesDir` + Resume/Finish prompt (#295, #307) | Same file under `Library/Application Support`, excluded from backup | **Yes, unchanged in logic** |
| `keepScreenOn` while the runner shows (`RunnerRoute.kt`) | `UIApplication.isIdleTimerDisabled` | Yes |

## 1. The clock

### iOS suspends a backgrounded app, and its timers with it

- Apple: "Typically, an app is in a suspended state when it's in the background. However, there are a
  limited number of background execution modes your app can support that enable it to run when in
  the background, such as playing audio, receiving location updates, or processing scheduled tasks."
  ([Configuring background execution modes](https://developer.apple.com/documentation/xcode/configuring-background-execution-modes))
- Apple DTS (Quinn), on a timer: "iOS typically suspends apps that are in the background. If your
  app is suspended, any timers you schedule won't fire." ([forums 704384](https://developer.apple.com/forums/thread/704384), Apr 2022)
- `beginBackgroundTask` buys a short, unspecified grace period ("the maximum background time
  available" is `backgroundTimeRemaining`), and the app is terminated if it overruns
  ([Extending your app's background execution time](https://developer.apple.com/documentation/uikit/extending-your-app-s-background-execution-time)).
  It is for finishing a write, not for a 45-minute session.

### The audio background mode is not a legitimate keep-alive for Kettle

This is the route `docs/ios-plan.md` Phase 4 calls "defensible for a timer app". The primary sources
say otherwise:

- The `audio` mode means "The app plays audible content in the background"
  ([Configuring background execution modes](https://developer.apple.com/documentation/xcode/configuring-background-execution-modes)).
- Apple DTS (Kevin Elliott): "The 'audio' background category allows your app to remain awake while
  your audio session is active, which isn't quite the same as guaranteeing it will not be
  suspended", and one cause of a stop is that the app "stopped playing audio (went silent) for 'to
  long', so the system triggered an interruption and never resumed"
  ([forums 764096](https://developer.apple.com/forums/thread/764096), Sep 2024).
- Apple's own guidance for **"Navigation and Workout Apps"** — Kettle's exact category: "Do not
  activate the audio session until a prompt is needed. Always deactivate the audio session after a
  prompt is played." And for playback apps: "Use a background task instead of streaming silence to
  keep the app from being suspended."
  ([Audio Session Programming Guide, Audio Guidelines by App Type](https://developer.apple.com/library/archive/documentation/Audio/Conceptual/AudioSessionProgrammingGuide/AudioGuidelinesByAppType/AudioGuidelinesByAppType.html), archived but unreplaced)
  Navigation apps get away with that pattern because the *location* mode keeps them alive between
  prompts; a workout timer has nothing playing that role.
- A developer reports exactly Kettle's case — a workout timer cueing every few minutes — failing to
  start audio from the background with error 561015905 (`'!pla'`, `cannotStartPlaying`) unless audio
  was already running ([forums 791865](https://developer.apple.com/forums/thread/791865), Jul 2025,
  unanswered; the decoding of the code is ours). So the only way the audio mode keeps a
  between-cues timer alive is to stream silence continuously, which is the thing Apple says not to do.
- App Review 2.5.4: "Multitasking apps may only use background services for their intended
  purposes: VoIP, audio playback, location, task completion, local notifications, etc."
  ([App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)). A silent loop
  isn't audio playback.

**Verdict:** don't. It also costs nothing to leave out: the foreground cues need no background mode.

### `HKWorkoutSession` on iPhone: the only plausible true keep-alive, and unproven

- `HKWorkoutSession` is available on iOS/iPadOS 17+, and WWDC25 session 322 brought the full workout
  life cycle to iPhone and iPad ([HKWorkoutSession](https://developer.apple.com/documentation/healthkit/hkworkoutsession);
  [WWDC25 322](https://developer.apple.com/videos/play/wwdc2025/322/)).
- On **watchOS** it is documented as a keep-alive: "When your app has an active workout session, it
  continues to run in the background", and "Your app can alert the user using audio or haptic
  feedback while running in the background" ([Running workout sessions](https://developer.apple.com/documentation/healthkit/running-workout-sessions)).
- For **iPhone**, nothing Apple publishes says the same. The session talks about the lock screen only
  in terms of health-data access and Live Activities, and has crash recovery
  (`shouldHandleActiveWorkoutRecovery`, `recoverActiveWorkoutSession`). Apple's sample
  ([Building a workout app for iPhone and iPad](https://developer.apple.com/documentation/healthkit/building-a-workout-app-for-iphone-and-ipad))
  declares only `UIBackgroundModes = [processing]` and the `healthkit` entitlement — no `audio` — and
  drives a 1 Hz `Timer` that feeds its Live Activity, which suggests but does not state that the app
  keeps running (read from the sample's `Info.plist`, `.entitlements` and `WorkoutManager.swift`).
- Its cost is product, not code: 2.5.1 says "HealthKit should be used for health and fitness
  purposes and integrate with the Health app", and 5.1.3(ii) forbids writing "false or inaccurate
  data into HealthKit". Using it means saving real workouts to Health, a Health permission prompt,
  and a "workout data while locked" prompt. Nothing phones home (HealthKit is on-device), but it is a
  feature with its own decision, not plumbing.

**Verdict:** one experiment in the prototype (check P9 below). If it keeps a dispatch timer and
`AVAudioPlayer` alive on a locked iPad, it becomes the later upgrade that restores the ticking
countdown in the pocket. The recommended architecture doesn't depend on it.

### Other routes, ruled out

- **`BGContinuedProcessingTask`** (iOS 26): "A task that starts in the foreground and can continue
  running in the background", but it must "report progress", shows a Live Activity, and "The system
  prioritizes the termination of tasks that reflect minimal or no progress"
  ([BGContinuedProcessingTask](https://developer.apple.com/documentation/backgroundtasks/bgcontinuedprocessingtask)).
  Built for exports, not a session clock.
- **AlarmKit** (iOS 26): countdowns whose alert "overrides both a device's focus and silent mode",
  but it "expects a widget extension if an app supports a countdown presentation. Otherwise, the
  system may unexpectedly dismiss alarms and fail to alert", and needs `NSAlarmKitUsageDescription`
  plus an authorization prompt ([Scheduling an alarm with AlarmKit](https://developer.apple.com/documentation/alarmkit/scheduling-an-alarm-with-alarmkit)).
  An alarm is a ringing alert to dismiss, too heavy for every rest, and the widget extension is a
  Live Activity, out of scope. It is the answer to revisit if silent-mode cues matter after
  TestFlight.
- **Location** — not Kettle's purpose; 2.5.4.

### So: compute from wall time, and fix the one engine rule that assumes a punctual host

The clock itself ports cleanly. `RunnerClock.kt` already measures wall time, `ClockAnchor` pins it to
a monotonic clock, and `nextTimeUpdateAt` already wakes "at once" when a step is overdue
(`RunnerWake.kt`). On iOS:

- **Monotonic source:** Darwin's `CLOCK_MONOTONIC` "will continue to increment while the system is
  asleep"; `CLOCK_UPTIME_RAW` / `mach_absolute_time()` does not (`man clock_gettime` on this Mac;
  same libc as iOS). Swift's `ContinuousClock` is the same split
  ([ContinuousClock](https://developer.apple.com/documentation/swift/continuousclock)). Use the
  sleep-inclusive one. If `mach_absolute_time()` or `ProcessInfo.systemUptime` is used anywhere, the
  privacy manifest needs the system-boot-time category with reason `35F9.1`, "to measure the amount
  of time that has elapsed between events that occurred within the app or to perform calculations to
  enable timers" ([NSPrivacyAccessedAPIType](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype)).
  `clock_gettime` isn't on that list; P2 below checks whether Kotlin/Native's own monotonic source
  counts through sleep.
- **The rule that breaks:** `timeUpdate` (`RunnerTiming.kt`) ends an overdue step with
  `advance(state, now)`, which seeds the next step at `now`. `caughtUp` (`RunnerRecovery.kt`) does
  better for one rest — "committed at its own end, so it logs its length" — but still starts "the
  step after it ... fresh at [now], as a live catch-up starts it". On Android the host wakes on the
  second, so `now` *is* the end. On iOS, suspended, a rest that ends at 10:00:30 is noticed at
  10:03:10, and a timed hold or interval after it starts at 10:03:10, while the notification already
  told the user at 10:00:30 to start. The log records a 3-minute rest that was 30 seconds, and every
  pre-scheduled notification after the first is wrong.
- **The fix, in `:shared`:** a catch-up that **replays** — each step that ends by itself is
  committed at its own end and the next one started there, repeating until the first step only the
  user ends (a reps step, a count-up interval), a pause, or the end of the workout. This is the
  timeline the notifications were scheduled from, so the two agree by construction; on Android it
  changes nothing measurable, because the host is never late. It is a behaviour change, so it moves
  `conformance/steps` goldens and needs RunnerTiming tests, and it should land on Android first as
  its own PR. Cues and step-end effects produced by a replay are dropped, as `resumeRunner` already
  drops them: the notifications were the cues.

## 2. Cues

### In the foreground: `.playback` + `.mixWithOthers`

- `.playback`: "your app audio continues with the Silent switch set to silent or when the screen
  locks" — matching Android's `USAGE_MEDIA` cues that play with the ringer silent. "By default, using
  this category implies that your app's audio is nonmixable ... To allow mixing for this category, use
  the `mixWithOthers` option." ([AVAudioSession.Category.playback](https://developer.apple.com/documentation/avfaudio/avaudiosession/category-swift.struct/playback))
- `.duckOthers` "implicitly sets the mixWithOthers option", ducking "begins when you activate your
  app's audio session and ends when you deactivate the session", and "Don't use it to duck the audio
  of other apps for more than a few seconds" ([duckOthers](https://developer.apple.com/documentation/avfaudio/avaudiosession/categoryoptions-swift.struct/duckothers)).
  A tick a second for three seconds is within that, but it means activating and deactivating around
  every countdown. Android deliberately takes no audio focus (`RunnerCues.kt`: transient may-duck
  focus let music players pause "for every cue, ticks included"). **Mirror that: `.mixWithOthers`
  only, no ducking**, session activated when the runner appears, deactivated when it leaves. The
  cost is the same as Android's: loud music can mask a cue.
- Not `.ambient`: it is silenced by the Silent switch, which would make an iPhone on silent quieter
  than the Android build. (Category semantics per the `playback` page above; ambient's behaviour is
  the standard one and not separately cited here.)
- Do Not Disturb: Android skips cues outside "all". iOS has no public read of Focus for a plain app;
  `INFocusStatusCenter` exists for communication apps. **Accept the difference**: a foreground
  runner the user is looking at plays its cues in a Focus. Not verified further.

### Interruptions (a call mid-rest)

- On iOS 26 and earlier, observe `AVAudioSession.interruptionNotification` (`.began` / `.ended`,
  `.shouldResume`). From iOS 27 that is deprecated "in favor of life-cycle notifications":
  `didBecomeActiveNotification`, `didBecomeInactiveNotification` (with a `DeactivationContext`), and
  `resumptionRecommendationNotification`, which "isn't tied to the interrupting app's
  end-of-interruption signal, so your app doesn't stay stuck in an interrupted state if that signal
  never arrives" ([Handling audio interruptions](https://developer.apple.com/documentation/avfaudio/handling-audio-interruptions)).
  Their Objective-C names (`AVAudioSessionDidBecomeInactiveNotification` …) mean Kotlin can observe
  them through `NSNotificationCenter`.
- Policy, matching Android's "a late tick is wrong": a cue requested while the session is inactive
  is **skipped, not queued**. Reactivate on the resumption recommendation or the next foreground,
  never retry a missed cue ("Don't attempt to resume playback of a prompt that was interrupted",
  same archived guide as above).
- The engine needs nothing here: the clock keeps running through a call, as on Android.

### In the background: time-sensitive notifications, and their two limits

- `.timeSensitive`: "The system presents the notification immediately, lights up the screen, can play
  a sound, and breaks through system notification controls", and "The user can turn off the ability"
  ([timeSensitive](https://developer.apple.com/documentation/usernotifications/unnotificationinterruptionlevel/timesensitive)).
  It needs the Time Sensitive Notifications capability ([WWDC21 10091](https://developer.apple.com/videos/play/wwdc2021/10091/));
  `docs/ios-plan.md` already records that without the entitlement iOS downgrades it to `active`.
  The HIG: use it for "an event that's happening now or will happen within an hour", and "The first
  time a Time Sensitive notification arrives from your app, the system describes how such a
  notification works and gives people a way to turn it off" ([HIG, Managing notifications](https://developer.apple.com/design/human-interface-guidelines/managing-notifications)).
  A rest ending now qualifies.
- **Limit 1 — the Silent switch.** The HIG's table: Time Sensitive *does* override scheduled
  delivery and Focus, and **does not** override "Ring/Silent switch on iPhone and iPad"; only
  Critical does, which "you must get an entitlement to send" (same page; [defaultCritical](https://developer.apple.com/documentation/usernotifications/unnotificationsound/defaultcritical)).
  So a pocketed iPhone on silent gets the banner and a vibration, no sound. `docs/ios-plan.md`'s
  "`sound: 'default'` was the whole of the fix" is true only with the ringer on. The Critical route
  stays refused, as ios-plan says.
- **Limit 2 — once per step, no countdown.** A notification fires once. Custom sounds must be
  Linear PCM/IMA4/µLaw/aLaw in `.aiff`/`.wav`/`.caf`, under 30 seconds, in the main bundle or
  `Library/Sounds` ([UNNotificationSound](https://developer.apple.com/documentation/usernotifications/unnotificationsound)),
  so `exercise-change.wav` / `milestone.wav` can be the step-end sound if they are PCM. A 3-2-1
  could be approximated by one clip of three ticks and a change, fired three seconds early; that
  moves the banner too, so it's a product call for after the prototype has measured delivery
  latency (P5).
- **The 64-request budget.** Apple's `UILocalNotification` reference: the system "keeps the
  soonest-firing 64 notifications ... and discards the rest", and an Apple engineer confirms the
  same limit for `UNNotificationRequest` ([forums 811171](https://developer.apple.com/forums/thread/811171)).
  Schedule only the chain of self-ending steps up to the next user-ended step, capped well under 64
  and leaving room for the rest-day reminder; re-schedule on every transition and foreground. A
  long HIIT block may need a rolling window.
- **Gating** stays as `StepAlerts` has it: while the runner is visible, nothing is pending (the
  in-app cue plays); on leaving the foreground, schedule; on returning, remove all pending runner
  requests and let the engine catch up. Foreground presentation is never needed, which keeps
  ios-plan's "don't double-cue" property without a `willPresent` handler.
- **The Watch** gets these for free when the phone is locked (ios-plan Phase 4), so this design is
  also the watch-remote transport on iOS.

## 3. Process death

- A suspended app can be reclaimed without warning: `applicationWillTerminate` "may be called in
  situations where the app is running in the background (not suspended)" — i.e. not for a suspended
  one ([applicationWillTerminate](https://developer.apple.com/documentation/uikit/uiapplicationdelegate/applicationwillterminate(_:))).
  An App Switcher swipe terminates the app. Android's #297 "nothing stops it on a Recents swipe" has
  no iOS counterpart.
- **The runner-state file and the Resume/Finish prompt carry over unchanged in logic**, and become
  the normal path rather than a rare one. Two additions:
  - **Pending notifications outlive the process.** They will keep cueing a timeline the dead app
    can't see — which is what the user wants mid-rest. The launch that finds a state file removes
    all pending runner requests before showing the prompt, and a Finish removes them too.
  - **Write discipline.** The engine writes on transitions; on iOS a transition can only happen
    in the foreground (or during the replay at the next foreground), so the state file is always
    written while the app runs. Wrap the write that coincides with `didEnterBackground` in a
    `beginBackgroundTask`. The default file protection, "Complete until first user
    authentication", keeps the file writable while locked ([Encrypting your app's files](https://developer.apple.com/documentation/uikit/encrypting-your-app-s-files));
    don't raise it to `complete`.
- `resumeRunner` lands paused, which is right after a kill. With the replay rule from §1, its
  `caughtUp` can use the same replay, so a kill and a suspension catch up identically.

## 4. Kotlin/Native constraints

- **Objective-C APIs are reachable from Kotlin; Swift-only ones are not.** "A Swift library can be
  used in Kotlin code if its API is exported to Objective-C with `@objc`. Pure Swift modules are not
  yet supported." ([Interoperability with Swift/Objective-C](https://kotlinlang.org/docs/native-objc-interop.html))
  Kotlin/Native's iOS platform libraries include `AVFAudio`, `AVFoundation`, `UserNotifications`,
  `HealthKit` and `UIKit`; `ActivityKit` is shipped disabled (`ActivityKit.def.disabled`) because
  it is Swift-only ([JetBrains/kotlin `platformLibs/src/platform/ios`](https://github.com/JetBrains/kotlin/tree/master/kotlin-native/platformLibs/src/platform/ios), master).
  So **everything the recommended host needs can be written in Kotlin** (`iosMain`), consistent with
  M8's "thin hand-written facade, no Swift export". Only a Live Activity certainly needs a Swift
  island (AlarmKit has a platform-library definition, `AlarmKit.def`, but its API is Swift-first;
  how much of it Kotlin sees was not checked).
- **Delegates:** "Swift/Objective-C classes and protocols can be subclassed with a Kotlin `final`
  class" (same page), so a `UNUserNotificationCenterDelegate` or an observer is a final Kotlin class
  over `NSObject`.
- **Threading:** "Objects are stored in a shared heap and can be accessed from any thread"
  ([Kotlin/Native memory manager](https://kotlinlang.org/docs/native-memory-manager.html)), so a
  `RunnerLoop` on a serial `dispatch_queue` with `dispatch_after` is the direct port of
  `HandlerLoop`: one thread, every command and wake in order, as #305 requires. Prefer it to an
  `NSTimer`, which needs a run loop (the main one, in a mode that keeps firing during scrolls).
  Publishing to Compose goes through the same `StateFlow` as Android. `dispatch_after` from
  `DISPATCH_TIME_NOW` runs on the uptime clock, which stops in sleep; harmless, since every
  foreground re-arms from a fresh wall reading, exactly as `HandlerLoop`'s comment reasons today.
- Not checked: whether `AVAudioPlayer.play()` must be called on the main thread. Calling it from the
  runner queue is the plan; P3 confirms it on device.

## 5. Live Activities — noted, out of scope

The analogue of the ongoing notification, and of the 3-2-1 on the lock screen (a countdown view
the system renders without the app running). It needs ActivityKit (Swift-only, so a Swift island) and
a widget extension; `docs/native-migration-plan.md` already names "a Live Activity, if one is ever
built" as a SwiftUI island. Not for TestFlight. Both AlarmKit and an `HKWorkoutSession` on iPhone
lean on one, so it's the shared next step if either is adopted.

## Where this contradicts the map or the plans

1. **`docs/ios-plan.md` Phase 4** calls background audio "defensible for a timer app" and "an
   improvement on top" to evaluate on device. Apple's guidance for workout apps (activate per prompt,
   deactivate after, don't stream silence) and the background-start failure make it a keep-alive by
   silence, i.e. not defensible. Recommend dropping it rather than evaluating it.
2. **`docs/ios-plan.md` Phase 4** says `sound: 'default'` "was the whole of the fix" for the pocket.
   Not with the Silent switch on: Time Sensitive does not override it (HIG table).
3. **The map's "the iPad mini is enough for background audio, notifications, Focus and process
   death."** Mostly true, with three gaps: an iPad has **no Ring/Silent switch** (Silent is in
   Control Center), **no vibration motor**, and **no cellular calls** — so "silent iPhone in a pocket
   vibrates" can't be observed, and the call check has to be a FaceTime (or Continuity-relayed)
   call. Those three need an iPhone before App Store, not before TestFlight.
4. **"Engine reuse" in the map's not-yet-specified runner port** is true of the engine's clock and
   state machine, but not unchanged: the catch-up rule (§1) has to replay, and that is a `:shared`
   behaviour change with conformance goldens, best landed on Android first.
5. **Android's "the session survives leaving the app"** (#297) doesn't port: on iOS the session
   pauses its *host*, not its clock. The user-visible promise ("exits that leave the app keep the
   session running") still holds through notifications plus replay, but a swipe-kill now always
   ends in the Resume/Finish prompt.

## Recommended architecture

1. **`:shared` first (Android PR, ships to Play):** a replaying catch-up — self-ending steps end at
   their own moments and the next starts there, through to the first user-ended step, a pause or the
   end; replayed cues and step-end effects dropped; `caughtUp` uses the same rule. Plus a pure
   function that lists the **upcoming step-end moments** from a snapshot (the timeline the host
   schedules), held to the replay by tests: replaying to time *t* must cross exactly the moments the
   function listed before *t*.
2. **iOS host, `iosMain`, Kotlin only** — `RunnerSession` reused as-is over:
   - a `RunnerLoop` on a serial dispatch queue; `ClockAnchor` on `CLOCK_MONOTONIC`;
   - `didBecomeActive` → remove pending runner notifications, send a `TimeUpdate` (the replay), re-arm;
     `willResignActive` → schedule notifications for the upcoming moments, under a background task;
   - cues: three preloaded `AVAudioPlayer`s, `AVAudioSession` `.playback` + `.mixWithOthers`,
     active only while the runner is on screen, skipped while interrupted;
   - alerts: `UNNotificationRequest` per moment, `.timeSensitive`, sound = the cue `.wav` (or silent
     when "Session sounds" is off, as Android's alert is), same title/body as `stepEndAlert`;
   - `isIdleTimerDisabled` while the runner shows;
   - the state file in `Library/Application Support/runner/`, `isExcludedFromBackup`, default
     protection; at launch, a state file means: remove pending runner notifications, show the prompt.
3. **Entitlements and keys:** Time Sensitive Notifications; **no** `UIBackgroundModes`. Notification
   permission asked before the first session, with the session running regardless if denied (as
   #296 does on Android).
4. **Not now:** background audio (refused), Critical alerts (refused), AlarmKit and Live Activities
   (after TestFlight, together), `HKWorkoutSession` (only if P9 passes and the Health integration is
   wanted as a feature).

## Device checks for the prototype (#399)

On the iPad mini, iPadOS 27, running the **iPhone build in compatibility mode**, installed from
Xcode (debug), English, notifications allowed unless a check says otherwise. Each one states what to
read. Use a test workout of: 3 s count-in → rest 20 s → timed hold 15 s → rest 20 s → reps step →
rest 30 s → end, plus a HIIT block of 8 × (20 s work / 10 s rest) for P6.

- **P1 — Foreground cadence.** Runner on screen, unlocked. Log each cue with a wall timestamp
  (`os_log`). Pass: ticks at −3/−2/−1 and the change cue at 0 for every self-ending step, each within
  ±100 ms of the engine's moment; no double cue.
- **P2 — The clock through sleep.** Start the rest, lock for 5 min, unlock. Pass: the replayed log
  shows each step ended at its own moment (±1 s), the reps step started at the hold's end, and the
  session file's rest durations are the configured ones. Also log Kotlin's monotonic source and
  `CLOCK_MONOTONIC` before and after the lock: confirm which counts through sleep.
- **P3 — Mixing.** Music playing in another app (Music or a podcast). Pass: the music never pauses or
  ducks, every foreground cue is audible over it, and cues work from the runner queue (not the main
  thread) without warnings.
- **P4 — Background alerts.** Leave the app (Home) mid-rest, then repeat with the screen locked.
  Pass: one banner per self-ending step at its moment (log the delivery time against the scheduled
  one), with the cue sound, none while the runner is visible, and no in-app cue doubling it on
  return. Then with "Session sounds" off: banners silent.
- **P5 — Delivery latency.** From P4's logs, the worst and median delay between a request's
  scheduled time and its delivery. This decides whether a 3-second-early "3-2-1" clip is viable.
- **P6 — The 64 budget.** Start the HIIT block (16 self-ending steps plus the rest) and leave at
  once; count `getPendingNotificationRequests` and confirm the rest-day reminder is still pending.
  Lock through the block. Pass: every boundary cues; the replay on unlock lands on the right step.
- **P7 — Focus.** Turn on a Focus that allows no apps; repeat P4. Pass: the alerts break through
  (time-sensitive). Then turn off Time Sensitive for the app in Settings: alerts are held back, and
  the session still catches up correctly on return. Note what the first-delivery explanation says.
- **P8 — Silent mode.** Control Center → Silent on. Foreground: cues still play (`.playback`).
  Background: banner with no sound (expected per the HIG; the iPad can't show the iPhone's
  vibration). Record both.
- **P9 — `HKWorkoutSession` keep-alive (experiment).** Start an `HKWorkoutSession` with the
  runner, lock the screen. Pass/fail: does a 1 s `dispatch` timer keep logging for 5 min, and can an
  `AVAudioPlayer` *start* a cue while locked? Note every prompt shown. Discard the workout after.
  Informational; nothing in the architecture waits on it.
- **P10 — A call mid-rest.** A FaceTime audio call (or an iPhone call relayed via Continuity)
  answered during a rest, ended after the rest's end. Pass: no cue plays during the call, none is
  replayed after, the step-end alert is delivered, and the clock is unaffected; foreground cues
  work again after the call (log which interruption/life-cycle notifications arrived).
- **P11 — Process death, two ways.** (a) Mid-rest, swipe the app away in the App Switcher. (b)
  Mid-rest, background it and kill it from Xcode (stop) to stand in for a memory reclaim. Pass, both:
  the pending alerts still fire while dead; the next launch removes them, shows Resume/Finish; Resume
  lands paused on the right step with the session file intact; Finish stamps the last transition's
  time.
- **P12 — Notifications denied.** Deny at the prompt, run P4. Pass: the session still runs and
  catches up on return with no error shown, as #296 behaves on Android.
- **P13 — Idle timer.** Runner on screen for longer than Auto-Lock. Pass: the screen stays on; once
  the runner closes, Auto-Lock works again.
- **P14 — Clock change.** Mid-rest, background, change the time zone (and, separately, the clock by
  hand), return. Pass: no step moves, matching `ClockAnchor`'s promise on Android.

**Not checkable on the iPad mini**, to carry to an iPhone before the App Store: vibration on silent,
the hardware Ring/Silent switch, a cellular call, and iPhone-specific background policy differences
(none are documented, but the device class differs).
