// MUST be the first import in this file, and this module must be imported before anything renders.
//
// i18next v24+ routes pluralisation through Intl.PluralRules and has no fallback, and **Hermes does
// not implement Intl.PluralRules** — it ships Collator, DateTimeFormat and NumberFormat, but not this.
// So without the polyfill the app works in tests and on web (both V8) and throws on device, which is
// the worst possible failure shape. The polyfill no-ops where the API already exists.
import 'intl-pluralrules';

import { getCalendars, getLocales } from 'expo-localization';
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
 * this note exists to prevent. JSON takes no comments, so it has to live here.
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
 * Which weekday the user's calendar starts on, 1 = Sunday through 7 = Saturday per `expo-localization`.
 *
 * This exists because `startOfWeek` hardcoded Monday, which is right for most of Europe and wrong for
 * the US, Canada, Japan and much of Latin America — the "this week" stat silently measured a different
 * window than the user's own calendar. Falls back to Monday when the platform doesn't report one.
 */
export function firstWeekdayIndex(): number {
  const reported = getCalendars()[0]?.firstWeekday;
  // expo-localization uses 1 = Sunday; JS Date.getDay() uses 0 = Sunday. Convert here so callers work
  // in getDay()'s terms and never have to remember which convention they're holding.
  return typeof reported === 'number' ? (reported - 1 + 7) % 7 : 1;
}

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
