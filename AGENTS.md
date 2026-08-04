# Kettle

Backend-free workout tracker. A hand-editable YAML library (exercises, workouts, multi-week programs)
plus a local session log, one file per session. No server, no account.

> **Expo HAS CHANGED.** Read the exact versioned docs at https://docs.expo.dev/versions/v57.0.0/
> before writing any code. This applies to library choices too — verify what's current for SDK 57
> rather than recalling older Expo.

## Commands

**The package manager is pnpm, not npm** — `pnpm install`, never `npm install`. Installing with npm
strips `nodeLinker: hoisted` out of the layout and typecheck starts failing on transitive imports;
the reasoning and the benchmark are in the decision log. Trailing args need no `--` separator, so
it's `pnpm test --ci`, not `npm test -- --ci`.

- `pnpm run typecheck` — `tsc --noEmit`
- `pnpm run lint` — oxlint. **Keep it at zero warnings and zero errors.** A warning you genuinely
  can't fix wants a one-line disable naming the reason, not a new accepted baseline.
- `pnpm test` — jest via `jest-expo`. Covers the domain layer, the session runner, the highest-branch
  screens and the stores. Under a minute, so run it. A single file is `pnpm jest <path>`.
- `pnpm run format` — oxfmt (same Oxc toolchain as oxlint, so they agree). Run it instead of matching
  the style by hand, and never reach for `pnpm dlx prettier`, which has no config here and would
  reformat the file to its own defaults. **Markdown and `package.json` are excluded on purpose** —
  see the decision log before adding them. **`site/*.html` is not excluded**, and reads like it should
  be: editing the landing site by hand and skipping `format` because "it's only HTML" is what broke
  CI on #58, since `check` runs `oxfmt --check` over everything that isn't on that list.

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
      keys go into **both** `en.json` and `pt.json`. User data is never translated. See "Conventions
      and house rules"; these are cheap now and tedious later, which is why they're a checklist item.
- [ ] **Does it need error checks, an error boundary, or a graceful degrade?** Ask per layer: does a
      storage call need an `isFileStorageSupported` guard; does an in-app form need `validateConfig`-style
      validation (the zod schema does *not* run on that path); can a render throw here cost a workout in
      progress? Degrading beats crashing — `safe-iap.ts`, `safe-notifications.ts` and `library-file.ts`'s
      reseed of a corrupt library are the patterns to copy.
- [ ] **Add tests if it makes sense.** Pure logic, error and empty branches, validation wiring: yes.
      Layout, animation, real audio and file writes: drive the running app instead. See "Writing tests".
- [ ] `pnpm test`
- [ ] `pnpm run typecheck`
- [ ] `pnpm run format`
- [ ] `pnpm run lint` — it comes back completely silent, so anything it prints is yours.
- [ ] **Push the branch and open a PR** (`gh pr create`). The commit message is the durable record —
      root cause, alternatives, deliberate scope cuts go there, not into the docs (see "Docs").

Touching the YAML format, or adding a language? Both have gates of their own — see "Changing the YAML
format" below and [`docs/adding-a-language.md`](docs/adding-a-language.md).

## Where things live

- `src/domain/` — pure logic, no I/O. `types.ts` (domain model), `schema.ts` (zod validation of raw
  YAML), `yaml-mapping.ts`, `merge.ts` (import merges by `id`, whole-object replace), `program.ts`
  (week resolution + override application). The in-app forms keep their data models here too —
  `exercise-form.ts` (editing an exercise's *plan*) and `session-entry-form.ts` (editing what was
  actually *logged*) — so the screens stay thin and the parts worth testing need no React tree.
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

## Conventions and house rules

How new code is expected to arrive. The a11y and i18n workstreams have both had their pass, so they
are house rules here rather than a backlog — cheap as you go, tedious to retrofit.

### Code

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

### Accessibility and i18n

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
- Adding a key means adding it to **both** `en.json` and `pt.json`; they are kept at exact parity —
  by hand, since nothing tests it. A key missing from one bundle doesn't fail anywhere; i18next's
  `fallbackLng` quietly renders it in English. (No count is quoted here on purpose: the one that used
  to be went stale, and the bundles are the only honest answer.)

Shipping a whole new language is a six-place procedure of its own:
[`docs/adding-a-language.md`](docs/adding-a-language.md).

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
- Finish with `pnpm run format`; don't hand-align a new test file against the rest.
- `testTimeout` is 30s, not jest's 5s default. Screen tests pay a one-off lazy-init cost on the first
  render in a file (~0.5s locally, far more on CI's contended 2-core runner, where the default failed).
  It only costs time when something genuinely hangs.

What tests can't reach — layout, animation, real audio and file writes — is verified by driving the
running app in a browser. That procedure has enough traps to be its own document:
[`docs/verifying-in-the-browser.md`](docs/verifying-in-the-browser.md).

## Delegating

Mechanical, well-specified work — adding tests to an existing suite, repetitive renames, boilerplate —
should go to a cheaper subagent rather than being done inline. Point it at an existing suite to imitate
and give it the exact list of cases; that's most of the quality.

**Verify what comes back rather than trusting the report.** A subagent has reported pre-existing lint
damage that its own edits caused. Re-run `pnpm test`, `pnpm run typecheck` and `pnpm run lint` yourself
after any delegated change, and skim the diff.

## Changing the YAML format

This one stays inline rather than moving to `docs/`, because the failure mode is not realising you're
in it: a renamed key looks like a local change and silently breaks three copies you didn't open.

**`schema.ts` has three hand-maintained mirrors, and the public one is outside `docs/`.** Any change
to the format — a new field, a renamed key, a changed requirement, a new exercise type — has to land
in all of them in the same PR:

1. `docs/authoring-exercises-yaml.md` — the authoring reference.
2. `docs/product-plan.md` — its file-format section.
3. **`site/format.html`** — the published reference on the landing site, which is the copy an outside
   author or an assistant actually reads. It is the easiest to forget precisely because it doesn't
   look like a doc and nothing in `src/` imports it.

Three tests cover most of this, and it's worth knowing exactly where they stop:

- `site-samples.test.ts` runs every `<pre data-validate="library">` block on the site through the real
  `parseLibraryYaml`, and `docs-samples.test.ts` does the same for the complete samples in
  `authoring-exercises-yaml.md` and `product-plan.md`. A sample teaching a shape the schema refuses
  fails the suite.
- `format-mirrors.test.ts` parses the **type tables** in the markdown reference and in
  `site/format.html` and diffs the field names and optional flags against `schema.ts` itself. So a
  renamed, invented or missing config field now fails the build in both mirrors.
- **Ranges, units and prose are still unchecked.** `(>0)` versus `(≥0)`, "in kilograms", and every
  sentence around the tables are on you.

That split exists because the eyeball pass failed the first time it mattered: the first draft of the
published page shipped with five of seven type tables wrong (`emom` taking `rounds` instead of
`total_minutes`, `amrap` missing the required `time_cap_sec`, program weeks as `days[]` with
`overrides` as a mapping), all of it plausible enough to read as correct and every bit of it refused
on import. The first two of those are now regression-tested — reintroducing either one fails
`format-mirrors.test.ts`, which is how it was verified.

If you add a complete library sample to any page, mark it `data-validate="library"` so it joins the
checked set. Deliberate fragments — a bare `workouts:` list, a shape sketch with `{ ... }` — stay
unmarked, since they can't parse standalone.

The four `site/examples/*.yaml` libraries are a fourth copy of the format, but the only one that is
checked end to end rather than by eye (`site-examples.test.ts`) — a renamed key breaks them loudly.
Their prose is on `site/examples.html`, which isn't checked, same as the tables above.

## Docs

Every one of these opens with a banner saying what it is and whether it's live; the one-liners here
are the index, the banner is the contract.

- `docs/decisions.md` — **the decision log.** Why things are the way they are, for reasoning that
  doesn't fit in one commit. Cited from `AGENTS.md` and eight source files; this is the file they
  mean.
- `docs/open-work.md` — the backlog, and only the backlog.
- `docs/history.md` — write-ups of shipped work, split out so the backlog stays a backlog.
- `CHANGELOG.md` — **at the repo root**, not in `docs/`. What shipped per release, plus the Play
  release notes that went out with it. The `## Unreleased` section is where a shipped feature is
  written up for *users*; the copy lives beside what it describes rather than in `store/README.md`,
  which keeps only Play's rules. `store-copy.test.ts` asserts every block against its character
  limit, so an over-length note fails the suite rather than the upload.
- `docs/product-plan.md` — the data model, file formats and roadmap.
- `docs/authoring-exercises-yaml.md` — the YAML reference, kept exact against `schema.ts`.
- `docs/sdk-57-api-notes.md` — the `expo-file-system` / `expo-notifications` / `expo-iap` shapes
  confirmed from `node_modules`, because the published docs are wrong for two of the three.
- `docs/adding-a-language.md` — the six-place procedure for shipping a new UI language.
- `docs/verifying-in-the-browser.md` — driving the running app under Playwright.
- `docs/testing-a11y-i18n-plan.md` — executed; kept for its rationale, not as a backlog.
- `docs/watch-remote-plan.md` — **not executed.** Driving a running session from a Wear OS wrist via
  the notification shade, with no watch app and no data on the watch.
- `docs/timed-hold-auto-end-plan.md` — executed; kept for its rationale, not as a backlog. Ending a
  `timed_hold` at the top of its range, and making `hold_sec_min` optional.
- `docs/backup-folder-plan.md` — executed; kept for its rationale, and for the three device-only
  questions it leaves open. Writing the library and the log into a folder the user picks once.

Three rules about keeping the plan files honest, in the order they get broken:

- **Don't append a shipped feature just because it shipped.** The commit is the record — root cause,
  alternatives and scope cuts go there, where `git log -S` finds them. Add to the decision log only
  when the reasoning isn't discoverable from a single commit: a constraint that shapes future work,
  something rejected so it isn't re-proposed, or a decision assembled across several commits. This
  has already failed twice, both times growing hundreds of lines of completed work under a
  forward-looking heading; the reason the rule is this strict is that it doesn't hold on its own.

  **`CHANGELOG.md` is the exception, and the reason the rule can stay strict everywhere else.** A
  shipped feature does get written up there, for users, under `## Unreleased` — that is what the file
  is for. The rule above is about `docs/`, which is reference material and drowns when release notes
  leak into it. If you catch yourself wanting to record that something shipped, the changelog is
  where it goes.
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

And two about what doesn't get written down at all:

- **The README doesn't get a paragraph when a feature ships.** It is a router — pitch, install,
  how to run it, where everything else lives — and every audience it serves is one click away. The
  first rule above covers `docs/`, which left the README absorbing the same pressure unguarded: it
  had grown a 65-line feature narrative that duplicated `site/index.html` for users and this file for
  contributors, in one register that suited neither. A shipped feature earns at most an edit to an
  existing bullet. If it needs prose, it needs the site.
- **Never quote a hand-maintained count.** Test totals, key counts, file counts: they are wrong
  within two PRs, nothing fails when they are, and they were the specific claims the drift audit
  above caught. Name the source instead — `pnpm test`, the locale bundles.

Keep open-work entries short. If the list starts wanting states and assignees, move it to GitHub
issues (`origin` and `gh` are both available); until then the file is deliberately enough.
