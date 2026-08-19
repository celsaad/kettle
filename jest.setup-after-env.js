// Global per-test teardown, so no suite can leak state into the next one.
//
// Mock lifecycle is handled declaratively by `clearMocks` + `restoreMocks` in the jest config. Timers
// need this hook: a suite that calls jest.useFakeTimers() and doesn't hand them back leaves every
// later suite on a frozen clock, and the resulting failures surface far from their cause — the
// runner tests hit exactly that, as opaque AggregateErrors in tests that passed in isolation.
afterEach(() => {
  jest.useRealTimers();
});

// The locale needs the same isolation — a suite that switches to pt and doesn't switch back would
// leave every later suite asserting English against Portuguese output — but it has to be reset
// *before* each test rather than after.
//
// Resetting in afterEach ran while the test's tree was still mounted (RNTL's own cleanup afterEach
// had not run yet), and `changeLanguage` re-renders every mounted `useTranslation()` component. That
// re-render happens outside `act`, which React reports as an unwrapped update — a wall of warnings
// pointing at this file rather than at any test. In beforeEach nothing is mounted yet, so the same
// guarantee costs nothing.
beforeEach(async () => {
  if (i18next.isInitialized && i18next.language !== 'en') await i18next.changeLanguage('en');
});

// Display units get the same treatment, and for the same reason: zustand stores are module singletons
// that outlive a test, so one suite switching to imperial would leave every later suite reading "220.5
// lb" where it asserted "100". Metric is the store's own pre-hydration value, so this resets to the
// state a suite that never touches units would have had anyway.
//
// Reset to `idle` too, not just to metric — `hydrate()` returns early when it finds itself mid-load,
// so a suite that left the status behind could make the next one's hydrate silently do nothing.
//
// **`require` inside the hook, deliberately.** Requiring the store at this file's top level loads it
// before the test file is evaluated, so it binds the *real* `@/storage/preferences-file` and every
// suite's `jest.mock` of that module silently misses — which is exactly how the first version of this
// broke three suites. By the time a hook runs, the test file's hoisted mocks are registered, so this
// resolves to the same instance the test itself holds.
beforeEach(() => {
  const store = require('./src/state/preferences-store').usePreferencesStore;
  // A suite that mocks the store module outright has no setState, and nothing to reset either.
  //
  // **Every field, not just the ones a test is likely to care about.** A partial reset leaves the rest
  // `undefined` between suites, which is a shape no running app has — and `undefined` reads as false,
  // so a suite that never touches preferences gets the *opposite* of any boolean that defaults on. This
  // had already drifted once: `backupFolderUri` was missing here from the day it shipped. Adding a
  // preference means adding it here, and `Preferences` is the list.
  store?.setState?.({
    status: 'idle',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      restDayReminder: false,
      sessionSounds: true,
      backupFolderUri: null,
    },
  });
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
// `pt` and `ja` are loaded alongside `en` but not selected. That's what makes it possible to test
// that a screen is *genuinely* translated: an English-locale test cannot tell `t('x.y')` apart from
// the hardcoded English literal it returns, so only switching locale distinguishes them. Three
// screens have shipped with hardcoded strings that no English assertion could have caught.
//
// Every shipped language belongs here, not just the one the tests happen to use: a bundle absent from
// this map falls back to English, so a suite that switched to it would assert English against English
// and pass no matter what the bundle says.
require('intl-pluralrules');
const i18next = require('i18next');
const { initReactI18next } = require('react-i18next');
const en = require('./src/i18n/locales/en.json');
const pt = require('./src/i18n/locales/pt.json');
const ja = require('./src/i18n/locales/ja.json');

if (!i18next.isInitialized) {
  i18next.use(initReactI18next).init({
    resources: { en: { translation: en }, pt: { translation: pt }, ja: { translation: ja } },
    lng: 'en',
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
    returnNull: false,
  });
}
