# Kettle — open work

> **This is the backlog, and only the backlog.** What shipped is in [`history.md`](history.md); why
> it was built that way is in [`decisions.md`](decisions.md); the product model it implements is in
> [`product-plan.md`](product-plan.md). Nothing here has been built yet unless it is struck through.
>
> Named `implementation-plan.md` until the file was 869 lines, of which 411 were a decision log and
> 150 were write-ups of finished workstreams. Both were the same failure the docs rules warn about —
> completed work accumulating under a forward-looking heading — so both are gone from this file.

**Prune this file when you ship.** Deleting a finished bullet needs no write-up. Shipped part of a
multi-part entry? Leave the rest and say which part went. Keep entries short: if the list starts
wanting states and assignees, it wants GitHub issues instead.

## Settled scope

- Everything the original plan covered — persistence, timer engine, import/merge, export, and
  library/build CRUD — is implemented. The workstreams that carried it are in `git log`.
- **Config key style:** hand-editable YAML stays **snake_case** (`work_sec`, `hold_sec`); in-code
  domain objects stay **camelCase** (`workSec`, `holdSec`). `src/domain/yaml-mapping.ts` bridges the
  two at the file boundary, and nowhere else does.
- The SDK-57 API shapes this was all verified against — `expo-file-system`'s class-based
  `File`/`Directory`, `expo-notifications`, `expo-iap` — are recorded in
  [`sdk-57-api-notes.md`](sdk-57-api-notes.md), because the published docs for two of the three are
  wrong for the installed versions.

---

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
    `Object.prototype`, and a name carrying `<script>` renders as inert text. ~~The one real residual
    is that js-yaml applies no alias-expansion limit, so a crafted anchor bomb can hang the app.~~
    ✅ Closed, and the measurement corrected the claim in two ways worth keeping: an alias bomb never
    detonates on `load` at all (js-yaml resolves an alias to a *shared reference*, so 437 bytes
    describing 387M leaves parses in 3ms as a DAG — it detonates only on something that walks that DAG
    without tracking identity), and no placement of one survives `parseLibraryYaml`, because zod
    refuses an element without recursing into it and this schema has no recursive types. So the hang
    was unreachable rather than merely unexploited. `maxAliases: 1000` is set anyway, since that
    immunity is a property of the current schema rather than a guarantee about the next one.
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
    and the two biggest are the two riskiest to touch (`use-session-runner.ts` is the timer path).
    Two have since been split on their own merits — `state/selectors.ts` into one module per concern,
    and `workout-editor.tsx` into its block and picker components (the picker panels were the seam
    named here; the two block editors inside `renderItem` turned out to be the larger half).
    **`use-session-runner.ts` is the one to keep leaving alone**: a third of it is comments explaining
    wall-clock anchoring, and its timing refs are read by the tick effect, the foreground-resume effect
    and the notification fallback, so any split trades one long file for a coordination problem on the
    path that can lose a workout in progress. `yaml-mapping.ts` likewise — splitting it separates
    halves of a bijection that must be edited together.

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
  would make every wrist tap yank the phone open. (The i18n break this used to carry a fix for — the
  rest notification's hardcoded English — went with the timed-hold auto-end, which needed its own
  notification copy and wasn't going to add a second hardcoded string beside the first.)

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
