# Adding a language

The standing a11y/i18n house rules live in `AGENTS.md`; this is the one-off procedure for shipping a
new UI language.

Six places, and only the first two are load-bearing enough to fail loudly if you miss them. Verified
against the code rather than recalled, and re-checked when `ja` went in as the third. Two changes
since: step 1's parity is now machine-checked rather than eyeballed, and step 6 turned out to be both
wrong and skipped — the listing copy does live in this repo, and `ja` shipped in the app without it.

1. **`src/i18n/locales/<code>.json`** — the new bundle, at full parity with `en.json`.
   `i18n/__tests__/locale-bundles.test.ts` holds that parity in both directions against every language
   in `resources`, so a key you missed and a key you invented both fail the suite. What it can't check
   is whether the *strings* are right, or whether a screen renders one at all — for that, drive the
   screen in the new locale (`AGENTS.md`, "Writing tests").
2. **`src/i18n/index.ts`** — import it into `resources`. That map is the *only* list of supported
   languages: `deviceLanguage()` narrows the device's preferences against `Object.keys(resources)`,
   so nothing else anywhere selects a language. Key it by language, not region (`pt` serves pt-BR and
   pt-PT); region still drives dates, numbers and first-weekday through Intl, independently of this.
3. **`src/storage/seed-translations.ts`** — a `SeedTranslation` for the starter library. **This is the
   one that gets forgotten**, because nothing fails: a language with no table falls back to English
   per string, so a new user gets translated chrome around an English library and the suite stays
   green. `seed-library.test.ts` only checks languages *already in* the table (it enforces parity
   within one, in both directions, not that every UI language has one).
4. **`jest.setup-after-env.js`** — the harness inits i18next with its own explicit `{ en, pt, ja }`
   resources, deliberately not importing `@/i18n` (which would pull `expo-localization` into every
   test that touches formatting). A new language isn't visible to tests until it's added here too —
   and a suite that switches to a missing one asserts English against English and passes regardless.
5. **`README.md`** — the sentence naming which languages the UI and the seed library ship in.
6. **The Play listing.** The *copy* is in this repo — `store/README.md` carries a short and a full
   description per language, and `store-copy.test.ts` checks each block against Play's limit and
   against the length its own heading declares, so a new language has to be named in that test or
   its copy is unchecked. The listing copy also names which languages the app ships in, and the tip
   jar products need the new language too, keyed to the same tier labels the app renders. **Adding
   the language in the Play Console is a separate act**, and until it is done a release note tagged
   with the new locale is rejected on upload rather than ignored.

Two things that are *not* on the list, checked rather than assumed: `app.json` has no per-locale
config (the `expo-localization` plugin is language-agnostic), and nothing about date, number, weekday
or unit formatting needs touching — all of it reads the device locale through Intl in `i18n/format.ts`.

Three traps before picking one:

- **Write the plural keys the language actually has, not the ones English has.** i18next resolves
  `count` through `Intl.PluralRules`, so a bundle's plural forms are its own language's CLDR
  categories. Japanese has exactly one — `ja.json` carries `x_other` and no `x_one`, and a `_one`
  written by analogy with `en`/`pt` would never render and never be reviewed. The bundle test above
  refuses a category the language has no rule for. It does *not* demand every category CLDR lists:
  `pt` has a `many` that applies from a million upward, which no set, rep or session count reaches, so
  those keys are permitted rather than required. A language with a `few` in the app's real range is a
  different case — write it.
- **An RTL language is a project, not a bundle.** The `I18nManager` plumbing was deliberately deferred
  until a real RTL locale existed (see `testing-a11y-i18n-plan.md`), and the work it defers is the
  CSS-triangle glyphs, which are drawn and don't flip, plus the arrow characters baked into copy
  (`'Skip rest →'`, `'Done set →'`). Adding Arabic or Hebrew means doing that first.
- **A seed table's day labels must sort in training order.** `nextWeekAfter` walks a multi-day week by
  `day.localeCompare`, so `Dia 1`/`Dia 2`/`Dia 3` and `1日目`/`2日目`/`3日目` work and weekday names do
  not — the per-language seed test catches it, and this is why it runs every structural invariant
  against every language.
