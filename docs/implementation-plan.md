# Kettle — Implementation Plan

Plan for building out the four workstreams the README originally listed as *not yet implemented*:
reading/writing `exercises.yaml` and `sessions/`, library import/merge, export, and the
wall-clock-drift-hardened timer engine (keep-awake, background notifications) — plus wiring the
library/build CRUD screens.

**Status: all workstreams (A–F) are implemented.** `npm run typecheck` and `npm run lint` are clean.
See [`exercise-tracker-product-plan.md`](exercise-tracker-product-plan.md) for the product model this
plan implements.

**Since this plan was written, later work (not tracked as workstreams here) shipped:** the interval
session runner for `hiit`/`emom`/`amrap`/`cardio`, multi-week programs with per-week overrides and
multi-session-per-week support, circuits/supersets, a workouts list with create/edit/**delete**
(replacing the single hardcoded workout), a pre-session 3-2-1 countdown with audio cues,
finish-session-early, exercise delete (mirroring the workout-delete confirm/in-use-guard pattern), and
a one-level `goPrev()` un-flush fix in the session runner. See `git log` for the individual commits.
§"What's genuinely left" below is kept current against all of that, not just workstreams A–F.

## Decisions (settled)

- **Scope:** everything in the README — persistence, timer engine, import/merge, export, and
  library/build CRUD.
- **Config key style:** hand-editable YAML stays **snake_case** (`work_sec`, `hold_sec`) per the
  product plan; in-code domain objects stay **camelCase** (`workSec`, `holdSec`). A mapping layer
  (`src/domain/yaml-mapping.ts`) bridges the two at the file boundary.

---

## 0. Dependencies & config

**Installed** (all via `npx expo install`, so versions are SDK-57-pinned): `js-yaml`, `zod`,
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
- ⚠️ **Web is not a persistence target.** `expo-file-system` has no web implementation. The storage
  layer detects this (`isFileStorageSupported` in `paths.ts`) and degrades gracefully: on web,
  `loadLibrary()` returns the seed library in-memory (no persistence, matching the old mock-data web
  experience) and `listSessions()`/all writes are no-ops instead of throwing. This was a real bug
  caught mid-implementation — `src/storage/paths.ts` originally constructed `Directory`/`File`
  instances at module-import time, which crashed `expo export --platform web` (and would have crashed
  the web runtime too); fixed by making path resolution lazy.

## A. Domain model + schemas (`src/domain/`) — ✅ done

`types.ts` (canonical camelCase domain types), `schema.ts` (zod, snake_case YAML shape), and
`yaml-mapping.ts` (bidirectional mapping + parse/serialize). One scope call: the exercise config
schema covers all 7 product-plan types (`hiit`/`emom`/`amrap`/`reps`/`timed_hold`/`cardio`/`rest`)
since library/import validation needs to recognize hand-authored files using any of them — but
`amrap`'s config is just `time_cap_sec` (the plan's "movements" sub-field is undefined in the spec
and unused anywhere, so it wasn't modeled). `SessionEntry`, by contrast, only covers `timed_hold` /
`reps` / `rest` — the only shapes the runner can produce; extending it to log `hiit`/`emom`/`amrap`/
`cardio` sessions is future work tied to extending the runner to those types (roadmap phases 3–4).

## B. Storage layer (`src/storage/`) — ✅ done

`paths.ts` resolves `<documentDir>/exercise-tracker/{exercises.yaml,sessions/}` lazily (see the web
note above) and ensures the directory exists; `library-file.ts` has `loadLibrary()`/`saveLibrary()`;
`session-files.ts` has `listSessions()`/`createSession()`/`appendSessionEntry()`/`finalizeSession()`,
all now wired into the live session runner (Workstream E) for incremental flush. `export.ts` wraps
`expo-sharing` for both the whole library and a single session file. No `.version` marker file was
added — the `version: 1` field lives inside each YAML file already, and a separate directory-level
marker has no consumer until an actual migration exists.

## C. Zustand store + hydration (`src/state/`) — ✅ done

`library-store.ts` and `session-history-store.ts`, hydrated in parallel with font loading in
`src/app/_layout.tsx`; the app blocks on both before rendering. `selectors.ts` derives all the display
aggregates (`blockChips`, `workoutSummary`, `recentSessionsView`, `historyStats`,
`historySessionsView`) that used to be hardcoded in mock data. Both stores now carry write mutators
(`replaceLibrary`, `saveExercise`, `saveWorkout` on the library store; `startSession`, `logEntry`,
`completeSession` on the session-history store) — these were deliberately left out of the initial
foundation pass until D/E/F actually called them, to avoid landing unexercised code; all are now used.
Every screen and `use-session-runner.ts` reads from the stores instead of `@/constants/mock-data`,
which is deleted.

## D. Import/merge + export — ✅ done

- `src/domain/merge.ts` — merge-by-`id` for exercises and workouts (§6). No-duplicate-ids is
  guaranteed by construction (both sides merge through the same `Map`); the one thing actively
  validated is that every workout block still references an exercise that exists post-merge (the
  "known type + required config" requirement is already enforced upstream by zod at parse time).
  Returns a `MergeSummary` (new/updated exercise and workout ids).
- `src/app/import.tsx` — rewritten: `File.pickFileAsync()` → read → `parseLibraryYaml()` →
  `mergeLibraries()` against the current library → real pre-merge summary (counts + a changed-items
  list) → confirm → `replaceLibrary()` (persists + updates the store) → close. Errors from a bad pick,
  malformed YAML, or a merge that references an unknown exercise are shown inline, not swallowed.
- `src/storage/export.ts` wraps `expo-sharing`. Entry points: an "Export" action next to "Import" in
  the library header (whole `exercises.yaml`), and an "Export" action inside each expanded history
  card (that one session's file).

## E. Timer engine hardening (core value, §7.1) — ✅ done

`src/hooks/use-session-runner.ts` was rewritten around wall-clock timestamps instead of accumulated
`setInterval` ticks:

- Each phase is anchored by `phaseStartedAtRef` (a `Date.now()` timestamp) plus `pausedAtRef` /
  `pausedMsRef` to account for pause time; elapsed/remaining is always `computeElapsedSec()` — a
  timestamp diff, never a counter. `setInterval` only triggers a recompute-and-render, it is not the
  source of truth.
- An `AppState` listener recomputes on background→foreground and catches up a rest phase that fully
  elapsed while backgrounded (auto-advances immediately rather than staying stuck).
- `useKeepAwake()` (from `expo-keep-awake`) is active for the lifetime of the session screen.
- Entering a rest phase schedules an `expo-notifications` local notification at the phase-end
  wall-clock time as a background fallback; it's cancelled and rescheduled on pause/resume/step
  change so it never fires stale or doubles up.
- `expo-haptics` fires on every `advance()` (set done, rest skipped, phase transition).
- **Incremental flush:** a session file is created via `startSession()` as soon as the runner mounts;
  each completed set is appended to a per-block pending-sets buffer and flushed as a `SessionEntry`
  the moment its block finishes (boundary = the next step belongs to a different block, or there is no
  next step); standalone Rest blocks flush their own entry immediately. `completeSession()` writes
  `ended_at` when the last step advances past the end. A mid-workout crash loses at most the
  in-progress set, per §7.2.
- ✅ `goPrev()` un-flushes the most recent `advance()`, one level deep: a `lastCommitRef` records
  what the last `commitCurrentStep`/`flushMember` call did (pushed to a pending buffer, or
  flushed/direct-logged a `SessionEntry`), and `goPrev()` reverses exactly that — popping the pending
  set/round/minute, or removing the just-written entry via a new `removeLastSessionEntry` (mirrors
  `appendSessionEntry`: a full rewrite of this session's own file, same cost as the append it undoes)
  and restoring all-but-its-last set back into the pending buffer for a multi-set member. A second
  `goPrev()` without an intervening `advance()` just moves the step index, as before — full
  multi-step undo was judged out of scope for this pass.

## F. Library/Build CRUD — ✅ done

- `src/app/exercise-editor.tsx` (new modal route): add or edit a single exercise — name, a type
  selector (all 7 types), and a config form driven by a declarative per-type field table. New
  exercises get a slugified id from the name; editing keeps the existing id and locks the type
  (changing type on an existing exercise would orphan its old config shape). Saves via
  `useLibraryStore().saveExercise()`.
- `src/app/(tabs)/library.tsx` — the FAB opens the editor in add mode; tapping a card opens it in edit
  mode for that exercise.
- `src/app/(tabs)/build.tsx` — rewritten around a local `draft: Workout` buffer compared by reference
  to the store's workout to derive a `dirty` flag (cheap and correct: `saveWorkout` writes back the
  exact same object reference, so `dirty` clears itself post-save with no extra reset code needed).
  Supports: rename (pencil → inline `TextInput`), remove a block (✕ per row), add a block (a picker
  panel listing every library exercise, including Rest), Cancel (revert to the stored workout), Save
  (`useLibraryStore().saveWorkout()`).
- ✅ **Drag-to-reorder blocks** (was previously "not implemented" — the ⣿ handle used to be decorative
  only). `src/components/reorderable-list.tsx` is a new generic `ReorderableList<T>` component (no
  workout-specific concepts, so it's reusable for a future program-week list): press-and-hold the
  handle (`Gesture.Pan().activateAfterLongPress(150)`, so ordinary `ScrollView` scrolling still works
  untouched) to pick an item up, live reflow of siblings computed from measured per-item heights
  (`onLayout` into a shared array — needed because exercise rows and circuit blocks are very different
  heights), release to drop. Items never leave their original React children order/keys during the
  drag — only a `translateY` transform moves them — so an open `TextInput` inside a circuit block
  (rounds/rest/block-id fields) doesn't lose focus or remount while another item is dragged; `data`
  changes exactly once, on drop. Required adding `GestureHandlerRootView` to `src/app/_layout.tsx`,
  which hadn't existed yet anywhere in the app. Auto-scrolling the `ScrollView` when a drag reaches the
  viewport edge is explicitly out of scope (blocks lists are short enough in practice; a contained
  follow-up if that's ever wrong).

---

## Decision log

Why things ended up the way they did, for decisions that span more than one commit. Named "What's
genuinely left" until it had grown to ~210 lines of *completed* work under a backlog heading — the
label had stopped being true, which is how it grew without anyone noticing.

**Adding to this file:** don't log a shipped feature here just because it shipped. The commit message
is the record — it has the root cause, the alternatives, and correct attribution, and `git log -S` can
find it. Add an entry only when the reasoning **isn't discoverable from a single commit**: a
constraint that shapes future work, something deliberately rejected (so it isn't re-proposed), or a
decision assembled across several commits. Open work belongs in the sections at the bottom, not here.

- ✅ **Full program CRUD, including override editing.** Drag-to-reorder blocks and exercise delete
  exist too (`deleteExercise` in the library store, wired to a "Delete exercise" button in
  `exercise-editor.tsx` with the same confirm-dialog/in-use-guard pattern as workout delete — blocked
  if a workout block or circuit member still references it) — library CRUD (exercises + workouts) is
  complete. `src/app/program-editor.tsx` does name / add-remove-week / per-week week-number-stepper+
  day+workout-picker+notes / delete-program, via the already-existing `saveProgram` plus a new
  `deleteProgram` (mirrors `deleteWorkout`/`deleteExercise`'s shape exactly; no in-use guard needed
  since nothing in the domain model references a program by id — a past session's `program` field is
  already denormalized, same as everything else in `sessions/`). Reachable from a FAB on
  `programs.tsx` (create) and a pencil icon on `program-detail.tsx` (edit). One thing this pass
  deliberately did *not* do: reuse `ReorderableList` for the weeks list, even though it was built with
  reuse in mind — a program week's display order is driven entirely by its `week` number field
  (everything that reads it, `program-detail.tsx` and `src/domain/program.ts`'s
  `findProgramWeek`/`programWeekNumbers`, sorts/looks up by that number, not array position), so
  dragging rows around wouldn't actually change anything.

  **Override editing** (initially deferred as a fast-follow, now shipped): a week's `overrides` — the
  per-exercise/per-circuit config patches — are editable via a new `src/components/
  program-override-editor.tsx`, embedded per-week in `program-editor.tsx`. The genuinely hard part was
  the snake_case/camelCase mismatch: `ProgramOverride.config` is a partial *raw* (snake_case) patch
  (per `applyExerciseOverride`/`applyBlockOverride`), but every in-app config form works in camelCase
  domain values. Solved with two new exported inverses in `yaml-mapping.ts`,
  `diffExerciseOverride`/`diffBlockOverride`, which round-trip a base and an edited value through the
  same private `exerciseToRaw`/`workoutBlockToRaw` used internally by `applyExerciseOverride` and
  return only the raw keys that actually changed — so the override editor can just reuse
  `exercise-editor.tsx`'s existing `CONFIG_FIELDS`/`configToStrings`/`buildExercise` (now exported) as
  an ordinary camelCase form and never has to know about snake_case itself. Editing an *existing*
  override pre-fills the form from the base value with that override already applied (via
  `applyExerciseOverride`/`applyBlockOverride`, so it shows what's actually in effect, not the
  unmodified base), rather than resetting to defaults. The "add override" target picker is scoped to
  what the week's selected workout actually contains: exercises referenced by its blocks/circuit
  members, and circuit blocks that have their own `id` set (an id-less circuit can't be a block-override
  target, matching the documented rule in `program-guide.tsx`). Deliberately unhandled: an override
  isn't re-validated or cleared if the week's workout is later changed to something that no longer
  contains its target — matches the existing looseness elsewhere (`merge.ts` doesn't validate override
  targets either); a stale/unresolvable override still renders (read-only, no edit tap) with its
  remove control intact.
- ✅ **Per-exercise progression, including a volume chart.** `src/app/exercise-editor.tsx` shows a
  "Recent" section (only when editing an existing exercise, and only if it's actually been logged)
  listing the last few times it was done, newest first, via `exerciseHistory(sessions, exerciseId,
  limit)` in `src/state/selectors.ts` (also exports the existing `sessionEntrySummary`, previously
  private, as the per-type formatter — "8 · 7 · 5 reps", "20s · 18s · 15s", etc.). Above that list sits
  a new `src/components/volume-chart.tsx` — a small `react-native-svg`-based bar chart (that dependency
  was already installed, unused until now) built by following the `dataviz` skill's procedure: one
  consistent numeric "volume" per exercise type (`entryVolume` in `selectors.ts`, next to
  `sessionEntrySummary` — Σreps×weight for `reps` (or Σreps if bodyweight), Σhold-seconds for
  `timed_hold`, `roundsCompleted` for `hiit`/`amrap`, Σreps for `emom`, distance-or-duration for
  `cardio`), one flat accent-colored bar per session (single series, no legend needed), rounded-top/
  flat-baseline bars via a custom `Path` (`Rect`'s `rx` rounds all four corners, which isn't what a
  bar-anchored-to-a-baseline should look like), and — a deliberate, explicitly-reasoned deviation from
  the skill's "always ship a hover/tap tooltip" default — direct value labels on each bar instead of
  interactive tooltips, since touch has no hover, the chart is a small embedded sparkline (axes
  intentionally omitted, per Tufte's original definition) sitting directly above a list that already
  spells out every exact value as text, and the skill itself names direct labels as the sanctioned
  alternative for exactly this case. The chart reads oldest→newest left-to-right (natural
  progress-over-time reading order) while the list below it stays newest-first — a deliberate, called-
  out inversion between the two, not an inconsistency. Verified by actually driving two real sessions
  to completion (6 reps/set, then 9 reps/set) and looking at the rendered output, per the skill's
  final "render it and look at it" step, not just trusting the code: two bars, correctly ordered,
  correct heights (24 vs 36), correct labels.

  Also deliberately *not* done: no "last time" hint live inside the session runner while performing a
  set — that's a separate, larger feature (touches the timer-critical `use-session-runner.ts`), flagged
  as a follow-up rather than folded in here; and no chart interactivity beyond the direct labels (see
  above), since a tap tooltip would just repeat what the list below already shows.
- ✅ **Quick-add a new exercise from the workout builder.** New `src/components/new-exercise-form.tsx`
  — a mini version of `exercise-editor.tsx`'s own form (name, type pills, per-type `CONFIG_FIELDS`
  grid, notes), reusing its now-exported `buildExercise`/`CONFIG_FIELDS`/`TYPE_OPTIONS` — embedded
  inline in both of `workout-editor.tsx`'s pickers behind a "+ New exercise" row, rather than a
  navigate-to-`exercise-editor`-and-back flow (the workout draft being edited is unpersisted local
  state; navigating away would need real plumbing to avoid losing it). On create, the new exercise is
  persisted via the existing `saveExercise` and then dropped into whichever picker triggered it: the
  plain block picker calls `addBlock` (closes the picker, same as picking an existing exercise); the
  circuit picker calls `toggleCircuitMember` and **stays open**, since building a circuit means
  picking several exercises, not just the one just created. Has its own local `slugify` copy — the
  4th one in the codebase now (`exercise-editor.tsx`, `workout-editor.tsx`, `program-editor.tsx`, this
  one), consistent with the existing precedent of small per-screen copies rather than a shared util.
- **Considered and rejected: consolidating `sessions/` into monthly files to reduce IO.** Would cut
  against the explicit "never rewrite all of history on save" rule (product plan §5.2) — the
  mid-workout incremental flush (`writeSession()` full-overwrites its file on every completed set)
  would have to rewrite an entire month's accumulated sessions instead of just the current one, a cost
  that grows through the month, regressing the thing the product plan calls "the make-or-break issue."
  It also weakens crash isolation between sessions. The actual unbounded-growth risk is
  `listSessions()` reading every session file at app launch (O(total sessions ever logged)) — if that
  ever becomes a real problem, the fix is lazy/paginated loading or a small separate index file, not
  changing the live-workout write path.
- **Web has no persistence** (by necessity — `expo-file-system` doesn't support it); it now degrades
  to an ephemeral in-memory seed library instead of crashing, which is a reasonable dev/preview
  experience but not real usage.
- ✅ **Fixed: starting a workout with zero runnable steps trapped the user on a blank screen.** Found
  by deliberately reasoning through edge cases, then confirmed in the running app: a workout with no
  blocks (fully allowed by `workout-editor.tsx` — only `name` is validated on save) resolved to
  `buildSteps() === []`, and `session.tsx`'s `ActiveSession` did `if (!step) return null` *above* its
  header/Finish-button markup — so after the pre-session 3-2-1 countdown played, the user landed on a
  fully blank black screen with nothing tappable, only escapable via the OS back gesture. The real
  condition is broader than "zero blocks," too: any workout where every block's exercise has
  sets/rounds/minutes at 0 hits the identical empty-`steps` case — and that's reachable through normal
  use, since `exercise-editor.tsx`'s `Sets` field (and its equivalents) is a plain numeric `TextInput`
  with no minimum, unlike the circuit `Rounds` stepper which clamps to ≥1 — a blank/zero value there
  silently produces a runnable-looking exercise that isn't. Fixed at the source: `buildSteps` is now
  exported from `use-session-runner.ts`, and `session.tsx` calls it once up front to check
  `steps.length === 0` *before* ever showing the countdown, short-circuiting to a "Nothing to run"
  screen (workout name, explanation, a `Close` button) instead of mounting the runner at all. Verified
  live: created a zero-block workout, started it through a real program week (not a raw URL — web has
  no persistence, so a full page navigation would've reset the in-memory library), confirmed the
  empty-state screen renders and `Close` navigates back cleanly. Deliberately not addressed in this
  same pass: adding a minimum-value guard to the in-app config forms (`exercise-editor.tsx`,
  `new-exercise-form.tsx`) so a 0-sets exercise can't be *created* in the first place — imported YAML
  already can't produce this (the zod schema requires `sets > 0`), but the in-app save path bypasses
  schema validation entirely, so it's the only way in today. Worth doing, but a separate, smaller
  follow-up rather than bundled into this fix.
- ✅ **Fixed: "next up" tracked a completed-session count instead of actual program progress; added Today-screen metrics.** `nextUpView`'s old logic (`src/state/selectors.ts`) picked
  `weeks[completedSessionCount % weeks.length]` — correct only if you always progressed through a
  program's weeks in strict lockstep, but `program-detail.tsx` lets you tap "Start this week" on *any*
  week card, so redoing a week or jumping ahead silently desynced the count from reality, which looked
  "random." Real fix: `Session` (`src/domain/types.ts`, `src/domain/schema.ts` as `program_week`/
  `program_day`, backward-compatible via `.nullable().default(null)` so pre-existing session files keep
  parsing) now records which week/day it was actually started under, threaded from `session.tsx`'s
  already-resolved `week`/`day` params through `use-session-runner.ts` → `session-history-store.ts` →
  `session-files.ts` at session creation. `nextWeekAfter` in `selectors.ts` replaces the counting
  heuristic with a direct lookup: find the most recent session tied to the active program that has a
  tracked week (`sessions` is newest-first, so `.find` gets it directly), locate that week in the
  program's sorted week list, and return the one after it — wrapping to the first week past the end (so
  finishing a program restarts it) and falling back to the first week when there's no tracked session
  yet (brand new program, or every existing session predates this change). Verified live: jumped
  straight to a program's last week (skipping the earlier ones — the case that broke the old count
  logic), completed it, confirmed Today's "next up" correctly showed the *first* week (wrap-around), not
  a count-derived guess.

  Also added a small stats row to the Today screen, reusing the existing `historyStats()` aggregator
  rather than a parallel stats shape: **current streak** (`currentStreak` — consecutive calendar days
  with ≥1 session, walking back from today; today not having one yet doesn't break it, only a full-day
  gap does) and **this week's activity** (`thisWeekStats` — `historyStats` pre-filtered to sessions
  since the current Monday, local time).
- ✅ **Start any workout ad-hoc, without a program.** `session.tsx` has always accepted a bare
  `workoutId` param (the non-program branch of its `resolved` memo), but nothing in the UI ever
  pushed it — the only two entry points were the Today tab's "next up" card and `program-detail.tsx`'s
  per-week "Start this week", both program-driven. So a plain "I just want to do this workout today"
  had no path at all. Fixed purely in the UI: each workout card on the Build tab now has a small
  circular play button that pushes `/session` with `{ workoutId }`; tapping the card text still opens
  the editor as before. No domain, storage, or runner changes — the resulting session simply has
  `program`/`programWeek`/`programDay` all null, which the "next up" logic already treats correctly as
  "not program progress" (`nextWeekAfter` filters on `session.program === program.id`, so an ad-hoc
  session can't shift a program's suggested week). Deliberately kept visually quiet after a first pass
  shipped a full-width solid-accent "Start" bar: that read as the card's primary action and made a list
  of workouts into a wall of orange, so it became a 36px accent-soft circle with just a play glyph, and
  the card's old `›` chevron was dropped since two right-pointing affordances side by side were
  confusing.
- ✅ **Fixed: leaving the session screen crashed with a redbox (web).** Read the library source rather
  than working around it, and the fix turned out to be a flag it already ships:
  `useKeepAwake(undefined, { suppressDeactivateWarnings: true })`. The mechanism, from
  `expo-keep-awake/src`: `useKeepAwake`'s cleanup calls `deactivateKeepAwake(tag)` with **no `.catch()`
  at all** unless that flag is set (`index.ts`), and the web `activate(tag)` only records the tag in
  its map *after* awaiting `navigator.wakeLock.request('screen')` (`ExpoKeepAwake.web.ts`). Any unmount
  that beats that promise leaves `deactivate` with no tag to release, so it throws
  `ERR_KEEP_AWAKE_TAG_INVALID` as an unhandled rejection. Every exit from this screen beat it, since
  finishing/closing navigates immediately — which is why it fired on completion, on `finishSession`,
  and on the empty-workout `Close` alike. Nothing leaks by suppressing it: the browser drops a screen
  wake lock on its own when the page goes away. Verified by driving a full session to completion and
  out: previously every such run logged the `pageerror`; the run after the fix logged no console
  errors of any kind.
- ✅ **Fixed: a batch of UI-truthfulness bugs found by auditing the screens against the code.**
  (1) `history.tsx` had **`"July 2026"` hardcoded** — frozen to one month, and sitting on top of stat
  tiles that are `historyStats(sessions)` over *all* sessions; now reads "All time", matching both the
  tiles and the unfiltered list below. (2) Today's "Recent" rows rendered a trailing `→` but were a
  plain `ThemedView` with no `Pressable` — the same broken-button look already removed from
  `SessionNextCard` in b547bdd; the arrow is gone (they stay non-interactive, since there's no
  per-session detail screen to open). (3) The expanded history card showed *"self-describing · stays
  valid if the definition later changes"*, a note about the data model that had no business in the UI;
  removed, leaving Export anchored right.
- ✅ **Fixed: no minimum-value validation on exercise config forms.** Flagged as a follow-up when the
  "Nothing to run" screen shipped and left open until now — it was the only reachable way to create a
  workout with zero runnable steps, since the in-app forms write straight to the library store and
  never pass through the zod schema (imported YAML can't do this; `sets` is `positive()` there). New
  exported `validateConfig(type, values)` in `exercise-editor.tsx` mirrors those schema constraints:
  required fields need ≥ 1, and `FieldDef` gained an optional `min` so the rest-length fields can carry
  `min: 0` — the schema has those as `nonnegative()`, and rejecting a zero-length rest would be
  stricter than the format itself. Wired into both creation paths (the full editor and
  `new-exercise-form.tsx`'s quick-add), since validating only one would leave the hole open through the
  other. Cardio deliberately keeps both fields optional: an unconfigured cardio exercise is a valid
  count-up stopwatch in the runner, not a broken step. Verified live: 0 sets is refused with "Sets must
  be at least 1", while a 0-second rest still saves.
- ✅ **Session delete, history search, and a Settings screen** (built by parallel agents; the
  integration and the two fixes below are the parent's).
  **Delete**: `deleteSession(id)` in `session-files.ts` (idempotent — `File.delete()` throws on a
  missing path, so the `exists` check is load-bearing) → a store action → a destructive-confirm control
  in History's expanded card, mirroring the exercise/workout delete pattern. Whole sessions only;
  editing a past session's individual entries is still not possible.
  **Search**: name search over the history list, reusing Library's search-bar styling. The stat tiles
  now aggregate the *visible* subset and the header switches from "All time" to "N of M", because three
  all-time numbers sitting above a filtered list would describe sessions that aren't on screen.
  Deliberately no filter pills: a session has no single type to pill on, being whatever mix got logged.
  **Settings**: a modal with three-way appearance, export/import, and library counts.
  `theme-context.tsx`'s `Scheme | null` override plus `toggle()` became a `ThemePreference`
  (`light | dark | system`) — the old toggle could never get back to following the OS, because "follow"
  and "currently light" were indistinguishable to it. Storing intent rather than outcome is what makes
  the third option expressible. The preference is still in-memory and resets on relaunch.
- ✅ **Fixed: `exportLibrary`/`exportSession` threw past their own `.catch()`.** Resolving `.uri`
  constructs an `expo-file-system` `File`, which has no web implementation and throws *synchronously* —
  before `share()`'s async body is entered — so the throw escaped the returned promise and every
  caller's `.catch()` missed it, surfacing as an unhandled error. Both are now `async`, which turns it
  into a rejection those existing handlers catch. Fixes the crash on Library's export and the same
  latent hole in History's.
## ✅ Two more audio cues in the runner

Both requested from real use, and both now sound a new rising two-note `milestone.wav` — deliberately
rising where the countdown tick is flat, because the two mean opposite things: a tick says "about to
end", a milestone says "keep going, you are partway".

- **Halfway through a HIIT work interval**, the point you would otherwise have to look up to pace
  yourself. Work intervals only: their rest already gets the 3-2-1 ticks. Not wired to emom or amrap —
  emom intervals are usually too short for a midpoint to mean much, though an amrap time cap is a
  reasonable future extension.
- **When a hold reaches its target.** Holds count *up* with the target as a marker, so nothing
  previously marked the moment you actually hit it — the one piece of information that matters with
  your eyes shut. Fires at the bottom of a range target, since that is where the set counts.

One sound serves both: which one you are hearing is never ambiguous, since you are either mid-interval
or mid-hold, and a third distinct tone would be more to learn for no added information.

**The once-per-step guard is the whole feature.** Both triggers are thresholds that stay true for the
rest of the step, so a 1Hz tick would re-chime every second — worst on a hold, which does not
auto-advance and can run well past target. A ref keyed on the step index (not reset on change, so it
survives the ticking effect being rebuilt by pause/resume) fixes that, and removing it fails two tests.
Verified in the app by patching HTMLMediaElement.play: silent at 14s, exactly one milestone.wav at the
15s target, still exactly one at 26s.

The asset is generated rather than sourced — two sine notes (A5 then E6) with attack and exponential
decay envelopes, since a raw sine starting or stopping at non-zero amplitude clicks audibly.

## ✅ Direct numeric entry for reps and load

The reported problem: the reps control was a −/+ stepper, so a 30-rep set cost 30 taps mid-workout,
out of breath. The new load stepper had the same ceiling.

Steppers stay — they are right for the small adjustment and for one-handed use. The value itself is now
tappable and opens `session-number-pad.tsx`, a keypad sheet. Wired to reps and load on the reps screen,
and to EMOM reps and AMRAP rounds/extra-reps on the interval screen.

Decisions worth keeping:

- **A custom keypad, not a `TextInput` with `keyboardType="numeric"`.** The runner screens are `flex: 1`
  with no ScrollView and already tight; the OS keyboard would cover the very controls being edited. A
  custom pad also keeps the digits large enough to hit while out of breath.
- **Typing starts a fresh value** rather than appending to the current one — the reason to open the pad
  at all is that the current value is far from what you want. Confirming without typing keeps the
  original.
- **The parent owns which field is being edited**, and renders one pad at the screen root. An overlay
  fills its parent view, not the screen, so a pad rendered inside a stepper block was clipped to a
  strip. That bug was caught before shipping but is the obvious thing to get wrong here.
- Decimal key only where it means something (load); everything else rounds to a whole number on
  confirm. A lone or trailing "." parses to NaN, so Set is disabled rather than writing garbage.

Also an accessibility win, and why it belongs with A11y-1 rather than as separate polish: it gives
screen-reader and motor-impaired users a way to set a value without N discrete activations.

Verified in the app: typing 27 into the reps pad (3 taps, versus 21 on the stepper) sets 27; Cancel
leaves the value untouched; the load pad accepts 42.5.

## ✅ Tests: phase 1 (pure logic) landed

`jest-expo` + `npm test`. **93 tests across 7 suites, ~4s.** No UI tests yet — that's phase 3, and the
plan deliberately sequences it after i18n so assertions don't get written against English copy.

Covered: `buildSteps` (rest interleaving, circuit round-robin, `memberKey` stability across rounds,
degenerate zero-step cases), `yaml-mapping` (round-trip across all 7 exercise and 7 entry types,
plus a key-contract test and an idempotency test — see below), `merge` (add / replace-by-id /
referential integrity), `program` (week resolution, override application, non-targeted exercises left
reference-identical), `selectors` (`historyStats`, `currentStreak`, `thisWeekStats`, `nextWeekAfter`,
`sessionEntrySummary`), `exercise-form` (the `min: 0` rest-field asymmetry), and `slug`.

Three things worth recording:

- **Two suites were verified against the bugs they pin**, by restoring the old logic and confirming
  they fail. The EMOM tests fail with `Expected: 600, Received: 300`; the `historyStats` tests fail on
  the fractional-hour form. A regression test that doesn't fail on the bug is worthless, so this check
  is worth repeating whenever one is written.
- **Round-trip tests alone are insufficient for the YAML mapping**, and the suite says so: a symmetric
  typo (both directions using the same wrong key) round-trips perfectly. The key-contract test asserting
  literal snake_case names is what actually pins the on-disk format.
- **Parsing materialises omitted optional keys as `undefined`** rather than leaving them absent. Not a
  defect — the serializer drops them and `serialize → parse → serialize` is byte-identical, which the
  idempotency test now pins. That's the property that matters for a file users hand-edit and re-save.

Refactors this forced, both behaviour-preserving: `buildSteps` and the step model moved to
`session-steps.ts` (importing the runner pulled in `expo-audio` and died on native-module init), and
`nextWeekAfter` is now exported so it can be tested without constructing a whole `Library`.

## ✅ Tests: phase 2 (the session runner) landed

**115 tests across 9 suites.** The runner is exercised through the real hook with `renderHook` and
Jest's modern fake timers, mocking haptics/sounds/notifications and the history store — the store at
*our* boundary, not `expo-file-system`, so assertions are about what got logged rather than about file
writes. Covers: the `-1` sentinel regression asserted on first render, countdown timing, pause
excluding paused time, foreground catch-up after backgrounding, per-type flushing, the `goPrev` undo
matrix (pending-pop, entry-removal with multi-set restore, one-level-only, floor at zero),
`finishSession` committing the in-progress set, and `addRestSeconds` rescheduling its notification.

**The plan's call not to inject a clock held up.** Fake timers mock `Date.now()` and `setInterval` from
one virtual clock, so the wall-clock design tests as-is; nothing in `use-session-runner.ts` changed to
accommodate the tests.

Three things worth knowing for the next person writing tests here:

- **RNTL 14's `renderHook` returns a Promise** (React 19 made rendering async-aware) and `act` must be
  awaited. Sync `act` around `advanceTimersByTime` nests scopes and React reports overlapping act calls.
- **`result` is not an own-enumerable property** of the renderHook result — `{ ...rendered }` silently
  drops it and every assertion then fails on `result.current`.
- **Cleanup is global now** (`clearMocks`/`restoreMocks` in the jest config, plus `useRealTimers` in
  `jest.setup-after-env.js`). A spy installed and restored inside one test previously left later tests
  failing with opaque `AggregateError`s while passing in isolation — the failure surfaced nowhere near
  its cause, which is exactly why this belongs in config rather than per-file.

## ✅ I18n-0: structured descriptors in the logic layer

The first step of the i18n plan, done ahead of the library so later work isn't rewriting assertions.
The logic layer returned finished English sentences, which made two things hard: tests had to assert on
prose i18n was about to rewrite, and pluralisation was scattered across a dozen template literals.

`src/domain/format.ts` is now the only place English is assembled. The producers return data:
`workoutSummary` → `workoutShape` (`{ blockCount, types, estimatedMinutes }`), `sessionEntrySummary` →
`sessionEntryResult` (a six-variant descriptor), `circuitSummary` → `circuitShape`. Views call
`formatWorkoutShape` / `formatEntryResult` / `formatCircuitShape`.

**This fixed live bugs rather than just moving code.** "1 blocks" was on the Today card and every Build
row; "1 exercises", "1 workouts", "1 rounds" and "1 reps" were reachable too. They're gone by
construction now — a single `plural()` helper — and `formatEntryResult` also drops the "N min" wording
for EMOM, which was wrong for any interval that isn't 60 seconds.

**`plural` is deliberately English-only.** The obvious implementation is `Intl.PluralRules`, but Hermes
doesn't ship it, so that would pass in tests and on web and crash on device. It's one function, and the
single seam to swap for CLDR categories when i18next and the `intl-pluralrules` polyfill land — which
matters because Polish and Arabic have three to six forms, not two.

The selectors test now asserts descriptors instead of sentences; converting it was a small live
demonstration of the rework this ordering avoids at scale.

**Still assembling English in the logic layer, deferred to the i18n pass proper:** `exerciseSummary`
(`exercise-badge.tsx`), `previewFor` (`session-steps.ts`), and the `toLocaleDateString('en-US', …)`
labels in `recentSessionsView`/`historySessionsView`/`exerciseHistory` — the date ones need the locale
work from I18n-3 to be worth touching, since they'd otherwise just move the hardcoded locale.

## Open bugs

Found while planning the tests/a11y/i18n work (see `testing-a11y-i18n-plan.md`), each verified against
the code. Listed worst first.

**Fixed since:** `historyStats`'s "1.5h 30m"; the EMOM interval count; weight never being captured;
side effects inside `setState` updaters; `addRestSeconds` not rescheduling its notification;
`currentStreak`'s DST stepping; the display-name chip comparison; and circuit members writing one
entry per round (below). Notes on the structural ones:

- **Circuit members wrote one entry per round** instead of accumulating. Found by the phase-2 tests,
  not by the architecture pass. `advance()` flushed whenever the *next* step belonged to a different
  member — which in a round-robin circuit is every hand-off — so a 3-round, 3-member circuit produced
  9 single-set entries where `session-steps.ts`'s own expansion comment says it should produce 3
  entries of 3 sets. The fix separates two questions that were conflated: "are we changing exercise
  right now?" (the audio cue, still true on every hand-off) from "is this member finished for the
  whole workout?" (the flush, now checking whether any later step shares the member). Verified in the
  app: History shows three rows reading `12 · 12 · 12 reps` rather than nine rows of one value.

- The **`setState` updater** fix reads the step index from a ref instead of the updater's argument, so
  every commit/flush/`logEntry` now runs once in the event handler. The ref is advanced eagerly so two
  `advance()` calls in one tick (the ticking interval and the foreground catch-up can both fire) don't
  repeat a step. Verified by driving a full session with a `goPrev` and redo mid-way: one entry per
  exercise and exactly 8 sets, where a duplicated commit would inflate both.
- The **`currentStreak`** fix steps with `setDate()`. Its regression tests are honest about their
  limits: Node ignores `TZ` on Windows, so on a DST-free machine they pass whether or not the bug is
  present. CI sets `TZ` explicitly, which is where they actually bite.

- **`Alert.alert` is a no-op on web.** react-native-web ships `class Alert { static alert() {} }`, so
  every confirm dialog silently does nothing in the browser — all the deletes and finish-session.
  Native is unaffected and web is a dev/preview target, so this is logged rather than fixed. It does
  mean a browser check of any confirm flow proves nothing unless the script patches it, which is how
  session delete was actually verified end to end.
- **`today`/`dateLabel` are computed at module scope** in `index.tsx`, so they freeze at first import —
  leave the app open past midnight and Today shows yesterday.
- **`sessionSetCount` under-reports interval work**, counting an EMOM entry as one "set" regardless of
  `minutes.length` and a HIIT entry as one regardless of `roundsCompleted`.
- **`slugify` yields an empty id for non-Latin names**, so the app can't name exercises in most
  scripts. It surfaces as the "Could not derive an id" error rather than corruption, but it makes the
  app unusable in those languages. Four duplicate copies of the function.
- **`session-hold.tsx` can compute `width: "NaN%"`** when `targetSec` is 0. `validateConfig` guards the
  in-app path, but imported YAML and `applyExerciseOverride` can still reach it.
- **Stale copy in `programs.tsx`** still says per-week overrides aren't editable in-app; override
  editing shipped in `09d2606`.

## Open questions from the product plan, still open

- Timed-hold display direction: count down from target, or count up with target as a marker (§12.2).
  Current UI counts up (unchanged from the pre-existing mock UI).
- Merge conflict transparency: diff view vs. simple updated count (§12.5). Currently a simple
  new/updated count + changed-id list, no field-level diff.

Settled since this was written: `hiit`/`emom`/`amrap`/`cardio` are all runnable in the session
screen now (a unified interval runner, with matching `SessionEntry` log shapes added to the domain
model), and the `blocks` model grew a `circuit` kind for supersets/circuits (§12.4) without a rewrite.
