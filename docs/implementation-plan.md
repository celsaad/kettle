# Kettle — Implementation Plan

Plan for building out the four workstreams the README lists as *not yet implemented*: reading/writing
`exercises.yaml` and `sessions/`, library import/merge, export, and the wall-clock-drift-hardened
timer engine (keep-awake, background notifications) — plus wiring the library/build CRUD screens.

**Status: all workstreams (A–F) are implemented.** `npm run typecheck` and `npm run lint` are clean.
See [`exercise-tracker-product-plan.md`](exercise-tracker-product-plan.md) for the product model this
plan implements.

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
- Known limitation: `goPrev()` doesn't un-flush an already-logged set. Revisiting a previous step to
  correct a mistake is a real product question or an already flagged limitation, left out of this pass.

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
  (`useLibraryStore().saveWorkout()`). **Not implemented:** drag-to-reorder blocks — the existing drag
  handle (⣿) is currently decorative; real reordering needs a drag gesture library and was judged too
  large an addition for this pass.

---

## What's genuinely left (not part of this implementation pass)

- **Drag-to-reorder** blocks in Build (noted above).
- **`goPrev` doesn't un-flush** logged sets in the session runner (noted above).
- **`hiit`/`emom`/`amrap`/`cardio` aren't runnable** in the session screen — `useSessionRunner`'s
  `buildSteps()` only turns `timed_hold`/`reps`/`rest` blocks into steps (this predates this
  implementation pass; extending it is roadmap phase 4, and would also need the corresponding
  `SessionEntry` log shapes added to the domain model, deliberately left out per Workstream A above).
- **No exercise/workout delete** — only add/edit. Delete wasn't present in the original mock UI either
  and wasn't asked for.
- **Web has no persistence** (by necessity — `expo-file-system` doesn't support it); it now degrades
  to an ephemeral in-memory seed library instead of crashing, which is a reasonable dev/preview
  experience but not real usage.

## Open questions from the product plan, still open

- Timed-hold display direction: count down from target, or count up with target as a marker (§12.2).
  Current UI counts up (unchanged from the pre-existing mock UI).
- Merge conflict transparency: diff view vs. simple updated count (§12.5). Currently a simple
  new/updated count + changed-id list, no field-level diff.
- Keep the `blocks` model shaped to allow nested groups later (supersets/circuits) without a rewrite
  (§12.4) — unaffected by this pass; `WorkoutBlock` is still flat.
