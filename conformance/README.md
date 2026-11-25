# Conformance corpus

The library and session formats' behaviour, pinned as data rather than as one implementation's tests.
It is the format's authority: what a file means is what these cases and their goldens say, and the
prose around them, here and in `site/format.html`, explains them and loses to them. It was built to
hold the Kotlin port in [`docs/native-migration-plan.md`](../docs/native-migration-plan.md) to the
TypeScript parser, a case one passed and the other failed being a fork of the file format. The TS
parser was retired in #348 and deleted in #353 (the decision log): Kotlin took the goldens over
with a regeneration that changed no file, so the corpus is exactly what TS wrote, and it now holds
`:shared` to itself and to every platform that reuses it.

Each suite is two files:

- **`<suite>.yaml`** — the inputs, hand-written. Every case has a `name`, and some have a `why`.
- **`<suite>.expected.json`** — the outputs, **generated** from `:shared` and keyed by case name.
  Never hand-edit these.

`native/android/shared/src/jvmTest/.../ConformanceTest.kt` checks `:shared` against these files, read
in place, and writes them. It finds suites by listing this directory, and one with no runner is
reported as skipped under its own name, never as passing. Adding a suite is adding its runner there.
`LibrarySchemaFileTest.kt` beside it holds `content/library-schema.json`, the hand-maintained
JSON Schema the assistant brief carries, to the `library` suite's goldens.

When a change to `:shared` is meant to change behaviour, run
`./gradlew :shared:jvmTest -PupdateConformance` from `native/android`, then `pnpm run format`. The
golden diff it leaves is part of the review, because a change there is a change to what users' files
mean. The writer keeps the key order the TS writer left (until #348), so key order alone never
shows up as a diff.

## Suites

| Suite | Operation | Output |
|---|---|---|
| `library` | `parseLibraryYaml(input)`, or the file at `source` | parse result (below) |
| `localize` | `localizeLibrary(library, translationFor(tables, language))`, the library inline or at `source`, each table inline or a JSON path | the localized library |
| `session` | `parseSessionYaml(input)` | parse result |
| `repair` | `repairLibraryBounds(input)` | `{ repaired: false }`, or `{ repaired: true, result }` where `result` is the parse result of the repaired text |
| `merge` | `mergeLibraries(parse(existing), parse(incoming))` | `{ ok: true, library, summary }` or `{ ok: false, error }` |
| `program` | `findProgramWeek` / `resolveWorkoutForWeek` per query, `programWeekNumbers` per program | `{ weekNumbers, queries: [{ query, found, resolved }] }` |
| `overrides` | `mergeExerciseOverride`, `mergeBlockOverride`, `diffExerciseOverride`, `diffBlockOverride` per op | `[{ op, result }]` |
| `units` | `toDisplayWeight`, `fromDisplayWeight`, `weightStep`, `emomIntervalCount` | a number |
| `search` | `rankByName` and `filterByName` over `names` with `query` | `{ ranked, filtered }`, lists of names |
| `preferences` | `parsePreferences(input)`, the text of a `preferences.json` | the preferences with defaults filled in, or `null` where the file is refused |
| `supporter` | `parseSupporterState(input)`, the text of a `supporter.json` | the supporter state, the empty one where the file is refused |
| `steps` | the runner's `buildStepsWithLimits`, its mid-session edits, `upcomingOutline` and Coming up's `comingUpTimeline`, over the workout in `library` | the step list, the list after each edit, or the outline or timeline per index (the file's header) |
| `records` | by `op`: `entryBest`, `sessionEntryResult` or `sessionSetCount` over the session file in `session`; `recordKey(exercise, kind)`; `sessionDurationMinutes` of `session`; or `sessionRecords` of `session` against the session files in `prior`, named from the inline `library` | a list of `{ kind, value, reps, oneRepMaxKg }` or `null` per entry; a list of entry results; a set count; the key string; whole minutes; a list of `{ kind, exerciseId, exerciseName, … }` records, the kind's own fields after |

The `source` cases read `site/examples/` and the shipped seed and packs in `content/` in place,
so an edit to that content moves their goldens, and the diff is the record of what changed. `localize`
is how the app translates that shipped content: a table only ever replaces names, notes and day
labels, and its header lists the rules.

`search` isn't part of the file format, but it is the same problem: every platform must find the
same workout for the same search. Its rules are in `:shared`'s `Search.kt`, held by
`search.yaml`.

`preferences` is the same again, for `preferences.json`, and `supporter` for `supporter.json`: the
app's own settings files, which a platform must read, and refuse, the same way. Both became suites
while RN and native shared those files (until the preferences flip, #238), and stay for the next
platform.

`records` is not a file at all but what one means: which entries are a record, and on what. It held
RN's runner and native Stats to one rule while both existed (until #308), and it still pins what
counts as doing more, and how long a session ran.

`steps` isn't a file either: it is what a workout *runs*, step by step. It held the native runner to
RN's while both existed (#303). The flip release (#308) deleted RN's and kept the corpus: it is still the
plainest statement of what each workout runs, and a change to it still shows up as a golden diff.

## Reading the goldens

These rules are what an implementation has to meet. Everything else is free.

- **A parse result** is either `{ ok: true, value }` or a refusal:
  - `{ ok: false, kind: "invalidYaml" }` when the text isn't YAML the loader accepts.
  - `{ ok: false, kind: "schemaMismatch", paths: [...] }` when it is YAML but the schema refuses it.
  `paths` holds the refused fields in dotted form (`exercises.0.config.sets`), de-duplicated and
  sorted, with `(root)` for the document itself. **The wording of the error is not pinned.** A
  loader's and a validator's messages are library output, and no second implementation can match them.
- **Values are the camelCase domain model** of `:shared` (`Library.kt`, `Session.kt`), as JSON. An absent optional
  field is **absent**, never `null`. `null` appears only where the domain type says `| null`.
- **Compare numbers numerically, not as text.** `3` and `3.0` are the same value.
- **Lists keep their order.** Order is behaviour here: merge keeps the existing order and appends new
  ids in the order they arrived.
- **What a writer emits is not pinned. That it reads back is.** The test checks that every library
  and session case that parses survives serialize → parse unchanged. A second implementation owes the
  same round-trip, never the Kotlin writer's layout.

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
- **Duplicate keys and tab indentation are YAML errors**, as is an empty document. A TAB anywhere
  before a block key or a `-` on its line is indentation, even after spaces, where some loaders count
  it as one more column. A key is compared
  **as JavaScript's `String(key)` spells it**, so `1:` beside `"1":`, `1.0:` beside `1:` and `True:`
  beside `"true":` are duplicates, an alias as a key counts as its scalar's value, and a sequence or
  mapping as a key is a YAML error of its own. A number key is spelled by JS's `Number::toString`:
  an integer past 2^53 rounds to a double first, the digits are the fewest that read back (the even
  one of two equally near), and the exponent form starts below 1e-6 and from 1e21. A platform's own
  number printing differs from that in layout, and some runtimes' in the digits too.
- **A continuation line is indented by spaces past its block collection**: every line after the
  first of a plain or quoted scalar, or of a flow collection (its closing bracket included), needs
  more leading spaces than the column of the block mapping or sequence it sits in, none at the top
  level. A TAB may follow those spaces but never counts, a line of only spaces and TABs is empty and
  passes, and so does a comment line. Some loaders skip TABs here or don't check quoted scalars.
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
  differently there. The JDK that writes the goldens and the runtime on a phone needn't agree. No case uses such a
  character, because no fix exists short of shipping one Unicode table to every platform.
- An EMOM's interval count is `floor(total_minutes * 60 / interval_sec)`, compared after the floor.
  Comparing the raw quotient refuses `total_minutes: 8.333333333333334` over a rounding error.

## Adding a case

1. **Add it to the suite's YAML.** A refusal case should carry one defect, so its `paths` don't
   depend on the order a validator checks things in.
2. **Run `./gradlew :shared:jvmTest -PupdateConformance`** from `native/android`, then
   `pnpm run format`, and read the golden diff. A case whose golden doesn't say
   what its name says is a finding about the format, not about the case.
3. **Commit both files together.**
