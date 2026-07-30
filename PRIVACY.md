# Privacy Policy

**Effective 30 July 2026**

Kettle does not collect, transmit, or share any personal data. There is no account, no server, and
no analytics. This policy is short because there is very little to describe.

## What Kettle stores, and where

Everything Kettle knows about you stays in the app's private storage on your device:

- **Your library** (`exercises.yaml`) — the exercises, workouts and programs you create or import.
- **Your session log** (`sessions/*.yaml`) — one file per completed workout.
- **Your preferences** — units, language and similar settings.
- **Supporter state** (`supporter.json`) — a count of tips you have left, so the app can say thank
  you. It records the tier and date, never a payment method or a transaction identifier.

None of it leaves the device unless you choose to send it somewhere. Uninstalling the app removes
all of it.

## Sharing and export

Kettle can export your library and session files through your device's normal share sheet. That is
always something you start, and you pick the destination — another app, a file manager, cloud
storage of your choosing. Once a file leaves Kettle, it is governed by whatever you sent it to, not
by this policy.

Importing works the same way in reverse: you choose a file or paste text, and it is parsed on the
device.

## Tips and purchases

Kettle has an optional tip jar. Purchases are handled entirely by Google Play Billing. Payment
details are processed by Google and are never visible to Kettle — the app only learns that a
purchase succeeded, and writes the local supporter state described above. Google's handling of that
transaction is covered by the [Google Privacy Policy](https://policies.google.com/privacy).

## Permissions

- **Notifications** — local reminders and a background fallback for workout timers. Scheduled and
  delivered on your device; nothing is sent to a push server.
- **Vibration** — haptic feedback during a session.
- **Foreground service / media playback** — keeps timer audio cues working while a workout runs.
- **Storage** (Android 12 and older only) — reading and writing library files you import or export.
- **Internet** — required by the app framework and used by Google Play Billing. Kettle itself makes
  no network requests of its own.

Kettle does not request microphone, camera, location, contacts, or health-platform access.

## What Kettle does not do

No analytics. No crash reporting. No advertising or ad identifiers. No third-party SDK that
transmits data off the device. No tracking across apps or websites. No selling or sharing of data,
because none is collected.

## Children

Kettle is not directed at children under 13 and collects no data from anyone, including children.

## Changes

If this policy ever changes, the revision will be published here with a new effective date. Because
Kettle collects nothing, any change would be about a new feature rather than a new use of existing
data.

## Contact

Questions or concerns: open an issue at
[github.com/celsaad/kettle](https://github.com/celsaad/kettle/issues).
