# Session controls — spec

> **Built (#552); its Wear OS 7 device checks, under Testing, are the maintainer's.** Action buttons
> on the runner's ongoing notification, promoted to a Live Update so a Wear OS 7 watch shows them: a
> running session driven from the wrist with no watch app and no data on the watch. Every decision here was made on the map
> [Wayfinder: session controls on the notification (watch remote)](https://github.com/celsaad/kettle/issues/540),
> and each section links the ticket that holds its reasoning; where this file and a ticket disagree,
> the ticket is the record and this file is the bug. The design canvas linked from
> [#544](https://github.com/celsaad/kettle/issues/544) is private and **predates the review in
> [#549](https://github.com/celsaad/kettle/issues/549)**: where they differ (EMOM, AMRAP, Back/Undo),
> this file wins.
>
> This replaces a plan written against React Native's runner and `expo-notifications`; that version is
> in git history, and the parts of its reasoning that still hold are folded in below.

**Session controls** are the buttons on the runner's ongoing notification. The phone's shade, its lock
screen and a Wear OS 7 watch are three places they show up; describe the feature as session controls,
never as a watch feature, since most people will meet them on a lock screen.

## The transport

([#541](https://github.com/celsaad/kettle/issues/541), [#543](https://github.com/celsaad/kettle/issues/543),
[#545](https://github.com/celsaad/kettle/issues/545))

- **Wear doesn't bridge the notification as it is today.** Bridging skips ongoing notifications, and
  Android marks every foreground-service notification non-clearable, which is skipped too.
- **A Live Update does bridge.** Request promotion on the runner's own ongoing notification
  (`setRequestPromotedOngoing(true)`, and `android.permission.POST_PROMOTED_NOTIFICATIONS` in the main
  manifest). On a Wear OS 7 emulator it arrived with its text, three actions and a ticking chronometer;
  taps advanced the phone without bringing the runner forward, locked or not; ending the session
  cleared it from the wrist.
- **Everywhere else it degrades with no code path of its own.** Below Android 16, or with Live Updates
  turned off for Kettle, it stays an ordinary ongoing notification with the same actions in the shade
  and on the lock screen.
- **One notification, never two.** A second, ordinary card would reach watches older than Wear OS 7,
  but it puts a second Kettle notification in every session on every phone; it is out of scope, and
  returns only as a toggle-gated card if someone on an older watch asks.
- **No Settings toggle** ([#542](https://github.com/celsaad/kettle/issues/542)): the ongoing
  notification shows in every session already, and the actions add nothing intrusive.
- The current notification meets every other promotion requirement (LOW channel, not colorized, no
  custom view, a title, ongoing, chronometer allowed). Don't add `setColorized`: it blocks promotion.

## What the card says

([#544](https://github.com/celsaad/kettle/issues/544))

**The step leads.** Title = the step: the exercise's name, verbatim (user data, never translated), or
"Rest" or "Get ready". Text = one detail line, or nothing. **The workout name moves to the header**
(`setSubText`), where it no longer costs the round face two lines: workout names in a real library run
long enough that a workout-name title alone fills the wrist. Long exercise names are not truncated; the
wrist card scrolls, and the timer may land below the fold on the longest ones.

| Step | Title | Detail line | Chronometer |
|---|---|---|---|
| Count-in | "Get ready" | the first exercise's name | down |
| Reps | exercise | set position · reps, and × weight when there is one | none |
| Rest | "Rest" | "Next: <exercise>" (`session.header.next`) | down |
| Hold | exercise | set position | up |
| HIIT | exercise | round position | down |
| EMOM | exercise | minute (or interval) position · target reps when there are some | down |
| AMRAP | exercise | rounds counted so far | down to the cap |
| Cardio | exercise | none | down with a duration, up without |
| Paused | the step's title | "Paused" (`session.paused`) | none |

- **Done logs what the card shows** ([#542](https://github.com/celsaad/kettle/issues/542)): the reps
  line reads the live `snapshot.inputs`, which a phone edit updates, never a recomputed seed. Format
  weight only through `toDisplayWeight`, and counts through `pluralText`.
- **Inside a circuit**, the circuit's round replaces the set or round position, never both, as the
  screen does.
- **Reuse before adding keys.** `preview.set` and `preview.roundOf` already say "Set 2 of 4" and
  "Round 3 of 8"; `RunnerText.kt`'s `reps()` and `weightText()` already pluralize and convert;
  `session.amrap.rounds` is the round count. Any key that is still new goes into `en`, `pt` and `ja`,
  arguments in sorted placeholder order. `session.notification.setPosition` and
  `session.notification.paused` go dead and leave all three bundles.

## The actions

([#542](https://github.com/celsaad/kettle/issues/542), [#544](https://github.com/celsaad/kettle/issues/544),
[#549](https://github.com/celsaad/kettle/issues/549))

Labels are the runner's own keys, so the actions add no strings, and Voice Control hears the same words
on the phone and the wrist.

| Step | Actions |
|---|---|
| Count-in | none |
| Reps | Log set (`session.reps.logSet`), Back |
| Rest | Skip rest (`session.rest.skip`), Back, +30s (`session.rest.add30`) |
| Hold, HIIT, cardio | Done (`session.done`), Back |
| EMOM | Back only: the minute ends on its clock and logs the reps the card shows. The screen's Done (rest out the minute) and Skip stay on the phone |
| AMRAP | Round N done (`session.amrap.roundDone`, sending `SetRounds(n)`), Back. No Done: the cap ends it |
| Paused | Resume (`session.resume`) only |

- **Back** reads **Undo** (`session.undo`) on the step right after a logged set, where it retracts that
  set (`RunnerView.Running.logged` is set); **Back** (`session.prev`) elsewhere. Same command.
- **Back is left off where there is nothing to go back to** (the first step): a notification action
  can't be disabled, and one that does nothing is worse than none.
- **+30s only on a rest**, matching the runner screen, though the engine would take it on a countdown
  interval.
- **Never on the card:** reps/weight/RPE editing (no data on the watch) and Finish (no confirm, no
  undo).

## Aiming every tap

([#549](https://github.com/celsaad/kettle/issues/549))

A tap can arrive late: the wrist card lags the phone, and a rest can end by itself while a tap is in
flight. Every action is aimed at the step it was drawn for, and refused anywhere else.

- **The aim lives in the intent's identity**, not its extras: a broadcast `PendingIntent` whose data URI
  carries the session and the visit, e.g. `kettle:session/<sessionId>/<visit>/<cmd>`. Extras aren't
  part of a `PendingIntent`'s identity, so with a fixed request code and `FLAG_UPDATE_CURRENT` every
  re-post rewrites the visit inside the token an older card still holds, and a late tap arrives
  aimed at the current step. The spike built them that way, which is why its taps were all accepted
  and refusal was never exercised. `openRunner()` in `RunnerService.kt` is the pattern *not* to copy.
- **The session id is part of the aim**: `visit` restarts at 0 in every session, so a visit alone lets a
  late tap from one session land in the next.
- **The receiver** is a main-source, non-exported `BroadcastReceiver`. It parses the URI, drops a tap
  whose session isn't the running one, and sends the command aimed at the visit it carries. It never
  aims with the current visit at tap time (the debug `RunnerCommandReceiver` does, for its own
  reasons). The one step command the engine doesn't aim, `SetRounds`, the receiver checks itself: it
  compares the URI's visit with the running snapshot's and drops a mismatch, so a late "Round N done"
  can't set the next AMRAP's rounds. That leaves a narrow window, since the snapshot it compares with
  can lag the engine; aiming `SetRounds` in the engine (#516) closes it. Resume's `TogglePause` can't
  be aimed by visit at all, since pausing doesn't move it; left as a toggle by decision
  ([#549](https://github.com/celsaad/kettle/issues/549)).
- **Actions are broadcasts, never `getService`**: `RunnerService.onStartCommand` enters the foreground
  on any intent before reading its action, so a late tap delivered to a stopped service would bring up
  a placeholder nothing stops. On API 31+ a broadcast can't start an activity, which is also what keeps
  a wrist tap from pulling the phone out of a pocket.

**Engine change (prerequisite, `:shared`), landed in #553:** `Prev(visit)` and
`AddRestSeconds(seconds, visit)`, refused when not aimed at the snapshot's visit, as `Advance` and
`Finish` already are; the screen sends its current visit. Without it, a late Back threw away the step
just started, and a late +30s from a rest landed on the HIIT round, EMOM minute or AMRAP cap that
follows. `SetRounds` is still unaimed in the engine: on the card, the receiver's own visit check
above covers "Round N done"; aiming it in the engine, which the screen needs, is on the follow-ups
list.

## The status-bar chip

([#544](https://github.com/celsaad/kettle/issues/544))

A promoted notification gets a chip in the Android 16+ status bar: the chronometer for rest, hold,
count-in and the countdown intervals, and `setShortCriticalText` otherwise: "Set 2/4" on reps (one new
key, kept to about 7 characters in every locale) and "Paused" while paused. Check both in pt and ja on
a device; the review flagged pt's "Série 2/4" as over the length the chip guarantees, and that was
left for the device. The system draws the chip, so its colour isn't Kettle's to set.

## Testing

- **`:shared`**: `Prev(visit)` and `AddRestSeconds(seconds, visit)` refused when stale, each proven
  against the bug it pins: a late Back after a rest ends by itself, and a late +30s landing on a HIIT
  round.
- **`:app`** (`RunnerNotificationsTest`, Robolectric): title, detail and actions per step kind, Undo
  versus Back, Back absent on the first step, EMOM and AMRAP actions, the intent URIs carrying session
  and visit, and the receiver dropping a foreign session or a stale visit. Drive the copy in `pt`
  (`@Config(qualifiers = "pt")`): an English assertion can't catch a hardcoded English string.
- **`scripts/session-check.sh` and `.ps1`**: they parse the ongoing notification's title and text from
  `dumpsys notification`, so update both twins for the step-first title, and add a check that fires a
  superseded card's intent after the step has moved and confirms the session didn't.
- **On a Wear OS 7 emulator paired with the phone AVD**: the card on the wrist with its actions, a tap
  advancing the phone without foregrounding it (locked and unlocked), the chronometer ticking, no buzz
  per step, the card gone after the session ends. **Pairing trap:** nothing bridges until the Wear OS
  companion's listener has notification access, which emulator pairing can leave off:
  `adb shell "cmd notification allow_listener com.google.android.apps.wear.companion/com.google.android.libraries.wear.companion.notification.service.CompanionNotificationListenerService"`.

## Out of scope, deliberately

- A standalone Wear OS app: data on the watch means syncing the library and the log over the Data
  Layer, a second APK and Wear review. Heart rate (Health Services) needs one, so it's out too.
- The second, ordinary card for watches older than Wear OS 7 (above).
- Apple Watch: parked in the iOS map's fog until there's an iOS runner to design against.
- No cloud relay of any kind: nothing may phone home.
- From the review, by decision ([#549](https://github.com/celsaad/kettle/issues/549)): Resume as a
  toggle, the card lagging rapid input edits, Log set or Done on the last step finishing the session,
  the cardio distance on the card, and the edge cases a builder settles in passing (a parked ad-hoc
  session, an EMOM without target reps, a rest with nothing after it).
