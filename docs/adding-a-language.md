# Adding a language

The standing a11y/i18n house rules live in `AGENTS.md`; this is the one-off procedure for shipping a
new UI language.

Four places on Android, and only the first two fail loudly if you miss them. Verified against the
code rather than recalled, and re-checked when `ja` went in as the third. Changes since: step 1's
parity is now machine-checked rather than eyeballed, and step 2 fails the build too since #351; step
4 turned out to be both wrong and skipped (the listing copy does live in this repo, and `ja` shipped
in the app without it); and at M7 (#357) `app.json`'s iOS locale list went, an M8 note below now.

1. **`locales/<code>.json`** — the new bundle, at full parity with `en.json`.
   `:strings` (`BundleCoverage.kt`) holds that parity in both directions, each plural against the
   language's own CLDR categories, for every bundle in the directory, so a key you missed and a key
   you invented both fail the build. If CLDR files 0 under `one` and that reads wrong in the
   language, as it does in Portuguese, add it to `ZERO_READS_PLURAL` there. Name it by language,
   not region (`pt` serves pt-BR and pt-PT); region still drives dates, numbers and first-weekday
   through the device's locale, independently of this. What it can't check
   is whether the *strings* are right, or whether a screen renders one at all — for that, drive the
   screen in the new locale (`AGENTS.md`, "Writing tests").
2. **`content/seed/<lang>.json`** — the starter library's string table, plus one per pack in
   `content/packs/<id>/`. A language with no table falls back to English per string, so a new
   user would get translated chrome around an English library. This used to be the one that got
   forgotten; `:app`'s `ShippedLibrariesTest` now fails until every shipped library has a table for
   every UI language, and holds each table to its library in both directions.
3. **`README.md`** — the sentence naming which languages the UI and the seed library ship in.
4. **The Play listing.** The *copy* is in this repo — `store/README.md` carries a short and a full
   description per language, and `:repo-checks`' `StoreCopyTest` checks each block against Play's
   limit and against the length its own heading declares, so a new language has to be named in that
   test or its copy is unchecked. The listing copy also names which languages the app ships in, and the tip
   jar products need the new language too, keyed to the same tier labels the app renders. **Adding
   the language in the Play Console is a separate act**, and until it is done a release note tagged
   with the new locale is rejected on upload rather than ignored.

**On iOS, from M8, a fifth:** the app's `CFBundleLocalizations`. iOS filters
`Locale.preferredLanguages` against what the bundle declares, so an undeclared language is reported
by no device, everything renders in the fallback, and a complete, parity-checked bundle sits in the
binary unreachable. Nothing throws, and from every angle except an iPhone the language looks shipped.
Until M7 (#357) the RN app declared it through `app.json` (`supportedLocales.ios`), with a test
diffing that list against the bundles; M8's build should hold the same check. `docs/ios-plan.md`
§2.5 has the mechanism.

One thing that is *not* on the list, checked rather than assumed: nothing about date, number,
weekday or unit formatting needs touching — all of it reads the device locale, through
`shell/i18n/LocaleFormats.kt`.

Nor is the native app's copy: `:strings` generates its `values-<code>/strings.xml` from whatever
bundles step 1 left in `locales`, on every build, and `GeneratedStringsTest` round-trips
whatever bundles it finds there. Only the `@LocalePreviews` annotation in `:app` and its test name
languages by hand, and missing them costs a Studio preview, not a translation.

Three traps before picking one:

- **Write the plural keys the language actually has, not the ones English has.** Android selects a
  plural by the language's CLDR categories, so a bundle's plural forms are its own language's.
  Japanese has exactly one — `ja.json` carries `x_other` and no `x_one`, and a `_one` written by
  analogy with `en`/`pt` would never render and never be reviewed. `:strings` refuses a category the
  language has no rule for (step 1). It does *not* demand every category CLDR lists: `pt` has a
  `many` that applies from a million upward, which no set, rep or session count reaches, so those
  keys are permitted rather than required. A language with a `few` in the app's real range is a
  different case — write it. And check what the rule does with **zero**: `pt` files 0 under `one`,
  which rendered "0 sessão", so `pt.json` carries a `_zero` beside every `_one` (`pluralText` reads
  `_zero` first whenever `count` is 0) and `ZERO_READS_PLURAL` in `:strings` requires them. French
  files 0 under `one` too and means it, so this is decided per language, not derived from CLDR.
- **An RTL language is a project, not a bundle.** RTL was deliberately deferred until a real RTL
  locale existed (see `testing-a11y-i18n-plan.md`). Compose flips rows and `start`/`end` padding
  with the layout direction, so what it defers is the Canvas-drawn glyphs, which don't flip, plus
  any arrow character baked into copy (one is left, `import.emptyYamlLink`'s "→"). Adding Arabic or
  Hebrew means doing that first; `open-work.md` has what is known.
- **A seed table's day labels must stay distinct within a week.** The app finds its place in a
  program by week and `day`, and runs a week's days in the order the file writes them (`rotation`
  in `NextUp.kt`), so a table that gives two days one label folds two slots into one.
  `ShippedLibrariesTest` catches it, and this is why it runs every structural invariant against
  every language. (RN's Home sorted a week's days by label, so until #235 they also had to *sort*
  in training order; that constraint is gone.)
