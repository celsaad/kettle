import { Fragment, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Svg, { Path, Text as SvgText } from 'react-native-svg';

import { useTheme } from '@/hooks/use-theme';

export type VolumeChartDatum = { label: string; value: number };

const CHART_HEIGHT = 96;
const BAR_AREA_HEIGHT = 56;
const DATE_LABEL_HEIGHT = 18;
const MIN_BAR_WIDTH = 16;
const MAX_BAR_WIDTH = 40;
const BAR_RADIUS = 4;

/** An SVG rect path with only the top two corners rounded — bars should look anchored to the baseline, not like floating pills. */
function roundedTopRectPath(x: number, y: number, width: number, height: number, radius: number): string {
  const r = Math.max(0, Math.min(radius, width / 2, height));
  return `M${x},${y + r} Q${x},${y} ${x + r},${y} L${x + width - r},${y} Q${x + width},${y} ${x + width},${y + r} L${x + width},${y + height} L${x},${y + height} Z`;
}

/**
 * A small sparkline-style bar chart — no axes, per Tufte's original definition: a word-sized graphic
 * meant to sit inline with text (here, embedded in exercise-editor.tsx's Recent section, right above a
 * list that already spells out every exact value). Legibility comes from a direct value label above
 * each bar and the date label below it, not from a scale or interactive tooltip — the values are
 * already available as text right next to it, so a tap/hover tooltip would just repeat them.
 */
export function VolumeChart({ data }: { data: VolumeChartDatum[] }) {
  const theme = useTheme();
  const [width, setWidth] = useState(0);

  const onLayout = (event: LayoutChangeEvent) => setWidth(event.nativeEvent.layout.width);

  if (data.length === 0) return null;

  const maxValue = Math.max(1, ...data.map((datum) => datum.value));
  const step = width / data.length;
  const barWidth = Math.max(MIN_BAR_WIDTH, Math.min(MAX_BAR_WIDTH, step - 8));

  return (
    <View style={styles.container} onLayout={onLayout}>
      {width > 0 && (
        <Svg width={width} height={CHART_HEIGHT}>
          {data.map((datum, index) => {
            const barHeight = Math.max(2, (datum.value / maxValue) * BAR_AREA_HEIGHT);
            const centerX = index * step + step / 2;
            const barX = centerX - barWidth / 2;
            const barY = CHART_HEIGHT - DATE_LABEL_HEIGHT - barHeight;

            return (
              <Fragment key={datum.label + index}>
                <SvgText x={centerX} y={barY - 6} fontSize={11} fill={theme.textSecondary} textAnchor="middle">
                  {Math.round(datum.value)}
                </SvgText>
                <Path d={roundedTopRectPath(barX, barY, barWidth, barHeight, BAR_RADIUS)} fill={theme.accent} />
                <SvgText x={centerX} y={CHART_HEIGHT - 4} fontSize={10} fill={theme.textSecondary} textAnchor="middle">
                  {datum.label}
                </SvgText>
              </Fragment>
            );
          })}
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
});
