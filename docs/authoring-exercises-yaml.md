# Authoring `exercises.yaml`

Reference for hand-writing exercise, workout, and program definitions, matching exactly what
`src/domain/schema.ts` validates and `src/domain/yaml-mapping.ts` parses. Field names below are the
literal YAML keys (snake_case) — the app maps them to camelCase internally.

## File shape

```yaml
version: 1

exercises:
  - id: <slug>
    name: <display name>
    type: <one of the 7 types below>
    config:
      ...
    notes: <optional freeform coaching cue>

workouts:
  - id: <slug>
    name: <display name>
    blocks:
      - type: exercise
        exercise: <exercise id>
        config: { duration_sec: <n> }   # optional, rest-block override only
      - type: circuit
        id: <optional slug>        # only needed if a program override targets this circuit
        rounds: <n>
        exercises:
          - exercise: <exercise id>
          - exercise: <exercise id>

programs:
  - id: <slug>
    name: <display name>
    weeks:
      - week: <n>
        workout: <workout id>
        notes: <optional>
        overrides:
          - exercise: <exercise id>        # exactly one of `exercise` or `block` per override
            config: { <partial config override> }
          - block: <circuit block id>
            config: { <partial config override> }
```

- `id` is a hand-typed slug (lowercase, hyphenated is conventional) — not a UUID. It's what
  `blocks[].exercise`, circuit members, and program references point at, and what re-importing the
  same file will match on to update. Any non-empty string is valid, in any script: the ids the app
  derives from a name keep whatever script that name was written in (`Приседания` → `приседания`),
  flattening Latin accents only (`Flexão` → `flexao`).
- `exercises`, `workouts`, and `programs` are flat lists in the same file.
- `version: 1` is required at the top level.
- `notes` is optional on every exercise — a freeform string for a coaching cue ("stop 2 reps shy of
  failure") or a video link.

## Exercise types and their `config` fields

Every field below is validated: numbers must be positive/non-negative as noted, and anything not
marked *optional* is required.

| type | config fields | notes |
|---|---|---|
| `hiit` | `work_sec` (>0), `rest_sec` (≥0), `rounds` (int >0) | work/rest interval repeated `rounds` times |
| `emom` | `interval_sec` (>0), `total_minutes` (>0), `target_reps` (int >0, *optional*) | every-minute-on-the-minute |
| `amrap` | `time_cap_sec` (>0) | as-many-rounds-as-possible within the cap |
| `reps` | `sets` (int >0), `target_reps_min` (int >0), `target_reps_max` (int >0, *optional*, ≥ `target_reps_min`), `target_weight` (≥0, *optional*, kg), `rest_sec` (≥0) | e.g. bench press; give only `target_reps_min` for a fixed target, or add `target_reps_max` for a range like "5 to 10 reps" |
| `timed_hold` | `sets` (int >0), `hold_sec_min` (>0), `hold_sec_max` (>0, *optional*, ≥ `hold_sec_min`), `rest_sec` (≥0) | e.g. L-sit, plank; same fixed-vs-range shape as `reps` |
| `cardio` | `duration_sec` (>0, *optional*), `distance_meters` (>0, *optional*) | give either or both |
| `rest` | `duration_sec` (≥0) | a standalone rest block between exercises — see below |

All 7 types run step-by-step in the session screen — there's no longer any limitation on which
types are runnable end-to-end.

## Workouts

Each block is tagged with a `type`, either a single `exercise` or a `circuit`:

```yaml
workouts:
  - id: calisthenics-a
    name: Calisthenics A
    blocks:
      - type: exercise
        exercise: l-sit          # references an exercise id above
      - type: exercise
        exercise: rest
      - type: exercise
        exercise: pullups
      - type: exercise
        exercise: rest
        config: { duration_sec: 120 }   # overrides this block's rest duration only

  - id: finisher-circuit
    name: Finisher Circuit
    blocks:
      - type: circuit
        id: finisher-rounds
        rounds: 3
        rest_between_exercises_sec: 15
        rest_between_rounds_sec: 60
        exercises:
          - exercise: pushups
          - exercise: l-sit
          - exercise: pullups
```

- Every `exercise` reference (in a `type: exercise` block or a circuit's `exercises` list) must
  match an `exercises[].id` in the same file (or already present in your library if you're
  importing a partial file — the app validates references against the *merged* result, per the
  import/merge rules below).
- `rest` is **not special-cased** — it's just another exercise with `type: rest`, referenced like any
  other. Define one `rest` exercise with a default duration, then reference it from multiple blocks;
  use the per-block `config: { duration_sec: N }` override when one particular rest should be longer
  or shorter than the default.
- `config` on a `type: exercise` block currently only supports overriding `duration_sec`, and only
  makes sense on a block referencing a `rest`-type exercise.
- A `circuit` block runs its `exercises` **round-robin** — A, B, C, A, B, C, ... for `rounds` rounds —
  not grouped by exercise (A, A, A, B, B, B, ...). It needs at least 2 members.
  `rest_between_exercises_sec` is the rest between consecutive members within a round (omit it, or
  set it to `0`, for back-to-back work with no rest — that's exactly how you author what's
  colloquially called a "superset": a 2-member circuit with no rest between exercises). There's no
  separate superset concept. `rest_between_rounds_sec` is the rest taken after finishing all members
  of a round, before the next round starts; both rest fields are optional.
- **A circuit member contributes exactly one visit per round, regardless of its own config.** The
  circuit's `rounds` is the only thing that repeats a member — a `reps` member's own `sets` (or a
  `timed_hold` member's own `sets`) is ignored inside a circuit, and no inter-set rest from the
  member's own `rest_sec` is inserted between visits (only the circuit's own rest fields apply
  between members/rounds). So `pushups` with `sets: 3` used as a circuit member still runs once per
  round, not 3 times per round — a 3-round circuit does 3 total pushup visits, not 9. This means
  every exercise type is fair game as a circuit member (a `reps`/`timed_hold` member just does one
  set worth of work per visit; `hiit`/`emom`/`amrap`/`cardio` members run their own full internal
  timing once per visit, since those types don't have a "sets" concept to ignore).
- `id` on a circuit block is optional — set it only if you want a program week's `overrides` to be
  able to target this circuit's own `rounds`/rest fields (see Programs below). It's meaningless on a
  `type: exercise` block, which has no params of its own beyond the `duration_sec` override.

## Programs

A program is a periodized, multi-week wrapper around workouts already defined above. Each week
points at a `workout` id and can optionally layer per-exercise config overrides on top of the base
`exercises:` definitions for that week only:

```yaml
programs:
  - id: pull-progression
    name: 6-Week Pull Progression
    weeks:
      - week: 1
        workout: calisthenics-a
        notes: Baseline — log where you land in each range.
      - week: 3
        workout: calisthenics-a
        notes: Add a 5th set once 2 clean sessions hit the top of the range.
        overrides:
          - exercise: pullups
            config: { sets: 5 }
      - week: 6
        workout: finisher-circuit
        notes: Deload — cut the finisher circuit down to 2 rounds.
        overrides:
          - block: finisher-rounds
            config: { rounds: 2 }
```

- Each entry in `overrides` targets **exactly one** of `exercise` (an `exercises[].id`) or `block` (a
  circuit block's own `id`, per the Workouts section above) — never both, and never neither.
  `overrides[].config` is a **partial** object using the same snake_case keys as what's being
  overridden: an exercise override uses that exercise's own config keys (e.g. `sets`,
  `target_reps_min`, `rest_sec` for a `reps` exercise); a block override uses the circuit's own keys
  (`rounds`, `rest_between_exercises_sec`, `rest_between_rounds_sec`) — only the keys you're changing.
- A block override only makes sense against a `type: circuit` block that has an `id`; it has nothing
  to patch on a `type: exercise` block (use an exercise override for that instead).
- Only include an `overrides` list for a week when that week changes something. Weeks are resolved
  independently against each exercise's/block's base definition in `exercises:`/`workouts:` — an
  override does **not** carry forward into later weeks on its own, so if you want week 3's change to
  persist through week 5 and beyond, repeat the override in each of those later weeks too.
- `notes` on a week is optional, same freeform-string idea as an exercise's `notes`.
- A week entry can optionally carry a `day` (a freeform label like `Monday` or `Push` — not a
  strict weekday enum) for programs that run more than one session per week. Add `day` when two or
  more entries need to share the same `week` number; each `(week, day)` pair must be unique within
  a program. Programs with one session per week (the common case) can omit `day` entirely, and
  behave exactly as before. For example, a 2-day/week split:

  ```yaml
  programs:
    - id: push-pull-split
      name: 6-Week Push/Pull Split
      weeks:
        - week: 1
          day: Monday
          workout: push-day
        - week: 1
          day: Thursday
          workout: pull-day
        - week: 2
          day: Monday
          workout: push-day
        - week: 2
          day: Thursday
          workout: pull-day
  ```

## A full, runnable example

```yaml
version: 1

exercises:
  - id: rest
    name: Rest
    type: rest
    config:
      duration_sec: 90

  - id: bench-press
    name: Bench Press
    type: reps
    config:
      sets: 5
      target_reps_min: 5
      target_weight: 60
      rest_sec: 120

  - id: l-sit
    name: L-Sit Hold
    type: timed_hold
    config:
      sets: 4
      hold_sec_min: 15
      hold_sec_max: 25
      rest_sec: 60
    notes: Escalate toward 25s before adding load.

  - id: pullups
    name: Pull-ups
    type: reps
    config:
      sets: 4
      target_reps_min: 6
      target_reps_max: 10
      rest_sec: 90
    notes: Beat last session within the range before adding a set.

  - id: pushups
    name: Push-ups
    type: reps
    config:
      sets: 3
      target_reps_min: 12
      target_reps_max: 20
      rest_sec: 45
    notes: Stop 2 reps shy of failure.

  - id: row-erg
    name: Row Erg
    type: cardio
    config:
      distance_meters: 2000

workouts:
  - id: calisthenics-a
    name: Calisthenics A
    blocks:
      - type: exercise
        exercise: l-sit
      - type: exercise
        exercise: rest
      - type: exercise
        exercise: pullups
      - type: exercise
        exercise: rest
        config: { duration_sec: 120 }

  - id: finisher-circuit
    name: Finisher Circuit
    blocks:
      - type: circuit
        id: finisher-rounds
        rounds: 3
        rest_between_exercises_sec: 15
        rest_between_rounds_sec: 60
        exercises:
          - exercise: pushups
          - exercise: l-sit
          - exercise: pullups

programs:
  - id: pull-progression
    name: 6-Week Pull Progression
    weeks:
      - week: 1
        workout: calisthenics-a
        notes: Baseline — log where you land in each range.
      - week: 3
        workout: calisthenics-a
        notes: Add a 5th set once 2 clean sessions hit the top of the range.
        overrides:
          - exercise: pullups
            config: { sets: 5 }
      - week: 6
        workout: finisher-circuit
        notes: Deload — cut the finisher circuit down to 2 rounds.
        overrides:
          - block: finisher-rounds
            config: { rounds: 2 }
```

## Getting it into the app

The library is **merge-by-id** on import (Library tab → Import): any `id` already in your library
gets replaced by the imported definition; new `id`s get added; everything else is left untouched.
There's no need to include your whole existing library in a file you're importing — just the
exercises/workouts/programs you want to add or change.

1. Save your YAML as a file (any name — `exercises.yaml`, `my-workout.yaml`, whatever).
2. In the app: **Library → Import**, pick the file.
3. Review the new/updated summary, then **Merge & import**.

If something's malformed, the import screen shows the validation error inline (from
`parseLibraryYaml`) rather than silently failing — e.g. a missing required field, an unknown
`exercise` reference in a block, or a `type` that isn't one of the seven above.
