import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

const PLOT_HEIGHT = 20;
const BAR_WIDTH = 4;
const GAP = 2;

/**
 * How many sessions a sparkline shows. Beyond this the most recent ones are kept and the rest fall
 * off the left — a fixed number of bars is what keeps every row on the Stats screen the same width,
 * and the trend a reader wants is the recent end of it.
 */
const MAX_BARS = 7;

/**
 * One exercise's recent values, as a word-sized bar chart with no axes.
 *
 * Tufte's original sense of the term: it sits inline in a row that already states the latest value and
 * the change in words, so it carries *shape* and nothing else. That is why it has no labels, no
 * baseline and no scale — every number it could print is already printed beside it, and a second copy
 * would make the row a table.
 *
 * **Scaled from zero, not from the minimum.** Anchoring the floor at the smallest value in the window
 * is how a sparkline lies: three sessions at 10, 10.5 and 11 kg would draw as an empty bar and a full
 * one, reporting a rout where there was a nudge. From zero, a small gain looks small — which is the
 * honest reading and the one the delta beside it confirms.
 */
export function Sparkline({ points }: { points: number[] }) {
  const theme = useTheme();

  const shown = points.slice(-MAX_BARS);
  const max = Math.max(...shown, 0);

  return (
    <View style={styles.row} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {shown.map((value, index) => (
        <View
          key={index}
          style={[
            styles.bar,
            {
              // A floor of 2px, so a genuine zero still reads as a bar that happened rather than as a
              // gap where a session is missing.
              height: max <= 0 ? 2 : Math.max(2, Math.round((value / max) * PLOT_HEIGHT)),
              // The most recent bar is the one the row is about; the rest are context.
              backgroundColor: index === shown.length - 1 ? theme.accent : theme.border,
            },
          ]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  // Decorative geometry with no text in it, so fixed heights are right here — the same reason the
  // runner's progress bars keep one. Nothing clips at a raised text size because nothing here is type.
  row: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    height: PLOT_HEIGHT,
    gap: GAP,
  },
  bar: {
    width: BAR_WIDTH,
    borderRadius: 1,
  },
});
