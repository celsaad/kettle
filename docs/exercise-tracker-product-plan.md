# Exercise Tracker — Product Plan

A backend-free React Native app for planning and tracking workouts. Exercises and workout templates live in a hand-editable YAML library; completed sessions are written by the app to a local, append-only store. No server, no account — fully local, portable, and power-user friendly.

---

## 1. Product Overview

**Goal:** Let users define exercises of different *types* (HIIT, EMOM, AMRAP, reps, timed holds, rest), assemble them into workouts, run those workouts with type-appropriate live timers and inputs, and keep a durable history of what they actually did.

**Guiding principles:**

- **Local-first, backend-free.** All data is files on device. Sync is the user's choice (iCloud, Dropbox, git).
- **Library is hand-editable; sessions are app-owned.** Users curate `exercises.yaml` in any text editor. Sessions are written and validated by the app only — never hand-edited.
- **Sessions are self-describing.** A completed session renders correctly forever, even if the underlying exercise definition is later changed or deleted.
- **Type determines behavior.** An exercise's type drives which timers run and what data is captured — it's not just a label.

**Positioning under consideration:** the hand-editable library is also what makes the app a good target
for AI-generated workouts — an assistant emits YAML well, and import already validates it and merges by
`id` non-destructively. The enabling work, and the constraint that the app must never call a model
itself, are in the implementation plan's planned-work list rather than restated here.

---

## 2. Core Concepts

Three distinct entities, deliberately kept separate:

| Concept | What it is | Who authors it | File |
|---|---|---|---|
| **Exercise** | A reusable definition — the "what" (Burpees, Bench Press, L-sit hold) | User (hand-editable) | `exercises.yaml` |
| **Workout** | An ordered template of exercises + rest blocks — the plan | User (hand-editable) | `exercises.yaml` |
| **Session** | A recorded *execution* of a workout — the actual data | App (write-only) | `sessions/<id>.yaml` |

Separating **template** (plan) from **session** (execution) lets users repeat workouts and track progression across time. Keeping the library and the session log in different files means the app never rewrites the user's hand-formatted library when it saves a session.

---

## 3. Exercise Types

Type is the heart of the model. Each type carries its own **config** (the plan) and produces its own **log shape** (the actual result).

| Type | Config (planned) | Log (actual) |
|---|---|---|
| `hiit` | work sec, rest sec, rounds | per-round elapsed, completed? |
| `emom` | interval sec, total minutes, target reps/interval | reps done per interval, missed intervals |
| `amrap` | time cap, movements | rounds + reps completed |
| `reps` | sets, target reps, target weight, rest sec | per-set: actual reps, weight, RPE, rest taken |
| `timed_hold` | sets, hold sec, rest sec | per-set: actual hold duration, rest taken |
| `cardio` | duration or distance | time, distance, pace |
| `rest` | duration sec | actual rest taken |

### 3.1 Mixed-mode sessions (reps + timed within one workout)

A single workout commonly mixes rep-counted and time-based movements — e.g. a calisthenics session with **L-sit holds** (timed) and **pull-ups** (reps). This is handled naturally by the model because **each block in a workout references an exercise that carries its own type.** The session runner switches its behavior per block:

- A `reps` block → rep/weight input UI, then a rest timer.
- A `timed_hold` block → a countdown/count-up hold timer, then a rest timer.

No special "mixed workout" type is needed. A workout is just an ordered list of typed blocks, and the runner adapts to each block's type as it advances. This keeps calisthenics circuits (hold → pull-ups → rest → repeat) and traditional strength days on the exact same engine.

---

## 4. File Formats

### 4.1 `exercises.yaml` — library + templates (hand-editable)

```yaml
version: 1

exercises:
  - id: burpees                 # stable slug, referenced by sessions
    name: Burpees
    type: hiit
    config:
      work_sec: 40
      rest_sec: 20
      rounds: 4

  - id: bench-press
    name: Bench Press
    type: reps
    config:
      sets: 5
      target_reps: 5
      target_weight: 60         # kg; unit is a global app setting
      rest_sec: 120

  - id: l-sit
    name: L-Sit Hold
    type: timed_hold
    config:
      sets: 4
      hold_sec: 20
      rest_sec: 60

  - id: pullups
    name: Pull-ups
    type: reps
    config:
      sets: 4
      target_reps: 8
      rest_sec: 90

  - id: rest
    name: Rest
    type: rest
    config:
      duration_sec: 90

workouts:
  - id: calisthenics-a
    name: Calisthenics A
    blocks:
      - exercise: l-sit         # timed hold
      - exercise: rest
      - exercise: pullups       # reps
      - exercise: rest
        config: { duration_sec: 120 }   # per-block override
```

**Design notes:**

- **`id` is a human-readable slug**, not a UUID — a hand-edited file stays legible, and sessions reference something meaningful.
- **Per-block config overrides** reuse one definition with tweaks (e.g. longer rest on the last block) without duplicating definitions.
- **Workouts live in the same file** so everything the user authors is in one place. If it grows, split into `workouts.yaml` later — the model doesn't change.

### 4.2 `sessions/<id>.yaml` — one file per session (app-written)

```yaml
version: 1
id: 2026-07-22T18-30-00
workout: calisthenics-a       # nullable — supports ad-hoc sessions
started_at: 2026-07-22T18:30:00Z
ended_at:   2026-07-22T19:05:12Z
entries:
  - exercise: l-sit
    type: timed_hold          # embedded — session is self-describing
    sets:
      - hold_sec: 20, rest_taken_sec: 58
      - hold_sec: 18, rest_taken_sec: 61
      - hold_sec: 15, rest_taken_sec: 60

  - exercise: pullups
    type: reps
    sets:
      - reps: 8, rest_taken_sec: 90, rpe: 7
      - reps: 7, rest_taken_sec: 95, rpe: 8
      - reps: 5, rest_taken_sec: 92, rpe: 9
```

Each entry **embeds its `type`** (and optionally a snapshot of the config used) so a session can be read and rendered without cross-referencing the library. Old sessions stay valid even if the exercise definition is later edited or deleted.

---

## 5. Storage Architecture

### 5.1 Directory layout (app document dir)

```
/exercise-tracker/
  exercises.yaml            # library + workout templates (hand-editable)
  sessions/
    2026-07-20T09-15-00.yaml
    2026-07-22T18-30-00.yaml
  supporter.json            # tip-jar state (app-owned, never hand-edited)
  preferences.json          # appearance + kg/lb (app-owned, never hand-edited)
```

A directory-level `.version` marker was planned here and deliberately **not** built: every YAML file
already carries its own `version: 1` field, so a separate marker has no consumer until a real
migration exists.

`supporter.json` and `preferences.json` are JSON rather than YAML on purpose: YAML is the format the
user is invited to edit and export, and neither purchase state nor app settings belong in a library
that gets shared. `supporter.json` is also the only place tip history can live — Play consumables
aren't restorable — so it self-heals to an empty state on a bad read rather than reporting an error
nobody can act on.

**One file per session** = the directory is the database. This gives append-only-log benefits (crash safety, easy sync, no full-file rewrites, no partial-write corruption of history) while keeping the hand-editable library as a single tidy file.

### 5.2 Critical rules for file-based storage

1. **Sessions must survive library changes.** With no DB and no foreign keys, denormalize: store `type` (and optionally the config snapshot) inside each session entry. This is the single most important rule.
2. **Never hold a live session only in memory.** A crash or a backgrounded-then-killed app mid-workout would lose everything. Flush incrementally to the session file after each set/round.
3. **Never rewrite all of history on save.** One file per session sidesteps this entirely — each save only writes/updates that session's own file.
4. **Validate on load and on import.** Even app-written files get schema-validated on load; hand-edited library files *will* sometimes be malformed. Fail with a clear error, not a crash.
5. **Version every file.** The `version` field enables future schema migrations.

---

## 6. Library Import — Merge Semantics

Importing an `exercises.yaml` **merges** into the existing library rather than replacing it.

**Merge algorithm (keyed by `id`):**

- For each incoming **exercise**: if the `id` exists, the imported definition **replaces** the existing one; if not, it's **added**.
- Same rule for **workouts** and **programs**, each keyed by its own `id`. (Programs postdate this section; they merge on identical terms — whole-object replace, never a field-level patch.)
- Existing items whose `id` is **not** present in the import are **kept untouched**.

**Before applying, validate the merged result as a whole:**

- No duplicate `id`s within the final library.
- Every workout block references an exercise `id` that exists post-merge.
- Every exercise has a known `type` and the required config fields for that type.

**Recommended UX:** show a pre-merge summary — "X new exercises, Y updated, Z workouts affected" — and let the user confirm before writing. Because a re-imported id overwrites, surface updates clearly so users don't silently lose local tweaks.

> **Note on sessions:** import/merge applies to the **library only**. Sessions are never imported or hand-edited; they are app-owned. On load they are validated leniently (they should always be well-formed since the app wrote them) and treated as read-only history.

---

## 7. Live Session Engine

The session runner is the hard part and the core value. Model it as an explicit **state machine**:

```
idle → running → resting → paused → complete
```

The runner advances block by block through the workout, adapting to each block's type:

- **Timed types** (`hiit`, `emom`, `amrap`, `timed_hold`): auto-running countdown/interval timers, large readable display, audio + haptic cues on every transition, auto-advance to the next block.
- **Rep types** (`reps`): input reps/weight per set, then a rest timer auto-starts; "Next set" advances. Rest can auto-expire **or** be skipped early with a tap.
- **Rest blocks**: a simple countdown; skippable.

**Global controls:** pause / resume / skip / previous, available throughout.

### 7.1 Timer reliability — the make-or-break issue

React Native timers drift and get throttled when the app backgrounds — fatal for a HIIT app running with the screen off. Address this from day one:

- **Track time via wall-clock deltas** (`Date.now()` diffs), never by accumulating `setInterval` ticks.
- On background→foreground, **recompute elapsed from timestamps** rather than trusting the JS timer.
- Use **`expo-keep-awake`** to prevent screen sleep during a session.
- Schedule a **local notification** as a fallback so interval cues still fire if the app is backgrounded.

### 7.2 Incremental persistence

Wire a **flush-to-disk** into each meaningful state transition (set logged, round completed). Write to the session's working file as you go; finalize (`ended_at`) on completion. A mid-workout crash then loses at most the current in-progress set.

Implemented as a write-through: an exercise's entry is appended when its first set lands and rewritten in place as each later set is added, so "as you go" means per set rather than per exercise. It first shipped flushing only when an exercise *finished*, which read as satisfying this section and didn't — a crash three sets into a four-set exercise wrote none of them.

---

## 8. Recommended Stack

| Concern | Choice |
|---|---|
| Framework | Expo (managed) — fast iteration, clean access to haptics/audio/notifications/keep-awake |
| YAML | `js-yaml` (parse/serialize) |
| Files | `expo-file-system` (read/write app document storage) |
| Import/export | `expo-sharing` (export library or session) + `File.pickFileAsync()` — no `expo-document-picker`, since SDK 57's `expo-file-system` exposes the picker directly |
| Validation | `zod` (validate YAML→JSON on every load and import) |
| State | Zustand — hydrate store from YAML on launch; store is runtime source of truth; persist changes back to disk |
| Timers/cues | `expo-audio` (audio; `expo-av` is superseded), `expo-haptics` (vibration), `expo-notifications` (background fallback), `expo-keep-awake` |
| Session runner | Hand-rolled, in `use-session-runner.ts` — wall-clock timestamps rather than a formal state machine; XState was not needed |

---

## 9. Data Flow

```
Launch  → read exercises.yaml + sessions/ → validate (zod) → hydrate Zustand store
Author  → edit in-app → serialize → write exercises.yaml
Import  → pickFileAsync → parse → validate → MERGE by id → validate merged → write exercises.yaml
Run     → session state machine → flush each entry → sessions/<id>.yaml
Finish  → write ended_at → finalize session file
Export  → serialize library or a session → expo-sharing → user saves/syncs the file
```

---

## 10. MVP Scope (v1) — ✅ shipped, and then some

Get the **live session engine** right — it's the differentiator.

1. **Exercise library** — load, view, and CRUD exercises with a type + default config; persist to `exercises.yaml`. ✅ full CRUD, including delete with an in-use-by-workout guard (mirrors the workout-delete pattern).
2. **Workout builder** — order exercises + insert rest blocks; save as a template in `exercises.yaml`. ✅, plus a full workouts list (create/edit/**delete**, with an in-use-by-program guard) rather than a single hardcoded workout.
3. **Live session runner** — timed types (hiit / emom / amrap / timed_hold) with audio + haptic cues and auto-advance; rep types with reps/weight input and rest timers; **mixed reps + timed within one workout**; pause/skip/previous. ✅ all types are runnable, plus a pre-session 3-2-1 countdown, tick/exercise-change audio cues, and finish-session-early (commits the in-progress set/round instead of discarding it). `goPrev()` un-flushes the most recent `advance()` (drops that set/round/minute and rewrites the exercise's entry, or retracts the entry outright when that was the only thing in it) — scoped to one level deep: a second `goPrev()` in a row without an intervening `advance()` just moves the step index, same as before.
4. **Session history** — list past sessions from `sessions/` with basic stats. ✅, and per-exercise progression shipped after this was written: an exercise's edit screen has a "Recent" section listing the last few times it was logged, newest first (`exerciseHistory` in `selectors.ts`), above a per-exercise volume chart (`components/volume-chart.tsx`). See §11 phase 4 and the implementation plan's decision-log entry.
5. **Import (merge) / export** — merge an imported library by `id`; export library or a single session. ✅

**Beyond the original MVP scope**, already built: **multi-week programs** (periodized wrappers around workouts, with per-week per-exercise/per-circuit overrides and multi-session-per-week support via a `day` field) and **circuits/supersets** (round-robin block grouping with configurable rest between exercises and between rounds) — both were explicitly deferred below and shipped anyway. The home screen derives "next up" from the actual week/day a session was started under (stored on the session itself), not a completed-session count — the count-based version looked "random" once you jumped to a non-sequential week or redid one, since `program-detail.tsx` lets you start any week, not just the suggested one. The home screen also shows a small stats row: current daily streak and this week's session count/time.

**Still deferred:** social, wearable sync, cloud backup, charts.

---

## 11. Roadmap

| Phase | Focus | Status |
|---|---|---|
| **1** | Domain model + YAML load/validate/merge + exercise library + workout builder (no timers yet) | ✅ done |
| **2** | Live session engine for one timed type (HIIT) end-to-end; nail timer reliability + incremental flush | ✅ done (shipped as part of the full interval runner below) |
| **3** | Extend runner to `reps` and `timed_hold`; support mixed reps+timed workouts (calisthenics case) | ✅ done |
| **4** | Add `emom` / `amrap`; session history + basic progression (last-time weights/holds, volume per session) | ✅ done — `emom`/`amrap`/`cardio` are runnable, session history exists, and an exercise's edit screen shows its recent history (last-time weight/reps/holds, newest first) |
| **5** | Polish: import/export UX, cloud sync guidance, programs/plans, charts | ⚠️ partial — programs shipped (see §10) with full in-app CRUD, including per-week override editing; a per-exercise volume chart shipped too (see §13); import/export UX and cloud sync guidance are not done |

See §13 for the concrete, current gap list.

---

## 12. Open Questions to Settle Before Building

1. **Rest as a first-class type vs. an attribute.** ✅ Settled: kept `rest` first-class.
2. **Timed-hold display direction.** Still open. Currently counts *up* only (unchanged since the original mock UI) — no countdown option, no target marker.
3. **Rep-block rest behavior.** ✅ Settled: auto-timer that's skippable.
4. **Supersets / circuits (repeat a group N times).** ✅ Settled and shipped — `WorkoutBlock` has a `circuit` kind (round-robin members, configurable rest between exercises and between rounds), no rewrite needed.
5. **Merge conflict transparency.** Still open. Currently a simple new/updated count + changed-id list; no field-level diff.

---

## 13. Known Gaps (current)

What's genuinely missing today, checked directly against the code:

- **No in-app cloud-sync guidance.** Sync (iCloud/Dropbox/git) is left entirely to the user, with no in-app pointers.
- **Merge conflict view has no field-level diff** (see open question 5 above).
- **Timed-hold display direction was never revisited** (see open question 2 above).
- **Web has no persistence** — `expo-file-system` doesn't support web, so the web build runs on an ephemeral in-memory seed library. This is a platform constraint, not a product gap.
- ~~**Known bug (web only): leaving the session screen crashes with a redbox.**~~ ✅ Fixed — see the
  implementation plan's entry for the details. `useKeepAwake` now passes
  `suppressDeactivateWarnings`, the library's own flag for exactly this race.
- ~~**No automated tests at all.**~~ ✅ Closed — jest via `jest-expo`, running in CI alongside
  typecheck and lint. (AGENTS.md carries the current suite size; a second copy of the count here only
  ever went stale — it read "230 tests across 22 files" long after that stopped being true.) Covers
  the domain layer, the wall-clock session runner
  (§7.1, "the make-or-break issue"), and the highest-branch screens. Layout, animation, real audio and
  file writes are still verified by driving the running app rather than by test.
- ~~**A logged session can't be deleted.**~~ ✅ Shipped — delete from the expanded card in History,
  behind the usual destructive confirm. Editing a past session's *entries* is still not possible; only
  whole-session delete.
- ~~**No history search, no settings screen.**~~ ✅ Both shipped — History has name search (which also
  narrows the stat tiles), and there's a Settings modal with three-way appearance (light/dark/system),
  export/import, and library counts.
- ~~**The appearance preference doesn't persist across relaunches.**~~ ✅ Fixed — `themePreference`
  joined `unitSystem` in `preferences.json`, and `theme-context.tsx` reads it from the preferences
  store instead of holding its own `useState`.
- **`Alert.alert` is a no-op on web** — react-native-web ships an empty implementation, so every
  confirm dialog (all the deletes, finish-session) silently does nothing in the browser. Native is
  unaffected, and web is a dev/preview target, so this is logged rather than fixed.
- ~~**No accessibility or i18n work has been done.**~~ ✅ Closed — both shipped and are now house
  rules for new work rather than workstreams (see AGENTS.md). Every control carries a role and label,
  touch targets are 44px minimum, colors are contrast-measured, the runner survives large text sizes
  and announces transitions, and the UI ships in English and Brazilian Portuguese with locale-aware
  dates, numbers and first-day-of-week. **Still open within them:** screen-reader reordering for
  `ReorderableList`, which is gesture-only and so impossible without sight; the `height`-based search
  bars and stat cards outside the runner, which degrade at large text sizes rather than block;
  `program-guide.tsx`'s prose, still English-only. ~~Unit conversion~~ ✅ shipped — Settings → Units
  switches every weight between kg and lb, seeded from the device's measurement system; storage stays
  metric, so an exported library reads the same for everyone.
