import { currentLocale } from '@/i18n';

/**
 * Locale-aware date formatting. Every one of these replaced a hardcoded `'en-US'`, which meant a user
 * in São Paulo or Berlin saw US month/day conventions in an app that was otherwise theirs.
 *
 * Hermes implements `Intl.DateTimeFormat` natively (via ICU on Android and NSLocale on iOS), so these
 * need no polyfill — unlike `Intl.PluralRules`, see `i18n/index.ts`.
 * `Intl.DurationFormat` is *not* available, which is why `formatDuration` is hand-rolled.
 */

export function formatMonthDay(date: Date): string {
  return date.toLocaleDateString(currentLocale(), { month: 'short', day: 'numeric' });
}
