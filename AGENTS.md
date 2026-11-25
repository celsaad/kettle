# Kettle

Backend-free workout tracker. A hand-editable YAML library (exercises, workouts, multi-week programs)
plus a local session log, one file per session. No server, no account.

## Commands

**Node is here for oxfmt alone**, and its package manager is pnpm: `pnpm install`, never
`npm install`, which ignores the lockfile. Everything else, every test included, is Gradle's, run from
`native/android`.

- `pnpm run format` — oxfmt, over `site/`, the JSON and the corpus. Run it instead of matching
  the style by hand, and never reach for `pnpm dlx prettier`, which has no config here and would
  reformat the file to its own defaults. **Markdown and `package.json` are excluded on purpose** —
  see the decision log before adding them. **`site/*.html` is not excluded**, and reads like it should
  be: editing the landing site by hand and skipping `format` because "it's only HTML" is what broke
  CI on #58, since `check` runs `oxfmt --check` over everything that isn't on that list.
- `./gradlew build` from `native/android` — every module's tests, ktlint, detekt, Android lint and
  compiler warnings, all failing on any finding. **Keep it at zero warnings.** A finding you
  genuinely can't fix wants a one-line suppression naming the reason, not a new accepted baseline.
  It covers more than the app: `:repo-checks` holds the docs, the site and the store copy, so a
  prose-only change runs it too. It needs no `pnpm install`, since it reads the repo's plain files
  and nothing from Node. `./gradlew ktlintFormat` formats the Kotlin; a single module's tests are
  `./gradlew :<module>:test` (`:shared:jvmTest`, `:app:testDebugUnitTest`).

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
      keys go into **all three** of `en.json`, `pt.json` and `ja.json`. User data is never
      translated. See "Conventions and house rules"; these are cheap now and tedious later, which is
      why they're a checklist item.
- [ ] **Does it need error checks, an error boundary, or a graceful degrade?** Ask per layer: can a
      storage call fail without throwing into its caller; does an in-app form need `validateConfig`-style
      validation (the importer's schema readers do *not* run on that path); can a render throw here cost a workout in
      progress? Degrading beats crashing — `RunnerService.kt`'s `notifyIfAllowed`,
      `PreparedLibrary.kt`'s reseed of a corrupt library, and native Support's store states
      (`TipJar.kt`) are the patterns to copy.
- [ ] **Add tests if it makes sense.** Pure logic, error and empty branches, validation wiring: yes.
      Layout, animation, real audio and file writes: drive the running app instead. See "Writing tests".
- [ ] `pnpm run format`, and `./gradlew ktlintFormat` from `native/android` if you touched Kotlin.
- [ ] `./gradlew build` from `native/android` — it comes back with no findings, so anything it
      reports is yours. A moved golden reaches it because the Kotlin runner reads the corpus.
- [ ] **Push the branch and open a PR** (`gh pr create`). The commit message is the durable record —
      root cause, alternatives, deliberate scope cuts go there, not into the docs (see "Docs").

Touching the YAML format, or adding a language? Both have gates of their own — see "Changing the YAML
format" below and [`docs/adding-a-language.md`](docs/adding-a-language.md).

Cutting a release is three separate things, deliberately: `/bump` decides the version and closes out
the changelog, `.github/workflows/android.yml` builds the `.aab` that goes to Play (see
[`docs/building-android.md`](docs/building-android.md)), and `/release` tags the commit and publishes
the APK for people who don't get their copy from Play. **Read the signing entry in the decision log
before you publish an APK** — every APK this repo can build is signed with the *upload* key, the
public one has to be the Play-signed universal APK from the Console, and the wrong key is the one
mistake here that cannot be fixed for anyone who has already installed it.

## Where things live

- `src/content/` — the content the app *ships*, as data, read through `ShippedContent`; native
  writes the seed on a first launch (`PreparedLibrary.kt`). It is an English `library.yaml` plus a `<lang>.json` string table per
  language (`:shared`'s `LibraryTranslation.kt`), and `packs.json` for the pack order. `:app`'s
  `ShippedLibrariesTest`, `ShippedSeedTest` and `ContentPacksTest` hold what the content says, in
  every language. **New shipped content goes in a pack, not in the seed** — the seed can only ever
  reach a fresh install. A new pack is a directory, a `packs.json` entry, its import-screen row
  (`import.packs.<id>.name`/`.detail` in all three bundles), and a line in `StarterPacks.kt`'s
  `packStrings`; `ContentPacksTest` fails when the first three disagree, `:app` won't compile
  without the row, and `ImportScreenTest` fails without the line. See the decision log for the four
  rules a pack has to respect; the prefixed-id one is the load-bearing one.
- The session runner is native's since the flip release (#308): a headless engine in `:shared`
  (`RunnerEngine.kt`, with the step model in `RunnerSteps.kt`) that `:app`'s foreground service
  (`shell/runner/RunnerService.kt`) owns and feeds the time, and the Compose runner over it. The
  product plan calls timer reliability "the make-or-break issue"; treat the engine and the service as
  high-risk and verify changes by running a real session (`scripts/session-check.sh`), not by
  reasoning alone.
- Routing is native's since #309 (`shell/nav/`: `Route.kt` is the route table, `Navigator.kt` the
  back stack and the runner's slot).
- `site/` — the public landing site (GitHub Pages), plain static HTML with no build step. Not part of
  the app build. `site/format.html` mirrors `library-schema.json` — see "Changing the YAML format"
  below. `site/examples/*.yaml` are complete libraries published for readers to import as they are;
  the corpus parses each one (`source:` cases), and `:repo-checks`' `SiteExamplesTest` merges it into
  the seed library the way the import screen does (which is what catches a dangling `exercise:`
  reference) and holds the id-prefix promise the examples page makes — every example prefixes its ids
  so importing one can't overwrite anything the reader already has.
- `native/android/repo-checks/` — `:repo-checks`, tests only: the guards on the repo's own prose and
  published files (the site's samples and examples, the docs' samples, the format mirrors' tables,
  the Play copy), read in place with `:shared` doing the parsing. It runs in `gradlew build`, so a
  docs-only change fails CI's native job.

- `native/android/` — the app: a Kotlin Multiplatform `:shared` module (the domain port), a
  Compose `:app`, and `:strings`, which generates `:app`'s string resources from the locale bundles
  on every build (never committed; plurals go through
  `pluralText`, not `pluralStringResource`), `:theme`, which generates its Compose theme from
  `src/constants/theme.json` the same way and copies the fonts its `Fonts` names out of
  `native/android/theme/fonts` (committed, with their OFL licences), and `:art`, which turns `src/content/art/*.svg` into
  VectorDrawables and fails the build on any SVG outside the subset `src/content/art/README.md`
  names.
  `gradlew build` from that directory compiles, tests and lints it, and ktlint, detekt and Android
  lint hold the zero-warning rule; `gradlew ktlintFormat` is its `pnpm run format`.
  The version is `gradle.properties`' (`kettle.versionCode` / `kettle.versionName`, which `/bump`
  moves), and the icons and manifest are edited in `app/src` itself. `scripts/native-dev.sh` is the
  dev loop: build, install on a phone, launch. Each device script is a `.sh` with a step-for-step
  `.ps1` twin for Windows; a change to one goes into both. `:benchmark` is a Macrobenchmark of cold
  start to a drawn Home, run on a phone by `scripts/startup-benchmark.sh`; its end point is
  native Home's `reportFullyDrawn` (`ReportDrawnWhen`), and today's app returns as a baseline
  in M7. `scripts/route-check.sh` steers the running `.dev`
  shell through every route by navigate message and fails on the first screen that isn't the
  expected one. Run it after touching navigation, and update its matrix whenever a screen goes
  native. `scripts/session-check.sh` runs a session's device checks unattended (timing with the screen
  off, cues, the step-end alert, process death, the finish) and restores `.dev` after. Run it on the
  `Medium_Phone_API_37.0` AVD after touching the runner, its service, cues or notifications; the PR
  template lists the checks it can't do.

- `conformance/` — the format's behaviour as data, and its authority: hand-written YAML inputs and
  golden outputs, checked and written by the Kotlin runner in `:shared`; its README is the contract.
  A behaviour change in `:shared` needs `./gradlew :shared:jvmTest -PupdateConformance` (then
  `pnpm run format`), and the golden diff is part of the review.

**YAML is snake_case, domain code is camelCase**, bridged only in `:shared`'s readers and writers
(the `*Schema.kt` readers, `LibraryYaml.kt`, `SessionYaml.kt`). Program
`overrides` are partial *raw* (snake_case) patches — that asymmetry is deliberate and load-bearing.

## Platform constraints that change how you work

- **There is no web build.** It ended in M6 (#298), so `Platform.OS` is only ever `android` or
  `ios`, and storage has no unsupported-platform guard. Don't add `react-native-web` back to check a
  layout: drive the running app on a device instead.
- **Nothing may phone home.** The Play listing declares zero data collected/shared, which is a product
  claim, not an accident (see the tip-jar entry in the decision log). Any SDK that transmits — Sentry,
  EAS Update, analytics, RevenueCat — breaks it and needs a Data Safety declaration. Don't add one
  without raising it first. Local notifications are fine (the runner's and the rest-day reminder's are
  posted on the device); a push token would not be.

## Conventions and house rules

How new code is expected to arrive. The a11y and i18n workstreams have both had their pass, so they
are house rules here rather than a backlog — cheap as you go, tedious to retrofit.

### Code

- **A card is for one privileged object; a list of peers is rows.** Lists use `ListRow` +
  `ListRowSeparator` + `ListHeaderRule` from native's `shell/list/ListRow.kt` — no fill, no border,
  no radius, a hairline between neighbours, and no horizontal padding, since the screen's own inset
  is the column everything else lines up on. A row that can't use the component (History's expands;
  Settings' are section siblings rather than list children) shares its exported metrics rather than
  retyping them. Sibling lists that look subtly unlike each other read as a bug, not a distinction.
  A row's name is `KettleType.rowTitle` in a list of things and `rowLabel` in a list of settings or
  details; `heading` is for headings (#490).
- Sheet screens: a `Destination` with `Presentation.Sheet` in `shell/nav/Route.kt`, and its branch in
  `RouteContent` (`ShellRoutes.kt`), which the `when` won't compile without. The shell presents it as
  its sheet, with the grabber and × drawn over the top by `SheetChrome`; the screen's content starts
  below that strip and adds no top padding of its own.
- **A place you go to is pushed; a task or a peek is a sheet** (#489). Settings, Exercise detail and
  Program detail are `Presentation.Pushed`: full screen over the bar, in the same back stack, with
  no chrome. Such a screen draws its own `BackRow` (`shell/nav/BackRow.kt`) first, which is the
  system's back and says only "Back", never the screen beneath. A new screen is a sheet unless it is
  somewhere rather than something to do.
- Theming is strict: `KettleTheme.colors`, `KettleType`, `Spacing.*`, `MaxContentWidth`. The
  session runner ignores the scheme: its field (`theme.json`'s `RunnerColors`) follows the step's clock. Colors, spacing and the type
  scale are edited in `constants/theme.json`, which native's `:theme` generates its Compose theme from;
  `constants/theme-notes.md` beside it holds the notes JSON can't.
- In-app forms write straight to the file and never pass through the schema, so they need their own
  validation (see `validateConfig` in `:shared`'s `ExerciseForm.kt`).
- **Weights are stored in kilograms, always.** The library is a file users export and share, so it
  can't change meaning with the reader's settings. `:shared`'s `Units.kt` is the only converter — never store a pound value, and never format a weight without
  going through `toDisplayWeight`. Anything editing a *stored* weight must also pass
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
  background is translucent. `constants/theme-notes.md` records the measured ratios. A control drawn with
  no fill takes `lineStrong` for its edge, held to 3:1; `border` is the decorative hairline.
- **No user-facing string in `:shared`.** It returns descriptors and the screens render them.
  Counts go through `pluralText` (never an `== 1` branch), and dates through `shell/i18n/`'s
  `LocaleFormats.kt` (never a hardcoded pattern or locale).
- **Never translate user data** — exercise, workout and program names, notes, and `ProgramWeek.day`
  come from the user's YAML and render verbatim. Key the English around them and interpolate the name.
- Adding a key means adding it to **all three** bundles — `en.json`, `pt.json` and `ja.json`. `pt`
  matches `en` key for key, plus a **`_zero` beside every `_one`** — CLDR files Portuguese 0 under
  `one`, which rendered "0 sessão" — and **`ja` carries only the `_other` form of a plural**, since
  Japanese has no separate singular category. `:strings` holds all three shapes against CLDR's
  plural categories (`BundleCoverage.kt`), so a missing or stray key fails the build instead of
  rendering quietly in English; the words themselves are still checked by hand. (No count is quoted
  here on purpose: the one that used to be went stale, and the bundles are the only honest answer.)
- **A string's arguments go in sorted order of their placeholder names** (code-point order, so
  lower camelCase reads alphabetically), a plural's `count` first, whatever their order in the
  sentence: `:strings` numbers them by name, not position, so rewording a sentence can't renumber its
  arguments under every call site (`AndroidStrings.kt`). `"Kettle {{version}} · {{note}}"` is called
  with `(note, version)`; the other order compiles and renders the two swapped.

Shipping a whole new language is a procedure of its own:
[`docs/adding-a-language.md`](docs/adding-a-language.md). Its iOS step arrives with M8: an
undeclared locale is unreachable on iOS with every test still green.

## Writing tests

- **Mock at our own boundary**, not at the platform's — assertions then read as "what got persisted"
  rather than "what got written". Native's screens take a `StoredFiles` (`FakeStoredFiles` in tests).
- **Prove a regression test fails against the bug it pins**, by reintroducing that bug. A test that
  passes either way is worthless, and this has caught more than one.
- **To test that a screen is translated, drive it in `pt`** (`@Config(qualifiers = "pt")` on a
  Robolectric test). An English-locale assertion *cannot* catch a hardcoded English string — the
  resource and the literal it returns render identically. Three screens have shipped with hardcoded
  strings for exactly this reason.
- Finish with `./gradlew ktlintFormat`; don't hand-align a new test file against the rest.

What tests can't reach — layout, animation, real audio and file writes — is verified by driving the
running app on a device or emulator: `scripts/native-dev.sh` puts the `.dev` shell on it, and
`scripts/route-check.sh` steers it through every route.

## Delegating

Mechanical, well-specified work — adding tests to an existing suite, repetitive renames, boilerplate —
should go to a cheaper subagent rather than being done inline. Point it at an existing suite to imitate
and give it the exact list of cases; that's most of the quality.

**Verify what comes back rather than trusting the report.** A subagent has reported pre-existing lint
damage that its own edits caused. Re-run `./gradlew build` yourself after any delegated change, and
skim the diff.

## Changing the YAML format

This one stays inline rather than moving to `docs/`, because the failure mode is not realising you're
in it: a renamed key looks like a local change and silently breaks the copies you didn't open.

A change to the format — a new field, a renamed key, a changed requirement, a new exercise type —
lands in one PR, in all of these:

- **`:shared`'s schema readers** (`SchemaReader.kt` and the `*Schema.kt` beside it), the format's
  behaviour, with a `conformance/` case for it: regenerate with
  `./gradlew :shared:jvmTest -PupdateConformance` (then `pnpm run format`) and read the golden diff.
- **`src/content/library-schema.json`**, the JSON Schema the assistant brief carries. It is
  hand-maintained, the declarative statement of the format. `LibrarySchemaFileTest` in `:shared`
  holds it to the importer: every `ok` corpus case validates, every `schemaMismatch` case is refused
  at its golden's paths, and every one-edit variant of `every-shape` is refused where `:shared`
  refuses it, except for the refinements it names, which the file doesn't state.
- **The three hand-maintained mirrors, and the public one is outside `docs/`:**

  1. `docs/authoring-exercises-yaml.md` — the authoring reference.
  2. `docs/product-plan.md` — its file-format section.
  3. **`site/format.html`** — the published reference on the landing site, which is the copy an
     outside author or an assistant actually reads. It is the easiest to forget precisely because it
     doesn't look like a doc and nothing in `src/` imports it.

The mirrors have tests of their own, and it's worth knowing exactly where they stop:

- `:repo-checks`' `SamplesTest` runs every `<pre data-validate="library">` block on the site, and
  every complete sample in `authoring-exercises-yaml.md` and `product-plan.md`, through `:shared`'s
  real `parseLibraryYaml`. A sample teaching a shape the importer refuses fails the build.
- Its `FormatMirrorsTest` parses the **type tables** in the markdown reference and in
  `site/format.html` and diffs the field names and optional flags against `library-schema.json`. So
  a renamed, invented or missing config field fails the build in both mirrors. The product plan's
  type table names fields in prose, so it is a mirror only by eye.
- **Ranges, units and prose are still unchecked.** `(>0)` versus `(≥0)`, "in kilograms", and every
  sentence around the tables are on you.

That split exists because the eyeball pass failed the first time it mattered: the first draft of the
published page shipped with five of seven type tables wrong (`emom` taking `rounds` instead of
`total_minutes`, `amrap` missing the required `time_cap_sec`, program weeks as `days[]` with
`overrides` as a mapping), all of it plausible enough to read as correct and every bit of it refused
on import. The first two of those are regression-tested — reintroducing either one fails
`FormatMirrorsTest`, which is how it was verified.

If you add a complete library sample to any page, mark it `data-validate="library"` so it joins the
checked set. Deliberate fragments — a bare `workouts:` list, a shape sketch with `{ ... }` — stay
unmarked, since they can't parse standalone.

The four `site/examples/*.yaml` libraries are a fourth copy of the format, but the only one that is
checked end to end rather than by eye (`SiteExamplesTest`) — a renamed key breaks them loudly.
Their prose is on `site/examples.html`, which isn't checked, same as the tables above.

## Docs

Every one of these opens with a banner saying what it is and whether it's live; the one-liners here
are the index, the banner is the contract.

- `docs/decisions.md` — **the decision log.** Why things are the way they are, for reasoning that
  doesn't fit in one commit. Cited from `AGENTS.md` and from source files; this is the file they
  mean.
- `docs/open-work.md` — the backlog, and only the backlog.
- `docs/history.md` — write-ups of shipped work, split out so the backlog stays a backlog.
- `CHANGELOG.md` — **at the repo root**, not in `docs/`. What shipped per release, plus the Play
  release notes that went out with it. The `## Unreleased` section is where a shipped feature is
  written up for *users*; the copy lives beside what it describes rather than in `store/README.md`,
  which keeps only Play's rules. `:repo-checks`' `StoreCopyTest` asserts every block against its
  character limit, so an over-length note fails the build rather than the upload.
- `docs/product-plan.md` — the data model, file formats and roadmap.
- `docs/authoring-exercises-yaml.md` — the YAML reference, kept exact against `library-schema.json`.
- `docs/adding-a-language.md` — the procedure for shipping a new UI language.
- `docs/agents/` — per-repo setup the engineering skills read: issue tracker, triage labels, and
  where the glossary and decision log live. Indexed by the "Agent skills" section at the end.
- `docs/building-android.md` — building the Play bundle on GitHub Actions rather than on EAS, and
  the keystore setup it needs first.
- `docs/testing-a11y-i18n-plan.md` — executed; kept for its rationale, not as a backlog.
- `docs/watch-remote-plan.md` — **not executed.** Driving a running session from a Wear OS wrist via
  the notification shade, with no watch app and no data on the watch.
- `docs/native-migration-plan.md` — **M0 executed (go); M1 landed; M2 shipped (0.11.2); M3 shipped
  (0.12.0); M4 shipped (0.13.0); M5 done (0.14.0); M6 done (0.15.0); M7 ticketed (map #335); M8's UI
  decided (Compose Multiplatform).** Moving to Kotlin/Compose (then iOS) inside the shipping
  app: the native shell hosted the not-yet-migrated RN screens via `expo-brownfield` until M7's
  first PR removed RN (#357), and each milestone ships to Play and deletes RN code. Its M0 spike
  report holds the brownfield build facts (AAR scopes, plugin resources, Windows CLI), history now.
- `docs/ios-plan.md` — **partly executed.** Shipping to the App Store: what already works unchanged,
  the six code changes that don't, and the background-cue and iCloud decisions. Five of those six
  have landed, plus the notification half of the background cue; the sixth is a form in App Store
  Connect. Everything gated on a Mac, a device or the $99 is untouched, and the banner in the file
  says which is which. Names the claims in this file, the
  decision log and the README that stop being true the day it ships.
- `docs/import-prominence-plan.md` — executed; kept for its rationale. Closing the gap between a
  listing that led with the file-you-own wedge and an app that reached it through a header link and a
  Settings row. Four copy-and-a-link slices and one translation, plus the list of louder options
  refused and why — which is the half that still applies.
- `docs/layout-review-plan.md` — executed; kept for its rationale. Acting on an external design
  review of every screen. Half of it is the list of the review's claims that are *false*, checked
  against the code, plus the four places the plan itself was wrong on device — both so the same
  arguments aren't made again from the same screenshots.
- `docs/timed-hold-auto-end-plan.md` — executed; kept for its rationale, not as a backlog. Ending a
  `timed_hold` at the top of its range, and making `hold_sec_min` optional.
- `docs/backup-folder-plan.md` — executed; kept for its rationale, and for the device-only checks it
  leaves open. Writing the library and the log into a folder the user picks once.
- `docs/program-rest-days-plan.md` — executed; kept for its rationale. A program week that says
  "today you rest" (`rest_day: true`, no `workout`), and how it clears itself off the home screen.
- `docs/exercise-images-plan.md` — executed; kept for its rationale, not as a backlog. A bundled line
  drawing per *seeded* exercise, keyed by id, with no format change and no storage. Shipped **vector**
  rather than the raster the plan assumed, which is why the alpha and `@3x` half of it reads as moot.
  The rest is why the obvious version — a user-supplied photo per exercise — was costed and cut,
  including the two `expo-file-system` picker facts that survey turned up.
- `docs/stats-redesign-plan.md` — executed; kept for its rationale. Reworking the Stats screen: a
  four-week training calendar in place of the weekly bars, Getting stronger narrowed to what actually
  moved (fixed-length holds out, flat rows folded into one), records marked inline, and the all-time
  totals dropped. Names what is left for later, including the per-exercise chart modal, and the two
  checks still open: the site screenshot and a large-text pass on a device.
- `docs/up-next-plan.md` — executed; kept for its rationale, and for the device checks it leaves open.
  The runner's "Coming up" sheet: tapping the workout name
  opens a read-only timeline of what's left, with the circuit round you're in spelled out and the rest
  collapsed. Names what it deliberately doesn't do (circuit names are the follow-up, and a format
  change), plus a bug it found outside its scope, RN's runner stranding a session left by back or
  swipe, which went with that runner (#308).

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
  at all. The YAML samples no longer rely on this: `:repo-checks`' `SamplesTest` runs every complete
  library sample in `authoring-exercises-yaml.md` and the product plan through the real `parseLibraryYaml`, so
  a doc that teaches a shape the importer refuses fails the build. Prose is still on you.

And two about what doesn't get written down at all:

- **The README doesn't get a paragraph when a feature ships.** It is a router — pitch, install,
  how to run it, where everything else lives — and every audience it serves is one click away. The
  first rule above covers `docs/`, which left the README absorbing the same pressure unguarded: it
  had grown a 65-line feature narrative that duplicated `site/index.html` for users and this file for
  contributors, in one register that suited neither. A shipped feature earns at most an edit to an
  existing bullet. If it needs prose, it needs the site.
- **Never quote a hand-maintained count.** Test totals, key counts, file counts: they are wrong
  within two PRs, nothing fails when they are, and they were the specific claims the drift audit
  above caught. Name the source instead — `./gradlew build`, the locale bundles.

Keep open-work entries short. If the list starts wanting states and assignees, move it to GitHub
issues (`origin` and `gh` are both available); until then the file is deliberately enough.

## Agent skills

### Issue tracker

Issues live in GitHub Issues on `celsaad/kettle`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default roles, each label named after its role (`needs-triage`, `needs-info`,
`ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: a root `GLOSSARY.md` (created lazily) and `docs/decisions.md` in place of `docs/adr/`.
See `docs/agents/domain.md`.
