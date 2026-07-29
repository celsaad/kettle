# Tests, Accessibility, i18n — plan

> **This plan has been executed.** All three workstreams landed in the order below; see
> [`history.md`](history.md) for what each one actually shipped and
> [`implementation-plan.md`](implementation-plan.md) for what remains open. It is kept because the
> *reasoning* — why the ordering is what it is, why Jest over Vitest, why announcements need design
> rather than a `liveRegion`, what is deliberately out of scope — still governs new work, and none of
> it is recoverable from the commits that carried it out. Read it as rationale, not as a backlog.
>
> One call in here was **not** followed, deliberately: the pseudo-locale (`en-XA`) was skipped in
> favour of going straight to real pt-BR translations. The units preference was deferred past the i18n
> pass rather than built with it, but has since shipped — Settings switches every weight between kg and
> lb, on the preferences store this plan called for, and storage stays metric as described below.

Produced by an architect pass over the tree at commit `b4e8c07` plus the session-delete/search and
settings work. Estimates are the planner's; treat them as relative sizing, not commitments.

## Why the ordering is what it is

The three workstreams are not independent, in two specific ways:

1. **Display strings are assembled in the logic layer, not the views.** `selectors.ts`
   (`workoutSummary`, `sessionEntrySummary`, `recentSessionsView`, `historySessionsView`,
   `exerciseHistory`), `exercise-badge.tsx` (`exerciseSummary`, `circuitSummary`) and
   `use-session-runner.ts` (`previewFor`) all return finished English. Tests asserting on those return
   values get invalidated by i18n, as does every `getByText('…')` query.
2. **Accessibility labels are themselves translatable strings.** Adding ~60 of them before i18n means
   migrating them twice unless i18n follows immediately.

Recommended order:

| # | Work | Why here |
|---|---|---|
| 1 | I18n-0: make the logic layer return structured descriptors, not English | Unblocks the rest; pure refactor; it's also where every plural bug lives |
| 2 | Tests phase 1 — pure domain + selectors | Assertions land on numbers/enums, so i18n can't invalidate them |
| 3 | Tests phase 2 — the session runner | Highest-value coverage in the repo |
| 4 | A11y 1–2 — labels, roles, touch targets, contrast | Before string migration, so labels get keyed once |
| 5 | I18n 1–3 — infra, migration, formatting | Sweeps up step 4's labels |
| 6 | A11y 3–4 — dynamic type, runner announcements | Announcement copy is the most inflection-heavy text in the app |
| 7 | Tests phase 3 — UI | Queries can use keys / a11y labels instead of brittle English |

**Escape hatch worth taking:** `schema.ts`, `yaml-mapping.ts`, `merge.ts`, `program.ts` and
`buildSteps` return no display strings at all, so those tests can start immediately, in parallel with
I18n-0. Only `selectors.ts` and `previewFor` are order-sensitive.

## Tests

**Runner: Jest via `jest-expo`, not Vitest** — reasoned against this repo specifically, not in general.
The preset's `transformIgnorePatterns` already covers `expo`, `@expo/*`, `@expo-google-fonts/*` and
`react-native-svg`, all of which this app imports; `babel-preset-expo` handles the reanimated/worklets
plugin that `reorderable-list.tsx` needs; and `jest-expo` ships the native-module mocks without which
importing `expo-audio`, `expo-file-system`'s class API, or `expo-router/unstable-native-tabs` throws at
import time. Vitest's ESM startup edge would apply mainly to phase 1, which is already fast, and costs
the most exactly where the RN surface is largest. Note `@testing-library/react-native` replaces
`react-test-renderer`, which doesn't support React 19 — this app is on 19.2.3. There's no
`babel.config.js` in the repo (Metro doesn't need one on SDK 57) but `babel-jest` does read one; expect
to add a two-line file.

**Phase 1, in priority order:** `yaml-mapping.ts` (highest risk per line — needs *both* a round-trip
property test and a committed golden fixture, since round-tripping alone cannot catch a symmetric typo
where both directions use the same wrong key); `schema.ts` (per-type accept/reject, the refinements,
and the backward-compat defaults that keep old session files readable); `buildSteps`; `merge.ts`;
`program.ts`; `selectors.ts` (needs `jest.setSystemTime`; export the currently-private `nextWeekAfter`).

**Phase 2 — the session runner. Verdict: do not inject a clock.** The hook already derives everything
from `Date.now()` deltas, and Jest's modern fake timers mock `Date.now()` and `setInterval` from one
virtual clock — so `setSystemTime` + `advanceTimersByTime` stays coherent for free. Threading a `now()`
parameter through the most bug-prone file in the app buys nothing the runner doesn't already provide.
Worth doing instead: move the module-private pure helpers (`previewFor`, `upcomingPreview`,
`bufferForStep`, `isDirectLogStep`, the two formatters) into a `session-steps.ts` so they're testable.
Do **not** extract a state machine before tests exist — that's a rewrite of the riskiest file with no
net.

The plan checks itself against a real shipped bug: the `useState(stepIndex)` sentinel that should have
been `-1` and silently skipped the first step of every HIIT/EMOM/AMRAP workout. A first-render
assertion (`restTargetSec === 40` before any timer runs) catches it immediately, without fake timers.
That's the bar any runner test suite has to clear.

**Phase 3 — UI.** Assert the things Playwright reaches poorly: error and empty branches, validation
wiring, the local-state reducers in `workout-editor.tsx` / `program-override-editor.tsx`, and that
`session.tsx` picks the right sub-screen per `step.kind`. Leave layout, animation, real audio/haptics
and file writes to the browser checks. Mock at **our** storage boundary (`@/storage/*`), not at
`expo-file-system` — and mocking `isFileStorageSupported → false` gets the web-degradation path tested
for free.

**CI:** add `npm test -- --ci --maxWorkers=2` alongside the existing typecheck/lint. Collect coverage
but set no threshold initially.

## Accessibility

Current state: accessibility props exist in **two** files (`modal-header.tsx`, `settings.tsx`, plus the
Today gear). Everything else has none.

The findings that matter most:

- **The live runner's prev/next buttons are CSS-triangle `View`s with no text child**, so a screen
  reader announces "button" with no name — and these are the primary in-workout controls.
- **`ReorderableList` is gesture-only.** There is no non-gesture path to reorder workout blocks, so a
  screen-reader user cannot do it at all. That's a feature gap, not a labelling gap, and it's sized
  separately (~0.5 d) so it doesn't disappear into "a11y polish".
- **Announcing the runner needs design, not a `liveRegion`.** Putting
  `accessibilityLiveRegion="polite"` on a per-second numeral would interrupt 60×/minute, talking over
  the user mid-set. Announce *transitions* instead (step change, rest start, 3/2/1, halfway on long
  rests) — ~5 per step — via `announceForAccessibility` gated on `isScreenReaderEnabled()`, and give
  numerals a duration label so focus reads "one minute thirty remaining", not "one colon three zero".
- **Dynamic type has one genuinely blocking failure**, not just ugly ones: the runner screens are
  `flex: 1` + `space-between` with no `ScrollView`, so at iOS AX5 the primary action can go off-screen.
- **Contrast, measured:** `Colors.light.textSecondary` (`#777166`) fails AA on two of its three
  surfaces (4.41 and 4.01) — and it's the app's most-used color, behind every caption. Suggested
  `#6b6558`. The runner's HIIT/HOLD/REPS pill labels fail at 4.17, and REST at 3.79; both need
  on-soft variants. White-on-accent for "Start session" is 3.64 — passing only because the label is
  large and bold, with nothing to spare.

## i18n

**Stack:** `expo-localization` + `i18next` + `react-i18next` + `intl-pluralrules`. `expo-localization`
earns its place independent of the message library: `measurementSystem` and `firstWeekday` directly fix
two bugs the app has today. **Hermes trap:** Hermes implements `Intl.Collator`/`DateTimeFormat`/
`NumberFormat` but **not `Intl.PluralRules`**, and i18next v24+ has no fallback — the polyfill import
must run before `init()`.

**The scoping boundary is the important decision:** user data — exercise/workout/program names, notes,
`ProgramWeek.day`, every id — is **never** translatable. It comes from the user's own YAML and renders
verbatim. Corollaries: never pass a user-authored name through `t()`; `slugify` stays
locale-independent; sort user names with `localeCompare`. The seeded "Rest" pseudo-exercise is the grey
zone — treat it as user data, seeded in the device language once and never re-translated.

**Storage stays metric.** The YAML is portable and shareable, so a file must not change meaning based
on who opens it. Convert at the display/input boundary only, driven by a `unitSystem` preference
defaulted from `measurementSystem`.

**This forces one piece of infrastructure:** there is currently nowhere to persist app preferences —
`theme-context.tsx` documents that its preference is in-memory precisely because of this. A units
preference resetting on every launch is actively wrong, so i18n requires a small preferences store
(~0.5 d), separate from the exported YAML library.

**Ship with a pseudo-locale** (`en-XA` — bracketed, accented, 30% expanded) rather than a real second
language: it catches hardcoded strings and layout overflow better than a real translation would at this
stage, for a couple of hours of work.

**RTL:** cheaper than expected — only 26 physical-direction properties across 11 files, and RN auto-
flips the row/gap/padding layouts. The real work is CSS-triangle glyphs (drawn, so they don't flip) and
24 arrow characters baked into copy (`'Skip rest →'`, `'Done set ↑'`). Getting arrows out of strings is
worth doing regardless. Defer the `I18nManager` plumbing until a real RTL locale exists — half-
implemented RTL is worse than none.

## Deliberately out of scope

Vitest; snapshot tests; Detox; extracting a state machine from the session runner before tests exist;
coverage thresholds on day one; real-filesystem storage tests; RTL plumbing; a real second locale;
translating `program-guide.tsx`'s prose; exhaustive `accessibilityHint` coverage; a web ARIA audit.
