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
  Kettle's to prescribe. **The listing itself is now live**
  ([`com.casco.kettle`](https://play.google.com/store/apps/details?id=com.casco.kettle)); whether its
  description carries this positioning is the open half, and it can't be answered from the repo —
  the listing copy isn't in it:

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

- **Colour-code the session progress indicator — mostly settled against, kept for the one live
  constraint.** This entry used to argue about the runner's per-block dots: what a dot should be
  coloured *by*, given a block can be a single exercise, a circuit or a rest, and the middle one has
  no single type to colour with. The dots are gone — both indicators are proportional bars now
  (`session-progress.tsx`), because one dash per member overflowed its track at 27 circuit members and
  a single-block workout drew one permanently-full dash that read as finished. See the decision log.

  That change also answered the entry's own closing question. It ended "whether the useful thing to
  encode is *type* or *progress* is the real question — both want the same channel, and progress is
  the one a person mid-workout is actually asking about." Progress won, and it now owns the channel;
  encoding type as well would need a second one.

  The constraint that outlives it, and would apply to any categorical colour in the runner: **the
  runner has two hues and they already mean something.** `RunnerColors` carries `accent` (warm) and
  `accentCalm` (blue), and the split in use is session vs circuit on the bars, work vs rest on the
  screens. A per-type scheme needs a categorical palette that doesn't exist yet — build it through
  the `dataviz` skill's categorical procedure and run its validator against the runner's fixed
  `background: '#17140d'` rather than picking hues by eye. And hue could not be the only channel:
  these are decorative geometry with a fixed height, which the a11y house rules exempt from the touch
  and contrast rules for exactly that reason, and the moment a colour carries meaning that exemption
  lapses.

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

- **The Stats screen made `listSessions()`'s cost real, and nothing has been done about it.** The
  scaling risk is in the decision log with the remedies; what changed is that it now has a consumer.
  `analytics.tsx` walks the whole log three times per render — `historyStats`, `sessionsPerWeek` and
  `exerciseProgress` — deliberately unmemoised, because all three read the clock and a cache keyed on
  the log alone would freeze them at whatever the date was when a session was last written. That is
  the right call for a short-lived modal over a few hundred sessions and the wrong one over a few
  thousand. Nothing is slow yet; measure before changing anything, and the remedy named in the
  decision log (lazy or paginated loading, or a small index file) still stands — explicitly *not*
  consolidating the per-session files.

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

- **Images on exercises — the half that is still open.** The bundled set shipped: every exercise the
  app itself ships — seeded, or in one of the bundled content packs — now carries a line drawing keyed
  by its id, described in every locale bundle and rendered in the exercise editor.
  [`exercise-images-plan.md`](exercise-images-plan.md) has the reasoning.

  What that deliberately does not reach is the case that actually needs a picture: an exercise from an
  imported program, or one an assistant generated. The bundled art is first-run polish on the starter
  library, not "teach me the move", and no amount of drawing gets it to a library the user built.

  **The app may not fetch a remote image.** Same line as the AI entry above — the listing declares zero
  data collected/shared (see the tip-jar entry in the decision log), and an `Image` with a
  `{ uri: 'https://…' }` source hands whatever host the YAML names the user's IP plus a timestamped
  record of which exercise they were looking at. It also fails in the one place the app gets used: a
  basement gym with no signal. So `image: <url>` is simultaneously the cheapest option and the most
  expensive one; if it goes in at all, the Data Safety argument gets made first, not afterwards.

  That leaves the **user-supplied photo**, whose survey — a filename in the YAML, base64, or the folder
  from [`backup-folder-plan.md`](backup-folder-plan.md) — is in the same plan so it isn't run again.
  Nothing built for the bundled set gets thrown away if it comes back: a user's own image overrides the
  drawing and the drawing becomes the fallback.

- **More languages.** Japanese shipped; the procedure and the two wrinkles it turned up are in
  [`adding-a-language.md`](adding-a-language.md).

  **Arabic is the RTL project**, the one `adding-a-language.md` warns is not a bundle. What's already
  known: the `I18nManager` plumbing was deferred deliberately, RN auto-flips the row/padding/gap
  layouts so the physical-direction properties are the small half, and the real work is the drawn glyphs
  that can't flip (the CSS triangles in `next-up-card.tsx`, `row-start-button.tsx`, `session-hold.tsx`
  and `session-interval.tsx`) plus the arrows baked into copy (`grep '→' src/i18n/locales/en.json` —
  getting those out of the strings is worth doing whether or not Arabic ever ships). Three that note
  doesn't carry: `I18nManager.forceRTL` only takes effect after an app restart, and Kettle reads its
  language from the device once at init and never switches it, so the first launch after a phone goes
  Arabic comes up in Arabic and still laid out left-to-right; Arabic has **six** plural categories against en/pt's
  two, which `intl-pluralrules` covers on Hermes but the bundles' key shape does not; and the runner is
  where a half-flipped layout costs a workout rather than just looking wrong. Half-implemented RTL is
  worse than none — the plan's words, still true.

- **Android per-app language.** Android 13+ lets the user set a language for one app, in system
  Settings, independent of the device's. Kettle doesn't appear there: it needs `android:localeConfig`
  on the manifest and a `locales_config.xml` listing the bundles, both of which `expo-localization`'s
  `supportedLocales` writes — the same option `app.json` already passes, keyed to `ios` alone. Making
  it `{ ios: [...], android: [...] }` is the whole change, plus a line in
  [`adding-a-language.md`](adding-a-language.md) step 3.

  It was scoped to iOS deliberately rather than by oversight: the option also appends
  `resourceConfigurations` to `build.gradle`'s `defaultConfig`, which strips every other locale's
  resources out of the APK — smaller build, and nothing of Kettle's own is lost since its strings are
  in the JS bundle, but it is a change to the *shipping* platform's binary that had no business
  riding along with an iOS fix. It wants its own build and its own look at the result.

  What makes it worth more than it looks: Kettle has **no in-app language switch**. `i18n/index.ts`
  reads `deviceLanguage()` once at init and never calls `changeLanguage` again, so a user whose phone
  is in English cannot see the Portuguese or Japanese bundles at all. The system per-app picker would
  be the only override there is — which is a feature, not a duplicate, and cheaper than building the
  Settings row it would otherwise take.

## Open bugs

Found while planning the tests/a11y/i18n work (see `testing-a11y-i18n-plan.md`), each verified against
the code. Listed worst first.

**Fixed since:** `historyStats`'s "1.5h 30m"; the EMOM interval count; weight never being captured;
side effects inside `setState` updaters; `addRestSeconds` not rescheduling its notification;
`currentStreak`'s DST stepping; the display-name chip comparison; circuit members writing one entry
per round (below); `today`/`dateLabel` freezing at module scope (fixed with the I18n-3 locale work —
it's per-render now); `programs.tsx`'s stale "overrides aren't editable in-app" copy; the four
duplicate `slugify` copies, now one `domain/slug.ts` that all four call sites import; and
`sessionSetCount`, `slugify`'s ASCII-only ids and `session-hold.tsx`'s `NaN%` (below); the milestone
chime not re-firing on a step redone with Prev; EMOM minutes seeding their reps to 0; and the fixed
`height` that clipped `+ Adicionar bloco` in the workout editor, swept across every text-bearing
control. Notes on the structural ones:

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
  exercise and exactly 8 sets, where a duplicated commit would inflate both. **The eager ref was only
  half of it**, though, and the half it left open was the worse one: it stops the two calls repeating
  each other's commit, not the second one committing and skipping the step the first had just moved
  to. Both timing effects now also refuse to act on a step the runner has already left, and a
  `finishedRef` covers the completion paths, where the index stops being a usable signal because
  `advance()` returns without moving it.
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

- **The share sheet's title is hardcoded English.** `storage/export.ts` passes `dialogTitle` as a
  literal — `'Export exercises.yaml'`, `'Export history'`, and one interpolating a session id —
  which is a user-facing string in the logic layer, the one thing the i18n house rule forbids. It
  shows in Android's app chooser, so a Portuguese or Japanese user gets an English title on a screen
  that is otherwise fully translated. Three keys and a `t()` at the call site; the id stays verbatim,
  being user data. (iOS ignores `dialogTitle` entirely — it is Android and web only — so this is an
  Android bug found while auditing the iOS surface, not an iOS one.)

- **`Alert.alert` is a no-op on web.** react-native-web ships `class Alert { static alert() {} }`, so
  every confirm dialog silently does nothing in the browser — all the deletes and finish-session.
  Native is unaffected and web is a dev/preview target, so this is logged rather than fixed. It does
  mean a browser check of any confirm flow proves nothing unless the script patches it, which is how
  session delete was actually verified end to end.

One of the three found in the runner audit that produced the resume-race fix above is still here. The
other two are fixed (see the roll-up above); this one stayed because it is not a defect:

- **Circuit rest is never recorded.** Its steps use `memberKey: '<block>:circuit-rest'`, which matches
  no member's accumulating log, so `commitCurrentStep` finds nothing to attribute the rest to. Reads as
  intended — `RunnerStep`'s own comment says inter-round rest is "folded into (or discarded after) the
  surrounding work" — so this is a note, not a defect, until someone wants circuit rest in the log.
