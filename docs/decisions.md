# Decision log

> **Reference, not a plan.** Nothing here is open work — this is why things ended up the way they
> did. It lived inside `implementation-plan.md` until it was 411 of that file's 869 lines, while
> eight source files and `AGENTS.md` cited "the decision log" by name and none by path. Open work is
> in [`open-work.md`](open-work.md); shipped write-ups are in [`history.md`](history.md).

Why things ended up the way they did, for decisions that span more than one commit. Named "What's
genuinely left" until it had grown to ~210 lines of *completed* work under a backlog heading — the
label had stopped being true, which is how it grew without anyone noticing.

**Adding to this file:** don't log a shipped feature here just because it shipped. The commit message
is the record — it has the root cause, the alternatives, and correct attribution, and `git log -S` can
find it. Add an entry only when the reasoning **isn't discoverable from a single commit**: a
constraint that shapes future work, something deliberately rejected (so it isn't re-proposed), or a
decision assembled across several commits. Open work belongs in the sections at the bottom, not here.

- ✅ **The four list screens are `FlatList`s, and their chrome is a `ListHeaderComponent` *element*.**
  Build, Programs, Library and History all rendered every row into a plain `ScrollView` + `.map`, so
  any state change on the screen re-rendered every card that existed — fine at seed-library size, and
  the reason a search box was going to feel bad on a real log. Two rules come out of it, and only the
  first is obvious from reading the code:

  - **Never `ListHeaderComponent={() => <Header/>}`.** An inline arrow is a new component *type* on
    every render, so React unmounts and remounts the whole header rather than reconciling it. Today
    that costs nothing visible; the moment a `TextInput` lives up there it silently eats every
    keystroke, because the remounted input has no focus. Passing an element (or a stable module-level
    component) is what makes the search work planned below possible at all. Library and History
    already have a search box in that header, so this is load-bearing now, not a precaution.
  - **A row's props have to be comparable for `memo` to do anything.** Every card navigates or calls a
    shared `useCallback` rather than taking a lambda built per row per render, which is what keeps the
    prop set down to values that actually compare equal. History is the one where this is tested
    directly (`history.test.tsx`): the cards take `expanded` as a prop now instead of each reading
    `expandedId`, and a comparator that ignored it would leave the wrong card open — three of those
    tests fail against exactly that bug.

- ✅ **Formatting is oxfmt's, and it deliberately does not own the docs.** The repo went a long time
  with no formatter, which cost real time: reaching for `pnpm dlx prettier` ad hoc reformats a file to
  Prettier's defaults and fights the whole codebase's style, and a subagent has no way to infer an
  unwritten convention. oxfmt was chosen over Prettier because the repo already lints with **oxlint,
  which is the same Oxc toolchain** — so the two agree instead of needing a compatibility shim. Vite+
  was considered first and rejected: it is commercial, and this app is free with a tip jar that only
  covers the Play developer fee, so a per-seat tool can't be justified here. It also couldn't have
  replaced Metro, which bundles the app, and must never be allowed to drag the test runner with it —
  `jest-expo` is what makes the RN preset, the transform ignore list and the native-module mocks work.

  Two exclusions in `.oxfmtrc.json` that are decisions, not oversights, and shouldn't be "fixed":

  - **Markdown.** oxfmt rewrites prose emphasis (`*raw*` → `_raw_`) and dropped the indent on a
    wrapped list continuation in AGENTS.md, which changes how it renders. The docs are hand-wrapped;
    that's a regression, not a normalization.
  - **`package.json`.** The package manager rewrites its key order on install, so two tools owning it
    means a dirty tree after every install.

  `printWidth` is 125 because that's where the code already sat — 92 lines exceeded it against 145 at
  120, so it rewraps what is genuinely long rather than relitigating lines that were fine.

- ✅ **The tip jar is the whole monetization story, and nothing is gated behind it.** Kettle ships to
  Google Play only for now ($25 one-time); the App Store's $99/yr is deferred until the app shows
  traction, so the bar to clear is deliberately low. The app is backend-free, so marginal cost per
  user is zero — which is why there is no subscription (nothing recurring is delivered) and no ads.

  Two constraints this places on future work, neither discoverable from the commit that added the
  screen:

  - **No third-party purchase or analytics SDK.** RevenueCat was the obvious choice and was rejected:
    it transmits app user IDs, device identifiers and purchase events to its own servers, which would
    force a Play **Data Safety** declaration. "No data collected / no data shared" is currently true,
    it is printed on the store listing, and it is the thing that distinguishes Kettle from Hevy,
    Strong and Fitbod. Play Billing keeps the transaction inside Google Play, where payment data never
    reaches app code. RevenueCat's real advantage is cross-platform entitlements, which is exactly
    what's deferred with iOS. **Adding EAS Update, Sentry, or any analytics would break the same
    claim** — treat zero declarations as a product constraint, not an accident.
  - **Tips must stay in-app.** Linking out to Ko-fi/PayPal violates the Play Payments policy for
    non-nonprofit developers. This is the policy risk, not the Data Safety form.

  Shape: three **consumable** tiers (`tip_small`/`tip_medium`/`tip_large`), so a repeat tip is
  possible — `finishTransaction({ isConsumable: true })` is load-bearing, since without it Play treats
  the SKU as owned and refuses every later purchase of that tier. Consumables aren't restorable from
  Play, so "has tipped" can only live locally: `supporter.json`, app-owned JSON deliberately outside
  the hand-editable YAML library. There is no receipt verification because there is no backend to
  verify against; the downside is a contrived free tip, which costs nothing that was ever owed.
  `useTipStore` is **not** in `_layout.tsx`'s startup gate — the root layout renders `null` until every
  store it awaits is ready, and cold start shouldn't wait on a file almost nobody has.

  The paywall line, if a Pro tier is ever revisited: **export stays free** regardless. Gating it would
  contradict the data-ownership pitch that the whole product rests on.

- ✅ **Full program CRUD, including override editing.** Drag-to-reorder blocks and exercise delete
  exist too (`deleteExercise` in the library store, wired to a "Delete exercise" button in
  `exercise-editor.tsx` with the same confirm-dialog/in-use-guard pattern as workout delete — blocked
  if a workout block or circuit member still references it) — library CRUD (exercises + workouts) is
  complete. `src/app/program-editor.tsx` does name / add-remove-week / per-week week-number-stepper+
  day+workout-picker+notes / delete-program, via the already-existing `saveProgram` plus a new
  `deleteProgram` (mirrors `deleteWorkout`/`deleteExercise`'s shape exactly; no in-use guard needed
  since nothing in the domain model references a program by id — a past session's `program` field is
  already denormalized, same as everything else in `sessions/`). Reachable from a FAB on
  `programs.tsx` (create) and a pencil icon on `program-detail.tsx` (edit). One thing this pass
  deliberately did *not* do: reuse `ReorderableList` for the weeks list, even though it was built with
  reuse in mind — a program week's display order is driven entirely by its `week` number field
  (everything that reads it, `program-detail.tsx` and `src/domain/program.ts`'s
  `findProgramWeek`/`programWeekNumbers`, sorts/looks up by that number, not array position), so
  dragging rows around wouldn't actually change anything.

  **Override editing** (initially deferred as a fast-follow, now shipped): a week's `overrides` — the
  per-exercise/per-circuit config patches — are editable via a new `src/components/
  program-override-editor.tsx`, embedded per-week in `program-editor.tsx`. The genuinely hard part was
  the snake_case/camelCase mismatch: `ProgramOverride.config` is a partial *raw* (snake_case) patch
  (per `applyExerciseOverride`/`applyBlockOverride`), but every in-app config form works in camelCase
  domain values. Solved with two new exported inverses in `yaml-mapping.ts`,
  `diffExerciseOverride`/`diffBlockOverride`, which round-trip a base and an edited value through the
  same private `exerciseToRaw`/`workoutBlockToRaw` used internally by `applyExerciseOverride` and
  return only the raw keys that actually changed — so the override editor can just reuse
  `exercise-editor.tsx`'s existing `CONFIG_FIELDS`/`configToStrings`/`buildExercise` (now exported) as
  an ordinary camelCase form and never has to know about snake_case itself. Editing an *existing*
  override pre-fills the form from the base value with that override already applied (via
  `applyExerciseOverride`/`applyBlockOverride`, so it shows what's actually in effect, not the
  unmodified base), rather than resetting to defaults. The "add override" target picker is scoped to
  what the week's selected workout actually contains: exercises referenced by its blocks/circuit
  members, and circuit blocks that have their own `id` set (an id-less circuit can't be a block-override
  target, matching the documented rule in `program-guide.tsx`). Deliberately unhandled: an override
  isn't re-validated or cleared if the week's workout is later changed to something that no longer
  contains its target — matches the existing looseness elsewhere (`merge.ts` doesn't validate override
  targets either); a stale/unresolvable override still renders (read-only, no edit tap) with its
  remove control intact.
- ✅ **Per-exercise progression, including a volume chart.** `src/app/exercise-editor.tsx` shows a
  "Recent" section (only when editing an existing exercise, and only if it's actually been logged)
  listing the last few times it was done, newest first, via `exerciseHistory(sessions, exerciseId,
  limit)` in `src/state/selectors.ts` (also exports the existing `sessionEntrySummary`, previously
  private, as the per-type formatter — "8 · 7 · 5 reps", "20s · 18s · 15s", etc.). Above that list sits
  a new `src/components/volume-chart.tsx` — a small `react-native-svg`-based bar chart (that dependency
  was already installed, unused until now) built by following the `dataviz` skill's procedure: one
  consistent numeric "volume" per exercise type (`entryVolume` in `selectors.ts`, next to
  `sessionEntrySummary` — Σreps×weight for `reps` (or Σreps if bodyweight), Σhold-seconds for
  `timed_hold`, `roundsCompleted` for `hiit`/`amrap`, Σreps for `emom`, distance-or-duration for
  `cardio`), one flat accent-colored bar per session (single series, no legend needed), rounded-top/
  flat-baseline bars via a custom `Path` (`Rect`'s `rx` rounds all four corners, which isn't what a
  bar-anchored-to-a-baseline should look like), and — a deliberate, explicitly-reasoned deviation from
  the skill's "always ship a hover/tap tooltip" default — direct value labels on each bar instead of
  interactive tooltips, since touch has no hover, the chart is a small embedded sparkline (axes
  intentionally omitted, per Tufte's original definition) sitting directly above a list that already
  spells out every exact value as text, and the skill itself names direct labels as the sanctioned
  alternative for exactly this case. The chart reads oldest→newest left-to-right (natural
  progress-over-time reading order) while the list below it stays newest-first — a deliberate, called-
  out inversion between the two, not an inconsistency. Verified by actually driving two real sessions
  to completion (6 reps/set, then 9 reps/set) and looking at the rendered output, per the skill's
  final "render it and look at it" step, not just trusting the code: two bars, correctly ordered,
  correct heights (24 vs 36), correct labels.

  Also deliberately *not* done: no "last time" hint live inside the session runner while performing a
  set — that's a separate, larger feature (touches the timer-critical `use-session-runner.ts`), flagged
  as a follow-up rather than folded in here; and no chart interactivity beyond the direct labels (see
  above), since a tap tooltip would just repeat what the list below already shows.
- ✅ **Quick-add a new exercise from the workout builder.** New `src/components/new-exercise-form.tsx`
  — a mini version of `exercise-editor.tsx`'s own form (name, type pills, per-type `CONFIG_FIELDS`
  grid, notes), reusing its now-exported `buildExercise`/`CONFIG_FIELDS`/`TYPE_OPTIONS` — embedded
  inline in both of `workout-editor.tsx`'s pickers behind a "+ New exercise" row, rather than a
  navigate-to-`exercise-editor`-and-back flow (the workout draft being edited is unpersisted local
  state; navigating away would need real plumbing to avoid losing it). On create, the new exercise is
  persisted via the existing `saveExercise` and then dropped into whichever picker triggered it: the
  plain block picker calls `addBlock` (closes the picker, same as picking an existing exercise); the
  circuit picker calls `toggleCircuitMember` and **stays open**, since building a circuit means
  picking several exercises, not just the one just created. It originally carried its own local
  `slugify` copy — the 4th, following the then-precedent of small per-screen copies — which is what
  finally made the duplication worth removing: all four now import `domain/slug.ts`.
- ✅ **kg/lb is a display preference, and there is now a preferences store to hold it.** Two things
  here shape future work and neither is visible from the commit that added the toggle.

  **Storage stays metric, unconditionally.** `exercises.yaml` is a file users hand-edit, export and
  share, so `target_weight: 60` has to mean the same thing wherever it's opened — a library whose
  numbers changed meaning with the reader's settings would be unshareable, which is most of the
  product's pitch. `domain/units.ts` is the only converter; `useUnitSystem()` the only reader of the
  preference. A pound value must never reach disk.

  **The lossy direction is solved by not converting, not by rounding.** Pounds display at 0.1 and
  kilograms store at 0.01, which is what makes a *pound-authored* value survive the trip (135 → 61.23
  kg → 135.0); showing pounds at two decimals instead would put the quantisation on screen as 134.99,
  in front of the person who just typed 135. The reverse trip can't be fixed the same way, because
  0.1 lb is coarser than 0.01 kg: 100 kg shows as 220.5 lb and converts back to 100.02. So
  `buildExercise` takes `previousWeightKg` and keeps the stored value when the field wasn't edited.
  Without it, opening an exercise in pounds and saving an unrelated change quietly moves its weight —
  a little further on each save — and in the override editor invents an override nobody asked for.
  Any future editor of a stored weight has to pass it too.

  Two smaller calls: the −/+ step is 5 lb rather than a converted 2.5 kg, because 5.51 lb matches no
  plate or rack anyone owns and the stepper exists to move in increments the equipment has; and
  `measurementSystem: 'uk'` maps to **metric**, since British gyms load kilo plates however the UK
  weighs people.

  **`preferences.json` now exists**, app-owned JSON next to `supporter.json` and deliberately outside
  the library — and the appearance preference has since migrated onto it, closing the reset-on-relaunch
  bug. Unlike `useTipStore` it *is* in `_layout.tsx`'s startup gate: it decides how every weight
  renders and what color the app is, so arriving after first paint would swap both under the user.
  `loadPreferences` returns `null` rather than a default, so "never chosen" reaches the store, which is
  the only layer that can answer it with the device's own measurement system.

  **Every field added here after the first release must be `.default()`ed, not required.** A missing
  key fails `safeParse`, and `loadPreferences` answers a failed parse with `null` — so a required new
  field would silently reset every *other* preference in the file for existing users. `themePreference`
  is the worked example.
- ✅ **A11y is complete, and two constraints from finishing it shape anything that touches it next.**
  The pass now covers every interactive control; what isn't visible from the commits is why two
  things are shaped the way they are.

  **The reorder handle carries the screen-reader path, not the row.** `ReorderableList` was
  gesture-only, and a drag has no non-visual equivalent, so the handle also takes
  `accessibilityRole="adjustable"` plus `increment`/`decrement` actions. The obvious alternative —
  putting the actions on the row wrapper — is wrong and shouldn't be re-proposed: making the wrapper
  `accessible` collapses the row into one element and *hides* the remove button and the circuit's
  text fields inside it, trading a reorder path for several controls. The labels are a required prop
  (`labelsFor`) rather than defaulted, because the component has no locale bundle of its own and an
  English default would be a hardcoded string no `pt` test could catch.

  **`react-native-web` drops `accessibilityActions` and `accessibilityValue` entirely** — it maps
  role and label only, so in a browser the handle is a named `role="slider"` with no actions and no
  position. Verified by reading the rendered attributes, not assumed. So the non-gesture reorder is
  **native-only, and a browser check cannot see it**; the jest tests cover the wiring, and TalkBack on
  a device is the only thing that can confirm the rest. Don't "fix" the missing web actions.

- ✅ **The public APK is signed with the Play App Signing key, not with the EAS keystore.** This is the
  one decision in the repo that cannot be undone for anyone who acts on it before it is fixed, which
  is the only reason it is written down at this length.

  **The trap.** The Play build is an `app-bundle`, which Play App Signing **re-signs with Google's
  key**. Android identifies an installed app by its signature, so an APK signed with any other key is
  a *different app* to the OS. It refuses to install over a Play install, and the only way across is
  uninstall and reinstall, which deletes the user's entire training log. The same applies in reverse:
  someone who sideloads first cannot then move to Play.

  **Every APK this repo can build is signed with the wrong key**, and that is not an accident to be
  fixed — it is what an upload key *is*. `.github/workflows/android.yml` signs with the upload
  keystore (`docs/building-android.md`: "Google holds the signing key and re-signs every upload"), and
  `eas build --profile preview` used the EAS keystore before it. Neither can produce the app signing
  key, because neither has it. Only Play does.

  **Why the Play key won.** The whole product rests on the data being the user's, and the backup
  folder above exists specifically because losing the log is the worst thing that can happen to
  someone using this app. Shipping a second artefact whose only failure mode is "you lose everything
  when you switch channels" contradicts both. The Play-signed universal APK — Play Console → App
  bundle explorer → Downloads → "Signed, universal APK" — carries the same signature Play installs, so
  a sideload and a Play install are the same app in both directions.

  **What it costs, so nobody re-proposes the alternative to save it.** That download is a manual
  Console step with no API, so a release cannot be fully automated, and `/release` deliberately stops
  and asks for the file rather than substituting an EAS build. Two alternatives were considered and
  rejected: signing with the EAS keystore and simply warning people (the warning does not help anyone
  who has already installed, and "you will lose your log" is not a footnote), and giving sideloaded
  builds their own application id so the two coexist (no silent loss, but two ids to maintain and
  moving between them is a manual library export — and the session log cannot move at all, see "The
  session log is export-only").

  **The workflow's `variant=apk` is for your own phone, and is the likeliest way this goes wrong now.**
  It is one `gh workflow run` away, it lives in this repo, and `docs/building-android.md` describes it
  as "sideloadable" — which it is, onto a device you control. Two things make it unfit for a release
  and neither announces itself: the upload-key signature above, and `arm64-v8a` only (deliberately, to
  skip three quarters of the C++ compile), where the Console's universal APK carries every ABI. The
  14-day artifact retention was set for the same reason and is worth reading as a hint: "an upload key
  is not a distribution key."

  **F-Droid is the same trap a third time**, and worth knowing before that conversation starts: it
  re-signs with its own key. IzzyOnDroid, which republishes the developer's own binary, does not.

- **Considered and rejected: consolidating `sessions/` into monthly files to reduce IO.** Would cut
  against the explicit "never rewrite all of history on save" rule (product plan §5.2) — the
  mid-workout incremental flush (`writeSession()` full-overwrites its file on every completed set)
  would have to rewrite an entire month's accumulated sessions instead of just the current one, a cost
  that grows through the month, regressing the thing the product plan calls "the make-or-break issue."
  It also weakens crash isolation between sessions. The actual unbounded-growth risk is
  `listSessions()` reading every session file at app launch (O(total sessions ever logged)) — if that
  ever becomes a real problem, the fix is lazy/paginated loading or a small separate index file, not
  changing the live-workout write path.
- **Web has no persistence** (by necessity — `expo-file-system` doesn't support it); it now degrades
  to an ephemeral in-memory seed library instead of crashing, which is a reasonable dev/preview
  experience but not real usage.
- ✅ **Fixed: starting a workout with zero runnable steps trapped the user on a blank screen.** Found
  by deliberately reasoning through edge cases, then confirmed in the running app: a workout with no
  blocks (fully allowed by `workout-editor.tsx` — only `name` is validated on save) resolved to
  `buildSteps() === []`, and `session.tsx`'s `ActiveSession` did `if (!step) return null` *above* its
  header/Finish-button markup — so after the pre-session 3-2-1 countdown played, the user landed on a
  fully blank black screen with nothing tappable, only escapable via the OS back gesture. The real
  condition is broader than "zero blocks," too: any workout where every block's exercise has
  sets/rounds/minutes at 0 hits the identical empty-`steps` case — and that's reachable through normal
  use, since `exercise-editor.tsx`'s `Sets` field (and its equivalents) is a plain numeric `TextInput`
  with no minimum, unlike the circuit `Rounds` stepper which clamps to ≥1 — a blank/zero value there
  silently produces a runnable-looking exercise that isn't. Fixed at the source: `buildSteps` is now
  exported from `use-session-runner.ts`, and `session.tsx` calls it once up front to check
  `steps.length === 0` *before* ever showing the countdown, short-circuiting to a "Nothing to run"
  screen (workout name, explanation, a `Close` button) instead of mounting the runner at all. Verified
  live: created a zero-block workout, started it through a real program week (not a raw URL — web has
  no persistence, so a full page navigation would've reset the in-memory library), confirmed the
  empty-state screen renders and `Close` navigates back cleanly. Deliberately not addressed in this
  same pass: adding a minimum-value guard to the in-app config forms (`exercise-editor.tsx`,
  `new-exercise-form.tsx`) so a 0-sets exercise can't be *created* in the first place — imported YAML
  already can't produce this (the zod schema requires `sets > 0`), but the in-app save path bypasses
  schema validation entirely, so it's the only way in today. Worth doing, but a separate, smaller
  follow-up rather than bundled into this fix.
- ✅ **Fixed: "next up" tracked a completed-session count instead of actual program progress; added Today-screen metrics.** `nextUpView`'s old logic (`src/state/selectors.ts`) picked
  `weeks[completedSessionCount % weeks.length]` — correct only if you always progressed through a
  program's weeks in strict lockstep, but `program-detail.tsx` lets you tap "Start this week" on *any*
  week card, so redoing a week or jumping ahead silently desynced the count from reality, which looked
  "random." Real fix: `Session` (`src/domain/types.ts`, `src/domain/schema.ts` as `program_week`/
  `program_day`, backward-compatible via `.nullable().default(null)` so pre-existing session files keep
  parsing) now records which week/day it was actually started under, threaded from `session.tsx`'s
  already-resolved `week`/`day` params through `use-session-runner.ts` → `session-history-store.ts` →
  `session-files.ts` at session creation. `nextWeekAfter` in `selectors.ts` replaces the counting
  heuristic with a direct lookup: find the most recent session tied to the active program that has a
  tracked week (`sessions` is newest-first, so `.find` gets it directly), locate that week in the
  program's sorted week list, and return the one after it — wrapping to the first week past the end (so
  finishing a program restarts it) and falling back to the first week when there's no tracked session
  yet (brand new program, or every existing session predates this change). Verified live: jumped
  straight to a program's last week (skipping the earlier ones — the case that broke the old count
  logic), completed it, confirmed Today's "next up" correctly showed the *first* week (wrap-around), not
  a count-derived guess.

  Also added a small stats row to the Today screen, reusing the existing `historyStats()` aggregator
  rather than a parallel stats shape: **current streak** (`currentStreak` — consecutive calendar days
  with ≥1 session, walking back from today; today not having one yet doesn't break it, only a full-day
  gap does) and **this week's activity** (`thisWeekStats` — `historyStats` pre-filtered to sessions
  since the current Monday, local time).
- ✅ **Start any workout ad-hoc, without a program.** `session.tsx` has always accepted a bare
  `workoutId` param (the non-program branch of its `resolved` memo), but nothing in the UI ever
  pushed it — the only two entry points were the Today tab's "next up" card and `program-detail.tsx`'s
  per-week "Start this week", both program-driven. So a plain "I just want to do this workout today"
  had no path at all. Fixed purely in the UI: each workout card on the Build tab now has a small
  circular play button that pushes `/session` with `{ workoutId }`; tapping the card text still opens
  the editor as before. No domain, storage, or runner changes — the resulting session simply has
  `program`/`programWeek`/`programDay` all null, which the "next up" logic already treats correctly as
  "not program progress" (`nextWeekAfter` filters on `session.program === program.id`, so an ad-hoc
  session can't shift a program's suggested week). Deliberately kept visually quiet after a first pass
  shipped a full-width solid-accent "Start" bar: that read as the card's primary action and made a list
  of workouts into a wall of orange, so it became a 36px accent-soft circle with just a play glyph, and
  the card's old `›` chevron was dropped since two right-pointing affordances side by side were
  confusing.
- ✅ **Fixed: leaving the session screen crashed with a redbox (web).** Read the library source rather
  than working around it, and the fix turned out to be a flag it already ships:
  `useKeepAwake(undefined, { suppressDeactivateWarnings: true })`. The mechanism, from
  `expo-keep-awake/src`: `useKeepAwake`'s cleanup calls `deactivateKeepAwake(tag)` with **no `.catch()`
  at all** unless that flag is set (`index.ts`), and the web `activate(tag)` only records the tag in
  its map *after* awaiting `navigator.wakeLock.request('screen')` (`ExpoKeepAwake.web.ts`). Any unmount
  that beats that promise leaves `deactivate` with no tag to release, so it throws
  `ERR_KEEP_AWAKE_TAG_INVALID` as an unhandled rejection. Every exit from this screen beat it, since
  finishing/closing navigates immediately — which is why it fired on completion, on `finishSession`,
  and on the empty-workout `Close` alike. Nothing leaks by suppressing it: the browser drops a screen
  wake lock on its own when the page goes away. Verified by driving a full session to completion and
  out: previously every such run logged the `pageerror`; the run after the fix logged no console
  errors of any kind.
- ✅ **Fixed: a batch of UI-truthfulness bugs found by auditing the screens against the code.**
  (1) `history.tsx` had **`"July 2026"` hardcoded** — frozen to one month, and sitting on top of stat
  tiles that are `historyStats(sessions)` over *all* sessions; now reads "All time", matching both the
  tiles and the unfiltered list below. (2) Today's "Recent" rows rendered a trailing `→` but were a
  plain `ThemedView` with no `Pressable` — the same broken-button look already removed from
  `SessionNextCard` in b547bdd; the arrow is gone (they stay non-interactive, since there's no
  per-session detail screen to open). (3) The expanded history card showed *"self-describing · stays
  valid if the definition later changes"*, a note about the data model that had no business in the UI;
  removed, leaving Export anchored right.
- ✅ **Fixed: no minimum-value validation on exercise config forms.** Flagged as a follow-up when the
  "Nothing to run" screen shipped and left open until now — it was the only reachable way to create a
  workout with zero runnable steps, since the in-app forms write straight to the library store and
  never pass through the zod schema (imported YAML can't do this; `sets` is `positive()` there).
  `validateConfig(type, values)` — added in `exercise-editor.tsx`, since moved to
  `domain/exercise-form.ts` where its tests live — mirrors those schema constraints:
  required fields need ≥ 1, and `FieldDef` gained an optional `min` so the rest-length fields can carry
  `min: 0` — the schema has those as `nonnegative()`, and rejecting a zero-length rest would be
  stricter than the format itself. Wired into both creation paths (the full editor and
  `new-exercise-form.tsx`'s quick-add), since validating only one would leave the hole open through the
  other. Cardio deliberately keeps both fields optional: an unconfigured cardio exercise is a valid
  count-up stopwatch in the runner, not a broken step. Verified live: 0 sets is refused with "Sets must
  be at least 1", while a 0-second rest still saves.
- ✅ **Session delete, history search, and a Settings screen** (built by parallel agents; the
  integration and the two fixes below are the parent's).
  **Delete**: `deleteSession(id)` in `session-files.ts` (idempotent — `File.delete()` throws on a
  missing path, so the `exists` check is load-bearing) → a store action → a destructive-confirm control
  in History's expanded card, mirroring the exercise/workout delete pattern. Whole sessions only;
  editing a past session's individual entries is still not possible.
  **Search**: name search over the history list, reusing Library's search-bar styling. The stat tiles
  now aggregate the *visible* subset and the header switches from "All time" to "N of M", because three
  all-time numbers sitting above a filtered list would describe sessions that aren't on screen.
  Deliberately no filter pills: a session has no single type to pill on, being whatever mix got logged.
  **Settings**: a modal with three-way appearance, export/import, and library counts.
  `theme-context.tsx`'s `Scheme | null` override plus `toggle()` became a `ThemePreference`
  (`light | dark | system`) — the old toggle could never get back to following the OS, because "follow"
  and "currently light" were indistinguishable to it. Storing intent rather than outcome is what makes
  the third option expressible. (It was in-memory at the time; it now persists via `preferences.json`.)
- ✅ **No ES2023 change-array-by-copy methods (`toSorted`, `toReversed`, `toSpliced`, `with`) until
  Hermes support is verified on a device.** This is the one thing standing between the five
  `unicorn/no-array-sort` / `no-array-reverse` warnings and a clean lint run, so it will come up again.
  All five sites already sort or reverse a **copy** (`[...weeks].sort(…)`); oxlint only recognises that
  for an array declared in the same scope, so it flags correct code. `toSorted` would satisfy it — but
  it's a runtime builtin, this app runs on Hermes 0.17 (RN 0.86), and nothing checked into the repo
  says whether that build ships it: `hermesc` compiles rather than runs, no RN or Expo package calls
  these methods, and the web build proves nothing because it's V8. Swapping a proven idiom for one
  whose failure mode is *device-only* isn't worth silencing a linter, so each site carries an
  `oxlint-disable-next-line` naming the reason instead. Revisit by running one on a device or an
  emulator, not by recalling a Hermes changelog.
- ✅ **The seed library is curated starter content, and three domain rules constrain what it may
  contain.** It ships two programs and four workouts a new user can press start on, rather than the
  format demo it used to be. The content itself is in `storage/seed-library.ts` and readable there;
  what isn't visible from that file is the rules any *edit* to it has to respect, all three of which
  the old seed broke:

  - **`programs[0]` is the default.** `activeProgram` (`selectors.ts`) falls back to the first library
    program when no session has been run yet, and there is no starter-program picker — so the
    no-equipment program stays first, and the dumbbell one is browse-only until explicitly started.
  - **Multi-day weeks sort by `day.localeCompare`** (`nextWeekAfter`), so `Monday`/`Wednesday`/
    `Friday` would walk a week as *Friday → Monday → Wednesday*. Day labels have to sort in training
    order, hence `Day 1`/`Day 2`/`Day 3` — and `Dia 1`/`Dia 2`/`Dia 3` in the translation, which is
    pinned per-language by the test rather than left to whoever writes the next one.
  - **Weeks resolve sparsely and overrides don't carry forward.** Weeks 1/3/6 make "next up" skip 2, 4
    and 5 entirely, so a seeded program enumerates every week and repeats an override in each later
    week it should still apply to.

  Two content rules, deliberate rather than incidental: exercise `notes` describe **the app's own
  progression model only** — never form cues, injury or diet, so the app keeps describing its data
  model rather than prescribing training — and the seed is also the web build's entire library and the
  reseed target for a corrupt `exercises.yaml`, so it has to stay small enough to be a reasonable thing
  to reset to.

  **The seed ships per-language, as one English structure plus a string table** (`seed-translations.ts`)
  — reversing an earlier English-only call, whose reasoning was that a locale-picked seed doubles every
  future content edit. That cost is real and this shape is the answer to it, so the rule an edit has to
  respect is: **structure changes once, and every language owes it a string.** `seed-library.test.ts`
  fails the language that didn't get one, in both directions (a missing translation and a stale key
  pointing at content that was renamed away), and runs every structural invariant above against every
  language, because a translated seed is a real library a real first launch lands on. Two constraints
  behind that shape, neither obvious from the files:

  - **Seed strings must never live in `en.json`/`pt.json`.** The seed is written to `exercises.yaml`
    and becomes the user's own data at that moment. Locale-bundle strings re-render on every language
    change, which would rename exercises the user has since edited and logged against — the exact thing
    the never-translate-user-data rule exists to prevent. Read once at seed time, then frozen.
  - **The pick is shared with the corrupt-file reseed**, so a library corrupted after the user switched
    device language comes back in the new one. Accepted rather than overlooked: the content is being
    regenerated from scratch anyway, and the alternative is reseeding into a language the user is no
    longer reading.
- ✅ **The app authors format, never training intent — it owns nothing the user could call theirs.**
  Settled while scoping the AI-authoring work, where the open question was whether shipping a prompt
  template would make Kettle the owner of the advice that template produces. It doesn't, provided the
  template carries **format only**: the schema, the user's own ids/names/types, and the importer's own
  error text. What it must never carry is intent — goals, set/rep prescriptions, progression schemes,
  exercise selection, or anything about the user's body. Ask of any string the app emits toward an
  assistant: if the generated program turns out badly, does this sentence make that Kettle's fault?

  This is the same line the seed library's `notes` rule is drawn to stay behind, and it doesn't
  disturb it: seeded content is editable starter data under that rule, not advice the app stands
  behind. The worked example already in the tree is import's repair prompt — "fix the YAML and send
  back the corrected file", not a word about training. The reason it's a constraint rather than a
  preference is that data ownership is the entire pitch: an app with no backend and no account has
  nothing to stand behind a training claim with, and the moment it ships one, the contents of the
  user's library stop being solely theirs.

- ✅ **Fixed: `exportLibrary`/`exportSession` threw past their own `.catch()`.** Resolving `.uri`
  constructs an `expo-file-system` `File`, which has no web implementation and throws *synchronously* —
  before `share()`'s async body is entered — so the throw escaped the returned promise and every
  caller's `.catch()` missed it, surfacing as an unhandled error. Both are now `async`, which turns it
  into a rejection those existing handlers catch. Fixes the crash on Library's export and the same
  latent hole in History's.

- ✅ **What counts as a personal record, and why the nudge is a rest-day nudge.** Both halves of the
  celebrate-progress work are judgment calls that the code can't explain on its own, and both are the
  kind of thing that gets re-proposed by whoever reads the selector next.

  - **A tie is not a record, and a first-ever entry is not a record.** `sessionRecords` compares
    strictly greater against a *prior* value, so an exercise with no history produces nothing. The
    alternative — celebrating the first entry — was rejected because it makes every exercise in a new
    user's first week a PR, and a badge that fires on everything is read as decoration by session
    three. The cost is real and accepted: the genuine first-ever lift of something is silent.
  - **Four entry types compete, three deliberately don't.** `reps` (on load, or on reps when the sets
    carry no `weightKg` at all), `timed_hold` and `amrap`. `hiit` rounds and `emom` minutes are both
    bounded by the exercise's own config, so "more than last time" there reports that the user edited
    the workout — a record that fires when you change a number is worse than none. `cardio` genuinely
    has records and is out only until distance and duration have comparability rules of their own
    (same distance across different routes is not the same comparison), so this one is a scope cut
    rather than a refusal.
  - **The estimated 1RM is Epley, is named on screen, and is never stored.** An unattributed 1RM is a
    number people argue with — Epley, Brzycki and Lombardi disagree visibly on the same set — so the
    formula's name ships with it, but as a single note under the record list rather than in
    parentheses after each value. Inline it competed with the number for attention while telling a
    lifter who has just finished a workout nothing they wanted at that moment; the attribution only
    matters to someone who has already decided to question the figure, and that person reads the
    note. It is recomputed from the log every time it is shown, so
    changing the formula later can never leave the app disagreeing with its own history. It also
    declines to answer above 12 reps, where Epley diverges far enough that the estimate is worse than
    none.
  - **The reminder fires relative to the last session, not on a clock.** One local notification, a
    couple of days past the most recent session, replaced every time a session is logged — so someone
    training regularly never receives it. A repeating `DAILY` trigger was the obvious alternative and
    was rejected: it fires whether or not you trained, which is the nagging an app whose pitch is
    "it doesn't bother anyone" cannot ship. It is off by default and stays off until asked for, and
    it is a *local* notification — nothing about it may grow a transport without breaking the
    zero-data Play declaration.

- ✅ **"Last time" on the set row: what adopt writes, and what the snapshot is actually for.** Three
  constraints that outlive the commit, in rising order of how easily they get undone.

  - **The adopt control writes the library's *base* `target_weight`, and is invisible under a program
    week that overrides it.** `resolveWorkoutForWeek` hands the runner exercises with the week's
    overrides already applied, so a week carrying `target_weight` wins over anything written to the
    base exercise — the adopt takes effect in the session and then appears to do nothing next week.
    Accepted rather than solved: the published example program overrides `sets`, not `target_weight`,
    and the alternative is writing into the program's `overrides`, which are partial *raw* snake_case
    patches (see the note in `AGENTS.md`) — a much larger change for a corner. If a program that
    periodises load by week ever ships, this is the thing to revisit.
  - **Adopt takes the load, never the reps.** Reps re-seed from the target on every set because
    varying reps is the normal thing being logged; pinning last week's would fight double
    progression, which is the progression model the `reps` config exists to express.
  - **The snapshot is for the subscription, not for correctness — and the test can only see one of
    those.** The runner reads the session log once at session start via `getState()`. Two separate
    things defend "last time" against reporting the set you finished two minutes ago: this snapshot,
    and `previousSetFor` skipping sessions with no `ended_at`. Mutation testing settled which is
    load-bearing, and the answer was not the expected one — reintroducing a live store read leaves
    every test green, because the in-flight session is unfinished and gets skipped either way. Only
    removing *both* fails. What the snapshot uniquely buys is not subscribing to a store that is
    rewritten on every logged set, which would re-render the whole session screen mid-workout; no
    assertion in the suite can observe that, so it is recorded here instead.

- ✅ **The step list is mutable now, and there is one rule every future mutation inherits.** Add/drop
  set promoted `steps` from a `useMemo` to state in the session runner. Three things came out of it
  that the next mutation (swap-exercise) has to honour.

  - **Any edit to `steps` must clear `lastCommitRef`.** `lastCommit.resultingIndex` is an *index into
    the array*, and `goPrev()` consumes it to undo exactly one level. Mutate the array without
    clearing it and the next Prev undoes a commit that now belongs to a different step — verified, not
    theorised: removing the clear makes a logged set vanish from the session file entirely when the
    user presses Prev after adding a set. That is data loss triggered by a navigation control, which
    is why the clear lives in one `mutateSteps` wrapper that both operations go through rather than in
    each of them.
  - **Drop takes the member's last set, never the one in progress, and the floor is what has already
    been logged.** The issue sketched `dropSet(stepIndex)`; an indexed drop overlaps with what Prev
    and skip already do, and it can reach a set that has reached the session file. A −/+ pair that
    changes "Set 2 of 4" to "of 3" is both the safer operation and the one that matches how a lifter
    describes it.
  - **Circuits are excluded, and that is structural rather than pending.** A circuit member's
    `setIndex`/`setTotal` is its position in the block's *rounds*, and its steps are interleaved with
    the other members' rather than contiguous — so "one more set" there means "one more round of the
    whole block", a different operation on a different object. The runner tests the block kind, which
    is the honest question; nothing in a `RunnerStep` alone can answer it, and adding a flag to the
    step model to fake it would have encoded the wrong idea.

  One smaller thing worth knowing, since it changes an existing behaviour without looking like it:
  `steps` no longer rebuilds when the library changes mid-session. It used to, because `session.tsx`
  subscribes to the library store and hands the runner a fresh `exercises` array on every write —
  including the set row's own adopt write-back. That was harmless while the list was derived, and is
  exactly what must not happen once the list can be edited.

  **Swap-exercise, the second half, turns on one line: every swap issues a member key nothing has ever
  used** (`` `${memberKey}~swap${n}` ``). That is the whole of invariants 1 and 2 from the issue, and
  it is worth stating why rather than leaving it to look like hygiene. `memberSetsRef`,
  `memberHiitRoundsRef`, `memberEmomMinutesRef` and `entryIndexRef` are all keyed by `memberKey`, so
  reusing the original makes the substitute's sets grow the *replaced* exercise's session entry —
  verified by doing it: one pull-up set and one dip set collapse into a single entry labelled `dips`,
  which is a lie about what the user lifted, written to a file the product's whole pitch is that they
  own. A fresh key leaves the original entry holding exactly the sets done under it, and the
  substitute's first `persistMember` appends a new entry at the end, which is the only thing
  `entryIndexRef`'s append-only assumption requires.

  Two product decisions came with it. **The substitute gets the remaining set count, not its own** —
  three sets left means three sets of the new exercise, because this is a substitution inside the
  workout rather than a rewrite of it; its reps, load, hold and rest targets still come from its own
  config, which is what makes it a different exercise. And **the picker offers the same type only**,
  which is what makes "the remaining count" coherent at all (a HIIT's rounds are not sets) and keeps
  the runner screen from changing kind under someone mid-set.

- ✅ **An ad-hoc session runs out of steps on purpose, and parks instead of ending.** A session with no
  pre-built workout has an empty step list at the start and again after every exercise finishes. The
  runner treats that as "waiting for the next decision", not as "done": `advance()` parks `stepIndex`
  one past the end and the screen offers Add exercise / Finish there, with `finishSession` the only
  thing that ends it.

  Parking *one past the end* rather than clamping to the last step is what makes adding free — the
  appended steps land at exactly the index the runner already points to, so no separate "jump to the
  new exercise" path exists to get wrong. The alternative, completing when the list empties, would
  have meant deciding the whole session up front, which is the thing ad-hoc exists to avoid.

  Two smaller notes worth keeping:

  - **Adding queues behind the current work rather than jumping to it.** Obvious in hindsight, and the
    first draft of the test assumed otherwise. Adding mid-exercise is a way to plan the next thing
    while resting, not a way to abandon the set you are on.
  - **The display layer needed no changes at all**, which is worth recording because the issue asked
    for an audit of it. `workoutNameFor` already returned null for a workout-less session and
    `formatSessionName` already rendered the translated stand-in — both landed with the "ad-hoc" naming
    work that predated this feature. History, Recent and `historyStats` were correct before this PR
    touched anything; the tests added for them pin behaviour rather than introduce it.

- ✅ **The runner's per-step reset keys on *which* step it is, not on where it sits — and it took two
  shipped bugs to learn that.** `resetForStepIndex` compared `stepIndex` alone, which was sufficient
  for exactly as long as the step list was immutable. Both of the mutations that followed break it by
  changing the step *under* a stable index:

  - **Swap** replaces the step at the current index, so the substitute inherited the replaced
    exercise's seeded reps and load — a dumbbell press starting at the bench press's 20 kg.
  - **Add-exercise** in an ad-hoc session lands the appended step on the index the runner parked at, so
    the first set inherited the parked state's zero and logged **0 reps**.

  Neither was caught by a test, because the tests that would have caught them were written against the
  same assumption as the code. What caught the second was driving a real session in the browser and
  reading the History entry — `Push-ups 0 · 8 · 8 reps` — which is the exact reason `AGENTS.md` says to
  verify this file by running it rather than by reasoning about it. The first only surfaced because
  fixing the second made it obvious the same hole existed one feature earlier.

  The key is now `stepIndex : memberKey : kind : setIndex`. Stopping at set number rather than using
  the step object's identity is deliberate: add-set and drop-set rebuild the array, so an
  identity-based key would snap a rep count the user had just dialled in back to the target for the
  crime of asking for one more set. There is a test for that direction too.

- ✅ **"Nothing is overwritten" is out of the published copy; "nothing is uploaded" stays.** Both
  claims sat together in the Play full description and on the landing page. The second is the one
  carrying the privacy weight and is a hard guarantee of the architecture. The first was a storage
  detail — it mostly promised the app doesn't rewrite your history behind your back — and it is the
  claim that blocks editing a mis-logged set (#56), because an edit path either breaks it or requires
  it reworded.

  Settled here rather than there because repositioning the pitch rewrites the very block it lives in,
  and writing that sentence twice to unwrite it later is waste. Dropping it costs nothing today: every
  remaining claim is still true, the log is still append-only in fact, and #56 is now free to add an
  edit path without reopening published copy. If the log ever *does* gain an edit, nothing needs
  saying — the promise was never made.

  The corollary for whoever does #56: the product decision is made, so what is left there is the
  storage question (correct-in-place versus append-a-correction), which is a smaller and more
  technical choice than the issue currently frames it as.

- ✅ **A logged set is corrected in place, and the log stopped being append-only in fact.** The
  storage question the entry above handed to #56, answered. A correction rewrites the entry where it
  sits (`replaceSessionEntry`, already the runner's own write path for every set it logs) rather than
  appending a correction record and rendering the resolved value.

  Append-a-correction is the more faithful design if the append-only property is worth preserving,
  and it isn't: no published claim rests on it any more, and buying it back would change the session
  file format — which triggers the whole "Changing the YAML format" gate in `AGENTS.md`, three
  hand-maintained mirrors plus `site/format.html`, in service of a promise nobody is owed. Every
  reader of a session file would also need to learn to resolve corrections before it could read one.

  So the phrase "the log is still append-only in fact" in the entry above **stopped being true here**,
  and the word came out of `AGENTS.md`, `docs/product-plan.md`, `site/privacy.html` and
  `site/format.html`. The privacy page now says what is actually guaranteed — the log is local, and
  you can correct or delete what is in it. `product-plan.md`'s one-file-per-session note keeps its
  "append-only-log benefits" wording, because those are properties of the *directory* (crash safety,
  no partial-write corruption of history) and one session's file being rewritten doesn't touch them.

- ✅ **An in-flight session refuses to be edited, and that is a constraint on anything else that ever
  writes to history.** The runner holds the session it is writing to in a ref and writes through that
  copy; the same session is also in the store's `sessions`, because `startSession` puts it there. So
  any second writer that edits it from outside leaves the runner holding a copy without the edit, and
  the runner's next `logEntry` spreads that stale copy back over the file. The correction disappears
  one set later, with nothing said and nothing logged.

  `editEntry`/`removeEntry` therefore refuse a session with no `ended_at`, and History hides the Edit
  affordance on one. This is why those two actions take a **session id** where the runner's
  `logEntry`/`replaceEntry` take a `Session`: the runner owns its object and needs the updated one
  back, but a screen that was handed one could hold it past the next write, which is the same bug in
  a different shape. `exerciseHistory` and `previousSetFor` already skip unfinished sessions on the
  read side; this is the same line drawn on the write side.

  Two smaller traps found while building it, both pinned by tests that fail if reintroduced. Entry
  indices are into `session.entries` and **not** into what History renders, which filters `rest`
  entries out — an editor built from the view sends corrections to whatever sits at that offset. And
  removals have to be applied back to front, since each one shifts every later index down by one.




---


---

## Migrated from the old workstream sections

The A–F workstream write-ups were deleted when this file was split out — they were completed-work
records, which `git log` carries. These entries were pulled out of them first, because each is a
constraint that outlived the work rather than a description of it.

- **`amrap`'s config is only `time_cap_sec`.** The product plan's "movements" sub-field is undefined
  in the spec and unused anywhere, so it was never modeled. Don't add it back without deciding what
  it means.
- **There is no `.version` marker file, deliberately.** `version: 1` already lives inside each YAML
  file, and a directory-level marker has no consumer until a real migration exists.
- **The session log is export-only.** Nothing imports a session back, which is why the assembled
  archive needs no parser and has none. A session round-trip is a new feature, not a gap.
- **Editing an exercise locks its type.** Changing the type of an existing exercise would orphan its
  config shape, so the editor keeps the id and the type and only the config is editable.
- **`goPrev()` is one level deep, and that was a scope call, not an oversight.** Full multi-step undo
  was judged out of scope; a second `goPrev()` without an intervening `advance()` just moves the step
  index.
- **`ReorderableList` items never leave their original React children order or keys during a drag** —
  only a `translateY` transform moves them, and `data` changes exactly once, on drop. This is what
  keeps an open `TextInput` inside a circuit block (rounds/rest/block-id) from losing focus or
  remounting while another item is dragged. Reordering the array live would look identical in a
  screenshot and break editing.
- **Auto-scrolling the `ScrollView` when a drag reaches the viewport edge is out of scope.** Block
  lists are short enough in practice; it's a contained follow-up if that ever stops being true.
- **`ReorderableList` measures rows with `measure()` on the UI thread, never `onLayout`.** Don't
  "simplify" it back. Accumulating `onLayout` into a shared array left that array empty on device
  while working in a browser, and with no geometry the hit test degenerates to "first position when
  dragging up, last when dragging down" — which is how the bug was reported, and it masked a separate
  arithmetic bug underneath. Why the layout event never landed is still unexplained; the fix routes
  around it rather than understanding it, so treat a recurrence elsewhere as plausible. `measure`
  requires `collapsable={false}` on the measured view, since Android flattens views it thinks draw
  nothing and returns null for those.
- **The drag handle's touch target is sized by `minWidth`/`minHeight`, not padding round the glyph.**
  It shipped at 14×30dp — about 2.5mm against a ~9mm fingertip — and missing it is indistinguishable
  from a drag refusing to start. `hitSlop` is not the alternative here: RNGH cannot expand a handler's
  area past the view's own bounds on Android.
- **The block drag deliberately outranks the scroll it sits in**, via the gesture-handler `ScrollView`
  plus `blocksExternalGesture`. Android's scroller claims any touch drifting past ~8dp and decides
  before the 150ms long-press elapses, so with `react-native`'s `ScrollView` most rows simply could
  not be picked up. A list handed the wrong `ScrollView` silently keeps that bug.

- ✅ **The package manager is pnpm, and Bun was measured and rejected.** Installs were the slowest
  part of the loop, so all three were benchmarked on the actual dependency tree, clean directory and
  warm cache each: **pnpm 30s, npm 137s, Bun 395s.** Bun was picked first on its general reputation
  for speed and turned out to be **~3x slower than the npm it was replacing** here — consistently,
  across cold, warm and clean-directory runs, with a correctly populated 590 MB cache and the default
  `hardlink` backend. The machine's Defender query fails with `0x800106ba`, so a third-party scanner
  is likely the multiplier, and it punishes file-copy-heavy installs worst. Recorded because the
  reputation is real on Linux CI and will suggest Bun again; on this Windows box the measurement is
  the answer. Bun is otherwise fine — the full suite passed under it before the switch.

  Three things about pnpm that are not obvious and each cost a debugging round:

  - **Settings live in `pnpm-workspace.yaml`, not `.npmrc`.** pnpm 10 began moving them and by 11 an
    `.npmrc` entry is read as an npm setting and ignored. `--node-linker=hoisted` on the command line
    is ignored too. Nothing errors — you just get the wrong layout and find out later.
  - **`nodeLinker: hoisted` is required, not a preference.** Metro and Gradle autolinking both walk a
    flat tree. Under the default isolated layout `src/hooks/safe-iap.ts` fails typecheck on
    `expo-modules-core`, which it imports directly but which is only a dependency of `expo`.
  - **`virtualStoreDirMaxLength: 60`.** The virtual store encodes a full package name plus a hash into
    one directory name; at the default of 120 the longest `@babel` entries breach Windows' 260-char
    path cap and the install dies with `ENOENT` on `mkdir`. `LongPathsEnabled` is off on this machine
    and turning it on needs admin plus a reboot, so the cap is worked around rather than lifted.

  The lockfile came from `pnpm import` against the existing `package-lock.json`, so the migration
  changed the manager without re-resolving a single version. Verified past jest, which doesn't
  exercise Metro: the web bundle was fetched from the dev server and built clean at 9.9 MB.

## Open questions from the product plan — all five settled

Kept as a record of where each landed, since §12 is where they're numbered.

- **Timed-hold direction** (§12.2): **count up, with the range as a marker.** A countdown has no
  non-arbitrary number to count from once the target is a range (`hold_sec_min` + optional
  `hold_sec_max`), and counting up is what the log records. The bar spans the top of the range with the
  minimum marked part-way along.

  **Amended when holds grew an end: the display decision above stands, and a hold now ends itself at
  the top of its range.** The two are separable, and reading the settled entry as ruling out an
  auto-end is the mistake this paragraph exists to prevent. What changed the balance was that the log
  was *wrong*: in a dead hang you can't reach the phone, so the clock kept running through the
  dismount and `holdSec` recorded several seconds that weren't the hold. Three consequences worth not
  re-deriving:

  - **The end is the maximum, not the minimum.** Ending at the minimum truncates every set to its
    floor, so no hold could ever log more than its prescription and hold progression dies. The
    maximum is also the number the bar was already scaled to, so a full bar and a finished set became
    the same event rather than two.
  - **`hold_sec_min` became optional**, which is what makes "hold as long as you can" expressible at
    all — the shape `cardio` already had with `duration_sec`. Rejected instead: a global "auto-end
    holds" preference, because whether a hold is prescribed or max-effort is a property of the
    exercise and one workout can contain both; and a "keep going" button, which reintroduces the
    reach-for-the-phone problem the auto-end removes.
  - **The logged value is clamped to the hold's end, not the elapsed clock.** A hold that ends while
    backgrounded is only noticed on foreground return, so the raw elapsed there is however long you
    were away — a 25s plank logged 60s until a regression test caught it. Anything that later ends a
    step from the catch-up path needs the same clamp.
- **Merge conflict transparency** (§12.5): **a field-level diff** (`domain/library-diff.ts`), shallower
  for workouts and programs than for exercises — see §12.5 for why that asymmetry is deliberate.
- Earlier: `rest` stayed first-class (§12.1), rep-block rest is an auto timer that's skippable
  (§12.3), and `blocks` grew a `circuit` kind for supersets/circuits (§12.4) without a rewrite.
  `hiit`/`emom`/`amrap`/`cardio` are all runnable now, on a unified interval runner with matching
  `SessionEntry` log shapes.
