# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v57.0.0/ before writing any code.
This applies to library choices too — verify what's current for SDK 57 rather than recalling older Expo.

# Kettle

Backend-free workout tracker. A hand-editable YAML library (exercises, workouts, multi-week programs)
plus an append-only local session log. No server, no account.

## Commands

- `npm run typecheck` — `tsc --noEmit`
- `npm run lint` — oxlint. **42 pre-existing warnings are accepted**, in exactly two categories:
  4 `unicorn/no-array-sort` / `no-array-reverse`, and 38 `import/no-named-as-default-member` from
  `i18next.t(...)` call sites (the i18n migration added those; whether to switch to a named `t`
  import is an open call, not an oversight). Leave both alone, don't add a third category, and check
  the count rather than assuming a clean run. Anything at `error` level is new and yours.
- `npm test` — jest via `jest-expo`. 287 tests across 25 files: the domain layer, the session runner,
  five screens (`workout-editor`, `exercise-editor`, `program-editor`, `session`, `import`), three
  components, and the tip and preferences storage/stores. Under a minute, so run it.
- `npm run format` — oxfmt (same Oxc toolchain as oxlint, so they agree). Run it instead of matching
  the style by hand, and never reach for `npx prettier`, which has no config here and would reformat
  the file to its own defaults. **Markdown and `package.json` are excluded on purpose** — see the
  decision log before adding them.

## Delegating

Mechanical, well-specified work — adding tests to an existing suite, repetitive renames, boilerplate —
should go to a cheaper subagent rather than being done inline. Point it at an existing suite to imitate
and give it the exact list of cases; that's most of the quality.

**Verify what comes back rather than trusting the report.** A subagent has reported pre-existing lint
damage that its own edits caused. Re-run `npm test`, `npm run typecheck` and `npm run lint` yourself
after any delegated change, and skim the diff.

## Where things live

- `src/domain/` — pure logic, no I/O. `types.ts` (domain model), `schema.ts` (zod validation of raw
  YAML), `yaml-mapping.ts`, `merge.ts` (import merges by `id`, whole-object replace), `program.ts`
  (week resolution + override application).
- `src/storage/` — all file I/O, via `expo-file-system`'s **class-based `File`/`Directory` API**.
  One file per session (`session-files.ts`), so a mid-workout flush never rewrites history.
- `src/state/` — zustand stores (`library-store`, `session-history-store`, `preferences-store`) and
  `selectors.ts`, which holds most derived/display logic.
- `src/hooks/use-session-runner.ts` — the wall-clock session engine. The product plan calls timer
  reliability "the make-or-break issue"; treat this file as high-risk and verify changes by running a
  real session, not by reasoning alone. Its pure parts live next door in `session-steps.ts`
  (`buildSteps`, `previewFor`, the step model) — importing the runner itself pulls in `expo-audio`
  and dies on native-module init, which is why that split exists.
- `src/app/` — expo-router routes. `src/components/` — shared UI.

**YAML is snake_case, domain code is camelCase**, bridged only in `yaml-mapping.ts`. Program
`overrides` are partial *raw* (snake_case) patches — that asymmetry is deliberate and load-bearing.

## Platform constraints that change how you work

- **Web has no persistence.** `expo-file-system` doesn't support it, so the web build runs on an
  ephemeral in-memory seed library. Every storage function is guarded by `isFileStorageSupported`.
- **`Alert.alert` is a no-op on web** — react-native-web ships literally `class Alert { static
  alert() {} }`. Confirm dialogs (deletes, finish-session) silently do nothing in the browser, so a
  browser check of a confirm flow verifies nothing unless you patch it at runtime in the test script.
- Adding a route file requires regenerating `.expo/types/router.d.ts` (briefly run the dev server) or
  `router.push('/new-route')` fails typecheck.
- **Nothing may phone home.** The Play listing declares zero data collected/shared, which is a product
  claim, not an accident (see the tip-jar entry in the decision log). Any SDK that transmits — Sentry,
  EAS Update, analytics, RevenueCat — breaks it and needs a Data Safety declaration. Don't add one
  without raising it first. `expo-notifications` is fine as used (local notifications only);
  `getExpoPushTokenAsync` would not be.

## Writing tests

- **Every RNTL 14 entry point returns a Promise** — `render`, `renderHook` *and* `fireEvent`. A
  missing `await` doesn't fail loudly: the assertion reads the pre-interaction tree, and the only hint
  is an "overlapping act() calls" warning from some later test. Around fake timers, `act` must be
  `async` too, or React reports nested scopes.
- **Mock at our own boundary** (`@/storage/*`, `@/state/session-history-store`), not at
  `expo-file-system` — assertions then read as "what got persisted" rather than "what got written".
  Screens read the library from the real zustand store, so setup is `useLibraryStore.setState(...)`.
- `src/test-support/` holds the shared fixtures and the `expo-router` stand-in. That one is a module
  rather than an inline factory because `jest.mock`'s factory is hoisted above every `const` and may
  not close over one; `jest.mock('expo-router', () => require('@/test-support/expo-router'))` works.
- **Prove a regression test fails against the bug it pins**, by reintroducing that bug. A test that
  passes either way is worthless, and this has caught more than one.
- **To test that a screen is translated, drive it in `pt`** (`changeLanguage('pt')`; the harness loads
  both bundles and resets the locale after every test). An English-locale assertion *cannot* catch a
  hardcoded English string — `t('x.y')` and the literal it returns render identically. It only catches
  a rendered key path. Three screens have shipped with hardcoded strings for exactly this reason.
- **Alert-driven confirm flows go through `pressAlertButton`**, which reaches into the spied
  `Alert.alert` call and runs the handler in its own `act` scope. `Alert` renders nothing, so its
  buttons aren't in the tree; and the handler writes to the store, so without `act` React reports an
  unwrapped update from inside the *store*, naming a file nowhere near the test that caused it.
- Finish with `npm run format`; don't hand-align a new test file against the rest.
- `testTimeout` is 30s, not jest's 5s default. Screen tests pay a one-off lazy-init cost on the first
  render in a file (~0.5s locally, far more on CI's contended 2-core runner, where the default failed).
  It only costs time when something genuinely hangs.

## Verifying in the browser

Tests cover the logic layer and four screens, but layout, animation, real audio and file writes are
still only verified by driving the running app. Doing this wrong wastes a lot of time, so:

- `npx expo start --web --port <port>`; poll with curl, it can take 60–90s. Use a distinct port if
  anything else might be running.
- Playwright is **not** a project dependency — install it in a scratch dir outside the repo. If the
  browser build is missing: `npx playwright install chromium-headless-shell`.
- **Never `page.goto` mid-flow.** A full reload resets the in-memory library, destroying anything you
  just created. Drive everything through real in-app navigation in one page instance.
- **React Navigation keeps previous screens mounted but hidden**, so the same text matches several
  times. Use `.filter({ visible: true }).last()`, not bare `.last()`. A bare `input` index will hit
  the Library screen's hidden search box — scope numeric fields with `input[inputmode="numeric"]`.
- To get a session into history: Build tab → the small round play button on a workout card (starts it
  ad-hoc) → repeatedly click whichever is visible of `Done set ↑`, `Log set → Rest`, `Skip rest →`,
  then `Done`.
- Always capture `page.on('console')` and `page.on('pageerror')`. The app is currently clean apart
  from one expo-notifications web warning, so any error means investigate.
- Actually read your screenshots. Don't claim something renders correctly without having looked.

## Conventions

- Modal screens: register in `src/app/_layout.tsx` with `presentation: 'modal', headerShown: false`,
  and open with the shared `ModalHeader` (grabber + pinned close button; it owns the top spacing, so
  the screen's own `scrollContent` has no `paddingTop`).
- Theming is strict: `useTheme`, `ThemedText`, `ThemedView`, `Spacing.*`, `MaxContentWidth`. The
  session runner is always dark (`RunnerColors`) regardless of scheme.
- In-app forms write straight to the store and never pass through the zod schema, so they need their
  own validation (see `validateConfig` in `domain/exercise-form.ts`).
- **Weights are stored in kilograms, always.** The library is a file users export and share, so it
  can't change meaning with the reader's settings. `domain/units.ts` is the only converter and
  `useUnitSystem()` the only reader of the preference — never store a pound value, and never format a
  weight without going through `toDisplayWeight`. Anything editing a *stored* weight must also pass
  `previousWeightKg`, or an untouched field silently drifts on save (see the decision log).
- **Comments explain why, not what** — trade-offs, root causes, and deliberate scope cuts. Match the
  surrounding density; don't annotate self-evident lines.

## Accessibility and i18n are house rules, not projects

Both workstreams have landed. New work is expected to arrive already conforming — these are cheap when
done as you go and tedious to retrofit, which is why they're here rather than on a backlog.

- **Every interactive element needs `accessibilityRole` and a label.** Icon-only controls need it most,
  having no text to fall back on — the runner's prev/next were CSS triangles announcing as an unnamed
  "button". Selected/expanded controls need `accessibilityState`. Touch targets are 44px minimum, via
  `minHeight` (never `height`, which breaks at large accessibility text sizes) or `hitSlop`.
- **Contrast-check new colors** against every surface they sit on, alpha-compositing where the
  background is translucent. `constants/theme.ts` records the measured ratios and the one pairing that
  clears AA-large only.
- **No user-facing string in the logic layer.** Producers return descriptors; `domain/format.ts` and
  the views render them. Counts go through i18next's `count` (never a `=== 1` ternary), and dates
  through `i18n/format.ts` (never `toLocaleDateString('en-US', …)`).
- **Never translate user data** — exercise, workout and program names, notes, and `ProgramWeek.day`
  come from the user's YAML and render verbatim. Key the English around them and interpolate the name.
- Adding a key means adding it to **both** `en.json` and `pt.json`; they are kept at exact parity.

## Docs

- `docs/implementation-plan.md` — settled decisions, the decision log, and the open-work lists.
- `docs/history.md` — write-ups of shipped work, split out of the plan so it stays a plan.
- `docs/exercise-tracker-product-plan.md` — the data model, file formats and roadmap.
- `docs/authoring-exercises-yaml.md` — the YAML reference, kept exact against `schema.ts`.
- `docs/testing-a11y-i18n-plan.md` — executed; kept for its rationale, not as a backlog.

**Don't append a shipped feature to any of them just because it shipped.** The commit message is the
record — write the root cause, the alternatives, and the deliberate scope cuts there, where
`git log -S` can find them. Add to the decision log only when the reasoning isn't discoverable from a
single commit: a constraint that shapes future work, something rejected so it isn't re-proposed, or a
decision assembled across several commits. This has already failed twice — the plan grew to ~210
lines of completed work under a heading reading "what's genuinely left", and then regrew ~330 more as
top-level `## ✅` sections after that heading was fixed. Both are now in `history.md`.

Open bugs and planned work live in the sections at the bottom of the implementation plan. Keep those
entries short — if the list starts wanting states and assignees, that's the signal to move it to
GitHub issues (`origin` and `gh` are both available); until then the file is deliberately enough.

Check these before assuming something is missing — but **verify against the code**, since they have
drifted before. A docs audit on 2026-07-28 found the README claiming the project had no tests when it
had 230, and three "open bugs" that were already fixed.
