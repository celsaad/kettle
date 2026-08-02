# Adding a language

The standing a11y/i18n house rules live in `AGENTS.md`; this is the one-off procedure for shipping a
new UI language.

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
  (`'Skip rest →'`, `'Done set →'`). Adding Arabic or Hebrew means doing that first.
- **A seed table's day labels must sort in training order.** `nextWeekAfter` walks a multi-day week by
  `day.localeCompare`, so `Dia 1`/`Dia 2`/`Dia 3` works and weekday names do not — the per-language
  seed test catches it, and this is why it runs every structural invariant against every language.
