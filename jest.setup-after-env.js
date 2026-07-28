// Global per-test teardown, so no suite can leak state into the next one.
//
// Mock lifecycle is handled declaratively by `clearMocks` + `restoreMocks` in the jest config. Timers
// need this hook: a suite that calls jest.useFakeTimers() and doesn't hand them back leaves every
// later suite on a frozen clock, and the resulting failures surface far from their cause — the
// runner tests hit exactly that, as opaque AggregateErrors in tests that passed in isolation.
afterEach(() => {
  jest.useRealTimers();
  // Same reasoning for the locale: a suite that switches to pt and doesn't switch back would leave
  // every later suite asserting English against Portuguese output.
  if (i18next.isInitialized && i18next.language !== 'en') i18next.changeLanguage('en');
});

// `domain/format.ts` renders through i18next, so it needs an initialised instance to return anything
// but raw key paths. Initialised here rather than by importing `@/i18n`, which would pull
// `expo-localization` into every test that touches formatting; this configures the same singleton
// with the English resources, so assertions read as plain English.
//
// `initReactI18next` matters as much as the resources: without it, a component's `useTranslation()`
// finds no bound instance and renders the key path itself — "session.countdown.getReady" instead of
// "GET READY". Screen tests appeared to work regardless, but only where something in the import
// graph happened to reach `@/i18n` (which registers the plugin as a side effect of loading), so a
// screen's assertions passed or failed on an unrelated module's imports. Registering it here makes
// that independent of what a screen happens to pull in.
//
// `pt` is loaded alongside `en` but not selected. It's what makes it possible to test that a screen
// is *genuinely* translated: an English-locale test cannot tell `t('x.y')` apart from the hardcoded
// English literal it returns, so only switching locale distinguishes them. Three screens have shipped
// with hardcoded strings that no English assertion could have caught.
require('intl-pluralrules');
const i18next = require('i18next');
const { initReactI18next } = require('react-i18next');
const en = require('./src/i18n/locales/en.json');
const pt = require('./src/i18n/locales/pt.json');

if (!i18next.isInitialized) {
  i18next.use(initReactI18next).init({
    resources: { en: { translation: en }, pt: { translation: pt } },
    lng: 'en',
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
    returnNull: false,
  });
}
