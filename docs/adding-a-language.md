# Adding a language

The standing a11y/i18n house rules live in `AGENTS.md`; this is the one-off procedure for shipping a
new UI language.

Five places on Android, and only the first two fail loudly if you miss them. Verified against the
code rather than recalled, and re-checked when `ja` went in as the third. Changes since: step 1's
parity is now machine-checked rather than eyeballed; step 5 turned out to be both wrong and skipped
(the listing copy does live in this repo, and `ja` shipped in the app without it); and at M7 (#357)
two places went: `app.json`'s iOS locale list, which is an M8 note below now, and the jest setup's
own list of bundles, which reads `resources` since.

1. **`src/i18n/locales/<code>.json`** — the new bundle, at full parity with `en.json`.
   `i18n/__tests__/locale-bundles.test.ts` holds that parity in both directions against every language
   in `resources`, so a key you missed and a key you invented both fail the suite. What it can't check
   is whether the *strings* are right, or whether a screen renders one at all — for that, drive the
   screen in the new locale (`AGENTS.md`, "Writing tests").
2. **`src/i18n/index.ts`** — import it into `resources`. That map is the list the bundle test and the
   jest setup read, so a bundle left out of it is checked by nothing and invisible to every test. Key
   it by language, not region (`pt` serves pt-BR and pt-PT); region still drives dates, numbers and
   first-weekday through Intl, independently of this.
3. **`src/content/seed/<lang>.json`** — the starter library's string table, imported in
   `seed-library.ts`, plus one per pack in `src/content/packs/<id>/`, imported in `content-packs.ts`.
   **This is the one that gets forgotten**, because nothing fails: a language with no table falls back
   to English per string, so a new user gets translated chrome around an English library and the suite
   stays green. `seed-library.test.ts` and `content-packs.test.ts` only check languages *already*
   imported (they enforce parity within one, in both directions, and that every table in a directory
   is imported, not that every UI language has one).
4. **`README.md`** — the sentence naming which languages the UI and the seed library ship in.
5. **The Play listing.** The *copy* is in this repo — `store/README.md` carries a short and a full
   description per language, and `store-copy.test.ts` checks each block against Play's limit and
   against the length its own heading declares, so a new language has to be named in that test or
   its copy is unchecked. The listing copy also names which languages the app ships in, and the tip
   jar products need the new language too, keyed to the same tier labels the app renders. **Adding
   the language in the Play Console is a separate act**, and until it is done a release note tagged
   with the new locale is rejected on upload rather than ignored.

**On iOS, from M8, a sixth:** the app's `CFBundleLocalizations`. iOS filters
`Locale.preferredLanguages` against what the bundle declares, so an undeclared language is reported
by no device, everything renders in the fallback, and a complete, parity-checked bundle sits in the
binary unreachable. Nothing throws, and from every angle except an iPhone the language looks shipped.
The RN app declared it through `app.json` (`supportedLocales.ios`), with a test diffing that list
against `resources`; M8's build should hold the same check. `docs/ios-plan.md` §2.5 has the
mechanism.

One thing that is *not* on the list, checked rather than assumed: nothing about date, number,
weekday or unit formatting needs touching — all of it reads the device locale through Intl in
`i18n/format.ts`, and native's through the platform locale.

Nor is the native app's copy: `:strings` generates its `values-<code>/strings.xml` from whatever
bundles step 1 left in `src/i18n/locales`, on every build, and `GeneratedStringsTest` round-trips
whatever bundles it finds there. Only the `@LocalePreviews` annotation in `:app` and its test name
languages by hand, and missing them costs a Studio preview, not a translation.

Three traps before picking one:

- **Write the plural keys the language actually has, not the ones English has.** i18next resolves
  `count` through `Intl.PluralRules`, so a bundle's plural forms are its own language's CLDR
  categories. Japanese has exactly one — `ja.json` carries `x_other` and no `x_one`, and a `_one`
  written by analogy with `en`/`pt` would never render and never be reviewed. The bundle test above
  refuses a category the language has no rule for. It does *not* demand every category CLDR lists:
  `pt` has a `many` that applies from a million upward, which no set, rep or session count reaches, so
  those keys are permitted rather than required. A language with a `few` in the app's real range is a
  different case — write it. And check what the rule does with **zero**: `pt` files 0 under `one`,
  which rendered "0 sessão", so `pt.json` carries a `_zero` beside every `_one` (i18next reads `_zero`
  first whenever `count` is 0) and the bundle test's `ZERO_READS_PLURAL` list requires them. French
  files 0 under `one` too and means it, so this is decided per language, not derived from CLDR.
- **An RTL language is a project, not a bundle.** The `I18nManager` plumbing was deliberately deferred
  until a real RTL locale existed (see `testing-a11y-i18n-plan.md`), and the work it defers is the
  CSS-triangle glyphs, which are drawn and don't flip, plus the arrow characters baked into copy
  (`'Skip rest →'`, `'Done set →'`). Adding Arabic or Hebrew means doing that first.
- **A seed table's day labels must sort in training order.** `nextWeekAfter` walks a multi-day week by
  `day.localeCompare`, so `Dia 1`/`Dia 2`/`Dia 3` and `1日目`/`2日目`/`3日目` work and weekday names do
  not — the per-language seed test catches it, and this is why it runs every structural invariant
  against every language.
