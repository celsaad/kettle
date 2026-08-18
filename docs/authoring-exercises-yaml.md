# Authoring `exercises.yaml`

> **Live reference, and one of `schema.ts`'s three hand-maintained mirrors.** The other two are
> `product-plan.md` §4 and `site/format.html`; a format change lands in all three in the same PR, per
> `AGENTS.md` § "Changing the YAML format". The complete samples here are parsed by
> `docs-samples.test.ts` and the field tables by `format-mirrors.test.ts` — so a field name that
> doesn't exist fails the suite. The surrounding prose is not checked.

Reference for hand-writing exercise, workout, and program definitions, matching exactly what
`src/domain/schema.ts` validates and `src/domain/yaml-mapping.ts` parses. Field names below are the
literal YAML keys (snake_case) — the app maps them to camelCase internally.

For whole worked programs rather than field-by-field rules, `site/examples/*.yaml` holds four
complete libraries (published at `celsaad.github.io/kettle/examples.html`) that between them use
every type and structure documented here.

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
- **All three lists are required keys, even when empty.** A file that adds two exercises and nothing
  else still needs `workouts: []` and `programs: []`, or it's refused with a schema error naming the
  missing one. (Their *contents* are what's optional — see merge-by-id at the bottom.)
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
| `timed_hold` | `sets` (int >0), `hold_sec_min` (>0, *optional*), `hold_sec_max` (>0, *optional*, ≥ `hold_sec_min`, needs `hold_sec_min`), `rest_sec` (≥0) | e.g. L-sit, plank; same fixed-vs-range shape as `reps`, and the hold **ends itself** at the top of the range — omit both targets for a max-effort hold that counts up until you tap Done |
| `cardio` | `duration_sec` (>0, *optional*), `distance_meters` (>0, *optional*) | give either, both, or neither — an empty `config: {}` is valid and runs as a plain count-up stopwatch |
| `rest` | `duration_sec` (≥0) | a standalone rest block between exercises — see below |

All 7 types run step-by-step in the session screen — there's no longer any limitation on which
types are runnable end-to-end.

### How a `timed_hold` ends

The two target fields are both optional, and which you give decides when the set stops. The runner
always counts **up** — the number on screen is the number written to your session log — so the
targets set the end rather than the display.

| what you write | when the set ends | on screen |
|---|---|---|
| `hold_sec_min: 30` | at 30s, by itself | bar fills to 30s |
| `hold_sec_min: 15` + `hold_sec_max: 25` | at 25s, by itself | bar fills to 25s, with 15s marked part-way along and a chime as you cross it |
| neither | never — you tap **Done set** | no bar; the clock just counts |

Leaving both out is how you write "hold as long as you can": the set runs until you end it and logs
whatever you managed, which is what makes a hold PR visible in your history. Give a target and the
step ends on its own, with a 3-2-1 of ticks into the end so you can prepare the dismount without
looking at the phone — the point being that in a dead hang you can't reach it anyway.

`hold_sec_max` on its own is refused: a range needs both ends.

> **If you already have holds in your library, they now end themselves.** `hold_sec_min` used to be
> required, so every existing hold has a target and will stop there — a "60s minimum, hold to
> failure" set is cut at 60. Delete `hold_sec_min` to get the old count-until-you-tap behaviour.

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
  set it to `0`, for back-to-back work with no rest). `rest_between_rounds_sec` is the rest taken
  after finishing all members of a round, before the next round starts; both rest fields are
  optional. A superset is a circuit — see "Supersets" below.
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

### Supersets

**A superset is a two-member circuit.** There is no separate superset block type, and you don't need
one — a circuit already runs its members round-robin, which is exactly what alternating A/B is.
"3 × 8 chin-ups supersetted with 3 × 8 dips, 90s after each pair" is:

```yaml
- type: circuit
  rounds: 3                        # the *set* count: 3 chin-up sets and 3 dip sets
  rest_between_exercises_sec: 0    # within the pair — straight from A into B
  rest_between_rounds_sec: 90      # after each full A+B pair
  exercises:
    - exercise: chinups
    - exercise: dips
```

That runs `chinups → dips → 90s → chinups → dips → 90s → chinups → dips`, with no trailing rest
after the last pair. The three fields map onto how you'd say it out loud:

| You'd say                     | Field                                    |
| :---------------------------- | :--------------------------------------- |
| "3 sets of each"              | `rounds: 3`                              |
| "no rest between the two"     | `rest_between_exercises_sec: 0` (or omit)|
| "90 seconds after each pair"  | `rest_between_rounds_sec: 90`            |

Two consequences of the circuit rules above are worth spelling out, because they're the ones that
bite:

- **`rounds` carries the set count, not the members' own `sets`.** A circuit member is visited once
  per round whatever its config says, so `chinups` keeps whatever `sets` and `rest_sec` make sense
  for it *on its own*, in some other workout — those values are simply ignored here. Don't edit an
  exercise's `rest_sec` to `0` to express a pairing; that changes the exercise everywhere it's used,
  and the pairing still won't happen.
- **Both members get the same number of sets.** Unequal pairs — 2 sets of A against 3 of B — can't
  be expressed as one circuit today. Author the common rounds as a circuit and the odd sets as a
  separate block after it.

The mistake this replaces: writing the two exercises as separate `type: exercise` blocks and setting
`rest_sec: 0` on the first one. That runs A, A, A, B, B, B — not a superset — and gives the first
exercise no rest at all between its own sets.

## Programs

A program is a periodized, multi-week wrapper around workouts already defined above. Each week
points at a `workout` id — or declares itself a rest day — and can optionally layer per-exercise
config overrides on top of the base `exercises:` definitions for that week only:

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
- **Week numbers are taken literally, not as a schedule to fill in.** The example above jumps 1 → 3 →
  6 to keep it short; a program authored that way genuinely has three weeks, and the home screen's
  "next up" walks 1 → 3 → 6, never offering 2, 4 or 5. Enumerate every week you intend to train.
- Only include an `overrides` list for a week when that week changes something. Weeks are resolved
  independently against each exercise's/block's base definition in `exercises:`/`workouts:` — an
  override does **not** carry forward into later weeks on its own, so if you want week 3's change to
  persist through week 5 and beyond, repeat the override in each of those later weeks too.
- `notes` on a week is optional, same freeform-string idea as an exercise's `notes`.
- A week entry can optionally carry a `day` (a freeform label like `Monday` or `Push` — not a
  strict weekday enum) for programs that run more than one session per week. Add `day` when two or
  more entries need to share the same `week` number; each `(week, day)` pair must be unique within
  a program. Programs with one session per week (the common case) can omit `day` entirely, and
  behave exactly as before. **The label is display text and is never parsed** — the days of one week
  number run in the order you list them, so name them however you like but write them in the order
  you train them. For example, a 2-day/week split:

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

- A week entry can instead be a **rest day**: set `rest_day: true` and leave `workout` out. It runs
  nothing, logs nothing and appears in no session history, but it holds its place in the rotation —
  the home screen shows a rest card instead of queuing the next workout, and moves past it by itself
  a day later. Rules, all enforced on import:
  - `workout` is still **required on every week that isn't a rest day**. A dropped or misspelled
    `workout:` line is an error, not a silent day off.
  - A `rest_day: true` week may not also name a `workout`, and may not carry `overrides` — it runs
    nothing, so there is nothing to override.
  - `day` and `notes` work exactly as they do on any other week. A note is the natural place for
    "walk 20 minutes" or "stretch"; there is no separate active-recovery type.
  - A program must have at least one week that runs a workout.
- **Write out every rest day you actually take**, including the ones at the end of the week. Kettle
  spends one rest entry per calendar day, so a three-session week written as three entries rolls
  into the next week the day after your last session. Seven entries make the week a week:

  ```yaml
  programs:
    - id: full-body-week
      name: Full Body · 3 Days
      weeks:
        - week: 1
          day: Day 1
          workout: full-body-a
        - week: 1
          day: Day 2
          rest_day: true
          notes: Walk, stretch, nothing heavy.
        - week: 1
          day: Day 3
          workout: full-body-b
        - week: 1
          day: Day 4
          rest_day: true
        - week: 1
          day: Day 5
          workout: full-body-a
        - week: 1
          day: Day 6
          rest_day: true
        - week: 1
          day: Day 7
          rest_day: true
  ```

## Writing in another language

The format itself has no language. Keys are always English snake_case — `target_reps_min` is a key,
not a word that ever reaches the screen — and every value you *do* see in the app (`name`, `notes`, a
program week's `day`) is user data: it renders verbatim, in whatever language you typed it, and is
never passed through the app's translations. The app's own chrome (tabs, buttons, the set counter,
the rest screen) follows the device language independently, in English or Brazilian Portuguese.

So there is no per-locale name field, and nothing to add for one — a library is one person's file,
and a Portuguese library is one whose names are written in Portuguese:

```yaml
version: 1

exercises:
  - id: descanso
    name: Descanso
    type: rest
    config:
      duration_sec: 90

  - id: flexoes
    name: Flexões
    type: reps
    config:
      sets: 3
      target_reps_min: 8
      target_reps_max: 15
      rest_sec: 60
    notes: Pare 2 repetições antes da falha.

  - id: prancha
    name: Prancha
    type: timed_hold
    config:
      sets: 3
      hold_sec_min: 20
      hold_sec_max: 45
      rest_sec: 45

workouts:
  - id: treino-a
    name: Treino A
    blocks:
      - type: exercise
        exercise: flexoes
      - type: exercise
        exercise: descanso
      - type: exercise
        exercise: prancha

programs: []
```

**Translating a library you already have is a rename of `name` (and `notes`) and nothing else.** Ids
are the wiring — blocks, circuit members and program weeks all reference exercises and workouts by
`id`, never by name — and every other field is a number, a type, or a duration, none of which change
meaning with the reader. Keep the ids as they are and the whole structure survives the translation
untouched. The practical route is to export the library (**Library → export**, or **Settings →
Export library**), edit the names in the exported file, then import it back:

- **Ids stay put.** Changing an `id` to match the new name doesn't rename anything — merge-by-id
  would *add* a second exercise under the new id and leave the old one behind, with every workout
  still pointing at the old one. Translate the names, leave the ids alone. (New ids you're inventing
  from scratch can be written in any script — the app flattens Latin accents when deriving one from a
  name, `Flexão` → `flexao`, but a hand-typed `flexões` is equally valid.)
- **Each translated item has to carry its whole definition.** Import replaces a matching `id` with
  the imported object outright rather than patching it, so an exercise entry that's only `id` +
  `name` doesn't keep its old config — it fails validation for the missing `type`/`config`. Editing
  an exported file rather than writing a fresh one gets this for free.
- **Renaming one exercise is faster in the app** — Library → the exercise → edit its name. Export and
  re-import is for doing all of them in one pass.

The starter library the app seeds itself with on first launch follows the same rule from the other
side: it's seeded in your device's language (English or Portuguese) and then it's *yours* — switching
the app's language later won't rename it, because by then those names are your data like any others.
Renaming them is the same edit as renaming anything else.

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
      - week: 7
        rest_day: true
        notes: Nothing scheduled. Start the block over from week 1 when you come back.
```

## Getting it into the app

The library is **merge-by-id** on import (Library tab → Import): any `id` already in your library
gets replaced by the imported definition; new `id`s get added; everything else is left untouched.
There's no need to include your whole existing library in a file you're importing — just the
exercises/workouts/programs you want to add or change.

If an assistant is writing the YAML for you, start from **Library → Import → Copy the format for an
assistant**: that puts a JSON Schema generated from `schema.ts` itself, plus every id/name/type
already in your library, on the clipboard to paste into a chat. The ids matter as much as the schema
— import merges by `id`, so a generated program can only reference exercises that already exist (or
that the same file defines).

There are two ways in, both reaching the same validation and the same preview:

1. **A file.** Save your YAML anywhere (any name — `exercises.yaml`, `my-workout.yaml`, whatever),
   then **Library → Import → Choose exercises.yaml** and pick it.
2. **Pasted text.** **Library → Import → Paste YAML instead**, paste the whole thing, then **Review
   paste**. Nothing is saved to a file first, which is the shorter path when the YAML came out of a
   chat window rather than an editor.

Either way, review the new/updated summary and confirm with **Merge & import**.

If something's malformed, the import screen shows the validation error inline (from
`parseLibraryYaml`) rather than silently failing — e.g. a missing required field, an unknown
`exercise` reference in a block, or a `type` that isn't one of the seven above. The error names the
offending ids, and a **Copy error** button beside it puts the whole refusal on the clipboard — so if
an assistant wrote the YAML, the fix is to paste that refusal back to it rather than to hunt through
the file by hand.
