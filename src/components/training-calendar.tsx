import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { Spacing, type ThemeColor } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMonthDay, formatWeekday } from '@/i18n/format';
import { CALENDAR_MINUTES, type CalendarDay, type CalendarLevel, type CalendarWeek } from '@/state/selectors/history-stats';

/**
 * A cell's height. Fixed, unlike every touch target in this codebase: a cell is decorative geometry
 * with no text in it, so nothing clips at a large text size — the same reason the old weekly bars and
 * the modal grabber keep one. The labels around the grid are text and scale normally.
 */
const CELL_HEIGHT = 24;

const LEVEL_TOKEN: Record<CalendarLevel, ThemeColor> = {
  0: 'calendarEmpty',
  1: 'calendarLevel1',
  2: 'calendarLevel2',
  3: 'calendarLevel3',
};

/**
 * The widest width any caller has reported, for lining a column up across separate rows.
 *
 * Each week is its own row, so the date labels don't share a column the way a table's would, and at a
 * large text size "Aug 17" and "Sep 7" differ by enough to push one row's cells sideways. Each label
 * reports its width and every label — and the header's spacer — takes the widest. It only ever grows,
 * which is what makes it settle: once every label is at the widest width, no report changes it.
 */
function useWidest(): [number, (event: LayoutChangeEvent) => void] {
  const [widest, setWidest] = useState(0);
  const onLayout = (event: LayoutChangeEvent) => {
    const width = Math.ceil(event.nativeEvent.layout.width);
    setWidest((current) => (width > current ? width : current));
  };
  return [widest, onLayout];
}

/**
 * The last few weeks, one row each, one cell per day, shaded by how long you trained.
 *
 * It replaced a column chart of sessions per week, and keeps that chart's one reading — the week's
 * count, in the right-hand column — while adding the three it couldn't show: which days, how long, and
 * where the gaps are.
 *
 * **Each week row is one accessible element**, named with its count and time. Twenty-eight cells for a
 * screen reader to step through would be a table read one square at a time, and a cell has nothing to
 * say that its row's label doesn't; so the cells and the weekday header are hidden, and the row speaks
 * for them — the way the weekly bars spoke per column rather than per bar.
 */
export function TrainingCalendar({ weeks }: { weeks: CalendarWeek[] }) {
  const { t } = useTranslation();
  const [labelWidth, onLabelLayout] = useWidest();
  const [countWidth, onCountLayout] = useWidest();

  const legend: { level: CalendarLevel; label: string }[] = [
    { level: 0, label: t('analytics.calendarRest') },
    { level: 1, label: t('analytics.calendarShort', { minutes: CALENDAR_MINUTES.mid }) },
    { level: 2, label: t('analytics.calendarMid', { from: CALENDAR_MINUTES.mid, to: CALENDAR_MINUTES.long }) },
    { level: 3, label: t('analytics.calendarLong', { minutes: CALENDAR_MINUTES.long }) },
  ];

  return (
    <View style={styles.grid}>
      <View style={styles.row} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <View style={{ minWidth: labelWidth }} />
        <View style={styles.cells}>
          {weeks[0]?.days.map((day) => (
            <View key={day.date.toISOString()} style={styles.slot}>
              <ThemedText type="small" themeColor="textSecondary" style={styles.weekday}>
                {formatWeekday(day.date, 'narrow')}
              </ThemedText>
            </View>
          ))}
        </View>
        <View style={{ minWidth: countWidth }} />
      </View>

      {weeks.map((week) => {
        const weekLabel = formatMonthDay(week.weekStart);
        const time = t('analytics.hoursMinutes', { hours: Math.floor(week.minutes / 60), minutes: week.minutes % 60 });

        return (
          <View
            key={week.weekStart.toISOString()}
            accessible
            accessibilityRole="text"
            accessibilityLabel={t('analytics.calendarWeekLabel', { count: week.sessions, week: weekLabel, time })}
            style={styles.row}>
            <View onLayout={onLabelLayout} style={{ minWidth: labelWidth }}>
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
                {weekLabel}
              </ThemedText>
            </View>
            <View style={styles.cells}>
              {week.days.map((day) => (
                <DayCell key={day.date.toISOString()} day={day} />
              ))}
            </View>
            <View onLayout={onCountLayout} style={{ minWidth: countWidth }}>
              <ThemedText type="smallMedium" themeColor={week.sessions > 0 ? 'text' : 'textSecondary'} style={styles.count}>
                {week.sessions}
              </ThemedText>
            </View>
          </View>
        );
      })}

      {/* Starts under the first cell rather than under the dates, so the swatches read as belonging to
          the grid. The labels interpolate `CALENDAR_MINUTES`, which is what the shading reads. */}
      <View style={[styles.legend, { paddingStart: labelWidth + Spacing.two }]}>
        {legend.map((item) => (
          <View key={item.level} style={styles.legendItem}>
            <Swatch level={item.level} />
            <ThemedText type="small" themeColor="textSecondary">
              {item.label}
            </ThemedText>
          </View>
        ))}
      </View>
    </View>
  );
}

/**
 * One day. The slot carries today's ring and the cell carries the shade, so the ring sits *around* the
 * cell with a gap of the card showing through — a border on the cell itself would eat into the shade.
 * Every slot has the same transparent border, so ringing today moves nothing.
 *
 * A day still to come is an outline, not an empty cell: "rest" is something you did, and Friday hasn't
 * happened yet.
 */
function DayCell({ day }: { day: CalendarDay }) {
  const theme = useTheme();

  return (
    <View style={[styles.slot, styles.ringSlot, day.isToday && { borderColor: theme.text }]}>
      <View
        style={[
          styles.cell,
          day.isFuture ? { borderWidth: 1, borderColor: theme.border } : { backgroundColor: theme[LEVEL_TOKEN[day.level]] },
        ]}
      />
    </View>
  );
}

function Swatch({ level }: { level: CalendarLevel }) {
  const theme = useTheme();
  return <View style={[styles.swatch, { backgroundColor: theme[LEVEL_TOKEN[level]] }]} />;
}

const styles = StyleSheet.create({
  grid: {
    gap: Spacing.one,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  cells: {
    flex: 1,
    flexDirection: 'row',
  },
  slot: {
    flex: 1,
  },
  // The transparent border is the ring's reserved space; the padding is the gap between ring and cell.
  ringSlot: {
    borderWidth: 1.5,
    borderColor: 'transparent',
    borderRadius: 6,
    padding: 1,
  },
  cell: {
    height: CELL_HEIGHT,
    borderRadius: 4,
  },
  weekday: {
    textAlign: 'center',
  },
  // Tabular so a 1 and a 12 line up down the column; the column is too narrow for the eye to forgive it.
  count: {
    textAlign: 'right',
    fontVariant: ['tabular-nums'],
  },
  legend: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    columnGap: Spacing.three - 2,
    rowGap: Spacing.one,
    marginTop: Spacing.two,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one + 2,
  },
  swatch: {
    width: 10,
    height: 10,
    borderRadius: 3,
  },
});
