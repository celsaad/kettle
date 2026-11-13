# PROTOTYPE — background runner (#399)

Throwaway, on `prototype/ios-background-runner`, never merged. It answers the device checks P1–P14
at the end of the [#398 findings](https://github.com/celsaad/kettle/blob/research/ios-background-runner/docs/research/ios-background-runner.md),
on the iPad mini running this iPhone-only build in compatibility mode.

Swift, not Kotlin `iosMain`: every check is OS behaviour (suspension, notifications, audio session,
process death), and Swift skips the framework wiring. The engine is a ~40-line stand-in with the
**replaying catch-up** #398 proposes, not `RunnerEngine`.

## Run it on the iPad

1. Xcode → Settings → Accounts: sign in with the developer account.
2. iPad: Settings → Privacy & Security → Developer Mode on (restart), connect by cable, trust.
3. `brew install xcodegen && xcodegen` here, open `RunnerPrototype.xcodeproj`, pick your team in
   Signing & Capabilities (Time Sensitive Notifications and HealthKit are already in the
   entitlements), run on the iPad.
4. Allow notifications at the first Start.

The log is on screen, in `os_log` (subsystem `com.casco.kettle.prototype.runner`), and in
`Documents/PROTOTYPE-log.txt` (Files app → On My iPad → Runner PROTO, or **Share log**). Each
finished session also writes `PROTOTYPE-session-*.txt` with planned vs actual step lengths.

## Workouts

- **basic**: count-in 3 s → Rest A 20 s → Timed hold 15 s → Rest B 20 s → Reps (tap Done) → Rest C 30 s.
- **HIIT**: count-in 3 s → 8 × (Work 20 s, Rest 10 s), for P6.

## What it already showed in the simulator

- Cues play from the runner's serial queue, not main (P3's threading question).
- **Schedule on `didEnterBackground`, not `willResignActive`.** The first run disarmed the live cues
  when the notification-permission dialog appeared; Control Center (where P8 turns Silent on) and
  Notification Center resign the app the same way while the runner is still visible.
- Kill mid-rest, relaunch 40 s later, Resume: Rest A, Timed hold and Rest B each committed at their
  planned length, stopping at the reps step, landing paused; the state file survives the next launch.
- The simulator never suspends a background app (the heartbeat kept logging) and won't grant
  notifications, so P2 and P4–P12 are device-only.
