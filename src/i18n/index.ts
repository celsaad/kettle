// MUST be the first import in this file, and this module must be imported before anything renders.
//
// i18next v24+ routes pluralisation through Intl.PluralRules and has no fallback, and **Hermes does
// not implement Intl.PluralRules** — it ships Collator, DateTimeFormat and NumberFormat, but not this.
// So without the polyfill the app works in tests and on web (both V8) and throws on device, which is
// the worst possible failure shape. The polyfill no-ops where the API already exists.
import 'intl-pluralrules';

import { getLocales } from 'expo-localization';
import i18next, { use } from 'i18next';
import { initReactI18next } from 'react-i18next';

import type { UnitSystem } from '@/domain/units';
import en from '@/i18n/locales/en.json';
import ja from '@/i18n/locales/ja.json';
import pt from '@/i18n/locales/pt.json';

/**
 * Keyed by language, not by region: `pt` covers pt-BR and pt-PT, and `getLocales()` reports
 * `languageCode` separately from `regionCode` for exactly this. Region still matters for *formatting*
 * — dates, numbers and the first day of the week come from the device locale via Intl, not from here,
 * so a pt-BR user gets a Sunday-start week while pt-PT gets Monday, both reading the same strings.
 *
 * **A bundle carries only the plural categories its own language has.** `ja` has one — CLDR gives it
 * `other` and nothing else — so `ja.json` holds `x_other` and no `x_one`. Written by analogy with
 * `en`/`pt` it would carry `_one` keys that never resolve and never get reviewed, which is the shape
 * this note exists to prevent.
 *
 * **`pt` carries a `_zero` beside every `_one`.** CLDR files Portuguese 0 under `one`, so a count of
 * zero rendered "0 sessão"; Brazilian Portuguese says "0 sessões". i18next consults a `_zero` key before
 * the plural rule whenever `count` is 0, so the fix is in the bundle rather than a patch to i18next's
 * resolver, and `locale-bundles.test.ts` requires one for every `_one`. JSON takes no comments, so all
 * of this has to live here.
 */
export const resources = {
  en: { translation: en },
  pt: { translation: pt },
  ja: { translation: ja },
} as const;

/**
 * The device's preferred language, narrowed to one we actually ship. `getLocales()` is ordered by the
 * user's preference, so the first supported match wins rather than just the first entry.
 */
function deviceLanguage(): string {
  const supported = Object.keys(resources);
  for (const locale of getLocales()) {
    if (locale.languageCode && supported.includes(locale.languageCode)) return locale.languageCode;
  }
  return 'en';
}

// i18next's named exports (`use`, `t`, `changeLanguage`, …) are this same default instance's own
// methods, bound to it at module load — so calling `use(…)` here configures exactly the singleton the
// default import below reads, and the domain layer's `import { t } from 'i18next'` calls the instance
// this line initialises. Only `language` has no named counterpart, hence the default import.
use(initReactI18next).init({
  resources,
  lng: deviceLanguage(),
  fallbackLng: 'en',
  // RN has no <script> escaping to worry about, and double-escaping mangles the app's own text.
  interpolation: { escapeValue: false },
  returnNull: false,
});

/**
 * The unit system to start from when the user has never chosen one, per `expo-localization`'s
 * `measurementSystem` (`metric` | `us` | `uk` | null).
 *
 * **`uk` maps to metric deliberately.** It's nominally an imperial system, and it is the one the UK
 * uses for body weight — but British gyms load kilo plates and sell kilo dumbbells, so handing a
 * British user pounds would put the app in a different unit from the equipment in front of them.
 * Anything unreported falls back to metric, which is what the stored data already is.
 */
export function deviceUnitSystem(): UnitSystem {
  return getLocales()[0]?.measurementSystem === 'us' ? 'imperial' : 'metric';
}

/** The active UI language tag, for Intl formatters. */
export function currentLocale(): string {
  return i18next.language || 'en';
}
