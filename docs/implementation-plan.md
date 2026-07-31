# Kettle — Implementation Plan

Plan for building out the four workstreams the README originally listed as *not yet implemented*:
reading/writing `exercises.yaml` and `sessions/`, library import/merge, export, and the
wall-clock-drift-hardened timer engine (keep-awake, background notifications) — plus wiring the
library/build CRUD screens.

**Status: all workstreams (A–F) are implemented.** `npm run typecheck` and `npm run lint` both pass
clean.
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
- ✅ **expo-iap API confirmed** from `node_modules/expo-iap/build/*.d.ts` (installed at 4.7.2 — the
  published docs site still shows 3.4 examples, and the `hyochan/expo-iap` repo is archived in favour
  of the OpenIAP monorepo, so the types are the only trustworthy source). Shapes used in
  `src/app/support.tsx`: `useIAP({ onPurchaseSuccess, onPurchaseError, onError })` returning
  `{ connected, products, fetchProducts, requestPurchase }`; `fetchProducts({ skus, type: 'in-app' })`;
  `requestPurchase({ request: { google: { skus } }, type: 'in-app' })` (`android` is deprecated in
  favour of `google`); the **module-level** `finishTransaction({ purchase, isConsumable })`, used
  instead of the hook's so the success handler doesn't close over a binding the hook hasn't produced
  yet; `ErrorCode.UserCancelled` (kebab-case values — the `E_`-prefixed codes are long gone);
  `Product.displayPrice` (the store's localized string; `price` is `number | null` on Android, hence
  never used for ordering) and `Purchase.purchaseState: 'pending' | 'purchased' | 'unknown'`.
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
and unused anywhere, so it wasn't modeled). `SessionEntry` started narrower than the config schema —
`timed_hold` / `reps` / `rest`, the only shapes the runner could produce at the time — and now covers
all seven, added alongside the interval runner that produces them (roadmap phases 3–4, both ✅).

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
  every completed set writes through to it immediately — the exercise's `SessionEntry` is appended on
  its first set and rewritten in place on each one after (`persistMember`), so a crash loses at most
  the set in progress, per §7.2. This is the second design: the first buffered an exercise's sets in
  memory and flushed them only when it *finished*, which lost a whole exercise rather than a set.
  `completeSession()` writes `ended_at` when the last step advances past the end.
- ✅ `goPrev()` un-flushes the most recent `advance()`, one level deep: a `lastCommitRef` records what
  the last `commitCurrentStep` call did, and `goPrev()` reverses exactly that — dropping the
  set/round/minute from the exercise's log and rewriting its entry, or removing the entry outright
  (via `removeLastSessionEntry`, a full rewrite of this session's own file, same cost as the write it
  undoes) when that set was the only thing in it. A second
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

- ✅ **Formatting is oxfmt's, and it deliberately does not own the docs.** The repo went a long time
  with no formatter, which cost real time: reaching for `npx prettier` ad hoc reformats a file to
  Prettier's defaults and fights the whole codebase's style, and a subagent has no way to infer an
  unwritten convention. oxfmt was chosen over Prettier because the repo already lints with **oxlint,
  which is the same Oxc toolchain** — so the two agree instead of needing a compatibility shim. Vite+
  was considered first and rejected: it is commercial, and this app is free with a tip jar that only
  covers the Play developer fee, so a per-seat tool can't be justified here. It also couldn't have
  replaced Metro, which bundles the app, and must never be allowed to drag the test runner with it —
  `jest-expo` is what makes the RN preset, the transform ignore list and the native-module mocks work.

  Two exclusions in `.oxfmtrc.json` that are decisions, not oversights, and shouldn't be "fixed":

  - **Markdown.** oxfmt rewrites prose emphasis (`*raw*` → `_raw_`) and dropped the indent on a
    wrapped list continuation in AGENTS.md, which changes how it renders. The docs are hand-wrapped;
    that's a regression, not a normalization.
  - **`package.json`.** npm rewrites its key order on install, so two tools owning it means a dirty
    tree after every install.

  `printWidth` is 125 because that's where the code already sat — 92 lines exceeded it against 145 at
  120, so it rewraps what is genuinely long rather than relitigating lines that were fine.

- ✅ **The tip jar is the whole monetization story, and nothing is gated behind it.** Kettle ships to
  Google Play only for now ($25 one-time); the App Store's $99/yr is deferred until the app shows
  traction, so the bar to clear is deliberately low. The app is backend-free, so marginal cost per
  user is zero — which is why there is no subscription (nothing recurring is delivered) and no ads.

  Two constraints this places on future work, neither discoverable from the commit that added the
  screen:

  - **No third-party purchase or analytics SDK.** RevenueCat was the obvious choice and was rejected:
    it transmits app user IDs, device identifiers and purchase events to its own servers, which would
    force a Play **Data Safety** declaration. "No data collected / no data shared" is currently true,
    it is printed on the store listing, and it is the thing that distinguishes Kettle from Hevy,
    Strong and Fitbod. Play Billing keeps the transaction inside Google Play, where payment data never
    reaches app code. RevenueCat's real advantage is cross-platform entitlements, which is exactly
    what's deferred with iOS. **Adding EAS Update, Sentry, or any analytics would break the same
    claim** — treat zero declarations as a product constraint, not an accident.
  - **Tips must stay in-app.** Linking out to Ko-fi/PayPal violates the Play Payments policy for
    non-nonprofit developers. This is the policy risk, not the Data Safety form.

  Shape: three **consumable** tiers (`tip_small`/`tip_medium`/`tip_large`), so a repeat tip is
  possible — `finishTransaction({ isConsumable: true })` is load-bearing, since without it Play treats
  the SKU as owned and refuses every later purchase of that tier. Consumables aren't restorable from
  Play, so "has tipped" can only live locally: `supporter.json`, app-owned JSON deliberately outside
  the hand-editable YAML library. There is no receipt verification because there is no backend to
  verify against; the downside is a contrived free tip, which costs nothing that was ever owed.
  `useTipStore` is **not** in `_layout.tsx`'s startup gate — the root layout renders `null` until every
  store it awaits is ready, and cold start shouldn't wait on a file almost nobody has.

  The paywall line, if a Pro tier is ever revisited: **export stays free** regardless. Gating it would
  contradict the data-ownership pitch that the whole product rests on.

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
  picking several exercises, not just the one just created. It originally carried its own local
  `slugify` copy — the 4th, following the then-precedent of small per-screen copies — which is what
  finally made the duplication worth removing: all four now import `domain/slug.ts`.
- ✅ **kg/lb is a display preference, and there is now a preferences store to hold it.** Two things
  here shape future work and neither is visible from the commit that added the toggle.

  **Storage stays metric, unconditionally.** `exercises.yaml` is a file users hand-edit, export and
  share, so `target_weight: 60` has to mean the same thing wherever it's opened — a library whose
  numbers changed meaning with the reader's settings would be unshareable, which is most of the
  product's pitch. `domain/units.ts` is the only converter; `useUnitSystem()` the only reader of the
  preference. A pound value must never reach disk.

  **The lossy direction is solved by not converting, not by rounding.** Pounds display at 0.1 and
  kilograms store at 0.01, which is what makes a *pound-authored* value survive the trip (135 → 61.23
  kg → 135.0); showing pounds at two decimals instead would put the quantisation on screen as 134.99,
  in front of the person who just typed 135. The reverse trip can't be fixed the same way, because
  0.1 lb is coarser than 0.01 kg: 100 kg shows as 220.5 lb and converts back to 100.02. So
  `buildExercise` takes `previousWeightKg` and keeps the stored value when the field wasn't edited.
  Without it, opening an exercise in pounds and saving an unrelated change quietly moves its weight —
  a little further on each save — and in the override editor invents an override nobody asked for.
  Any future editor of a stored weight has to pass it too.

  Two smaller calls: the −/+ step is 5 lb rather than a converted 2.5 kg, because 5.51 lb matches no
  plate or rack anyone owns and the stepper exists to move in increments the equipment has; and
  `measurementSystem: 'uk'` maps to **metric**, since British gyms load kilo plates however the UK
  weighs people.

  **`preferences.json` now exists**, app-owned JSON next to `supporter.json` and deliberately outside
  the library — and the appearance preference has since migrated onto it, closing the reset-on-relaunch
  bug. Unlike `useTipStore` it *is* in `_layout.tsx`'s startup gate: it decides how every weight
  renders and what color the app is, so arriving after first paint would swap both under the user.
  `loadPreferences` returns `null` rather than a default, so "never chosen" reaches the store, which is
  the only layer that can answer it with the device's own measurement system.

  **Every field added here after the first release must be `.default()`ed, not required.** A missing
  key fails `safeParse`, and `loadPreferences` answers a failed parse with `null` — so a required new
  field would silently reset every *other* preference in the file for existing users. `themePreference`
  is the worked example.
- ✅ **A11y is complete, and two constraints from finishing it shape anything that touches it next.**
  The pass now covers every interactive control; what isn't visible from the commits is why two
  things are shaped the way they are.

  **The reorder handle carries the screen-reader path, not the row.** `ReorderableList` was
  gesture-only, and a drag has no non-visual equivalent, so the handle also takes
  `accessibilityRole="adjustable"` plus `increment`/`decrement` actions. The obvious alternative —
  putting the actions on the row wrapper — is wrong and shouldn't be re-proposed: making the wrapper
  `accessible` collapses the row into one element and *hides* the remove button and the circuit's
  text fields inside it, trading a reorder path for several controls. The labels are a required prop
  (`labelsFor`) rather than defaulted, because the component has no locale bundle of its own and an
  English default would be a hardcoded string no `pt` test could catch.

  **`react-native-web` drops `accessibilityActions` and `accessibilityValue` entirely** — it maps
  role and label only, so in a browser the handle is a named `role="slider"` with no actions and no
  position. Verified by reading the rendered attributes, not assumed. So the non-gesture reorder is
  **native-only, and a browser check cannot see it**; the jest tests cover the wiring, and TalkBack on
  a device is the only thing that can confirm the rest. Don't "fix" the missing web actions.

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
  never pass through the zod schema (imported YAML can't do this; `sets` is `positive()` there).
  `validateConfig(type, values)` — added in `exercise-editor.tsx`, since moved to
  `domain/exercise-form.ts` where its tests live — mirrors those schema constraints:
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
  the third option expressible. (It was in-memory at the time; it now persists via `preferences.json`.)
- ✅ **No ES2023 change-array-by-copy methods (`toSorted`, `toReversed`, `toSpliced`, `with`) until
  Hermes support is verified on a device.** This is the one thing standing between the five
  `unicorn/no-array-sort` / `no-array-reverse` warnings and a clean lint run, so it will come up again.
  All five sites already sort or reverse a **copy** (`[...weeks].sort(…)`); oxlint only recognises that
  for an array declared in the same scope, so it flags correct code. `toSorted` would satisfy it — but
  it's a runtime builtin, this app runs on Hermes 0.17 (RN 0.86), and nothing checked into the repo
  says whether that build ships it: `hermesc` compiles rather than runs, no RN or Expo package calls
  these methods, and the web build proves nothing because it's V8. Swapping a proven idiom for one
  whose failure mode is *device-only* isn't worth silencing a linter, so each site carries an
  `oxlint-disable-next-line` naming the reason instead. Revisit by running one on a device or an
  emulator, not by recalling a Hermes changelog.
- ✅ **The seed library is curated starter content, and three domain rules constrain what it may
  contain.** It ships two programs and four workouts a new user can press start on, rather than the
  format demo it used to be. The content itself is in `storage/seed-library.ts` and readable there;
  what isn't visible from that file is the rules any *edit* to it has to respect, all three of which
  the old seed broke:

  - **`programs[0]` is the default.** `activeProgram` (`selectors.ts`) falls back to the first library
    program when no session has been run yet, and there is no starter-program picker — so the
    no-equipment program stays first, and the dumbbell one is browse-only until explicitly started.
  - **Multi-day weeks sort by `day.localeCompare`** (`nextWeekAfter`), so `Monday`/`Wednesday`/
    `Friday` would walk a week as *Friday → Monday → Wednesday*. Day labels have to sort in training
    order, hence `Day 1`/`Day 2`/`Day 3` — and `Dia 1`/`Dia 2`/`Dia 3` in the translation, which is
    pinned per-language by the test rather than left to whoever writes the next one.
  - **Weeks resolve sparsely and overrides don't carry forward.** Weeks 1/3/6 make "next up" skip 2, 4
    and 5 entirely, so a seeded program enumerates every week and repeats an override in each later
    week it should still apply to.

  Two content rules, deliberate rather than incidental: exercise `notes` describe **the app's own
  progression model only** — never form cues, injury or diet, so the app keeps describing its data
  model rather than prescribing training — and the seed is also the web build's entire library and the
  reseed target for a corrupt `exercises.yaml`, so it has to stay small enough to be a reasonable thing
  to reset to.

  **The seed ships per-language, as one English structure plus a string table** (`seed-translations.ts`)
  — reversing an earlier English-only call, whose reasoning was that a locale-picked seed doubles every
  future content edit. That cost is real and this shape is the answer to it, so the rule an edit has to
  respect is: **structure changes once, and every language owes it a string.** `seed-library.test.ts`
  fails the language that didn't get one, in both directions (a missing translation and a stale key
  pointing at content that was renamed away), and runs every structural invariant above against every
  language, because a translated seed is a real library a real first launch lands on. Two constraints
  behind that shape, neither obvious from the files:

  - **Seed strings must never live in `en.json`/`pt.json`.** The seed is written to `exercises.yaml`
    and becomes the user's own data at that moment. Locale-bundle strings re-render on every language
    change, which would rename exercises the user has since edited and logged against — the exact thing
    the never-translate-user-data rule exists to prevent. Read once at seed time, then frozen.
  - **The pick is shared with the corrupt-file reseed**, so a library corrupted after the user switched
    device language comes back in the new one. Accepted rather than overlooked: the content is being
    regenerated from scratch anyway, and the alternative is reseeding into a language the user is no
    longer reading.
- ✅ **The app authors format, never training intent — it owns nothing the user could call theirs.**
  Settled while scoping the AI-authoring work, where the open question was whether shipping a prompt
  template would make Kettle the owner of the advice that template produces. It doesn't, provided the
  template carries **format only**: the schema, the user's own ids/names/types, and the importer's own
  error text. What it must never carry is intent — goals, set/rep prescriptions, progression schemes,
  exercise selection, or anything about the user's body. Ask of any string the app emits toward an
  assistant: if the generated program turns out badly, does this sentence make that Kettle's fault?

  This is the same line the seed library's `notes` rule is drawn to stay behind, and it doesn't
  disturb it: seeded content is editable starter data under that rule, not advice the app stands
  behind. The worked example already in the tree is import's repair prompt — "fix the YAML and send
  back the corrected file", not a word about training. The reason it's a constraint rather than a
  preference is that data ownership is the entire pitch: an app with no backend and no account has
  nothing to stand behind a training claim with, and the moment it ships one, the contents of the
  user's library stop being solely theirs.

- ✅ **Fixed: `exportLibrary`/`exportSession` threw past their own `.catch()`.** Resolving `.uri`
  constructs an `expo-file-system` `File`, which has no web implementation and throws *synchronously* —
  before `share()`'s async body is entered — so the throw escaped the returned promise and every
  caller's `.catch()` missed it, surfacing as an unhandled error. Both are now `async`, which turns it
  into a rejection those existing handlers catch. Fixes the crash on Library's export and the same
  latent hole in History's.

---

## Shipped work

Write-ups of what landed — audio cues, the number pad, the three test phases, I18n-0 through I18n-2,
and A11y-1 through A11y-4 — live in [`history.md`](history.md). They were moved out of this file to
keep it a plan: the same ~330 lines of completed work under a forward-looking heading is the exact
failure mode the decision-log note above warns about, regrown one heading level up.

## Planned work

- **Lean into AI-generated workouts — the format is already the feature.** A hand-editable YAML
  library that merges by `id` is exactly what an assistant is good at emitting, and the pipeline it
  would land in already exists and is already safe: zod validates on import, `mergeLibraries` replaces
  whole objects by id rather than patching, and the summary names what changed before anything is
  written. Nothing in the data model needs to change for this, which is the whole argument for
  positioning the app this way (store listing and README, not a new SKU — see the tip-jar entry on why
  there's no paid tier to attach it to). **The plumbing is now complete** — all four pieces below
  shipped, and the loop runs end to end: copy the format out, paste the YAML back, copy the refusal
  out if it's wrong. The README half of the positioning has landed too ("Bring your own assistant"),
  written to the ownership line in the decision log: it describes the format, the validation and the
  fact that the app never calls a model, and it closes by saying what happens *in* a workout isn't
  Kettle's to prescribe. **What's left is the Play store listing**, which isn't in this repo:

  - ~~**A paste path into import.**~~ ✅ Shipped. Both input sources funnel through one
    `review(text, source)`, so a paste is refused for the same reasons and in the same words as a
    file, and the parse → merge → summary pipeline is untouched. Two things the commit can't tell
    you: whitespace-only input is guarded **twice** (disabled button *and* an early return) because
    unguarded it reaches js-yaml and comes back "expected a document, but the input is empty" — an
    accusation aimed at someone who hasn't typed yet; and the hostile-YAML question was settled
    empirically rather than assumed, so it doesn't need re-litigating each time this area grows:
    js-yaml 5's default schema rejects `!!js/function` outright, a `__proto__` key never reaches
    `Object.prototype`, and a name carrying `<script>` renders as inert text. The one real residual
    is that js-yaml applies no alias-expansion limit, so a crafted anchor bomb can hang the app —
    reachable through the picker long before paste existed, and a DoS on the user's own device.
  - ~~**A machine-readable schema to hand the model.**~~ ~~**The user's existing ids, alongside
    it.**~~ ✅ Both shipped, as one payload: `domain/assistant-brief.ts` builds a "Copy the format for
    an assistant" brief of `z.toJSONSchema(rawLibrarySchema)` plus every id/name/type currently in the
    library, and the import screen copies it. Generated rather than written out, so it can't drift
    from what the importer accepts — which was the whole argument, given
    `authoring-exercises-yaml.md` had already drifted. Two things worth knowing before extending it:
    `toJSONSchema` **silently drops zod's cross-field refinements** (`target_reps_max >=
    target_reps_min`, an override targeting exactly one of `exercise`/`block`, unique `(week, day)`),
    so the brief tells the model the importer checks more than the schema shows and leaves the rest to
    the repair loop rather than restating those rules and becoming the third copy; and the brief is
    scoped to the *format* side of the ownership line (decision log), which is what a test in
    `assistant-brief.test.ts` pins rather than a comment.
  - ~~**A repair loop from the errors we now return.**~~ ✅ Shipped. A refusal now carries a "Copy
    error" button that puts one framing line plus the refusal itself on the clipboard, so a rejected
    file goes straight back to the assistant that wrote it. Two calls worth not re-litigating: the
    button appears only for refusals about the *content* (`ParseError`/`MergeError`) and never for a
    failed read, a failed write or an unhydrated library — handing "no space left on device" to a
    model asks it to fix something it can't reach — and the rejected YAML is deliberately **not**
    attached, since an assistant that just emitted it still has it and a hand-edited file is on the
    user's own disk.

  **Hard constraint, decided in advance:** the app must never call a model itself. The Play listing
  declares zero data collected/shared (see the tip-jar entry), and an API key field or an in-app
  "generate" button breaks that claim and needs a Data Safety declaration. This is bring-your-own
  assistant: generated anywhere, imported here. **Settled:** a prompt template may ship, because it
  will carry format and nothing else — see the ownership entry in the decision log for where that line
  falls.

- ~~**Audit for graceful degradation.**~~ ✅ Done, and it found what it predicted it would: the gap was
  entirely in the throws no boundary covers. Three shapes, all now closed, and the reasoning behind the
  fixes is what's worth keeping:

  - **A workout must outlive the disk that's recording it.** Session writes are synchronous and happen
    inside the runner's `advance()` — an event handler or an interval tick, so *no* error boundary sees
    them. A full disk ended the session between two sets. `writeSession` is now the single non-throwing
    choke point every session write funnels through; a failure is recorded and stepped over, and
    `takeWriteFailure()` is what keeps that from being silent. Deliberately not surfaced mid-set: the
    person is holding a plank, and a dialog about free space is no more useful then than it is honest
    to hide it afterwards.
  - **The store's `errors` were collected and never rendered.** Since hydration existed: a session file
    that wouldn't parse was known about and never mentioned. History shows them now, which is also
    where the write failures above land.
  - **Every library write reached the screens uncaught.** `saveExercise`/`saveWorkout`/`saveProgram`
    and the three deletes were `await`ed and then followed by `close()`, so a failed write left the
    modal sitting there looking like the button hadn't been pressed. All six now catch into the error
    line each editor already had. Import was the only screen that ever did this, and it's the pattern
    the rest copied.

  Left alone deliberately: `savePreferences` already returns a boolean instead of throwing, and the tip
  store already checks it.

- ✅ **Audit for refactoring opportunities — surveyed, and the answer is mostly "don't".** Held to the
  bar the `slugify` dedup set: only where the copies have actually drifted or would. Recorded because
  each of these looks like an obvious cleanup until you check, and re-proposing them is the likelier
  failure than leaving them:

  - **The parallel `switch` blocks stay.** There are five, not four — three over `SessionEntry`
    (`entrySetCount`, `sessionEntryResult`, `entryVolume`) and two over `Exercise`
    (`estimateExerciseSeconds`, `memberVisitSeconds`). Every one is exhaustive over a discriminated
    union **with no `default`**, so adding a type is already a *compile error* in each place that
    hasn't been updated. The drift they'd be consolidated to prevent cannot happen silently, and a
    per-type record of lambdas would trade three separately-named, separately-documented functions
    computing three different things for one table with worse narrowing.
  - **`new-exercise-form.tsx` is no longer a meaningful copy.** It and `exercise-editor.tsx` and
    `program-override-editor.tsx` all import the same `buildExercise`/`CONFIG_FIELDS`/`TYPE_OPTIONS`/
    `validateConfig`/`configToStrings`/`fieldUnitLabel` from `domain/exercise-form.ts`. What's left
    duplicated is JSX layout for a different container with different actions, and no logic has
    drifted — `validateConfig` is wired in both save paths.
  - **The long files stay until something needs changing in them.** No defect tracks to any of them,
    and the two biggest are the two riskiest to touch (`use-session-runner.ts` is the timer path). If
    `workout-editor.tsx` (785) is opened for a real reason, the seam is its two picker panels — they're
    self-contained, and `new-exercise-form.tsx` already embeds inside them.

- **Color-code the session progress indicator.** The dots at the top of the runner
  (`session-progress-dots.tsx`) are one per **workout block** — `total` is `workout.blocks.length` — not
  one per circuit, so the first thing this has to settle is what a dot is colored *by*: a block is a
  single exercise, a circuit, or a rest, and the middle one has no single type to color with. Three
  constraints, none visible from the component itself:

  - **The runner has two hues and they already mean something.** `RunnerColors` carries `accent` (warm)
    and `accentCalm` (blue), and the split in use is work vs rest — `session-rest.tsx` is the only
    screen on the calm one. A per-type scheme needs a categorical palette that doesn't exist yet; build
    it through the `dataviz` skill's categorical procedure and run its validator against the runner's
    fixed `background: '#17140d'` rather than picking seven hues by eye.
  - **Hue can't be the only channel.** The dots are decorative geometry today — no label, no
    screen-reader path, and a fixed `height: 4` that the a11y house rules exempt for exactly that
    reason. The moment a color carries meaning that exemption lapses: it needs a second channel and it
    stops being exempt from the contrast check.
  - **Only the active dot is distinguished at all.** `dotActive` widens to 22 and takes `accent`;
    completed and upcoming dots are an identical 9×4 at 22% opacity. So whether the useful thing to
    encode is *type* or *progress* is the real question — both want the same channel, and progress is
    the one a person mid-workout is actually asking about.

- **Are seven exercise types enough?** Checked against the config shapes rather than brainstormed, so
  the survey doesn't get re-run: one candidate is genuinely a new type, and most of what sounds like one
  is already expressible.

  - **`for_time` is the real gap** — fixed work, measure the clock. It's exactly `amrap` inverted
    (`amrap` fixes the clock at `timeCapSec` and counts rounds), and nothing today records "3 rounds,
    how long did that take". Needs its own `SessionEntry` shape (elapsed, plus whether a cap was hit)
    and a count-up runner screen against a round target.
  - **Already expressible — don't add a type for these:** Tabata is `hiit` at 20/10×8; E2MOM is `emom`
    with `intervalSec: 120`; an unconfigured `cardio` is already a count-up stopwatch (see the
    `validateConfig` entry); and distance repeats (400m × 6) are a one-member circuit with `rounds` and
    rest, since `session-steps.ts` caps a circuit member at one set per round.
  - **Config extensions, not types:** a weighted hold (`TimedHoldConfig` has no `targetWeightKg`, nor
    `TimedHoldSetLog` a weight), tempo prescriptions, and per-set ladders/pyramids/drop sets. The last
    is the largest by far — `RepsConfig` carries one target range for *all* sets and `buildSteps`
    expands them uniformly, so per-set targets change the step model, not just a form.

  Either way the compiler names the work: `ExerciseType` feeds the `Exercise` union, `CONFIG_FIELDS`'s
  `Record<ExerciseType, …>`, and the two exhaustive `switch`es over `Exercise` — and a new
  `SessionEntry` variant breaks the three over that — which is the payoff the decision log's
  "parallel switches stay" entry was banking. `schema.ts`, `yaml-mapping.ts`, `TYPE_OPTIONS` and both
  locale bundles are the parts it can't catch for you.

- **An analytics screen — progress across the whole log, rather than one exercise at a time.**
  *Analytics* here means **charts over the user's own local sessions**, and the word is doing dangerous
  double duty: an analytics *SDK* is a hard no (see the tip-jar entry — zero data collected/shared is a
  printed store claim), and nothing in this entry sends anything anywhere. Spelled out because "add
  analytics" read out of context is precisely the change that breaks the Data Safety declaration.

  **Placement is settled: it branches off History**, as a modal route pushed from that screen — not a
  sixth tab. `(tabs)/_layout.tsx` already has five `NativeTabs.Trigger`s, which is the conventional
  ceiling for a native tab bar, and History is where this belongs on the merits anyway: it owns the
  session log and already carries the stat tiles and the search that narrows them. Concretely that means
  a new `src/app/analytics.tsx` sibling registered in `_layout.tsx` with
  `presentation: 'modal', headerShown: false` and opened with the shared `ModalHeader` — the same shape
  as every other non-tab screen in the app, `program-detail.tsx` being the closest precedent (reached
  from a tab, not from another modal). Two consequences: adding the route file means regenerating
  `.expo/types/router.d.ts` by briefly running the dev server, or `router.push('/analytics')` fails
  typecheck; and the entry point wants to be a header control on History rather than a row in the list,
  which is already full of sessions.

  Most of the math is already written and just isn't collected anywhere: `historyStats`, `thisWeekStats`
  and `currentStreak` are on Today and History already, and `exerciseHistory` + `entryVolume` cover
  per-exercise volume. Four things to settle before building, in rising order of cost:

  - **`entryVolume` and `sessionSetCount` are module-private** in `selectors.ts` and need exporting,
    exactly as `sessionEntrySummary` and `nextWeekAfter` did before them. The cheap part.
  - **`VolumeChart` is the wrong component to reuse**, by its own design note: it's deliberately a
    sparkline — no axes, direct value labels, sized to sit inline above a list that already states every
    value. A screen-sized chart needs a scale, so it needs axes, gridlines and a tick strategy. That's a
    new component built through the `dataviz` skill, not a wider `VolumeChart`.
  - **Branching off History raises one question the tab version wouldn't have:** does the screen inherit
    History's active search filter, or always aggregate all-time? History already sets a precedent in
    the *other* direction — its stat tiles narrow to the visible subset, and the header switches from
    "All time" to "N of M", because three all-time numbers above a filtered list would describe sessions
    that aren't on screen. Arriving from a filtered History and showing unfiltered charts would break
    that same expectation, so inheriting the filter is the consistent answer; it needs the same honest
    header treatment rather than silently charting a subset.
  - **This is the consumer that makes `listSessions()`'s O(all sessions ever logged) real.** It's in the
    decision log as a risk with nothing exercising it; a screen whose whole job is aggregating all of
    history is that something. The remedy named there still stands — lazy or paginated loading, or a
    small index file — and explicitly *not* consolidating the per-session files.

  One thing to decide up front rather than discover: an SVG chart has no screen-reader story, and the
  precedent already set is that **the numbers ship as text as well** — the volume chart sits directly
  above a list that spells out every value. A screen of charts with no textual equivalent would be the
  first a11y regression since the house rules landed.

- **Drive a running session from the wrist.** Wear OS bridges phone notifications, action buttons
  included, so an ongoing notification carrying Done / Back / +30s is a watch remote with no watch app,
  no second APK and no sync layer — and the runner's public API is already exactly that vocabulary
  (`advance()` takes no arguments and reads the step's targets). Full write-up, the verified
  expo-notifications facts it rests on and the deliberate scope cuts in
  [`watch-remote-plan.md`](watch-remote-plan.md). Two things worth knowing before opening it: the
  standalone Wear OS app was costed and rejected because per-device `expo-file-system` storage makes a
  data-carrying watch a Bluetooth sync project, and `opensAppToForeground` defaults to `true`, which
  would make every wrist tap yank the phone open. Carries a fix for a live i18n break at
  `use-session-runner.ts:511`, where the rest notification's copy is hardcoded English outside the
  locale bundles.

## Open bugs

Found while planning the tests/a11y/i18n work (see `testing-a11y-i18n-plan.md`), each verified against
the code. Listed worst first.

**Fixed since:** `historyStats`'s "1.5h 30m"; the EMOM interval count; weight never being captured;
side effects inside `setState` updaters; `addRestSeconds` not rescheduling its notification;
`currentStreak`'s DST stepping; the display-name chip comparison; circuit members writing one entry
per round (below); `today`/`dateLabel` freezing at module scope (fixed with the I18n-3 locale work —
it's per-render now); `programs.tsx`'s stale "overrides aren't editable in-app" copy; the four
duplicate `slugify` copies, now one `domain/slug.ts` that all four call sites import; and
`sessionSetCount`, `slugify`'s ASCII-only ids and `session-hold.tsx`'s `NaN%` (below). Notes on the
structural ones:

- **A crash mid-exercise lost that exercise's sets, not just the set in progress** — §7.2's "loses at
  most the in-progress set" was a claim the code didn't honour, since sets accumulated in memory until
  the *exercise* finished. Now a write-through: the exercise's entry is appended on its first set and
  rewritten on each one after. Both flush-shaped bugs on this list came from the same place — a
  buffer whose flush boundary had to be inferred from the step list — and the write-through removes
  the question rather than answering it again.

- **Circuit members wrote one entry per round** instead of accumulating. Found by the phase-2 tests,
  not by the architecture pass. `advance()` flushed whenever the *next* step belonged to a different
  member — which in a round-robin circuit is every hand-off — so a 3-round, 3-member circuit produced
  9 single-set entries where `session-steps.ts`'s own expansion comment says it should produce 3
  entries of 3 sets. Fixed at the time by separating two questions that had been conflated ("are we
  changing exercise right now?" for the audio cue, "is this member finished?" for the flush); the
  second question no longer exists, since each set now writes itself into its member's own entry.

- The **`setState` updater** fix reads the step index from a ref instead of the updater's argument, so
  every commit/flush/`logEntry` now runs once in the event handler. The ref is advanced eagerly so two
  `advance()` calls in one tick (the ticking interval and the foreground catch-up can both fire) don't
  repeat a step. Verified by driving a full session with a `goPrev` and redo mid-way: one entry per
  exercise and exactly 8 sets, where a duplicated commit would inflate both.
- The **`currentStreak`** fix steps with `setDate()`. Its regression tests are honest about their
  limits: Node ignores `TZ` on Windows, so on a DST-free machine they pass whether or not the bug is
  present. CI sets `TZ` explicitly, which is where they actually bite.
- **`sessionSetCount`** now counts one set per interval actually performed — a HIIT/AMRAP round, an
  EMOM minute — instead of one per entry, which had made a 20-minute EMOM worth the same as a single
  hold in every History and Today tile. `cardio` stays 1, `rest` 0.
- **`slugify` keeps the user's own script** rather than transliterating: diacritics are stripped
  (`Flexão` → `flexao`, previously the mangled `flex-o`) and any other letter or digit is kept, so
  `Приседания` and `腕立て伏せ` get real ids. Two things worth not rediscovering — the ids run through
  `NFD`-strip-`NFC` so a composed and a decomposed `ã` can't become two ids for one name, and the
  match is by token (`[\p{L}\p{N}][\p{L}\p{N}\p{M}]*`) rather than by replacing a negated class,
  because Indic/Thai vowel signs are combining marks that must stay attached to their consonant
  while an emoji's U+FE0F is also a mark and must not become an id of its own.
- **`session-hold.tsx`'s `NaN%`** is guarded the way `session-interval.tsx` already was. Worth noting
  for the next 0-config bug: nothing validates a program week's override config — the schema types it
  as a free record of numbers and the in-app override editor doesn't call `validateConfig` — so the
  runner screens can't assume the constraints `validateConfig`/`schema.ts` enforce elsewhere.

- **`Alert.alert` is a no-op on web.** react-native-web ships `class Alert { static alert() {} }`, so
  every confirm dialog silently does nothing in the browser — all the deletes and finish-session.
  Native is unaffected and web is a dev/preview target, so this is logged rather than fixed. It does
  mean a browser check of any confirm flow proves nothing unless the script patches it, which is how
  session delete was actually verified end to end.

## Open questions from the product plan — all five settled

Kept as a record of where each landed, since §12 is where they're numbered.

- **Timed-hold direction** (§12.2): **count up, with the range as a marker.** A countdown has no
  non-arbitrary number to count from once the target is a range (`hold_sec_min` + optional
  `hold_sec_max`), and counting up is what the log records. The bar spans the top of the range with the
  minimum marked part-way along.
- **Merge conflict transparency** (§12.5): **a field-level diff** (`domain/library-diff.ts`), shallower
  for workouts and programs than for exercises — see §12.5 for why that asymmetry is deliberate.
- Earlier: `rest` stayed first-class (§12.1), rep-block rest is an auto timer that's skippable
  (§12.3), and `blocks` grew a `circuit` kind for supersets/circuits (§12.4) without a rewrite.
  `hiit`/`emom`/`amrap`/`cardio` are all runnable now, on a unified interval runner with matching
  `SessionEntry` log shapes.
