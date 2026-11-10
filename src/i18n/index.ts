import i18next from 'i18next';

import en from '@/i18n/locales/en.json';
import ja from '@/i18n/locales/ja.json';
import pt from '@/i18n/locales/pt.json';

/**
 * The bundles, keyed by language, not by region: `pt` covers pt-BR and pt-PT. Region still matters
 * for *formatting*, since dates, numbers and the first day of the week come from the locale via Intl,
 * not from here, so a pt-BR reader gets a Sunday-start week while pt-PT gets Monday, both reading the
 * same strings. `jest.setup-after-env.js` initialises i18next with them; the app's own strings are
 * native's, generated from the same files by `:strings`.
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

/** The active UI language tag, for Intl formatters. */
export function currentLocale(): string {
  return i18next.language || 'en';
}
