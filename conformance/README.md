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

`native/android/shared/src/jvmTest/.../ConformanceTest.kt` checks the Kotlin port against the same
files, read in place. It finds suites by listing this directory, and one with no Kotlin runner yet is
reported as skipped under its own name, never as passing. Porting a suite is adding its runner there.

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
| `search` | `rankByName` and `filterByName` over `names` with `query` | `{ ranked, filtered }`, lists of names |
| `preferences` | `parsePreferences(input)`, the text of a `preferences.json` | the preferences with defaults filled in, or `null` where the file is refused |

`search` isn't part of the file format, but it is the same problem: two implementations must agree on
which workout a search finds. Its rules are in the header of `src/domain/search.ts`.

`preferences` is the same again, for `preferences.json`: RN writes it until M5, and the native shell
reads its scheme from it, so the two must refuse the same files.

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
  - **`<<` is not a merge key.** That's YAML 1.1, and some 1.2 loaders still apply it by default.
    Here it is an unknown key like any other, and dropped.
  - A plain `${NAME}` is a string. Some loaders give it an environment-variable tag.
  - A float literal too big for a double (`1e400`) is a **string**. Only `.inf`, `+.inf` and
    `-.inf` spell infinity, and the schema refuses all three where it wants a number.
  - `!!set` is an unknown tag, and so a YAML error. Some loaders still build it.
- **Unknown keys are dropped, at every level.** A strict decoder would refuse them.
- `3.0` passes an integer field. `2.5` doesn't, and neither does the string `"3"`. An integer field
  takes JavaScript's safe-integer range (up to 2^53 − 1), so a 32-bit int is too narrow for one.
- A key with nothing after it is `null`, and `null` doesn't satisfy an optional string.
- **Duplicate keys and tab indentation are YAML errors**, as is an empty document.
- **At most 1000 aliases** per document. The 1001st is a YAML error, raised before the schema runs.
- **Repair clamps what the schema reports as past a ceiling, and the top of the safe-integer range
  is one.** `target_reps_min: 1e16` has no ceiling of its own, and still repairs to 2^53 − 1.
- Rounding is JavaScript's `Math.round`: an exact half rounds **up**, toward +∞. Kotlin's
  `kotlin.math.round` rounds it to even instead. `roundToLong()` matches. Swift's default
  `rounded()` also matches for the non-negative values here, but rounds negative halves away from
  zero.
- **Search compares code points, not UTF-16 units.** Kotlin and Swift strings index differently from
  JS, and an edit distance over UTF-16 counts an astral character as two edits. Hiragana fold to
  katakana by a fixed offset (U+3041–U+3096, +0x60). Typo tolerance is decided by explicit code-point
  ranges, never a regex script class, so no engine's Unicode tables can change *that*. Word
  splitting, accent folding and lowercasing still use each platform's own tables (`\p{L}`/`\p{N}`/
  `\p{M}`, NFD/NFC, case mapping), so a character newer than a runtime's Unicode version can split
  differently there. CI's JDK 17 is behind the Node that writes the goldens. No case uses such a
  character, because no fix exists short of shipping one Unicode table to every platform.
- An EMOM's interval count is `floor(total_minutes * 60 / interval_sec)`, compared after the floor.
  Comparing the raw quotient refuses `total_minutes: 8.333333333333334` over a rounding error.

## Adding a case

1. **Add it to the suite's YAML.** A refusal case should carry one defect, so its `paths` don't
   depend on the order a validator checks things in.
2. **Run `pnpm run conformance:update`** and read the golden diff. A case whose golden doesn't say
   what its name says is a finding about the format, not about the case.
3. **Commit both files together.**
