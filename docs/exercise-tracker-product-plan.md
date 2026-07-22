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
  .version                  # schema version marker
```

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
- Same rule for **workouts**, keyed by workout `id`.
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

---

## 8. Recommended Stack

| Concern | Choice |
|---|---|
| Framework | Expo (managed) — fast iteration, clean access to haptics/audio/notifications/keep-awake |
| YAML | `js-yaml` (parse/serialize) |
| Files | `expo-file-system` (read/write app document storage) |
| Import/export | `expo-document-picker` (pick YAML to import) + `expo-sharing` (export library or session) |
| Validation | `zod` (validate YAML→JSON on every load and import) |
| State | Zustand — hydrate store from YAML on launch; store is runtime source of truth; persist changes back to disk |
| Timers/cues | `expo-av`/`expo-audio` (audio), `expo-haptics` (vibration), `expo-notifications` (background fallback), `expo-keep-awake` |
| Session runner | Explicit state machine (XState or a hand-rolled reducer) |

---

## 9. Data Flow

```
Launch  → read exercises.yaml + sessions/ → validate (zod) → hydrate Zustand store
Author  → edit in-app → serialize → write exercises.yaml
Import  → document-picker → parse → validate → MERGE by id → validate merged → write exercises.yaml
Run     → session state machine → flush each entry → sessions/<id>.yaml
Finish  → write ended_at → finalize session file
Export  → serialize library or a session → expo-sharing → user saves/syncs the file
```

---

## 10. MVP Scope (v1)

Get the **live session engine** right — it's the differentiator.

1. **Exercise library** — load, view, and CRUD exercises with a type + default config; persist to `exercises.yaml`.
2. **Workout builder** — order exercises + insert rest blocks; save as a template in `exercises.yaml`.
3. **Live session runner** — timed types (hiit / emom / amrap / timed_hold) with audio + haptic cues and auto-advance; rep types with reps/weight input and rest timers; **mixed reps + timed within one workout**; pause/skip/previous.
4. **Session history** — list past sessions from `sessions/` with basic stats.
5. **Import (merge) / export** — merge an imported library by `id`; export library or a single session.

**Deferred:** social, programs/plans, charts, wearable sync, cloud backup, supersets/circuits-as-groups.

---

## 11. Roadmap

| Phase | Focus |
|---|---|
| **1** | Domain model + YAML load/validate/merge + exercise library + workout builder (no timers yet) |
| **2** | Live session engine for one timed type (HIIT) end-to-end; nail timer reliability + incremental flush |
| **3** | Extend runner to `reps` and `timed_hold`; support mixed reps+timed workouts (calisthenics case) |
| **4** | Add `emom` / `amrap`; session history + basic progression (last-time weights/holds, volume per session) |
| **5** | Polish: import/export UX, cloud sync guidance, programs/plans, charts |

---

## 12. Open Questions to Settle Before Building

1. **Rest as a first-class type vs. an attribute.** Recommended: keep `rest` first-class — it composes better with "breaks and all" and shows up in history.
2. **Timed-hold display direction.** Count *down* from target hold, or count *up* to see how long the user actually held (useful when they exceed or fall short of the target)? Consider count-up with the target shown as a marker.
3. **Rep-block rest behavior.** Fixed auto-timer vs. tap-to-end. Recommended: support both (auto-timer that's skippable).
4. **Supersets / circuits (repeat a group N times).** Complicates the runner significantly — recommend deferring, but design the workout `blocks` model to allow nesting later so it's not a rewrite.
5. **Merge conflict transparency.** How much detail to show when an imported `id` overwrites a locally-tweaked definition (diff view vs. simple "updated" count)?
