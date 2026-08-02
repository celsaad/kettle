# Watch as a step controller — plan

> **Not executed.** This is a forward-looking plan; nothing below has shipped. Written against the
> tree at `59a9663`.

The watch never holds data. It holds no library, writes no session files, and has nothing to merge —
it only drives the step the phone is already running. That constraint is the entire reason this is a
few days of work instead of a few weeks, so it is the first thing to defend if the scope starts
moving.

**The transport is the Android notification.** Wear OS bridges phone notifications to the wrist
automatically, action buttons included, so there is no watch app, no second APK, no Play track, no
Wear quality review, and no Bluetooth sync layer. The whole feature lives in the phone codebase.

## Why this shape rather than a real watch app

A standalone Wear OS app was costed first and rejected for v1. Its expensive part is not the UI, it's
that Kettle's storage is per-device `expo-file-system`: the watch has its own filesystem, so a watch
that holds data needs the library pushed to it and sessions merged back, over the Wearable Data Layer
— native Kotlin on both sides, no Expo module — plus a separately-versioned APK and a separate review.
A cloud relay is not an escape hatch: "nothing may phone home" is a product claim backing the Play
zero-data-collected declaration, not a preference.

Reducing the watch to a remote deletes all of that at the cost of one real capability (a live ticking
timer on the wrist) and one nice-to-have (heart rate, which needs Health Services and therefore a
native watch app regardless).

Also considered and rejected: **hijacking the media session.** `expo-audio` already registers an
`AudioControlsService` with `foregroundServiceType="mediaPlayback"`, and Wear shows transport controls
for an active media session, so next/prev could map to `advance`/`goPrev` for almost nothing. Don't —
it steals the media controls from whatever music the user is running the workout to, which is the
common case, not the edge case.

## What the remote can and cannot do

The runner's public surface already *is* the remote's vocabulary, verified at
[`use-session-runner.ts:618-648`](../src/hooks/use-session-runner.ts#L618-L648):

- `advance()` — exported four times over as `doneSet`, `logSet`, `skipRest` and `logInterval`. **It
  takes no arguments**; it reads `reps` / `weightKg` / `rpe` from runner state, which default to the
  step's target. This is what makes a data-free remote coherent: "Done" from the wrist logs the
  prescribed set.
- `goPrev()` — undoes exactly one `advance()`, un-flushing what it wrote.
- `addRestSeconds()`, `setPaused()`, `finishSession()`.

So the wrist gets three buttons: **Done / Next**, **Back**, **+30s**. Deliberately not on the wrist:

- **Any numeric edit** (reps, weight, RPE). That is the "no data on the watch" line, and it's also
  where the notification transport has nothing to offer — `NotificationAction.textInput` exists but
  prompting for a typed rep count on a watch is worse than reaching for the phone.
- **`finishSession()`**, which is destructive and has no confirm affordance in a notification. A
  mis-tap on the wrist would end a workout with no undo.
- **Pause.** Available and harmless, but three buttons is already the practical limit of what fits on
  a bridged notification card; pause loses to `+30s`, which is the one people actually reach for mid-rest.

## Platform facts, verified against the installed source

These were read out of `node_modules/expo-notifications` rather than the docs, because two of them
contradict the docs. Re-check them if the SDK moves.

| Fact | Where | Consequence |
|---|---|---|
| `NotificationRequestInput.identifier?: string` exists | `Notifications.types.d.ts:585-589` | A stable id per session, so each step *replaces* the card instead of stacking a new one |
| `categoryIdentifier` is tagged `@platform ios` in the types but **Android implements it** | `ArgumentsNotificationContentBuilder.java:142`, `ExpoNotificationBuilder.kt:39-74` | Action buttons do work on Android. The doc tag is wrong; the field sits on the shared `NotificationContentInput`, so typecheck passes anyway |
| Actions are built as `NotificationCompat.Action` | `ExpoNotificationBuilder.kt:77-80` | This is precisely the class Wear OS bridges to the wrist — the reason no watch code is needed |
| `opensAppToForeground: false` → `PendingIntent.getBroadcast`, no activity. `true` (**the default**) on API 31+ routes through `NotificationForwarderActivity`, which calls `openAppToForeground` | `NotificationsService.kt:478-489`, `NotificationForwarderActivity.kt` | **The single most important flag in the feature.** Left at its default, every wrist tap yanks the phone out of your pocket and onto the session screen |
| No chronometer / `usesChronometer` binding anywhere in the module | — | No live ticking countdown on the wrist. Shapes the content design below |
| Nothing in `src/` calls `setNotificationChannelAsync`, and the `app.json` plugin block sets only `icon` and `color` | `app.json`, grep over `src/` | Every step update would land on the default channel at default importance — a heads-up card and a **wrist buzz per step**. A dedicated channel is not optional polish |

One consequence worth stating plainly rather than burying: with `opensAppToForeground: false` the tap
arrives as a broadcast, and **if the app process is dead there is no JS runtime to receive it, so the
tap is silently dropped.** That is the safe direction — a lost tap, never a wrong advance — and during
a session the process is alive anyway (keep-awake is held, audio is initialised). Accept it; don't
build a foreground service to work around it in v1.

## Design

### The seam

The problem to solve is structural, not algorithmic: the response listener is module-level and global,
while the runner is a hook owned by the session screen — and `use-session-runner.ts` is the file
AGENTS.md flags as high-risk and make-or-break.

**Proposal: `src/hooks/session-remote.ts`**, a module holding a ref to the live runner's command
handlers. The session screen registers on mount and clears on unmount; the notification listener reads
the ref and calls through. The runner exports what it already exports and barely changes.

Rejected alternative: a zustand slice. Commands are transient events, not state, and a store write
would re-render the session screen on every wrist tap for nothing.

**Stale-tap guard.** Stamp each notification's `data` with `{ sessionStartedAt, stepIndex }`; the
handler drops any response whose `stepIndex` doesn't match the runner's current index. This closes the
only data-integrity risk in the feature — a queued or delayed tap becoming a double-advance — and it
is about ten lines. Do it in the same phase as the seam, not as a follow-up.

### What the card shows

No chronometer means the body is static for the life of a step. Per kind, keyed off the same data
`previewFor` already assembles: the exercise name (never translated — it's user data) as title, the
target as body (`Set 2 of 4 · 12 reps`). Rest is the awkward one, and there are two options:

- **(a) static `Rest · 60s`**, leaning on the existing scheduled rest-complete notification for the
  end cue. Zero extra cost, and it matches what that fallback already does.
- **(b) re-present every 10s** for a coarse countdown. Costs a notification write per 10s and risks a
  wrist buzz per write even on a silent channel.

**Take (a) for v1.** If the wrist ends up feeling dead during rests, (b) is a contained follow-up.

### Relationship to the existing rest notification

[`use-session-runner.ts:504-525`](../src/hooks/use-session-runner.ts#L504-L525) stays as-is — it does a
different job (a *scheduled future* cue, not a live card). But it must move onto the new channel with
everything else, or the two notifications will have different buzz behaviour for no reason the user
can see.

## The i18n bug this work has to fix on the way past

[`use-session-runner.ts:511`](../src/hooks/use-session-runner.ts#L511) passes the literals
`'Rest complete'` and `` `${workout.name} · back to work` `` straight into the notification. Those are
user-facing strings outside the locale bundles — against the house rule, and invisible to the suite
because no test drives that path in `pt`. This plan roughly triples the notification copy surface, so
fix it in the same PR rather than widening a known break. Budget ~10 new keys, in **both** `en.json`
and `pt.json`, which are kept at exact parity by hand.

## Phases

Each phase is independently verifiable, and phase 0 exists because two of its three answers would
change the design rather than just the code.

**Phase 0 — spike (half a day, throwaway branch).** Three questions, in order of how much damage a
"no" does:

1. Does re-scheduling with the same `identifier` **replace** the card on Android, or stack a second
   one? The type permits the id; replace-in-place is inferred from Android's `notify(id)` semantics and
   has not been run. A "no" means rethinking the whole update model.
2. Do a JS-registered category's actions actually render on Android, given the `@platform ios` tag?
   The native source says yes; confirm it rather than trusting a code read.
3. On a paired Wear OS emulator, do those actions appear on the wrist, and does tapping one with
   `opensAppToForeground: false` reach JS **without** opening the phone?

(3) needs a phone AVD + Wear AVD pair, which is the only unusual setup cost in the whole plan.

**Phase 1 — channel and i18n groundwork.** A dedicated LOW-importance channel with no vibration and
no sound; move the existing rest notification onto it; key the two hardcoded strings above.

**Phase 2 — the seam.** `session-remote.ts`, registration from the session screen, the stale-tap
stamp and guard. No notification changes yet; testable on its own.

**Phase 3 — the ongoing card.** A pure `step → { title, body, data }` builder, plus the present/update/
dismiss lifecycle tied to session start, step change and finish. Put the builder in a pure module in
the style of `session-steps.ts` — no native imports — or it can't be tested.

**Phase 4 — the actions.** Category registration, `opensAppToForeground: false`, the listener wired to
the seam.

**Phase 5 — the Settings toggle.** A permanent ongoing notification is intrusive for the majority who
have no watch. **Default it off**, one switch under Settings, described as what it is ("Show session
controls in the notification shade") rather than as a watch feature — it's equally useful on a phone
lock screen, and describing it as "watch" would puzzle everyone without one.

## Testing

Testable in jest, and worth it: the seam (register/clear, and a stale stamp being rejected — prove
that one fails against the bug it pins, per the house rule), the pure content builder, and the copy,
by driving a session in `pt`. An English-locale assertion cannot catch a hardcoded English string,
which is exactly how line 511 shipped.

Not testable there, so a manual checklist on the AVD pair: the card appears on the wrist, the three
buttons appear, a tap advances the phone **without** foregrounding it, the card replaces rather than
stacks across a whole workout, the wrist doesn't buzz per step, and the card is gone after
`finishSession`.

## Out of scope, deliberately

Named here so they're decisions rather than omissions:

- No watch app, no APK, no Play track, no Wear quality review, no Data Layer.
- No library or session data on the watch, in either direction.
- No rep / weight / RPE editing, and no finish-session, from the wrist.
- No live ticking timer — a hard platform limit of this transport, not a cut.
- No heart rate; Health Services needs a native watch app.
- **Android only.** Apple Watch bridges notifications too, but Play-only is the current distribution
  call and the iOS action and audio-session behaviour differ enough that designing for both here would
  be speculative.

## If it works, what it's worth

The seam and the stale-tap guard are the same two pieces a real Wear OS app would sit on; only the
transport would change. So the cheap version is a probe, not a dead end — it answers whether driving a
session from the wrist is something you actually reach for, in days rather than the weeks the
standalone app would cost to find out the same thing.
