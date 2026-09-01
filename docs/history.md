# Kettle — shipped-work history

Narrative write-ups of features that have shipped, moved out of `open-work.md` so that
file stays a plan rather than a changelog. Nothing here is open work.

Kept because each entry records reasoning that a single commit message does not carry — a
constraint discovered mid-build, an approach rejected, or a trap the next person would otherwise
rediscover. **Do not append to this file just because something shipped.** The commit is the record;
`git log -S` finds it. The bar for landing here is the same as for the decision log: reasoning that
is not discoverable from one commit.

Counts quoted below are as-of the entry that mentions them and drift as work continues. Nothing
restates them as current: `pnpm test` is the test count and the locale bundles are the key count, and
a hand-maintained copy in prose is a claim no test checks.

---

## ✅ Two more audio cues in the runner

Both requested from real use, and both now sound a new rising two-note `milestone.wav` — deliberately
rising where the countdown tick is flat, because the two mean opposite things: a tick says "about to
end", a milestone says "keep going, you are partway".

- **Halfway through a HIIT work interval**, the point you would otherwise have to look up to pace
  yourself. Work intervals only: their rest already gets the 3-2-1 ticks. Not wired to emom or amrap —
  emom intervals are usually too short for a midpoint to mean much, though an amrap time cap is a
  reasonable future extension.
- **When a hold reaches its target.** Holds count *up* with the target as a marker, so nothing
  previously marked the moment you actually hit it — the one piece of information that matters with
  your eyes shut. Fires at the bottom of a range target, since that is where the set counts.

One sound serves both: which one you are hearing is never ambiguous, since you are either mid-interval
or mid-hold, and a third distinct tone would be more to learn for no added information.

**The once-per-step guard is the whole feature.** Both triggers are thresholds that stay true for the
rest of the step, so a 1Hz tick would re-chime every second — worst on a hold, which does not
auto-advance and can run well past target. A ref keyed on the step index (not reset on change, so it
survives the ticking effect being rebuilt by pause/resume) fixes that, and removing it fails two tests.
Verified in the app by patching HTMLMediaElement.play: silent at 14s, exactly one milestone.wav at the
15s target, still exactly one at 26s.

The asset is generated rather than sourced — two sine notes (A5 then E6) with attack and exponential
decay envelopes, since a raw sine starting or stopping at non-zero amplitude clicks audibly.

## ✅ Direct numeric entry for reps and load

The reported problem: the reps control was a −/+ stepper, so a 30-rep set cost 30 taps mid-workout,
out of breath. The new load stepper had the same ceiling.

Steppers stay — they are right for the small adjustment and for one-handed use. The value itself is now
tappable and opens `session-number-pad.tsx`, a keypad sheet. Wired to reps and load on the reps screen,
and to EMOM reps and AMRAP rounds/extra-reps on the interval screen.

Decisions worth keeping:

- **A custom keypad, not a `TextInput` with `keyboardType="numeric"`.** The runner screens are `flex: 1`
  with no ScrollView and already tight; the OS keyboard would cover the very controls being edited. A
  custom pad also keeps the digits large enough to hit while out of breath.
- **Typing starts a fresh value** rather than appending to the current one — the reason to open the pad
  at all is that the current value is far from what you want. Confirming without typing keeps the
  original.
- **The parent owns which field is being edited**, and renders one pad at the screen root. An overlay
  fills its parent view, not the screen, so a pad rendered inside a stepper block was clipped to a
  strip. That bug was caught before shipping but is the obvious thing to get wrong here.
- Decimal key only where it means something (load); everything else rounds to a whole number on
  confirm. A lone or trailing "." parses to NaN, so Set is disabled rather than writing garbage.

Also an accessibility win, and why it belongs with A11y-1 rather than as separate polish: it gives
screen-reader and motor-impaired users a way to set a value without N discrete activations.

Verified in the app: typing 27 into the reps pad (3 taps, versus 21 on the stepper) sets 27; Cancel
leaves the value untouched; the load pad accepts 42.5.

## ✅ Tests: phase 1 (pure logic) landed

`jest-expo` + `npm test`. **93 tests across 7 suites, ~4s.** No UI tests at this point — those are
phase 3 below, deliberately sequenced after i18n so assertions don't get written against English copy.

Covered: `buildSteps` (rest interleaving, circuit round-robin, `memberKey` stability across rounds,
degenerate zero-step cases), `yaml-mapping` (round-trip across all 7 exercise and 7 entry types,
plus a key-contract test and an idempotency test — see below), `merge` (add / replace-by-id /
referential integrity), `program` (week resolution, override application, non-targeted exercises left
reference-identical), `selectors` (`historyStats`, `currentStreak`, `thisWeekStats`, `nextWeekAfter`,
`sessionEntrySummary`), `exercise-form` (the `min: 0` rest-field asymmetry), and `slug`.

Three things worth recording:

- **Two suites were verified against the bugs they pin**, by restoring the old logic and confirming
  they fail. The EMOM tests fail with `Expected: 600, Received: 300`; the `historyStats` tests fail on
  the fractional-hour form. A regression test that doesn't fail on the bug is worthless, so this check
  is worth repeating whenever one is written.
- **Round-trip tests alone are insufficient for the YAML mapping**, and the suite says so: a symmetric
  typo (both directions using the same wrong key) round-trips perfectly. The key-contract test asserting
  literal snake_case names is what actually pins the on-disk format.
- **Parsing materialises omitted optional keys as `undefined`** rather than leaving them absent. Not a
  defect — the serializer drops them and `serialize → parse → serialize` is byte-identical, which the
  idempotency test now pins. That's the property that matters for a file users hand-edit and re-save.

Refactors this forced, both behaviour-preserving: `buildSteps` and the step model moved to
`session-steps.ts` (importing the runner pulled in `expo-audio` and died on native-module init), and
`nextWeekAfter` is now exported so it can be tested without constructing a whole `Library`.

## ✅ Tests: phase 2 (the session runner) landed

**115 tests across 9 suites.** The runner is exercised through the real hook with `renderHook` and
Jest's modern fake timers, mocking haptics/sounds/notifications and the history store — the store at
*our* boundary, not `expo-file-system`, so assertions are about what got logged rather than about file
writes. Covers: the `-1` sentinel regression asserted on first render, countdown timing, pause
excluding paused time, foreground catch-up after backgrounding, per-type flushing, the `goPrev` undo
matrix (pending-pop, entry-removal with multi-set restore, one-level-only, floor at zero),
`finishSession` committing the in-progress set, and `addRestSeconds` rescheduling its notification.

**The plan's call not to inject a clock held up.** Fake timers mock `Date.now()` and `setInterval` from
one virtual clock, so the wall-clock design tests as-is; nothing in `use-session-runner.ts` changed to
accommodate the tests.

Three things worth knowing for the next person writing tests here:

- **RNTL 14's `renderHook` returns a Promise** (React 19 made rendering async-aware) and `act` must be
  awaited. Sync `act` around `advanceTimersByTime` nests scopes and React reports overlapping act calls.
- **`result` is not an own-enumerable property** of the renderHook result — `{ ...rendered }` silently
  drops it and every assertion then fails on `result.current`.
- **Cleanup is global now** (`clearMocks`/`restoreMocks` in the jest config, plus `useRealTimers` in
  `jest.setup-after-env.js`). A spy installed and restored inside one test previously left later tests
  failing with opaque `AggregateError`s while passing in isolation — the failure surfaced nowhere near
  its cause, which is exactly why this belongs in config rather than per-file.

## ✅ Tests: phase 3 (screens) landed

**200 tests across 19 suites.** Four screens: `workout-editor`, `exercise-editor`, `session`,
`import`. Per the plan, these assert what the browser check reaches worst — validation wiring, delete
guards, error branches, step-kind dispatch — and leave layout, animation, real audio and file writes
to the browser. Every one was verified against the bug it pins by reintroducing that bug and
confirming the test fails; all seven were caught.

Two harness problems had to be solved first, and both are worth knowing about because neither
announces itself:

- **`jest.resolver.js` composes two resolvers rather than choosing one.** `react-native-worklets`
  resolves to `.native.ts` entry points that reach for a JSI binding at *import* time, so any screen
  containing a `ReorderableList` — which is the two biggest editors — died with
  `Cannot read properties of undefined (reading 'loadUnpackers')`, naming nothing relevant. Worklets
  ships a resolver that strips `.native` from the extension list, but jest allows only one `resolver`
  and jest-expo already installs React Native's. Setting either would silently discard the other.
- **`react-i18next` was never registered in the test harness.** `jest.setup-after-env.js` initialised
  the i18next singleton but not the React plugin, so `useTranslation()` found no bound instance and
  rendered key paths — `session.countdown.getReady` instead of `GET READY`. Screen tests appeared to
  work anyway wherever something in the import graph happened to reach `@/i18n`, which registers the
  plugin as a side effect of loading. So a screen's assertions passed or failed based on an unrelated
  module's imports. Now registered in the setup file.

**RNTL 14's `fireEvent` also returns a Promise**, completing the set with `render` and `renderHook`.
Missing the `await` fails silently — the assertion just reads the pre-press tree, and the only hint is
an "overlapping act() calls" warning from some later test.

Shared fixtures live in `src/test-support/` (outside `__tests__`, which jest would otherwise treat as
test files). The `expo-router` stand-in is a module rather than an inline factory because
`jest.mock`'s factory is hoisted above every `const` and may not close over one.

One test documents behaviour rather than asserting a wish, and says so: re-importing an identical file
reports every item as "updated", because `mergeById` classifies by id and not by value. Harmless — the
merge is a whole-object replace — but it means the "no changes" line is reachable only by an empty
file, which is worth knowing before someone loosens that test to "fix" it.

## ✅ I18n-0: structured descriptors in the logic layer

The first step of the i18n plan, done ahead of the library so later work isn't rewriting assertions.
The logic layer returned finished English sentences, which made two things hard: tests had to assert on
prose i18n was about to rewrite, and pluralisation was scattered across a dozen template literals.

`src/domain/format.ts` is now the only place English is assembled. The producers return data:
`workoutSummary` → `workoutShape` (`{ blockCount, types, estimatedMinutes }`), `sessionEntrySummary` →
`sessionEntryResult` (a six-variant descriptor), `circuitSummary` → `circuitShape`. Views call
`formatWorkoutShape` / `formatEntryResult` / `formatCircuitShape`.

**This fixed live bugs rather than just moving code.** "1 blocks" was on the Today card and every Build
row; "1 exercises", "1 workouts", "1 rounds" and "1 reps" were reachable too. They're gone by
construction now — a single `plural()` helper — and `formatEntryResult` also drops the "N min" wording
for EMOM, which was wrong for any interval that isn't 60 seconds.

**`plural` is deliberately English-only.** The obvious implementation is `Intl.PluralRules`, but Hermes
doesn't ship it, so that would pass in tests and on web and crash on device. It's one function, and the
single seam to swap for CLDR categories when i18next and the `intl-pluralrules` polyfill land — which
matters because Polish and Arabic have three to six forms, not two.

The selectors test now asserts descriptors instead of sentences; converting it was a small live
demonstration of the rework this ordering avoids at scale.

**Still assembling English in the logic layer, deferred to the i18n pass proper:** `exerciseSummary`
(`exercise-badge.tsx`), `previewFor` (`session-steps.ts`), and the `toLocaleDateString('en-US', …)`
labels in `recentSessionsView`/`historySessionsView`/`exerciseHistory` — the date ones need the locale
work from I18n-3 to be worth touching, since they'd otherwise just move the hardcoded locale.

## ✅ A11y-1 and A11y-2: labels, touch targets, contrast

**Contrast (A11y-2).** Re-measured independently rather than taking the audit's word for it; the
numbers matched. `light.textSecondary` was `#777166` — 4.41 on `background` and 4.01 on
`backgroundSelected`, failing AA on two of three surfaces, and it's the app's most-used color (every
caption, count and summary line). Now `#6b6558`: 5.28 / 5.79 / 4.79. The runner's soft pill labels
failed too (`accent` 4.17, `accentCalm` 3.79 composited over their translucent backgrounds, at 12px),
so they get dedicated `accentOnSoft` / `accentCalmOnSoft` tokens measuring 5.68 and 5.75 — separate
tokens rather than lightening the fills, so the pill shapes keep their intended weight. White-on-accent
measures 3.64 and is left as-is: it's only used for the 20px semibold "Start session" label, which
clears AA-large, and that constraint is now written into `constants/theme.ts` so it isn't reused for
body text by accident.

**Labels and targets (A11y-1).** The worst cases were the runner's prev/next buttons — CSS-triangle
`View`s with no text child at all, so a screen reader announced "button" with no name, on the primary
in-workout controls. Also labelled: the Build play buttons, all three FABs, the Programs help button,
both search inputs, the RPE pills (with `accessibilityState.selected`), and every `✕` remove control in
the editors, which were previously N indistinguishable "✕" buttons in a list. History's session card
gained `accessibilityState.expanded`, which the chevron glyph alone can't convey. The RPE pills were
~26px tall — the smallest target in the app, in the live runner where you're least precise — and are
now `minHeight: 44`, using minHeight rather than height so they still grow at large text sizes.

Verified in the browser: the caption color computes to `rgb(107, 101, 88)`, the runner pill to
`rgb(221, 138, 92)`, the RPE pill measures 44px, and every named control is reachable via its
accessible name. That last point had a side benefit — the verification script could stop guessing pixel
coordinates and click `getByLabel('Start Calisthenics A')` instead.

**Still open from the a11y audit:** screen-reader reordering for `ReorderableList`, which is
gesture-only and so currently impossible without sight. A11y-3 (dynamic type) and A11y-4
(announcements, reduce-motion) are done — see below.

## ✅ I18n-1 and I18n-3: infrastructure and locale-aware formatting

`expo-localization` + `i18next` + `react-i18next` + `intl-pluralrules`. `src/i18n/index.ts` initialises
i18next from the device's preferred language, narrowed to a shipped one; `src/i18n/format.ts` holds the
Intl wrappers. **Second locale is `pt`** (Brazilian Portuguese), scaffolded and registered — keyed by
language rather than region, since `pt` covers pt-BR and pt-PT while *formatting* still follows the
device's full locale.

**The Hermes trap is handled and is the thing to remember here.** i18next v24+ routes pluralisation
through `Intl.PluralRules`, and Hermes doesn't implement it — it has Collator, DateTimeFormat and
NumberFormat, and not this. Without the polyfill imported *first*, the app works in tests and on web
(both V8) and throws on device: the worst failure shape there is. `DateTimeFormat`/`NumberFormat` need
no polyfill, which is why only pluralisation is special-cased.

**Formatting bugs fixed, which affect users today rather than hypothetically:**

- Four hardcoded `toLocaleDateString('en-US', …)` call sites now use the device locale. Verified:
  en-US renders "Monday, Jul 27", pt-BR renders "segunda-feira, 27 de jul." — day before month, as
  Brazil writes it.
- **`startOfWeek` assumed Monday.** Brazil, the US, Canada and Japan start on Sunday, so "this week"
  silently measured a different seven days than the calendar the user reads — invisible until the
  boundary day. Now driven by `getCalendars()[0].firstWeekday`.
- The month badge uppercases with `toLocaleUpperCase(locale)` rather than `toUpperCase()`, which is
  wrong for Turkish (i → İ) and meaningless for scripts without case.
- Today's date label was computed at **module scope**, freezing at first import — leave the app open
  past midnight and it showed yesterday. Now per render. (This was on the open-bugs list.)

The `thisWeekStats` tests now mock `firstWeekdayIndex` and cover **both** conventions, rather than
depending on whatever locale the test machine reports. They failed when the fix landed, which is
exactly right: they had encoded the hardcoded Monday as if it were a requirement.

**Still to do — I18n-2, the string migration (~250–300 keys).** The infrastructure and the descriptor
layer are in place, so this is now mechanical: replace literals with `t()` calls and fill `en.json` and
`pt.json`. Nothing is translated yet; the locale files are empty scaffolds. Also still assembling
English in the logic layer: `exerciseSummary` and `previewFor`.

## ✅ I18n-2: the string migration

**276 keys, `en` and `pt` at parity, no missing keys either way.** Brazilian Portuguese is a real
translation, not placeholders. Covers the tab bar, all five tab screens, the runner, settings, import,
every editor, and the display-string layer (`domain/format.ts`, `exerciseSummary`, `previewFor`).

The `plural()` helper is gone — i18next resolves CLDR categories from `count`, so a locale with three
or six plural forms is a matter of adding `_few`/`_many` keys rather than changing code.

**Verified in both locales end to end**, which is what caught the three gaps a file-by-file pass
missed:

- The **tab bar** was never migrated, in either the native or web layout — a pt-BR user would have
  navigated an entirely English tab bar. Easy to miss because the labels live in config arrays.
- **"NEXT UP · WEEK 1"** stayed English because `nextUpView` assembled `` `Week ${n}` `` in the logic
  layer. `NextUpView` now returns `weekNumber`/`weekDay` and the view composes it.
- The **workout summary** (`"4 blocks · mixed hold + reps"`) needed `domain/format.ts` itself
  migrated, which was blocked on i18next being available under jest.

That last one is worth recording. `format.ts` imports `i18next` **directly**, not via `@/i18n` — same
singleton, but going through that module would pull `expo-localization` into the domain layer and into
every test that touches formatting. `jest.setup-after-env.js` initialises the same singleton with the
English resources, so `format.test.ts` keeps asserting plain English and never needed editing to
accommodate the change.

**Not translated, by design:** exercise, workout and program names, notes, and `ProgramWeek.day` — all
user data from their own YAML, rendered verbatim. `program-guide.tsx`'s prose was the one exception
that wasn't by design — it kept its own namespace as D5 of the import-prominence plan, and the YAML
samples inside it stay English on the same rule as the rest of this paragraph: they are the file the
reader is about to write, not the page's own words.

## ✅ A11y-3: dynamic type

The one genuinely *blocking* accessibility failure rather than a degradation: the runner screens are
`flex: 1` with `space-between` and **no ScrollView**, so at large accessibility text sizes content
clips instead of scrolling — and what clips is the bottom, where the primary action lives. A user at
iOS AX5 could be left unable to log a set.

Three changes, cheapest first:

- **Capped the giant numerals** at `maxFontSizeMultiplier={1.3}`. They're already 88–100px and are the
  single biggest driver of overflow; they're also the one element already legible at arm's length, so
  capping costs nothing and buys the most room. The hold screen's `s` unit is capped to match —
  letting it scale while the number doesn't would break their alignment rather than help.
- **`height` → `minHeight` on the primary action buttons** (log set, skip rest, add time, done), so a
  wrapped label grows the button instead of being clipped by it. The circular prev/next buttons keep a
  fixed height deliberately: a fixed width bounds them anyway, and stretching a circle vertically
  distorts it without making the glyph more legible.
- **`SessionNextCard` removes itself above `fontScale > 1.5`.** It's the only genuinely supplementary
  element on those screens, and it previews a step the user is about to reach regardless.

**Pinned by test, because the browser can't reach it:** react-native-web always reports
`fontScale: 1`, so the large-text branch is unreachable there. `session-next-card.test.tsx` mocks
`useWindowDimensions` and covers both sides of the threshold.

That test also unblocked component testing generally, by fixing two things that would have stopped any
of it: `constants/theme.ts` imports `global.css`, which jest can't parse — it surfaces as a bare
`Unexpected token ':'` pointing at `:root`, nowhere near the test that triggered it, now mapped to a
stub — and anything rendering `ThemedText` needs `ThemeOverrideProvider`. Note also that RNTL 14's
`render` returns a Promise and must be awaited, the same trap as `renderHook`.

**Still open:** the `height`-based search bars, stat cards and modal header row outside the runner,
which degrade rather than block; and verifying by hand at iOS AX5 / Android 200%, which nothing
automated here substitutes for.

## ✅ A11y-4: runner announcements and reduce-motion

**Announcements needed design, not just an API call.** The obvious implementation —
`accessibilityLiveRegion` on the timer numeral — would announce once per second, roughly sixty
interruptions a minute, talking over the user mid-set. It would also be reading out the one thing they
don't need: the *timing* is already carried eyes-free by the audio cues.

What's missing without sight is **identity** — which exercise, which set, how many left. So
`use-session-announcements.ts` announces transitions only: one sentence per step, nothing in between.
Roughly five utterances per exercise instead of ninety. Deduped, because the derived string is rebuilt
as the runner re-renders; gated on a screen reader actually running, so the runner does no per-step
work in the ~99% of sessions where nobody is listening.

**A test caught a real bug here, not a test artifact.** `isScreenReaderEnabled()` resolves
asynchronously, and the first step's announcement arrives before it does. Held in a ref, that first
announcement was silently dropped — at exactly the moment identity matters most, the start of a
session. Holding it as state means resolving the check re-runs the effect and the pending announcement
still gets spoken.

**Reduce-motion:** the three infinite `withRepeat` pulses (hold, interval, rest) now hold at full
opacity when `useReducedMotion()` is set. The pulse is decoration — its only job, signalling "this is
live", is already carried by the numeral counting.

Exercise names are interpolated into the announcement rather than translated, per the user-data rule.

**Still open from the a11y audit:** screen-reader reordering for `ReorderableList`. It's
`Gesture.Pan().activateAfterLongPress`-only, so reordering workout blocks is currently impossible
without sight — a feature gap rather than a labelling one, and worth sizing on its own rather than
letting it hide inside "a11y polish".
