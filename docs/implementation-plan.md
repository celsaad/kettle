# Kettle — Implementation Plan

Plan for building out the four workstreams the README lists as *not yet implemented*: reading/writing
`exercises.yaml` and `sessions/`, library import/merge, export, and the wall-clock-drift-hardened
timer engine (keep-awake, background notifications) — plus wiring the library/build CRUD screens.

See [`exercise-tracker-product-plan.md`](exercise-tracker-product-plan.md) for the product model this
plan implements.

## Decisions (settled)

- **Scope:** everything in the README — persistence, timer engine, import/merge, export, and
  library/build CRUD.
- **Config key style:** hand-editable YAML stays **snake_case** (`work_sec`, `hold_sec`) per the
  product plan; in-code domain objects stay **camelCase** (`workSec`, `holdSec`) as they already are
  in `src/constants/mock-data.ts`. A mapping layer bridges the two at the file boundary.

## Where things stand now

**Workstreams A + B + C are done.** The app runs on real files, not mock data:

- `src/domain/types.ts`, `schema.ts`, `yaml-mapping.ts` — canonical camelCase domain types, zod
  schemas for the snake_case YAML shape, and the bidirectional mapping between them.
- `src/storage/paths.ts`, `library-file.ts`, `session-files.ts`, `seed-library.ts` — file I/O on
  `expo-file-system`'s SDK-57 `File`/`Directory` class API (confirmed from the installed package's
  `.d.ts`s — see "Resolved" below). `exercises.yaml` is seeded from the former mock content on first
  launch; sessions are one file per id under `sessions/`, never a full-history rewrite.
- `src/state/library-store.ts`, `session-history-store.ts` — zustand stores exposing `status` /
  `error` / `hydrate()`, hydrated in parallel with font loading in `src/app/_layout.tsx`; the app
  blocks on both before rendering. `src/state/selectors.ts` derives all the display aggregates
  (`blockChips`, `workoutSummary`, `recentSessionsView`, `historyStats`, `historySessionsView`) that
  used to be hardcoded in mock data.
- Every screen (`(tabs)/index.tsx`, `library.tsx`, `build.tsx`, `history.tsx`, `session.tsx`) and
  `use-session-runner.ts` now reads from the stores instead of `@/constants/mock-data`, which has
  been deleted. `npm run typecheck` and `npm run lint` are clean.

**Scope note:** the library/session stores are currently **read + hydrate only** — no `saveExercise`/
`saveWorkout`/write-session-entry mutators were added, since nothing calls them yet (Workstreams D, E,
F below are what will need them). Building them now would have been unexercised, unverified code.
`src/storage/session-files.ts` does already expose `createSession` / `appendSessionEntry` /
`finalizeSession` as it's cheap, mechanical, and directly informed by the documented session file
format — Workstream E wires these into the runner and the session-history store next.

**Not yet done:** import/merge (D), export (D), the wall-clock-hardened timer + incremental flush
wired into the runner (E), and library/build CRUD (F). See below — unchanged from the original plan.

---

## 0. Dependencies & config

**Installed:** `js-yaml`, `zod`, `zustand`, `expo-file-system`, `expo-keep-awake`, `expo-haptics` (via
`npx expo install`, so versions are SDK-57-pinned). js-yaml v5 and zod v4 ship their own types, no
`@types/*` needed.

**Still to add when D/E land:** `expo-document-picker` or `File.pickFileAsync` (the new
`expo-file-system` API exposes file picking directly — evaluate whether a separate
`expo-document-picker` dependency is even needed before adding it), `expo-sharing`,
`expo-notifications`, `expo-audio`.

- `expo-notifications` needs a `plugins` entry in `app.json` + iOS permission strings when E lands.
- ✅ **API-version risk resolved:** SDK 57's `expo-file-system` is the class-based `File` / `Directory`
  / `Paths` API (confirmed from `node_modules/expo-file-system/build/*.d.ts`), not the legacy
  `readAsStringAsync`/`documentDirectory` functions. Key shapes used in `src/storage/`:
  `Paths.document`, `new Directory(...)` / `new File(...)`, sync `.exists`, sync `.create(options)`,
  async `.text()`, sync `.write(content)`, sync `.list()` (filter results with `instanceof File`).

## A. Domain model + schemas (`src/domain/`) — ✅ done

Implemented as planned, with one scope call: the exercise config schema covers all 7 product-plan
types (`hiit`/`emom`/`amrap`/`reps`/`timed_hold`/`cardio`/`rest`) since library/import validation
needs to recognize hand-authored files using any of them — but `amrap`'s config is just `time_cap_sec`
(the plan's "movements" sub-field is undefined in the spec and unused anywhere, so it wasn't modeled).
`SessionEntry`, by contrast, only covers `timed_hold` / `reps` / `rest` — the only shapes the current
runner can produce; extending it to log `hiit`/`emom`/`amrap`/`cardio` sessions is future work tied to
extending the runner (roadmap phases 3–4).

## B. Storage layer (`src/storage/`) — ✅ done

Implemented as planned. `paths.ts` resolves `<documentDir>/exercise-tracker/{exercises.yaml,sessions/}`
and ensures the directory exists; `library-file.ts` has `loadLibrary()`/`saveLibrary()`;
`session-files.ts` has `listSessions()`/`createSession()`/`appendSessionEntry()`/`finalizeSession()`.
No `.version` marker file was added yet — the `version: 1` field lives inside each YAML file already,
and a separate directory-level marker has no consumer until an actual migration exists.

## C. Zustand store + hydration (`src/state/`) — ✅ done

Implemented as planned, minus the write-mutators noted above (deferred to D/E/F). Cutover is complete:
every screen reads from `useLibraryStore`/`useSessionHistoryStore` + `src/state/selectors.ts`, and
`mock-data.ts` is deleted.

## D. Import/merge + export — not started

- `src/domain/merge.ts` — merge-by-`id` (exercises + workouts), then **validate the merged whole**:
  no duplicate ids, every block references an existing exercise, every exercise has valid type+config
  (§6). Return a summary (`new` / `updated` / `workouts affected`) + the merged library.
- Wire `src/app/import.tsx`: replace the hardcoded `changedItems`/counts with `expo-document-picker`
  → parse → `merge()` → show the real pre-merge summary → confirm → `saveLibrary()` + rehydrate.
- Export: `expo-sharing` to share `exercises.yaml` or a single session file. Add entry points
  (library "Export" action; a session share button in history).

## E. Timer engine hardening (core value, §7.1) — not started

Rewrite the timing in `src/hooks/use-session-runner.ts`:

- Track each phase by **`startedAt` timestamp**; compute elapsed/remaining from `Date.now()` deltas.
  `setInterval` only drives re-render, never the source of truth.
- On `AppState` background→foreground, recompute from timestamps.
- `expo-keep-awake` active during a session.
- On entering a timed phase, schedule an `expo-notifications` local notification at the phase-end
  wall-clock time as a backgrounded fallback; cancel on early advance/skip.
- `expo-haptics` + `expo-audio` cues on every phase transition.
- **Incremental flush:** create the session file on start (`started_at`); `writeSessionEntry()` on
  each set logged / round completed; `finalizeSession()` (write `ended_at`) on completion (§7.2).

## F. Library/Build CRUD — not started

The Library FAB (+), Build "Add block" / "Save" / edit affordances currently do nothing. Wire them to
store mutations that persist to `exercises.yaml` (MVP §10 items 1–2). This needs `saveExercise`/
`saveWorkout` mutators added to `useLibraryStore`, which were intentionally left out of Workstream C.
This is the largest *new-UI* piece.

---

## Suggested sequencing

1. ~~**A + B + C** — domain, storage, store cutover.~~ **Done.** App runs on real files (seeded from
   the former mock content), every screen fed by store selectors, launch gated on hydration.
2. **E** — timer hardening + incremental session persistence. Highest product value; next up.
3. **D** — import/merge + export.
4. **F** — library/build CRUD UI.

## Open questions from the product plan to settle during build

- Timed-hold display direction: count down from target, or count up with target as a marker (§12.2).
- Rep-block rest behavior: auto-timer that's skippable (recommended, §12.3).
- Merge conflict transparency: diff view vs. simple updated count (§12.5).
- Keep the `blocks` model shaped to allow nested groups later (supersets/circuits) without a rewrite
  (§12.4).
