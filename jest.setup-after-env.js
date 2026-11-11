// Global per-test teardown, so no suite can leak state into the next one.
//
// Mock lifecycle is handled declaratively by `clearMocks` + `restoreMocks` in the jest config. Timers
// need this hook: a suite that calls jest.useFakeTimers() and doesn't hand them back leaves every
// later suite on a frozen clock, and the resulting failures surface far from their cause — the
// runner tests hit exactly that, as opaque AggregateErrors in tests that passed in isolation.
afterEach(() => {
  jest.useRealTimers();
});

// The locale needs the same isolation: a suite that switches to pt and doesn't switch back would
// leave every later suite asserting English against Portuguese output.
beforeEach(async () => {
  if (i18next.isInitialized && i18next.language !== 'en') await i18next.changeLanguage('en');
});

// `domain/format.ts` and `domain/assistant-brief.ts` render through i18next, so it needs an
// initialised instance to return anything but raw key paths. This configures the singleton with
// `@/i18n`'s bundles, in English, so assertions read as plain English.
//
// `pt` and `ja` are loaded alongside `en` but not selected. That's what makes it possible to test
// that a string is *genuinely* translated: an English-locale test cannot tell `t('x.y')` apart from
// the hardcoded English literal it returns, so only switching locale distinguishes them.
//
// Every shipped language belongs here, not just the one the tests happen to use: a bundle absent from
// this map falls back to English, so a suite that switched to it would assert English against English
// and pass no matter what the bundle says. Importing `resources` rather than listing the files keeps
// that automatic.
const i18next = require('i18next');
const { resources } = require('./src/i18n');

if (!i18next.isInitialized) {
  i18next.init({
    resources,
    lng: 'en',
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
    returnNull: false,
  });
}
