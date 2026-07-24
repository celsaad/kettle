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

## What's genuinely left (current, updated past workstreams A–F)

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
- ✅ **Per-exercise progression** — `src/app/exercise-editor.tsx` now shows a "Recent" section (only
  when editing an existing exercise, and only if it's actually been logged) listing the last few times
  it was done, newest first, via a new `exerciseHistory(sessions, exerciseId, limit)` selector in
  `src/state/selectors.ts` (also exports the existing `sessionEntrySummary`, previously private, as the
  per-type formatter — "8 · 7 · 5 reps", "20s · 18s · 15s", etc.). Deliberately scoped to a static list,
  not a trend/volume computation or a chart — see "No charts" below. Also deliberately *not* done: no
  "last time" hint live inside the session runner while performing a set — that's a separate, larger
  feature (touches the timer-critical `use-session-runner.ts`), flagged as a natural follow-up rather
  than folded in here.
- **No charts, and no computed "volume" metric** (sets × reps × weight, or a trend direction) — the
  Recent section above is a raw list of past values, not a derived stat.
- **Web has no persistence** (by necessity — `expo-file-system` doesn't support it); it now degrades
  to an ephemeral in-memory seed library instead of crashing, which is a reasonable dev/preview
  experience but not real usage.
- **Known bug (web only): completing a session crashes with a redbox.** `useKeepAwake()` in
  `session.tsx` throws `"The wake lock with tag _r_0_ has not activated yet"` on unmount — `onComplete`
  calls `router.back()` immediately when the last step finishes, racing the async browser Wake Lock API
  before its activation promise settles. Found while testing the progression feature above (Playwright
  drove a full 16-step session to completion — the first time in this project's history that's
  happened under automation, which is exactly the kind of fast, back-to-back interaction that hits the
  race reliably). Not caused by this pass — nothing here touches `use-session-runner.ts` or `session.tsx`
  — and not data-destructive (the session had already saved and `router.back()` had already fired
  before the error surfaces; dismissing the redbox reveals the app underneath working normally), but
  it's a jarring crash on every web session completion and deserves a real fix, not a footnote.

## Open questions from the product plan, still open

- Timed-hold display direction: count down from target, or count up with target as a marker (§12.2).
  Current UI counts up (unchanged from the pre-existing mock UI).
- Merge conflict transparency: diff view vs. simple updated count (§12.5). Currently a simple
  new/updated count + changed-id list, no field-level diff.

Settled since this was written: `hiit`/`emom`/`amrap`/`cardio` are all runnable in the session
screen now (a unified interval runner, with matching `SessionEntry` log shapes added to the domain
model), and the `blocks` model grew a `circuit` kind for supersets/circuits (§12.4) without a rewrite.
