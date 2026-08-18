import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMonthDay } from '@/i18n/format';
import type { WeekTally } from '@/state/selectors/history-stats';

/**
 * The plot area's height. Fixed on purpose, unlike every touch target in this codebase: this is
 * decorative geometry with no text in it, so it has nothing to clip at large accessibility text sizes
 * — the same reason the progress bars and the modal grabber keep a fixed height. The labels around it
 * are text and scale normally.
 */
const PLOT_HEIGHT = 96;

/**
 * A bar you can see is worth more than a bar that is proportionally honest. Without a floor, one
 * session in a week where the best week had seven renders 13px tall next to a 96px neighbour and reads
 * as nothing — the difference between "I trained once" and "I didn't train" is the single most
 * important distinction on this chart, so the floor protects it.
 */
const MIN_BAR_HEIGHT = 4;

/**
 * Sessions per week, as a column chart.
 *
 * One series, so there is no legend and no categorical palette — the heading names it, and the bars
 * take the app's accent. Magnitude over time is the job, which makes columns the form and one hue the
 * colour rule; a second measure (duration, sets) would need a second chart rather than a second axis.
 *
 * **Only the tallest bar is labelled.** A number on every column is the standard way this chart goes
 * wrong: eight labels turn a shape you read in one glance into a table you read one cell at a time,
 * and the shape is the entire point. The exact counts are not lost — every bar carries its own
 * accessible name, which is what a screen reader reads and what stands in for the tooltip a touch
 * target this thin can't offer.
 */
export function WeekBars({ weeks }: { weeks: WeekTally[] }) {
  const theme = useTheme();
  const { t } = useTranslation();

  const max = Math.max(...weeks.map((week) => week.sessions), 0);
  // The tallest week, taking the *last* one when several tie — with a run of equal weeks, the label
  // belongs on the most recent rather than on whichever the scan happened to reach first.
  const peakIndex =
    max > 0 ? weeks.reduce((best, week, index) => (week.sessions >= weeks[best].sessions ? index : best), 0) : -1;

  return (
    <View>
      <View style={styles.plot}>
        {weeks.map((week, index) => {
          const height = max === 0 ? 0 : Math.max(MIN_BAR_HEIGHT, Math.round((week.sessions / max) * PLOT_HEIGHT));
          const label = formatMonthDay(week.weekStart);

          return (
            <View key={week.weekStart.toISOString()} style={styles.column}>
              {/* The count sits on the cap of the tallest column only — see the note above. It wears a
                  text colour rather than the bar's, so the bar carries the identity and the type
                  carries the reading. */}
              {index === peakIndex && (
                <ThemedText type="small" themeColor="textSecondary" style={styles.peakLabel}>
                  {week.sessions}
                </ThemedText>
              )}
              <View
                // The whole column is the accessible element, not the drawn bar: a zero week has no
                // bar at all, and a week with one session has a 4px one, neither of which a screen
                // reader could land on. This is also where the exact count lives for every week the
                // peak label doesn't cover.
                accessible
                accessibilityRole="text"
                accessibilityLabel={t('analytics.weekBarLabel', { count: week.sessions, week: label })}
                style={styles.barSlot}>
                <View
                  style={[
                    styles.bar,
                    {
                      height,
                      backgroundColor: week.sessions > 0 ? theme.accent : 'transparent',
                    },
                  ]}
                />
              </View>
            </View>
          );
        })}
      </View>

      {/* A baseline rather than a full grid: with one series and eight columns, gridlines would be
          more ink than the data. It is the axis the bars are anchored to, so it stays. */}
      <View style={[styles.baseline, { backgroundColor: theme.border }]} />

      <View style={styles.axisRow}>
        {weeks.map((week, index) => (
          <View key={week.weekStart.toISOString()} style={styles.column}>
            {/* Every other label, and always the last one. Eight dates at phone width collide into an
                unreadable smear; the ends are what a reader actually needs to date the span, and the
                most recent week is the one they came here to find. */}
            {(index % 2 === weeks.length % 2 || index === weeks.length - 1) && (
              <ThemedText type="small" themeColor="textSecondary" style={styles.axisLabel} numberOfLines={1}>
                {formatMonthDay(week.weekStart)}
              </ThemedText>
            )}
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  plot: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    height: PLOT_HEIGHT,
    // The 2px surface gap between touching marks, expressed once here rather than as a margin on each
    // bar — the gap is the separator, never a border drawn around the bars.
    gap: 2,
  },
  column: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  barSlot: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  bar: {
    // Capped rather than filling the slot: the leftover width is deliberate air, and a bar wider than
    // this stops reading as a mark and starts reading as a block.
    width: '100%',
    maxWidth: 24,
    // Rounded at the data end, square at the baseline — the shape says which end is the measurement.
    borderTopLeftRadius: 4,
    borderTopRightRadius: 4,
  },
  peakLabel: {
    marginBottom: 2,
  },
  baseline: {
    height: 1,
    width: '100%',
  },
  axisRow: {
    flexDirection: 'row',
    gap: 2,
    marginTop: Spacing.one,
  },
  axisLabel: {
    textAlign: 'center',
  },
});
