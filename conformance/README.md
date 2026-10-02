# Conformance corpus

The library and session formats' behaviour, pinned as data rather than as one implementation's tests,
so that a second implementation can be held to exactly what the first does. It exists for the Kotlin
port in [`docs/native-migration-plan.md`](../docs/native-migration-plan.md): while two parsers exist,
a case one passes and the other fails is a fork of the file format, and it fails CI rather than a
user's import.

Each suite is two files:

- **`<suite>.yaml`** — the inputs, hand-written. Every case has a `name`, and some have a `why`.
- **`<suite>.expected.json`** — the outputs, **generated** from the TypeScript implementation and
  keyed by case name. Never hand-edit these.

`src/domain/__tests__/conformance.test.ts` checks the TypeScript output against the goldens. When a
change to `src/domain/` is meant to change behaviour, run `pnpm run conformance:update`. The golden
diff it leaves is part of the review, because a change there is a change to what users' files mean.

## Suites

| Suite | Operation | Output |
|---|---|---|
| `library` | `parseLibraryYaml(input)`, or the file at `source` | parse result (below) |
| `session` | `parseSessionYaml(input)` | parse result |
| `repair` | `repairLibraryBounds(input)` | `{ repaired: false }`, or `{ repaired: true, result }` where `result` is the parse result of the repaired text |
| `merge` | `mergeLibraries(parse(existing), parse(incoming))` | `{ ok: true, library, summary }` or `{ ok: false, error }` |
| `program` | `findProgramWeek` / `resolveWorkoutForWeek` per query, `programWeekNumbers` per program | `{ weekNumbers, queries: [{ query, found, resolved }] }` |
| `overrides` | `mergeExerciseOverride`, `mergeBlockOverride`, `diffExerciseOverride`, `diffBlockOverride` per op | `[{ op, result }]` |
| `units` | `toDisplayWeight`, `fromDisplayWeight`, `weightStep`, `emomIntervalCount` | a number |

## Reading the goldens

These rules are what an implementation has to meet. Everything else is free.

- **A parse result** is either `{ ok: true, value }` or a refusal:
  - `{ ok: false, kind: "invalidYaml" }` when the text isn't YAML the loader accepts.
  - `{ ok: false, kind: "schemaMismatch", paths: [...] }` when it is YAML but the schema refuses it.
  `paths` holds the refused fields in dotted form (`exercises.0.config.sets`), de-duplicated and
  sorted, with `(root)` for the document itself. **The wording of the error is not pinned.** js-yaml's
  and zod's messages are library output, and no second implementation can match them.
- **Values are the camelCase domain model** from `src/domain/types.ts`, as JSON. An absent optional
  field is **absent**, never `null`. `null` appears only where the domain type says `| null`.
- **Compare numbers numerically, not as text.** `3` and `3.0` are the same value.
- **Lists keep their order.** Order is behaviour here: merge keeps the existing order and appends new
  ids in the order they arrived.
- **What a writer emits is not pinned. That it reads back is.** The test checks that every library
  and session case that parses survives serialize → parse unchanged. A second implementation owes the
  same round-trip, never js-yaml's layout.

## Behaviour a port is most likely to get wrong

Every one of these has a case, because each is a default that differs between YAML libraries:

- The loader uses the **YAML 1.2 core schema**:
  - `yes`, `no`, `on` and `off` are strings, not booleans.
  - **There is no timestamp type**, so an unquoted `2024-01-01` is a string.
  - An unquoted `300` is a number, so it's refused where a string is required. A type-directed
    decoder that reads every scalar as the field's type would wrongly accept it.
- **Unknown keys are dropped, at every level.** A strict decoder would refuse them.
- `3.0` passes an integer field. `2.5` doesn't, and neither does the string `"3"`.
- A key with nothing after it is `null`, and `null` doesn't satisfy an optional string.
- **Duplicate keys and tab indentation are YAML errors**, as is an empty document.
- **At most 1000 aliases** per document. The 1001st is a YAML error, raised before the schema runs.
- Rounding is JavaScript's `Math.round`: an exact half rounds **up**, toward +∞. Kotlin's
  `kotlin.math.round` rounds it to even instead. `roundToLong()` matches. Swift's default
  `rounded()` also matches for the non-negative values here, but rounds negative halves away from
  zero.
- An EMOM's interval count is `floor(total_minutes * 60 / interval_sec)`, compared after the floor.
  Comparing the raw quotient refuses `total_minutes: 8.333333333333334` over a rounding error.

## Adding a case

1. **Add it to the suite's YAML.** A refusal case should carry one defect, so its `paths` don't
   depend on the order a validator checks things in.
2. **Run `pnpm run conformance:update`** and read the golden diff. A case whose golden doesn't say
   what its name says is a finding about the format, not about the case.
3. **Commit both files together.**
