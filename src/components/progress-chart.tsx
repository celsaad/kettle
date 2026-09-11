import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import Svg, { Circle, Line, Path, Text as SvgText } from 'react-native-svg';

import { Fonts } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMonthDay } from '@/i18n/format';
import type { ProgressSession } from '@/state/selectors/exercise-progress';

/**
 * The drawing's height. Fixed, as decorative geometry is throughout this codebase: nothing in it is a
 * control, and every number it draws is also in the session list under it, which is text and scales.
 */
const HEIGHT = 168;
/** Room above the plot for the start and end labels. */
const PAD_TOP = 20;
/** Room below the baseline for the dates. */
const PAD_BOTTOM = 24;
/** Keeps an end dot's ring inside the drawing rather than clipped at its edge. */
const PAD_X = 6;
/** The start label is dropped when the two ends are closer than this — see below. */
const MIN_LABEL_GAP = 72;

type Props = {
  /** Oldest first, as `exerciseProgress` returns them. At least two, since a row needs two. */
  sessions: ProgressSession[];
  /** The window the x axis spans. Time, not session order: a two-week gap draws as a gap. */
  from: Date;
  to: Date;
  /** The first and last values as finished text, in the reader's units. */
  firstLabel: string;
  lastLabel: string;
};

/**
 * One exercise's value, session by session, across the window — the chart the Getting stronger
 * row's sparkline is a thumbnail of.
 *
 * **One series, one hue, and only two numbers.** The line takes the accent; a dot marks each session
 * that set a new best, plus the latest; the first and last values are labelled at the line's ends,
 * and nothing else is. Every other value is in the session list directly below, which is the table
 * this chart would otherwise need — so there is no y axis, no gridlines, and no tap-for-a-value
 * tooltip a touch screen couldn't hover anyway.
 *
 * **Scaled from zero**, as the sparkline is and for the same reason: from the minimum, 10 kg to
 * 10.5 kg would draw as a cliff. From zero, a small gain looks small, which is the honest reading.
 *
 * **Hidden from assistive tech.** The list below says everything the drawing does, row by row, in
 * words a screen reader can step through; the drawing would only be read as an unlabelled image.
 */
export function ProgressChart({ sessions, from, to, firstLabel, lastLabel }: Props) {
  const theme = useTheme();
  const [width, setWidth] = useState(0);
  const onLayout = (event: LayoutChangeEvent) => setWidth(event.nativeEvent.layout.width);

  const baseline = HEIGHT - PAD_BOTTOM;
  const span = Math.max(1, to.getTime() - from.getTime());
  const max = Math.max(...sessions.map((session) => session.value), 0);
  const xOf = (date: Date) => PAD_X + ((date.getTime() - from.getTime()) / span) * (width - PAD_X * 2);
  const yOf = (value: number) => (max <= 0 ? baseline : baseline - (value / max) * (baseline - PAD_TOP));

  const points = sessions.map((session) => ({ x: xOf(new Date(session.at)), y: yOf(session.value), ...session }));
  const first = points[0];
  const last = points.at(-1)!;
  const line = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)},${point.y.toFixed(1)}`)
    .join(' ');
  const area = `${line} L${last.x.toFixed(1)},${baseline} L${first.x.toFixed(1)},${baseline} Z`;
  const middle = new Date(from.getTime() + span / 2);

  return (
    <View
      onLayout={onLayout}
      style={styles.container}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants">
      {width > 0 && sessions.length > 0 && (
        <Svg width={width} height={HEIGHT}>
          <Line x1={0} x2={width} y1={baseline} y2={baseline} stroke={theme.border} strokeWidth={1} />
          <Path d={area} fill={theme.accent} fillOpacity={0.1} />
          <Path d={line} stroke={theme.accent} strokeWidth={2} fill="none" strokeLinejoin="round" strokeLinecap="round" />
          {points.map((point, index) =>
            point.newBest || index === points.length - 1 ? (
              // The ring is the card's own colour, so a dot stays legible where it sits on the line.
              <Circle
                key={point.at}
                cx={point.x}
                cy={point.y}
                r={4}
                fill={theme.accent}
                stroke={theme.backgroundElement}
                strokeWidth={2}
              />
            ) : null,
          )}

          {/* Two ends that close together would print one label over the other; the latest is the one
              the screen is about, so the first gives way. Every label names the app's own face: SVG
              text inherits nothing from the surrounding `ThemedText`, and falls back to a serif. */}
          {last.x - first.x >= MIN_LABEL_GAP && (
            <SvgText
              x={first.x}
              y={first.y - 10}
              fontSize={11}
              fontFamily={Fonts.body}
              fill={theme.textSecondary}
              textAnchor="start">
              {firstLabel}
            </SvgText>
          )}
          <SvgText
            x={last.x}
            y={last.y - 10}
            fontSize={11}
            fontFamily={Fonts.bodySemiBold}
            fill={theme.text}
            textAnchor="end">
            {lastLabel}
          </SvgText>

          <SvgText x={0} y={HEIGHT - 6} fontSize={11} fontFamily={Fonts.body} fill={theme.textSecondary} textAnchor="start">
            {formatMonthDay(from)}
          </SvgText>
          <SvgText
            x={width / 2}
            y={HEIGHT - 6}
            fontSize={11}
            fontFamily={Fonts.body}
            fill={theme.textSecondary}
            textAnchor="middle">
            {formatMonthDay(middle)}
          </SvgText>
          <SvgText
            x={width}
            y={HEIGHT - 6}
            fontSize={11}
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
    height: HEIGHT,
    width: '100%',
  },
});
