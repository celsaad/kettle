import { currentLocale } from '@/i18n';

/**
 * Locale-aware date formatting. Every one of these replaced a hardcoded `'en-US'`, which meant a user
 * in São Paulo or Berlin saw US month/day conventions in an app that was otherwise theirs.
 *
 * `formatDuration` is hand-rolled rather than `Intl.DurationFormat`, which Hermes didn't have when this
 * was written.
 */

export function formatMonthDay(date: Date): string {
  return date.toLocaleDateString(currentLocale(), { month: 'short', day: 'numeric' });
}
