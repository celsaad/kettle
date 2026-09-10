import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ListRow, ListRowMinHeight, ListRowSeparator, ListRowVerticalPadding } from '@/components/list-row';
import { ModalHeader } from '@/components/modal-header';
import { Sparkline } from '@/components/sparkline';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { TrainingCalendar } from '@/components/training-calendar';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatProgressDelta, formatProgressReading, type ProgressReading } from '@/domain/format';
import type { Exercise } from '@/domain/types';
import { toDisplayWeight } from '@/domain/units';
import { exerciseProgress, type ExerciseProgress, type ProgressView } from '@/state/selectors/exercise-progress';
import { exerciseName } from '@/state/selectors/exercise-lookup';
import { currentStreak, thisWeekStats, trainingCalendar } from '@/state/selectors/history-stats';
import { useLibraryStore } from '@/state/library-store';
import { useUnitSystem } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/**
 * How many weeks the training calendar shows.
 *
 * Four, where the weekly bars it replaced showed eight: a calendar is seven cells a week rather than
 * one bar, and eight rows of them is too much at a large text size, which is where this screen has to
 * hold up. Four still shows a lapse, and the week you're in is always the bottom row.
 */
const CALENDAR_WEEKS = 4;

/**
 * How far back Getting stronger looks.
 *
 * Longer than the calendar on purpose. Four weeks is two or three sessions of most exercises, which is
 * not a trend; eight is two months — long enough for a number to move, short enough to still be about
 * what you're doing now. The two sections answer different questions, and each heading names its own
 * window.
 */
const TREND_WEEKS = 8;

/**
 * The numbers behind History, on their own screen.
 *
 * They used to be six stat cards stacked at the top of the History tab, which filled a phone's entire
 * first screen and pushed the session log — the thing that tab is *for* — below the fold. Moving them
 * here is what let History go back to being a log with a one-line summary.
 *
 * **No all-time totals.** They were three tiles here, and are gone rather than demoted to a footer:
 * History's header says the same thing in the same words, one screen back, and a total is the number
 * nobody opens a screen twice to read. What this screen is for is the two questions totals can't
 * answer — am I turning up, and is anything moving.
 *
 * **Nothing here narrows with History's search.** The tiles did, back when they sat above a filtered
 * list and had to describe it. This screen is not looking at a list, so it always reports the whole
 * log, which is also the only reading that makes "this week" and a streak mean anything.
 *
 * A modal route rather than a fifth tab, following `program-detail.tsx`: it is reached from History,
 * it has no state of its own, and the tab bar is a place for destinations you return to rather than
 * for a screen you check.
 */
export default function AnalyticsScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const library = useLibraryStore((state) => state.library);

  // Computed per render, deliberately. All three read the clock: `thisWeekStats` resolves the current
  // week's boundary, `currentStreak` walks back from today, and the calendar marks today and the days
  // still to come — so a cache keyed on the log alone would freeze them at whatever the date was when
  // a session was last written. A modal is short-lived, and this is not a screen taking keystrokes.
  const weekStats = thisWeekStats(sessions);
  const streak = currentStreak(sessions);
  const calendar = trainingCalendar(sessions, CALENDAR_WEEKS);
  const progress = useMemo(() => exerciseProgress(sessions, library?.exercises ?? [], TREND_WEEKS), [sessions, library]);

  const hasHistory = sessions.length > 0;

  return (
    // SafeAreaView with a `top` edge, matching `program-detail.tsx` and every other modal route.
    // `ModalHeader` carries the modal's own top spacing but not the *device's* — without this the close
    // button renders under the status bar and the notification icons sit on top of it.
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={() => router.back()} />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="title">{t('analytics.title')}</ThemedText>

        <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
          {t('history.thisWeekLabel')}
        </ThemedText>
        <View style={styles.statsRow}>
          <Tile value={String(weekStats.sessions)} label={t('history.sessions')} />
          <Tile
            value={t('analytics.hoursMinutes', { hours: weekStats.hours, minutes: weekStats.minutes })}
            label={t('history.time')}
          />
          {/* "day streak" rather than "streak": it is the one number in this group `currentStreak`
              doesn't scope to the week, so under a THIS WEEK heading a bare "streak" would read as a
              weekly count and a 30-day run would announce itself as "30 · this week". */}
          <Tile value={String(streak)} label={t('history.streak')} />
        </View>

        {/*
          Hidden on an empty log rather than drawn as four weeks of rest. A calendar of nothing is not a
          reading — it is a grid that says "no data" in the most expensive way available, and the tiles
          above already say it in three numbers. It appears with the first session.
        */}
        {hasHistory && (
          <>
            <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
              {t('analytics.calendarTitle', { count: CALENDAR_WEEKS })}
            </ThemedText>
            <ThemedView type="backgroundElement" style={[styles.chartCard, { borderColor: theme.border }]}>
              <TrainingCalendar weeks={calendar} />
            </ThemedView>
          </>
        )}

        {/*
          The half of this screen that answers a question the tiles above cannot: whether the number is
          moving, per exercise, which is the reason to open Stats a second time.

          Below the calendar rather than above it: turning up is the precondition for getting stronger,
          and a lapse explains a flat row better than a flat row explains itself.
        */}
        <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
          {t('analytics.progressTitle')}
        </ThemedText>
        <ProgressSection progress={progress} exercises={library?.exercises ?? []} />
      </ScrollView>
    </SafeAreaView>
  );
}

/**
 * What moved, and — one tap away — what didn't.
 *
 * **The rows that held steady fold into one.** They used to be listed with the rest, most recently
 * trained first, which on a real log put a dozen "no change" rows above the one exercise that moved.
 * A flat row is a real answer, so it stays reachable; it just doesn't get to bury the others.
 *
 * The toggle is text, not a chevron, following import's "Show all": its accessible name is exactly the
 * words on screen, which is what Voice Control matches, and `expanded` is what tells a screen reader it
 * reveals rather than navigates.
 */
function ProgressSection({ progress, exercises }: { progress: ProgressView; exercises: Exercise[] }) {
  const { t } = useTranslation();
  const [showSteady, setShowSteady] = useState(false);
  const { movers, steady, fixedHoldsLeftOut } = progress;

  if (movers.length === 0 && steady.length === 0) {
    // Two different empties. With fixed holds left out, "train something twice" would be telling
    // someone who trained a dozen things twice to go and do it — so they are told why instead.
    return (
      <ThemedText type="small" themeColor="textSecondary">
        {fixedHoldsLeftOut > 0 ? t('analytics.progressOnlyFixedHolds') : t('analytics.progressEmpty')}
      </ThemedText>
    );
  }

  const renderRow = (row: ExerciseProgress) => <ProgressRow row={row} name={exerciseName(exercises, row.exerciseId)} />;

  return (
    <>
      <ThemedText type="small" themeColor="textSecondary" style={styles.progressBody}>
        {fixedHoldsLeftOut > 0
          ? t('analytics.progressBodyFixedHolds', { count: TREND_WEEKS })
          : t('analytics.progressBody', { count: TREND_WEEKS })}
      </ThemedText>

      {movers.length === 0 && (
        <ThemedText type="small" style={styles.progressBody}>
          {t('analytics.nothingMoved', { count: TREND_WEEKS })}
        </ThemedText>
      )}

      {movers.map((row, index) => (
        <View key={row.exerciseId}>
          {index > 0 && <ListRowSeparator />}
          {renderRow(row)}
        </View>
      ))}

      {steady.length > 0 && (
        <>
          {movers.length > 0 && <ListRowSeparator />}
          <Pressable
            onPress={() => setShowSteady((shown) => !shown)}
            accessibilityRole="button"
            accessibilityState={{ expanded: showSteady }}
            style={({ pressed }) => [styles.steadyToggle, pressed && styles.pressed]}>
            <ThemedText type="smallMedium" style={styles.progressText}>
              {t('analytics.heldSteady', { count: steady.length })}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {showSteady ? t('analytics.hideSteady') : t('analytics.showSteady')}
            </ThemedText>
          </Pressable>
          {showSteady &&
            steady.map((row) => (
              <View key={row.exerciseId}>
                <ListRowSeparator />
                {renderRow(row)}
              </View>
            ))}
        </>
      )}
    </>
  );
}

/**
 * One exercise's trend: what it is at now, the shape of how it got there, and the change.
 *
 * The name is user data and renders verbatim. The weight branch is the only one that has to reach the
 * unit preference, which is why this reads `useUnitSystem` rather than taking finished strings —
 * `exerciseProgress` deals in kilograms, seconds and reps, and the conversion belongs here.
 */
function ProgressRow({ row, name }: { row: ExerciseProgress; name: string }) {
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();
  // Same lookup the runner's load row uses; there is no shared helper for it.
  const unit = t(unitSystem === 'imperial' ? 'units.lb' : 'units.kg');

  const reading = (value: number): ProgressReading => {
    if (row.kind === 'longestHold') return { kind: 'hold', holdSec: Math.round(value) };
    if (row.kind === 'heaviestSet') {
      return { kind: 'weight', weight: `${toDisplayWeight(value, unitSystem)} ${unit}` };
    }
    return { kind: 'reps', reps: Math.round(value) };
  };

  const sign = Math.sign(row.delta);

  return (
    <ListRow>
      <View style={styles.progressText}>
        <ThemedText type="smallMedium">{name}</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {formatProgressReading(reading(row.latest))}
          {/* Inline rather than a records section of its own: nearly every record in the window is also
              a mover, so a separate list would say most things twice. */}
          {row.bestEver && (
            <ThemedText type="small" themeColor="accentText">
              {' '}
              {t('analytics.bestEver')}
            </ThemedText>
          )}
        </ThemedText>
      </View>

      <Sparkline points={row.points} />

      {/*
        The one number on this screen that can be negative, so it is the one that would most tempt a
        red/green pair — and it does not get one. A dip is information, not a failure, and colouring it
        as one turns a deload week into a scolding. It takes the accent when something moved and a
        secondary tone when nothing did, so the eye finds the rows that changed.
      */}
      <ThemedText type="smallMedium" themeColor={sign === 0 ? 'textSecondary' : 'accentText'} style={styles.progressDelta}>
        {formatProgressDelta(reading(Math.abs(row.delta)), sign)}
      </ThemedText>
    </ListRow>
  );
}

/** One stat card. Local to this screen — it is three lines and nothing else renders a tile now. */
function Tile({ value, label }: { value: string; label: string }) {
  const theme = useTheme();

  return (
    <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
      <ThemedText type="heading">{value}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingBottom: Spacing.six,
  },
  sectionLabel: {
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
  },
  statsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  statCard: {
    flex: 1,
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.two + 4,
  },
  progressBody: {
    marginBottom: Spacing.two,
  },
  progressText: {
    flex: 1,
    gap: 2,
  },
  // Right-aligned in a fixed column so the deltas line up down the screen instead of floating at the
  // end of sparklines that are all the same width anyway.
  progressDelta: {
    minWidth: 72,
    textAlign: 'right',
  },
  // A list row's own metrics, shared rather than copied — see `ListRowMinHeight` — so the toggle is
  // exactly as tall and as tappable as the rows around it.
  steadyToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    minHeight: ListRowMinHeight,
    paddingVertical: ListRowVerticalPadding,
  },
  pressed: {
    opacity: 0.7,
  },
  chartCard: {
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.three,
  },
});
