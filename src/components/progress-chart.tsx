import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Line, Path, Text as SvgText } from 'react-native-svg';

import { CHART_HEIGHT, progressChartLayout } from '@/components/progress-chart-layout';
import { Fonts } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMonthDay } from '@/i18n/format';
import type { ProgressSession } from '@/state/selectors/exercise-progress';

/** The value labels' size. Shared by the drawn labels and the hidden ones that measure them. */
const LABEL_SIZE = 11;

type Props = {
  /** Oldest first, as `exerciseProgress` returns them. At least two, since a row needs two. */
  sessions: ProgressSession[];
  /** The window the x axis spans. */
  from: Date;
  to: Date;
  /** The first and last values as finished text, in the reader's units. */
  firstLabel: string;
  lastLabel: string;
};

/**
 * One exercise's value, session by session, across the window — the chart the Getting stronger
 * row's sparkline is a thumbnail of. Where everything goes is `progressChartLayout`'s decision; this
 * measures the labels and draws.
 *
 * **One series, one hue, and only two numbers.** The line takes the accent. A filled dot marks each
 * session that set a new best, which is what the key under the chart names; the latest session, when
 * it didn't, gets a hollow ring instead, so a dip at the end can't be read as a record. The first and
 * last values are labelled and nothing else is: every value is in the session list directly below,
 * which is the table this chart would otherwise need — so there is no y axis, no gridlines, and no
 * tap-for-a-value tooltip a touch screen couldn't hover anyway.
 *
 * **Hidden from assistive tech.** The list below says everything the drawing does, row by row, in
 * words a screen reader can step through; the drawing would only be read as an unlabelled image.
 */
export function ProgressChart({ sessions, from, to, firstLabel, lastLabel }: Props) {
  const theme = useTheme();
  const [width, setWidth] = useState(0);
  const [firstLabelWidth, setFirstLabelWidth] = useState(0);
  const [lastLabelWidth, setLastLabelWidth] = useState(0);

  const layout = progressChartLayout({ sessions, from, to, width, firstLabelWidth, lastLabelWidth });
  const middle = new Date(from.getTime() + (to.getTime() - from.getTime()) / 2);
  const measured = (set: (width: number) => void) => (event: LayoutChangeEvent) =>
    set(Math.ceil(event.nativeEvent.layout.width));

  return (
    <View
      onLayout={(event) => setWidth(event.nativeEvent.layout.width)}
      style={styles.container}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants">
      {/* Measured, never seen. SVG text can't report its own width before it is drawn, and whether the
          two value labels fit side by side depends on it — so each is laid out once as plain text in
          the same face and size, with font scaling off to match the SVG, which doesn't scale. */}
      <Text
        allowFontScaling={false}
        onLayout={measured(setFirstLabelWidth)}
        style={[styles.measure, { fontFamily: Fonts.body }]}>
        {firstLabel}
      </Text>
      <Text
        allowFontScaling={false}
        onLayout={measured(setLastLabelWidth)}
        style={[styles.measure, { fontFamily: Fonts.bodySemiBold }]}>
        {lastLabel}
      </Text>

      {width > 0 && layout.points.length > 0 && (
        <Svg width={width} height={CHART_HEIGHT}>
          <Line x1={0} x2={width} y1={layout.baseline} y2={layout.baseline} stroke={theme.border} strokeWidth={1} />
          <Path d={layout.area} fill={theme.accent} fillOpacity={0.1} />
          <Path
            d={layout.line}
            stroke={theme.accent}
            strokeWidth={2}
            fill="none"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
          {layout.points.map((point) =>
            point.mark ? (
              // A new best is a filled dot ringed in the card's colour, so it stays legible on the line;
              // the latest, when it isn't one, is the same size inverted — a ring on the card.
              <Circle
                key={point.at}
                cx={point.x}
                cy={point.y}
                r={4}
                fill={point.mark === 'newBest' ? theme.accent : theme.backgroundElement}
                stroke={point.mark === 'newBest' ? theme.backgroundElement : theme.accent}
                strokeWidth={2}
              />
            ) : null,
          )}

          {/* Every label names the app's own face: SVG text inherits nothing from the surrounding
              `ThemedText`, and falls back to a serif. */}
          {layout.firstLabel && (
            <SvgText
              x={layout.firstLabel.x}
              y={layout.firstLabel.y}
              fontSize={LABEL_SIZE}
              fontFamily={Fonts.body}
              fill={theme.textSecondary}
              textAnchor="start">
              {firstLabel}
            </SvgText>
          )}
          {layout.lastLabel && (
            <SvgText
              x={layout.lastLabel.x}
              y={layout.lastLabel.y}
              fontSize={LABEL_SIZE}
              fontFamily={Fonts.bodySemiBold}
              fill={theme.text}
              textAnchor="start">
              {lastLabel}
            </SvgText>
          )}

          <SvgText
            x={0}
            y={CHART_HEIGHT - 6}
            fontSize={LABEL_SIZE}
            fontFamily={Fonts.body}
            fill={theme.textSecondary}
            textAnchor="start">
            {formatMonthDay(from)}
          </SvgText>
          <SvgText
            x={width / 2}
            y={CHART_HEIGHT - 6}
            fontSize={LABEL_SIZE}
            fontFamily={Fonts.body}
            fill={theme.textSecondary}
            textAnchor="middle">
            {formatMonthDay(middle)}
          </SvgText>
          <SvgText
            x={width}
            y={CHART_HEIGHT - 6}
            fontSize={LABEL_SIZE}
            fontFamily={Fonts.body}
            fill={theme.textSecondary}
            textAnchor="end">
            {formatMonthDay(to)}
          </SvgText>
        </Svg>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    height: CHART_HEIGHT,
    width: '100%',
  },
  // Out of the flow and invisible: only its width is wanted.
  measure: {
    position: 'absolute',
    left: 0,
    top: 0,
    opacity: 0,
    fontSize: LABEL_SIZE,
  },
});
