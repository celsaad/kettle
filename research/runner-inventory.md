# Today's session runner, as a behaviour inventory

Research for #294 (map: #292, `docs/native-migration-plan.md` M6). An inventory of what the RN runner
does at commit `8af5e2b` (0.14.0), so each M6 hand-off can take its section as acceptance. It
records behaviour and decides nothing.

**How to read it**

- Every item cites `file:line` at `8af5e2b`. Paths are abbreviated: `runner` =
  `src/hooks/use-session-runner.ts`, `steps` = `src/hooks/session-steps.ts`, `outline` =
  `src/hooks/session-outline.ts`, `screen` = `src/app/session.tsx`, `store` =
  `src/state/session-history-store.ts`, `files` = `src/storage/session-files.ts`, `notif` =
  `src/hooks/safe-notifications.ts`, `sounds` = `src/hooks/use-session-sounds.ts`, `announce` =
  `src/hooks/use-session-announcements.ts`, components by their file name.
- **Pinned** means a jest case asserts it, cited as `T: <suite>:<line> "<case>"`. Suites:
  `runner.test` = `src/hooks/__tests__/use-session-runner.test.tsx`, `build.test` =
  `build-steps.test.ts`, `outline.test` = `session-outline.test.ts`, `screen.test` =
  `src/app/__tests__/session.test.tsx`, component suites under `src/components/__tests__/`.
  **Code only** means nothing fails if it changes. A Kotlin port has to re-derive those items from
  the code, so they are the ones to read most carefully.
- **BUG** marks behaviour not to copy. **GAP** marks something that looks unintended but has no
  ticket yet. Both are collected again at the end.

---

## 1. Step model (pure, goes to `:shared`)

Everything here is pure TypeScript with no I/O, and `build.test` and `outline.test` already exercise
it without native modules. It is the cheapest slice to port first and to hold to the same cases.

### 1.1 The step union

- [ ] Four kinds: `hold`, `reps`, `interval` (variants `hiit | emom | amrap | cardio`), `rest`
      (`steps:33-107`). Each carries `blockIndex`, `memberKey`, `exerciseId`, optional `circuit`.
      Work steps carry `exerciseName`, `setIndex`/`setTotal`, `notes`.
- [ ] `hold` carries `holdTargetSec` (= `hold_sec_min`, the mark), `holdTargetMaxSec`, and
      `holdEndSec` = `holdSecMax ?? holdSecMin`, or absent for a max-effort hold (`steps:42-52`,
      `204-205`). A 0 or negative end becomes "no end", so the set can still be run by hand
      (`steps:199-205`). T: `build.test:80-106` (`holdEndSec` block, including "absent for a 0
      target").
- [ ] `interval.countUp` is true only for cardio with no `duration_sec` (`steps:82`, `347-360`).
      T: `build.test:165` "counts up for cardio with no configured duration".
- [ ] `rest.standalone` separates a Rest block, which is its own logged entry, from rest between
      sets, rounds or members, which is folded into the set before it (`steps:90-107`).

### 1.2 Expansion (`buildStepsWithLimits`, `steps:536-633`)

- [ ] **reps / timed_hold:** `sets` steps, with a non-standalone rest *between* them (never after the
      last) only when `rest_sec > 0` (`steps:197-263`). T: `build.test:43-56`, `69-79`,
      `177-198` (zero-length rest omitted).
- [ ] **hiit:** `rounds` intervals of `work_sec`, rest between rounds only when `rest_sec > 0`
      (`steps:264-293`). T: `build.test:121-128`.
- [ ] **emom:** interval count = `emomIntervalCount(intervalSec, totalMinutes)`, floored, minimum 1;
      `targetSec` = `interval_sec`; carries `targetReps`; no rest steps (`steps:294-326`).
      T: `build.test:130-156`.
- [ ] **amrap:** one countdown interval of `time_cap_sec` (`steps:327-345`).
- [ ] **cardio:** one interval, countdown of `duration_sec` or count-up without one; carries
      `distanceMeters` (`steps:346-367`). T: `build.test:158-175`.
- [ ] **rest block:** one standalone rest of `duration_sec`, with a block `configOverride` able to
      replace it; a 0-second standalone rest is kept (`steps:368-381`). T: `build.test:200`.
- [ ] **Circuits:** true round-robin A,B,C,A,B,C for `rounds`. Each visit is exactly one set: the
      member's own `sets` and per-set rest are ignored. `setIndex/setTotal` become round/rounds.
      `memberKey` = `${block}:${memberIndex}` is stable across rounds, so the member logs one entry.
      Rest between members and between rounds only when configured, never after the last
      round. Circuit rest has `memberKey ${block}:circuit-rest` and the *position of the work it
      follows* (`steps:559-629`, `22-31`, `125-136`). T: `build.test:207-320`.
- [ ] **A hiit/emom circuit member runs its own full interval count on every visit**, because the
      visit flag only reaches `timed_hold`/`reps` (`steps:198`, `235` vs `266`, `306`). This is
      deliberate, and the ceilings below exist because of it (`steps:141-147`).
- [ ] Circuit members are numbered over the members that *resolved* to an exercise; a missing
      exercise is skipped, not counted (`steps:561-576`). T: `build.test:291`.
- [ ] Missing exercise in an exercise block: the block is skipped silently (`steps:548-549`).
      T: `build.test:327`.
- [ ] **Ceilings:** `MaxStepsPerExercise = 2000` (clamps any count), `MaxStepsPerWorkout = 20_000`
      (checked per block and per circuit member, so truncation stops at whole blocks or visits).
      Either one sets `truncated` (`steps:159-195`, `540-591`). EMOM flooring is *not* truncation
      (`steps:302-308`). T: `build.test:588-700`.
- [ ] `buildSteps` = the steps alone (`steps:636-638`). The screen uses `buildStepsWithLimits` for
      the "too long" gate (§3.1); the runner rebuilds with `buildSteps` once, at mount (`runner:191`).

### 1.3 Mid-session mutation (pure, `steps:385-528`)

- [ ] `setStepsForMember`: counts a member's reps/hold steps (`steps:398-407`). T: `build.test:448-457`.
- [ ] `addSetForMember`: clones the member's last set after it, led by a clone of one of the
      member's own rests if it has any. A `rest_sec: 0` exercise gets no rest. Then renumbers
      `setIndex/setTotal` 1..n (`steps:414-444`). T: `build.test:337-402`.
- [ ] `dropLastSetForMember`: removes the last set and the rest that led into it; never drops the
      only set (`steps:453-464`). T: `build.test:404-446`, including the add/drop round trip.
- [ ] `swapExerciseForMember`: replaces the member's remaining steps from `fromIndex` with the new
      exercise's expansion under a new key. A set-based substitute is resized to the *remaining set
      count*; any other type runs its own configured shape (`steps:481-528`). T:
      `build.test:459-586`.
- [ ] None of these may target a circuit member, and the caller enforces it (`steps:391-395`,
      `runner:960-962`).

### 1.4 The Coming up outline (`outline:128-172`, pure)

- [ ] Built from the **live step list**, never `workout.blocks`, so swaps, added sets, ad-hoc queues
      and truncation come out right (`outline:7-11`). T: `outline.test:303-340`.
- [ ] Item kinds: `exercise` (with `left`, `unit`, `started`), standalone `rest`, `round` (current
      circuit's first round with work left, members spelled out), `moreRounds` (collapsed), and
      `circuit` (an unreached circuit, collapsed) (`outline:31-44`).
- [ ] Units: `set` for reps/hold, `round` for hiit, `minute` for an EMOM only at a 60s interval and
      `interval` otherwise, `null` for one-shot work (`outline:46-54`). T: `outline.test:155-182`.
- [ ] Inter-set and circuit rest are never listed (`outline:147-150`). T: `outline.test:131`.
- [ ] Circuit rounds come from `step.circuit.round`, not from counting steps, and HIIT members fold
      to one entry per round (`outline:56-119`). T: `outline.test:233-252`.
- [ ] Never throws. Past the end (a parked ad-hoc session) or an empty list gives `[]`
      (`outline:121-127`). T: `outline.test:342-356`.

### 1.5 Previews and labels (pure helpers in the runner file)

- [ ] `previewFor` / `upcomingPreview`: the Next card skips one immediately following rest and
      previews the next real work; rest itself previews nothing (`runner:122-161`). Code only (the
      card's rendering is pinned, the lookahead is not).
- [ ] `formatHoldTarget` (`15–25s`, `15s`, or null for max effort) and `formatRepsTarget`
      (`8–12`) (`steps:115-123`), shared by the Next card and the sheet.
- [ ] `restFollows` = the next step is a rest, which picks "Log set → Rest" vs the no-rest label
      (`runner:1131-1134`). T: `runner.test:657-687`.

---

## 2. Service (clock, writes, cues)

What the foreground service has to own. Most items here are stateful and timing-bound, and several
are code only.

### 2.1 Session lifecycle

- [ ] **The session file is created when the runner mounts, which is after the 3-2-1 count-in**,
      not when the screen opens (`runner:344-352`, `screen:247-255`). Leaving during the count-in
      writes nothing. A real workout with zero blocks creates nothing; an ad-hoc session creates
      its file at once with `workout: null` (`runner:345-349`). T: `runner.test:1618`,
      `screen.test:423` "lets the count-in go without asking".
- [ ] `startSession` stamps an ISO id (`:`/`.` → `-`), `startedAt`, `endedAt: null`, empty entries,
      flushes the file and sets `activeSessionId` (`store:144-149`, `files:76-97`).
- [ ] Notification permission is requested on every runner mount (`runner:340-342`). Code only.
- [ ] `priorSessions` (for "last time" and the PR marker) is snapshotted once at mount and never
      read live (`runner:204-215`). T: `runner.test:1117` "does not change when a set is logged
      mid-session".
- [ ] **Natural completion:** advancing past the last step of a real workout sets `finishedRef`,
      calls `completeSession` and then `onComplete(session)` (`runner:648-662`). T:
      `runner.test:620`.
- [ ] **Ad-hoc parking:** an ad-hoc session runs past its last step to `steps.length` and waits
      there rather than completing (`runner:649-657`). `addExercise` appends at exactly that index
      under key `adhoc<n>` (`runner:1043-1062`). T: `runner.test:1612-1716`.
- [ ] **Finish on demand:** commits the step on screen, read via `stepIndexRef` rather than the
      render's step, then completes (`runner:867-879`). A second call is a no-op (`finishedRef`,
      `runner:306-316`). T: `runner.test:860-889`.
- [ ] **Unmount net:** if the runner unmounts with the session open, it stamps `endedAt` *without*
      committing the step on screen (`runner:354-377`). T: `runner.test:891-925`.
- [ ] **Render-throw path:** the route's `ErrorBoundary` calls `abandonActiveSession`, which stamps
      the in-flight session by id. It is idempotent, because the unmount net usually got there first
      (`screen:32-66`, `store:185-201`). T: `screen.test:436-468`.

### 2.2 Wall-clock maths

- [ ] Phase timing is anchored to timestamps: `phaseStartedAtRef`, `pausedAtRef`, `pausedMsRef`,
      `stepEndSecRef` (`runner:328-333`). Elapsed = `floor((now|pausedAt − start − pausedMs)/1000)`,
      floored at 0 (`runner:335-338`). T: `runner.test:234-254`.
- [ ] All four are re-seeded **during render** when the step *identity* changes. Identity is
      `index:memberKey:kind:setIndex`, so a swap or an ad-hoc add re-seeds without the index
      moving, and add/drop set doesn't (`runner:379-453`). T: `runner.test:1718-1760`. The initial
      `''` makes the first step seed too, without which a countdown first step auto-advanced on its
      first tick (`runner:394-397`). T: `runner.test:214-232`.
- [ ] `stepEndSec` = rest `seconds`, countdown interval `targetSec`, hold `holdEndSec ?? 0`, else 0
      (`runner:409-420`).
- [ ] Display state: `holdElapsedSec` counts *up* for holds and count-up cardio;
      `restRemainingSec`/`restTargetSec` count *down* for rest and countdown intervals
      (`runner:217-233`, `448-452`). **An auto-ending hold still counts up on screen**, because the
      number shown is the number logged (`runner:218-222`).
- [ ] **1 Hz `setInterval` tick**, not aligned to second boundaries, torn down and recreated on every
      step or pause change (`runner:669-716`). It refuses to act once the index has moved or the
      session has finished (`runner:671-677`).
- [ ] Countdown steps advance at `remaining <= 0` (`runner:700-703`). T: `runner.test:235`.
- [ ] Holds with an end advance at `elapsed >= holdEndSec`, i.e. at the *top* of a range
      (`runner:691-698`). T: `runner.test:397-424`.
- [ ] Max-effort holds and count-up cardio never advance by themselves (`runner:224-233`).
      T: `runner.test:418`, `287`.
- [ ] **+30s on rest:** `addRestSeconds(30)` moves `stepEndSecRef` and `restTargetSec`, which
      re-runs the notification effect (`runner:1080-1087`, `780-785`, `session-rest.tsx:76`).
      T: `runner.test:1015-1043`.
- [ ] **Prev:** moves the index back by one, floored at 0 (`runner:861-863`). T: `runner.test:780`.
      Prev on step 0 keeps the same identity, so the clock is *not* restarted. Code only.

### 2.3 Pause

- [ ] `togglePause` banks paused time into `pausedMsRef` on resume; the ref mutation happens inside
      the `setPaused` updater (`runner:787-798`). T: `runner.test:243` "excludes paused time".
- [ ] While paused: no tick (`runner:670`), no foreground catch-up (`runner:737`), and the scheduled
      notification is cancelled and not rescheduled (`runner:755`). Code only.
- [ ] **BUG (pause carry-over).** `paused` is one session-wide flag. The step-change block re-seeds
      the new step *as paused* if the flag is set (`runner:407`), and nothing clears it. Pause a
      HIIT interval, a countdown interval or a hold, and then move on: the next step starts paused.
      **The rest screen has no Pause/Resume control** (`session-rest.tsx:21-28`, `64-83`), so a rest
      that starts paused sits at its full time with no visible sign until Skip or Prev. It also gets
      no background notification. Unpinned: no test covers pause across a step change. Raised in
      #295 as a port decision.

### 2.4 Backgrounding (`AppState`)

- [ ] On `active`, recompute from the wall clock and catch up a step that ended while away: an
      expired countdown or hold advances; otherwise the display re-syncs (`runner:718-750`).
      T: `runner.test:256-330`.
- [ ] **Catch-up advances at most one step.** A long rest that ended in the background advances to
      the next step, which starts fresh from the moment of return; the time away is not carried
      forward into later steps (`runner:405-408`, `741-745`). Code only, and probably what native
      should do on purpose rather than by accident.
- [ ] **Batching guard.** RN can deliver a queued tick and the `AppState` event in one batch before
      React re-renders; both read the *old* step's clock. Both effects capture `indexAtSetup` and
      refuse to act once the eager `stepIndexRef` has moved, and `finishedRef` covers the last step
      (`runner:292-305`, `306-316`, `726-733`, `677`, `738`). Without this, a step nobody performed
      was committed at 0 and skipped. T: `runner.test:332-395` (both orders, plus completion).
- [ ] Logged values are clamped to the configured end, so a 25 s plank noticed after 60 s away logs
      25: holds (`runner:514-521`) and countdown cardio (`runner:558-566`). Rest is **not** clamped:
      `restTakenSec` is the real time that passed (`runner:563-564`, `580`). T: `runner.test:275`,
      `298`, `310`.

### 2.5 Writes (write-through, one file per session)

- [ ] **Every commit reaches disk immediately.** Each member's accumulating log (`memberSetsRef`,
      `memberHiitRoundsRef`, `memberEmomMinutesRef`) is a working copy. `persistMember` appends the
      member's entry the first time and rewrites it in place after that, tracked by
      `entryIndexRef` (`runner:264-283`, `473-499`). T: `runner.test:462-532`.
- [ ] Entries are only appended at the end or removed from the end, so recorded indices stay valid
      (`runner:278-283`). T: `runner.test:1550`.
- [ ] Every write goes through `writeSession`, which never throws: a failure is recorded, stepped
      over, and surfaced later in History's errors (`files:56-74`, `store:94-107`). Code only here;
      covered by storage tests.
- [ ] Commit per kind (`commitCurrentStep`, `runner:501-604`):
  - [ ] **reps:** `{reps, weightKg || undefined, rpe, restTakenSec: 0}`. Bodyweight (0) is logged as
        an absent weight (`runner:522-531`). T: `runner.test:545`, `606`.
  - [ ] **hold:** `{holdSec: min(elapsed, holdEndSec), restTakenSec: 0}` (`runner:513-521`).
        An auto-end and a tapped Done log the same set. T: `runner.test:426`.
  - [ ] **hiit:** increments the member's rounds and rewrites `{type: hiit, roundsCompleted}`
        (`runner:540-542`). T: `runner.test:494`, `535`.
  - [ ] **emom:** pushes `{reps: reps || undefined}` per interval (`runner:543-547`). T:
        `runner.test:581-604`.
  - [ ] **amrap:** one-shot `{roundsCompleted, extraReps || undefined}`, dropped when both are zero
        (`runner:548-556`). T: `runner.test:847`.
  - [ ] **cardio:** one-shot `{durationSec, distanceMeters}`, skipped entirely at 0 s even when a
        distance is configured (`runner:557-577`). T: `runner.test:841`.
  - [ ] **standalone rest:** one-shot `{type: rest, restTakenSec: elapsed}` (`runner:581-586`).
  - [ ] **inter-set rest:** writes `restTakenSec` onto the member's last logged set and rewrites the
        entry. It writes nothing if that set was dropped as zero (`runner:587-599`). T:
        `runner.test:484`, `811`.
- [ ] **Zero-work trimming at write time:** a 0-rep or 0-second set is never written, and its
      following rest goes with it (`droppedLastSetRef`, `runner:284-289`, `532-534`,
      `logged-work.ts:15-17`). EMOM and HIIT are exempt (`logged-work.ts:11-13`). T:
      `runner.test:792-858`. At **read** time, `loggedSessions` also strips zero work and hides a
      finished session with no work, so no training view sees it (`logged-sessions.ts:18-27`).
- [ ] **Undo (Prev), one level deep:** `lastCommitRef` records what the last `advance()` committed.
      Prev straight after it pops that set, round or minute and rewrites the entry, or retracts the
      entry if it was the member's first, or removes a one-shot entry. A second Prev only moves the
      index (`runner:100-113`, `800-865`). A zero set or an inter-set rest leaves nothing to undo
      (`runner:631-643`). T: `runner.test:726-790`, `827`.
- [ ] **Every step-list mutation clears `lastCommitRef`** (`mutateSteps`, `runner:973-985`). This is
      flagged as "the single most dangerous line" (`runner:974`). T: `runner.test:1336`, `1572`.
- [ ] **Swap issues a fresh member key** `${key}~swap<n>`, so work done before the swap keeps its
      own entry and the substitute appends a new one (`runner:1022-1033`, `1064-1078`). T:
      `runner.test:1528-1610`.
- [ ] **Load carries across sets** of the same member (last logged `weightKg`, else the target);
      **reps re-seed from the target** every set; EMOM minutes seed from `targetReps` (`runner:425-445`).
      T: `runner.test:561-604`.
- [ ] RPE defaults to 8 and is **not** re-seeded per step, so it carries for the whole session
      (`runner:198`, `424-452`). Code only.
- [ ] **`setTargetWeight` (#220):** "adopt last time's load" sets the weight and sends
      `{type: setTargetWeight, exerciseId, weightKg}` in kilograms, without waiting for a reply.
      Reps only, and only when last time had a load (`runner:932-949`,
      `brownfield-module.ts:42-56`). T: `runner.test:1216-1265`.
- [ ] **`sessionFinished` → `backupResult` (#220):** `completeSession` writes `endedAt`, clears
      `activeSessionId` and `backupFailed`, *then* sends `sessionFinished`. Native backs up off the
      main thread and answers `backupResult{failed}`, which sets `backupFailed` (`store:174-184`,
      `brownfield-module.ts:30-40`, `safe-brownfield.ts:40-43`). The abandon path doesn't send it
      (`store:185-201`). Code only on the RN side.

### 2.6 Cues

- [ ] **Sounds** (`sounds:35-68`): three level-matched players: `tick`, `exercise-change` and
      `milestone` (`sounds:6-8`, `22-27`). The `sessionSounds` preference gates all three callbacks
      in this one place, and a mid-session toggle applies on the next cue (`sounds:29-34`, `46-62`).
      Audio mode is `playsInSilentMode: true, interruptionMode: 'duckOthers'` (`sounds:41-44`).
      T: `use-session-sounds.test.ts:44-91`.
  - [ ] **Tick:** each count-in numeral, 3 then 2 then 1 (`session-countdown.tsx:22-40`); the last 3 s
        of every countdown step (`runner:711`); the last 3 s before a hold's end (`runner:692-696`).
        T: `runner.test:439-452` (holds only; rest and interval ticks are code only).
  - [ ] **Exercise change:** when the count-in ends (`session-countdown.tsx:31-33`), and on an
        `advance()` whose next step has a different `memberKey` (`runner:621-626`). So it sounds on
        every circuit hand-off and for a rest that belongs to a different key, but not between sets.
        Nothing sounds at completion. Code only.
  - [ ] **Milestone:** a HIIT work interval at half its `targetSec` (`runner:705-710`); a hold
        crossing `holdTargetSec`, but only when that is below the end, so a fixed target doesn't
        double-cue (`runner:683-690`). Once per *visit*: it resets on identity change, so a step
        redone with Prev sounds again (`runner:237-256`, `423`). T: `runner.test:927-1013`.
- [ ] **Haptics:** one `impactAsync(Medium)` on every `advance()`, tapped or automatic, and errors
      are swallowed (`runner:608`). It isn't gated by the sounds preference, and Prev, Finish and
      the count-in fire none. Code only (mocked, never asserted).
- [ ] **Background cue notifications** (`runner:752-785`, `notif`):
  - [ ] Scheduled for **every self-ending step, foreground or not**: rest, countdown intervals, holds
        with an end. The delay is `max(1, stepEnd − elapsed)`. Cancelled in the effect cleanup on
        step change, pause, +30 s, a preference change or unmount, including a late-resolving
        schedule (`cancelled` flag) (`runner:754-779`). T: `runner.test:1027` (reschedule on +30 s);
        cancellation is code only.
  - [ ] Foreground notifications are suppressed (no banner, list, sound or badge) by the handler, so
        a cue arriving in the foreground is invisible (`notif:25-32`, `106-107`).
  - [ ] Content: `interruptionLevel: 'timeSensitive'` (needs the iOS entitlement in `app.json`).
        Sound on means `sound: 'default'`. Sound off means `sound: false` on iOS and **no `sound` key
        on Android**, where `false` would make the notification silent and drop the heads-up
        (`notif:50-93`). T: `safe-notifications.test.ts:32-86`, `runner.test:1045-1063`.
  - [ ] **No Android channel id is passed**, so the cue goes on expo-notifications' fallback channel,
        `expo_notifications_fallback_notification_channel` (`notif:118-121`;
        `node_modules/expo-notifications/.../BaseNotificationBuilder.kt:137`). Users' per-channel
        settings live on that id, which bears on #296's channel question.
  - [ ] **GAP:** the title is "Hold complete" for holds and "Rest complete · back to work" for
        *every other* self-ending step (`runner:759-765`, `en.json` `session.notification`), so a
        HIIT work interval, an EMOM, an AMRAP cap or a cardio duration ending in the background reads
        "Rest complete". Code only.
  - [ ] Expo Go is detected and the module is never required there; every call degrades to a no-op
        (`notif:1-38`, `40-48`, `127-131`).
- [ ] **Keep-awake:** `useKeepAwake` covers the whole session route: count-in, runner and completion
      screen (`screen:69-77`). Mocked in tests, never asserted.

---

## 3. UI (screens, sheets, pickers, guard, completion, announcements)

### 3.1 Before the first step (`screen:68-255`)

- [ ] Route params: `workoutId` | `programId`+`week`(+`day`) | `adhoc=1`. With none, it falls back to
      the library's first workout (`screen:89-120`). Program weeks resolve through
      `resolveWorkoutForWeek`, so overrides apply to the step list and to swap candidates.
- [ ] Library still loading, or an id that resolves to nothing: renders nothing (`screen:176-179`).
      T: `screen.test:123`.
- [ ] **Rest week:** explains it rather than rendering blank (`screen:122-174`). T: `screen.test:157`.
- [ ] **Nothing to run** (zero steps, not ad-hoc): an empty state with Close, before any count-in
      (`screen:181-205`). T: `screen.test:130-155`.
- [ ] **Too long** (`truncated`): a screen before the count-in with "Start anyway" and Close
      (`screen:207-245`). T: `screen.test:536-598`.
- [ ] **Count-in:** "Get ready", the workout name as headline, 3-2-1 at 1 s each, a first-session
      hint (only with no logged sessions and sounds on), then the runner mounts
      (`session-countdown.tsx:17-67`, `first-session-hint.tsx:67-76`). T: `screen.test:184`,
      `first-session-hint.test.tsx`.

### 3.2 The runner frame (`screen:315-725`)

- [ ] Header: a workout-name button with a chevron that opens Coming up (hint, `expanded` state,
      `hitSlop` to 44px), block progress, ADD (ad-hoc only) and Finish (`screen:488-540`).
- [ ] Circuit crumb: "Round n of N" plus member dots as one accessible node, with an invisible
      Finish spacer to keep the dot tracks aligned (`screen:541-590`). T: `screen.test:242-307`.
- [ ] Block progress draws nothing below 2 blocks; ad-hoc counts added exercises
      (`session-progress.tsx:52`, `runner:1101-1103`). T: `session-progress.test.tsx`.
- [ ] Step-kind dispatch to `SessionHold` / `SessionReps` / `SessionInterval` / `SessionRest`
      (`screen:593-684`). T: `screen.test:193-240`.
- [ ] **The runner is always dark** (`RunnerColors`), whatever the scheme.

### 3.3 Per-step screens

- [ ] **Hold** (`session-hold.tsx`): counts up; bar scaled to the *top* of the range, with a marker at
      the minimum only for a real range, and no bar for max effort. Prev (icon, labelled), Pause or
      Resume, and Done as the primary button. Shows the previous set read-only and a PR badge
      (`session-hold.tsx:85-106`, `141-155`, `196-222`). T: `session-hold.test.tsx`.
- [ ] **Reps** (`session-reps.tsx`): reps −/+ with the numeral opening the number pad; load −/+ in
      *display* units (2.5 kg or 5 lb steps; kg stored) with the numeral opening a decimal pad; RPE
      7/8/9 (selected state); last time's line, tappable to adopt a load; PR badge; Prev and "Log set"
      (`session-reps.tsx:16`, `81-99`, `138-310`). **No Pause** (no clock). T:
      `session-reps.test.tsx`.
- [ ] **Interval** (`session-interval.tsx`): variant pill; "Round / Minute / Interval n of N" (Minute
      only at a 60s EMOM); a reps-this-minute stepper for EMOM; rounds and extra-reps steppers for
      AMRAP; Pause/Resume; Done is filled for count-up and a quiet "skip" link for countdowns
      (`session-interval.tsx:148-165`, `211-277`). T: `session-interval.test.tsx`.
- [ ] **Rest** (`session-rest.tsx`): a pulsing pill (static under reduced motion), countdown
      against its total, Next card, Prev, +30s and Skip. **No Pause** (see the BUG in §2.3)
      (`session-rest.tsx:28-86`).
- [ ] **Next card:** previews the next real work with its line drawing. The drawing drops before the
      card does at large font scales (`session-next-card.tsx:45-52`). T: `session-next-card.test.tsx`.
- [ ] **Set count row** (reps and hold): "Set n of N", −/+ to drop or add a set (hidden in a circuit;
      − disabled, not hidden, at the floor), and SWAP (`session-set-count.tsx:30-73`,
      `runner:951-971`). The floor is logged sets + 1 (`runner:963-971`). T: `runner.test:1267-1427`,
      `session-reps.test.tsx:110-145`.
  - [ ] **BUG (#263):** − and + are drawn 26px, 4px apart, each with `hitSlop={12}`, so their slops
        overlap by about 20px, and SWAP's overlaps +. − removes a set (`session-set-count.tsx:39-71`,
        `96-105`). Layout; verify on a device.
  - [ ] GAP (doc drift only): the `canSwap` prop comment says "no other exercise of this type"
        (`session-set-count.tsx:14`), but swap offers any type since the rule changed
        (`runner:999-1018`).
- [ ] **Live PR marker:** prospective, strictly greater, raised by this session's own better sets;
      bodyweight competes on reps, loaded on load (`runner:888-930`). T: `runner.test:1143-1214`.
- [ ] **Last time:** matched on set number, falling back to the final set (`runner:881-886`).
      T: `runner.test:1065-1141`, `1386`.

### 3.4 Number pad (`session-number-pad.tsx`)

- [ ] A custom keypad overlay rather than the OS keyboard. Typing *replaces* the value; max 6 chars;
      one decimal point and only where allowed; confirm disabled on NaN or negative; backdrop and
      Cancel dismiss (`session-number-pad.tsx:21-117`). Reps round to whole numbers on confirm
      (`session-reps.tsx:320-324`, `session-interval.tsx:293`). Code only (no suite).
- [ ] The parent renders the pad at the screen root, because an overlay inside a stepper would be
      clipped (`session-interval.tsx:39-43`).
- [ ] **GAP:** the pad's open state is local to the step screen, and the leave guard doesn't know
      about it (`screen:421-426`). So Android back with the pad open opens "Finish session?" rather
      than closing the pad. The pad also has no `BackHandler`. Code only.

### 3.5 Pickers (`session-exercise-picker.tsx`)

- [ ] One component for both modes: **swap** ("Replacing <name>") and **ad-hoc add**
      (`session-exercise-picker.tsx:11-17`, `80-88`). Candidates arrive filtered and ordered;
      search appears above 8 candidates and matches anywhere, ignoring case and accents
      (`:23-24`, `52-53`). T: `session-exercise-picker.test.tsx`.
- [ ] Swap candidates: every library exercise except itself and `rest`, same type first; offered only
      on reps and hold steps outside circuits (`runner:999-1020`, `screen:477`). T: `runner.test:1436-1507`.
- [ ] Add candidates: every exercise except `rest` (`screen:357-359`).
- [ ] **The swap picker is bound to the step it opened on** and closes itself when that step ends,
      for example a hold ending under it, and doesn't reopen on the next set (`screen:332-341`).
      T: `screen.test:705-730`.
- [ ] While a picker is open the runner body is `aria-hidden` (`screen:484-487`, `436-438`). T:
      `screen.test:499`, `691`.

### 3.6 Coming up sheet (`session-upcoming.tsx`, `screen:343-356`, `712-722`)

- [ ] Opens from the workout name. **Bound to the step index it opened on**, and closes itself when
      the step changes; it doesn't reopen when Prev returns to that step (`screen:343-354`). T:
      `screen.test:600-663`.
- [ ] A read-only timeline. Its NOW row shows the live clock: rest counting down against its total,
      a hold counting up, nothing for reps (`session-upcoming.test.tsx:106-133`). Ad-hoc gets no
      "end" node (`screen:716`). T: `session-upcoming.test.tsx:135`.
- [ ] Closes from ×, the backdrop and **Android back**: its own `BackHandler` returns true
      (`session-upcoming.tsx:47-55`). T: `session-upcoming.test.tsx:142-170`.
- [ ] The runner beneath is `aria-hidden` while it's open (`screen:481-483`). T: `screen.test:622`.

### 3.7 Leave guard and the stranded session

- [ ] **Guard:** `usePreventRemove` (on everywhere but web) catches back and iOS swipe-down. It closes
      the Coming up sheet first, then the add picker, then the swap picker, and only then opens the
      Finish dialog (`screen:407-426`). T: `screen.test:365-421` (through a stand-in, not real
      gestures: `src/test-support/expo-router.ts:39-56`).
- [ ] The guard lives in `ActiveSession`, so the count-in and the completion screen are unguarded
      (commit `0b7d5c5`). T: `screen.test:423`.
- [ ] **Finish dialog:** "Finish session?", saying everything logged *including the set you're on*
      is saved; Cancel or Finish (destructive) (`screen:400-405`). Finish commits the step on screen.
- [ ] **The ad-hoc parked screen's Finish skips the dialog** and calls `finishSession` directly
      (`screen:451`). Code only.
- [ ] **A navigate message over the runner (#152 rule):** `safe-brownfield` dismisses everything
      above the tabs; the runner's guard holds that dismissal, so the sheet closes first, then
      "Finish session?" is asked. If the dismissal is refused, **the message is dropped**, and the
      user's answer decides (`safe-brownfield.ts:22-62`, `native-migration-plan.md:283-290`).
      Code only on the RN side; device-verified in M3.
- [ ] **BUG (stranded session), mostly closed:** `docs/up-next-plan.md:270-281` found that back or
      swipe left a session with no `endedAt` (zero minutes, skipped by `exerciseHistory`).
      `0b7d5c5` fixed it in code with the guard plus the unmount net (§2.1). **Still open:**
      - the gestures were never checked on a device (`docs/open-work.md:339-343`);
      - **process death or a swipe from Recents still strands the session**: neither the unmount
        net nor the boundary runs, so the file keeps `endedAt: null` with its sets on disk, and
        there is no recovery or resume (`runner:370-377` only runs on a React unmount);
      - the number-pad GAP above.
      What the port should *not* copy is the dependency on a React unmount to close a session.

### 3.8 Completion (`session-complete.tsx`, `screen:257-313`)

- [ ] Shown in place of the runner once `onComplete` fires; the runner unmounts, which is what keeps
      Prev from undoing into an ended session (`runner:805-809`). Done returns via `router.back()`
      (`screen:264`). T: `screen.test:309-363`.
- [ ] A checkmark, "Session complete" and the workout name (verbatim, or the ad-hoc stand-in via
      `formatSessionName`).
- [ ] **Records:** `sessionRecords(session, sessions, exercises)` (heaviest set with an Epley 1RM
      estimate, most reps, longest hold, most rounds), each with a text RECORD badge, in display
      units. The 1RM attribution note appears once, and only when a 1RM is shown
      (`session-complete.tsx:29-105`). Subscribes to the log only after the workout
      (`screen:283-313`). T: `session-complete.test.tsx`, `screen.test:324-363`.
- [ ] **Backup warning:** one line when `backupFailed`, pointing to Settings; never a dialog
      (`session-complete.tsx:107-114`, `screen:302-306`). Code only (no case passes
      `backupFailed: true`).

### 3.9 Error boundary (`screen:32-66`)

- [ ] Stamps the in-flight session ended (`abandonActiveSession`) and offers only "Go to History"
      (`dismissTo('/history')`). **No retry**, because remounting would start a second session file.
      The set in progress is lost, and the copy doesn't claim otherwise. T: `screen.test:436-468`.

### 3.10 Screen-reader announcements (`announce:18-46`, `screen:361-397`)

- [ ] One sentence per **step**, never per tick. Hold: name, set n of N, hold <end> s (or "as long as
      you can"); reps: target reps; interval: n of N, seconds; rest: "Rest, n seconds" (`screen:366-395`,
      `en.json` `session.announce`). The hold announces its *end*, not its minimum (`screen:371-372`).
- [ ] Only while a screen reader is running; the async check is held in state so the first step's
      announcement isn't dropped; deduped on the string (`announce:18-45`). T:
      `use-session-announcements.test.ts:21-48`.
- [ ] The rest announcement uses the configured `seconds`, not the +30s-adjusted target
      (`screen:392-393`). Code only.

### 3.11 Accessibility and i18n carried by the runner

- [ ] Every control has a role; icon-only controls (Prev, −/+, pad keys) have labels; text buttons
      take their name from their children; RPE uses `selected` and the header button `expanded`
      (throughout, e.g. `session-reps.tsx:182-310`, `screen:495-500`).
- [ ] Numerals cap `maxFontSizeMultiplier` at 1.3 (`session-rest.tsx:55`, `session-countdown.tsx:57`).
- [ ] Every string is in the locale bundles; exercise and workout names render verbatim. T: each
      suite's "in the active locale" case.

---

## Known bugs and gaps, collected

| | Where | Status |
|---|---|---|
| **BUG** Pause carries across step changes; a rest after a paused step starts paused with no Resume on screen and no background cue | `runner:407`, `session-rest.tsx:21-28` | Unpinned, open in #295 |
| **BUG** #263: set −/+ (and SWAP) touch targets overlap | `session-set-count.tsx:39-71`, `96-105` | Open, layout |
| **BUG** Stranded session: back/swipe fixed in code (`0b7d5c5`), device check open; **process death still strands** | `screen:421`, `runner:370`, `open-work.md:339` | Partly open |
| GAP Back with the number pad open asks "Finish session?" instead of closing the pad | `screen:421-426`, `session-number-pad.tsx` | Untracked |
| GAP Background cue says "Rest complete" for any non-hold step (HIIT work, EMOM, AMRAP, cardio) | `runner:759-765` | Untracked |
| GAP `canSwap` prop comment says same type only; the code allows any type | `session-set-count.tsx:14` | Comment drift |

## What bears on #295–#297

- **#295 (live state, process death).** The state a service has to hold is exactly the refs listed in
  §2.2 and §2.5: step list (mutable), index, `phaseStartedAt`, `pausedAt`, `pausedMs`, `stepEndSec`
  (moved by +30 s), the three per-member logs, `entryIndex`, `droppedLastSet`, `lastCommit`, swap and
  ad-hoc counters, and the transient reps/load/RPE/rounds values. **The session file alone can't
  rebuild that.** It has no step index, no phase start, no pause and no undo record, and zero sets
  are never written, so the position can't be inferred by counting entries. Today a kill leaves
  the file open with no recovery.
- **#296 (cues).** Today's background cue is a per-step scheduled notification, re-armed on every
  step, pause and +30 s, and suppressed in the foreground. The in-app sounds stop once JS suspends.
  A service that keeps playing sounds changes what "backgrounded" means. The Android channel is
  expo's fallback id. Haptics ignore the sound preference. Keep-awake covers the count-in and the
  completion screen too.
- **#297 (open, leave, hand back).** The file is created *after* the count-in; the guard order is
  sheet, then add picker, then swap picker, then dialog (and should learn about the pad); the
  ad-hoc Finish has no dialog; navigate messages over the runner are dropped when the guard holds;
  `sessionFinished` goes out only after `endedAt` is written, and not on the abandon path;
  `setTargetWeight` is fire-and-forget.

## Hard to reproduce natively

- **Render-time re-seeding keyed on step identity** (`runner:399-453`): native needs an explicit
  "step identity changed" event that fires on swap and ad-hoc add without an index move, and *not*
  on add/drop set.
- **Floor semantics and a free-running 1 Hz tick:** display and logged values both use
  `floor(elapsed)`. A native `Chronometer` or an aligned ticker will differ by up to a second at
  boundaries, which moves where the 3-2-1 ticks and the HIIT midpoint land.
- **One-level undo against write-through**, including retracting a member's whole entry and
  forgetting its index (`runner:845-857`). This is the most intricate state machine in the file
  and the best-tested one (`runner.test:726-790`, `1336`, `1572`), so port the tests with it.
- **Batched-event staleness:** the Kotlin analogue is two coroutines (ticker, lifecycle resume)
  racing on one step. The guard has to be "has this step already been left", not just a mutex.
