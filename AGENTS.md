# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v57.0.0/ before writing any code.
This applies to library choices too — verify what's current for SDK 57 rather than recalling older Expo.

# Kettle

Backend-free workout tracker. A hand-editable YAML library (exercises, workouts, multi-week programs)
plus an append-only local session log. No server, no account.

## Commands

- `npm run typecheck` — `tsc --noEmit`
- `npm run lint` — oxlint. **Keep it at zero warnings and zero errors.** A warning you genuinely
  can't fix wants a one-line disable naming the reason, not a new accepted baseline.
- `npm test` — jest via `jest-expo`. Covers the domain layer, the session runner, the highest-branch
  screens and the stores. Under a minute, so run it. A single file is `npx jest <path>`.
- `npm run format` — oxfmt (same Oxc toolchain as oxlint, so they agree). Run it instead of matching
  the style by hand, and never reach for `npx prettier`, which has no config here and would reformat
  the file to its own defaults. **Markdown and `package.json` are excluded on purpose** — see the
  decision log before adding them.

## Working on a feature

Run this in order. The two middle questions are the ones that get skipped and are the most expensive to
retrofit, so answer them out loud even when the answer is no.

- [ ] **Branch first** — `git checkout -b <name>`. Nothing lands straight on `master` (the default
      branch is `master`, not `main`).
- [ ] **Plan it first if it's big.** The tell is whether you can name the files you'll touch without
      opening them; if you can't, or it crosses layers, changes a file format, or won't land in one
      sitting, write the approach down and agree it before any code. Say what you're *not* doing — a
      scope cut named up front is a decision, and the same cut discovered halfway is a rewrite. Small
      contained changes skip this.
- [ ] **Implement the feature.**
- [ ] **Does it need a11y and i18n?** For anything with UI, assume yes: `accessibilityRole` + a label on
      every interactive element, `accessibilityState` where it's selected/expanded, 44px targets via
      `minHeight`, new colors contrast-checked, and no user-facing string outside the locale bundles —
      keys go into **both** `en.json` and `pt.json`. User data is never translated. See the house-rules
      section below; these are cheap now and tedious later, which is why they're a checklist item.
- [ ] **Does it need error checks, an error boundary, or a graceful degrade?** Ask per layer: does a
      storage call need an `isFileStorageSupported` guard; does an in-app form need `validateConfig`-style
      validation (the zod schema does *not* run on that path); can a render throw here cost a workout in
      progress? Degrading beats crashing — `safe-iap.ts`, `safe-notifications.ts` and `library-file.ts`'s
      reseed of a corrupt library are the patterns to copy.
- [ ] **Add tests if it makes sense.** Pure logic, error and empty branches, validation wiring: yes.
      Layout, animation, real audio and file writes: drive the running app instead. See "Writing tests".
- [ ] `npm test`
- [ ] `npm run typecheck`
- [ ] `npm run format`
- [ ] `npm run lint` — it comes back completely silent, so anything it prints is yours.
- [ ] **Push the branch and open a PR** (`gh pr create`). The commit message is the durable record —
      root cause, alternatives, deliberate scope cuts go there, not into the docs (see "Docs").

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
- `src/state/` — zustand stores (`library-store`, `session-history-store`, `preferences-store`,
  `tip-store`) and
  `selectors.ts`, which holds most derived/display logic.
- `src/hooks/use-session-runner.ts` — the wall-clock session engine. The product plan calls timer
  reliability "the make-or-break issue"; treat this file as high-risk and verify changes by running a
  real session, not by reasoning alone. Its pure parts live next door in `session-steps.ts`
  (`buildSteps`, `previewFor`, the step model) — importing the runner itself pulls in `expo-audio`
  and dies on native-module init, which is why that split exists.
- `src/app/` — expo-router routes. `src/components/` — shared UI.
- `site/` — the public landing site (GitHub Pages), plain static HTML with no build step. Not part of
  the app build. `site/format.html` mirrors `schema.ts` — see "Changing the YAML format" below.
  `site/examples/*.yaml` are complete libraries published for readers to import as they are;
  `site-examples.test.ts` parses each one, merges it into the seed library the way the import screen
  does (which is what catches a dangling `exercise:` reference), and holds the id-prefix promise the
  examples page makes — every example prefixes its ids so importing one can't overwrite anything the
  reader already has.

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

Tests cover the logic layer and five screens, but layout, animation, real audio and file writes are
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
- **The app boots in the browser's locale, so your selectors are probably Portuguese.** `text=Library`
  times out at 30s against a pt-BR machine and reads as a broken app rather than a wrong selector.
  Either dump `body.innerText` first and write selectors against what's actually there, or launch the
  context with `locale: 'en-US'` — but the pt run is the more valuable one, since an English pass
  can't catch a hardcoded English string (same reason the screen tests drive `pt`).
- To get a session into history: Build tab → the small round play button on a workout card (starts it
  ad-hoc) → repeatedly click whichever is visible of `Done set ↑`, `Log set → Rest`, `Skip rest →`,
  then `Done`.
- **`react-native-web` implements only part of the a11y API**, so a browser check under-reports it:
  `accessibilityRole`/`accessibilityLabel` map to `role`/`aria-label`, but `accessibilityActions`,
  `onAccessibilityAction` and `accessibilityValue` are dropped with no warning. The block-reorder
  handle looks actionless in the DOM and works fine under TalkBack. Assert those in jest, not here.
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

Both workstreams have had their pass, so new work is expected to arrive already conforming — cheap as
you go, tedious to retrofit, which is why they're here rather than on a backlog.

- **Every interactive element needs `accessibilityRole`; it needs an explicit `accessibilityLabel`
  only when its own children don't name it.** A `Pressable` wrapping a `Text` already takes its name
  from that text, and adding a label that duplicates it will drift from what's on screen and break
  Voice Control, which matches the visible words. Icon-only controls are the ones that genuinely need
  a label, having nothing to fall back on — the runner's prev/next were CSS triangles announcing as an
  unnamed "button". Selected/expanded/disabled controls need `accessibilityState`. Touch targets are
  44px minimum, via `minHeight` (never `height`, which breaks at large accessibility text sizes) or
  `hitSlop`. Decorative geometry — progress bars, CSS triangles, the grabber — keeps a fixed `height`;
  the rule is about controls, not every box.
- **Contrast-check new colors** against every surface they sit on, alpha-compositing where the
  background is translucent. `constants/theme.ts` records the measured ratios and the one pairing that
  clears AA-large only.
- **No user-facing string in the logic layer.** Producers return descriptors; `domain/format.ts` and
  the views render them. Counts go through i18next's `count` (never a `=== 1` ternary), and dates
  through `i18n/format.ts` (never `toLocaleDateString('en-US', …)`).
- **Never translate user data** — exercise, workout and program names, notes, and `ProgramWeek.day`
  come from the user's YAML and render verbatim. Key the English around them and interpolate the name.
- Adding a key means adding it to **both** `en.json` and `pt.json`; they are kept at exact parity
  (409 keys each today) — by hand, since nothing tests it. A key missing from one bundle doesn't fail
  anywhere; i18next's `fallbackLng` quietly renders it in English.

### Adding a language

Six places, and only the first two are load-bearing enough to fail loudly if you miss them. Verified
against the code rather than recalled — if you're adding the third language, re-check this list holds.

1. **`src/i18n/locales/<code>.json`** — the new bundle, at full parity with `en.json`.
2. **`src/i18n/index.ts`** — import it into `resources`. That map is the *only* list of supported
   languages: `deviceLanguage()` narrows the device's preferences against `Object.keys(resources)`,
   so nothing else anywhere selects a language. Key it by language, not region (`pt` serves pt-BR and
   pt-PT); region still drives dates, numbers and first-weekday through Intl, independently of this.
3. **`src/storage/seed-translations.ts`** — a `SeedTranslation` for the starter library. **This is the
   one that gets forgotten**, because nothing fails: a language with no table falls back to English
   per string, so a new user gets translated chrome around an English library and the suite stays
   green. `seed-library.test.ts` only checks languages *already in* the table (it enforces parity
   within one, in both directions, not that every UI language has one).
4. **`jest.setup-after-env.js`** — the harness inits i18next with its own explicit `{ en, pt }`
   resources, deliberately not importing `@/i18n` (which would pull `expo-localization` into every
   test that touches formatting). A new language isn't visible to tests until it's added here too.
5. **`README.md`** — the sentence naming which languages the UI and the seed library ship in.
6. **The Play listing**, which isn't in this repo.

Two things that are *not* on the list, checked rather than assumed: `app.json` has no per-locale
config (the `expo-localization` plugin is language-agnostic), and nothing about date, number, weekday
or unit formatting needs touching — all of it reads the device locale through Intl in `i18n/format.ts`.

Two traps before picking one:

- **An RTL language is a project, not a bundle.** The `I18nManager` plumbing was deliberately deferred
  until a real RTL locale existed (see `testing-a11y-i18n-plan.md`), and the work it defers is the
  CSS-triangle glyphs, which are drawn and don't flip, plus the arrow characters baked into copy
  (`'Skip rest →'`, `'Done set ↑'`). Adding Arabic or Hebrew means doing that first.
- **A seed table's day labels must sort in training order.** `nextWeekAfter` walks a multi-day week by
  `day.localeCompare`, so `Dia 1`/`Dia 2`/`Dia 3` works and weekday names do not — the per-language
  seed test catches it, and this is why it runs every structural invariant against every language.

## Docs

- `docs/implementation-plan.md` — settled decisions, the decision log, and the open-work lists.
- `docs/history.md` — write-ups of shipped work, split out of the plan so it stays a plan.
- `docs/exercise-tracker-product-plan.md` — the data model, file formats and roadmap.
- `docs/authoring-exercises-yaml.md` — the YAML reference, kept exact against `schema.ts`.
- `docs/testing-a11y-i18n-plan.md` — executed; kept for its rationale, not as a backlog.
- `docs/watch-remote-plan.md` — **not executed.** Driving a running session from a Wear OS wrist via
  the notification shade, with no watch app and no data on the watch.

Three rules, in the order they get broken:

- **Don't append a shipped feature just because it shipped.** The commit is the record — root cause,
  alternatives and scope cuts go there, where `git log -S` finds them. Add to the decision log only
  when the reasoning isn't discoverable from a single commit: a constraint that shapes future work,
  something rejected so it isn't re-proposed, or a decision assembled across several commits. This
  has already failed twice, both times growing hundreds of lines of completed work under a
  forward-looking heading; the reason the rule is this strict is that it doesn't hold on its own.
- **But do prune the open-work lists when you ship.** The rule above is about not *adding* and says
  nothing about subtracting, which reads as "touch no docs at all" — and leaves a shipped item in
  "planned work" claiming to be open. Shipped part of a multi-part entry? Leave the rest, say which
  part went. Deleting a finished bullet needs no write-up.
- **Verify against the code before believing any of them.** They have drifted before, in both
  directions: an audit found the README claiming no tests when there were 230, three "open bugs"
  already fixed, and this file asserting an a11y pass was complete when seven files had no a11y props
  at all. The YAML samples no longer rely on this: `docs-samples.test.ts` runs every complete library
  sample in `authoring-exercises-yaml.md` and the product plan through the real `parseLibraryYaml`, so
  a doc that teaches a shape the schema refuses fails the suite. Prose is still on you.

Keep open-work entries short. If the list starts wanting states and assignees, move it to GitHub
issues (`origin` and `gh` are both available); until then the file is deliberately enough.

## Changing the YAML format

**`schema.ts` has three hand-maintained mirrors, and the public one is outside `docs/`.** Any change
to the format — a new field, a renamed key, a changed requirement, a new exercise type — has to land
in all of them in the same PR:

1. `docs/authoring-exercises-yaml.md` — the authoring reference.
2. `docs/exercise-tracker-product-plan.md` — its file-format section.
3. **`site/format.html`** — the published reference on the landing site, which is the copy an outside
   author or an assistant actually reads. It is the easiest to forget precisely because it doesn't
   look like a doc and nothing in `src/` imports it.

`site-samples.test.ts` catches the part it can: every `<pre data-validate="library">` block on the
site is run through the real `parseLibraryYaml`, so a sample teaching a shape the schema refuses
fails the suite. **The tables and prose around those samples are not checked** — the same caveat as
the docs rule above. The first draft of that page shipped with five of seven type tables wrong
(`emom` taking `rounds` instead of `total_minutes`, `amrap` missing the required `time_cap_sec`,
program weeks as `days[]` with `overrides` as a mapping), all of it plausible enough to read as
correct and every bit of it refused on import. Diff the tables against `schema.ts` by hand; the test
will not do it for you.

If you add a complete library sample to any page, mark it `data-validate="library"` so it joins the
checked set. Deliberate fragments — a bare `workouts:` list, a shape sketch with `{ ... }` — stay
unmarked, since they can't parse standalone.

The four `site/examples/*.yaml` libraries are a fourth copy of the format, but the only one that is
checked end to end rather than by eye (`site-examples.test.ts`) — a renamed key breaks them loudly.
Their prose is on `site/examples.html`, which isn't checked, same as the tables above.
