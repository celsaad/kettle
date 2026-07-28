import { currentLocale } from '@/i18n';

/**
 * Locale-aware date/number formatting. Every one of these replaced a hardcoded `'en-US'`, which meant
 * a user in São Paulo or Berlin saw US month/day conventions in an app that was otherwise theirs.
 *
 * Hermes implements `Intl.DateTimeFormat` and `Intl.NumberFormat` natively (via ICU on Android and
 * NSLocale on iOS), so these need no polyfill — unlike `Intl.PluralRules`, see `i18n/index.ts`.
 * `Intl.DurationFormat` is *not* available, which is why `formatDuration` is hand-rolled.
 */

export function formatWeekday(date: Date, style: 'short' | 'long' = 'short'): string {
  return date.toLocaleDateString(currentLocale(), { weekday: style });
}

export function formatMonthDay(date: Date): string {
  return date.toLocaleDateString(currentLocale(), { month: 'short', day: 'numeric' });
}

export function formatFullDate(date: Date): string {
  return date.toLocaleDateString(currentLocale(), { weekday: 'long', month: 'short', day: 'numeric' });
}

/**
 * The short month label on a history card's date badge. Uppercasing is done with the locale rather
 * than `toUpperCase()`, which is wrong for Turkish (i → İ, not I) and pointless for scripts without
 * case — and some locales render the month as a number, which uppercases to itself harmlessly.
 */
export function formatMonthBadge(date: Date): string {
  return date.toLocaleDateString(currentLocale(), { month: 'short' }).toLocaleUpperCase(currentLocale());
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat(currentLocale()).format(value);
}
