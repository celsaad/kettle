import { router } from 'expo-router';
import { useMemo } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ListRow, ListRowSeparator } from '@/components/list-row';
import { ModalHeader } from '@/components/modal-header';
import { Sparkline } from '@/components/sparkline';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { WeekBars } from '@/components/week-bars';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatProgressDelta, formatProgressReading, type ProgressReading } from '@/domain/format';
import { toDisplayWeight } from '@/domain/units';
import { exerciseProgress, type ExerciseProgress } from '@/state/selectors/exercise-progress';
import { exerciseName } from '@/state/selectors/exercise-lookup';
import { currentStreak, historyStats, sessionsPerWeek, thisWeekStats } from '@/state/selectors/history-stats';
import { useLibraryStore } from '@/state/library-store';
import { useUnitSystem } from '@/state/preferences-store';
import { selectHistoryComplete, selectHistoryRead, useSessionHistoryStore } from '@/state/session-history-store';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/**
 * How many weeks the breakdown covers.
 *
 * Eight is two months, which is long enough to show a habit forming or lapsing and short enough that
 * eight columns still have room to be columns at phone width. Twelve was the other candidate and loses
 * on both counts: the bars thin to a few pixels, and a quarter is longer than the horizon anyone
 * adjusts their training on.
 */
const WEEKS_SHOWN = 8;

/**
 * The numbers behind History, on their own screen.
 *
 * They used to be six stat cards stacked at the top of the History tab, which filled a phone's entire
 * first screen and pushed the session log — the thing that tab is *for* — below the fold. Moving them
 * here is what let History go back to being a log with a one-line summary, and it gives the numbers
 * room to be more than three tiles: the per-week breakdown below could not have been added to a header
 * that was already too tall.
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
  /*
    Every number on this screen is a claim about the *whole* log — all-time totals, a day streak, a
    per-week chart, and an empty state that says you have never trained. An unread log makes all of
    them wrong in the same direction, and this screen has no gate of its own: it is reached from
    History's "Stats" link, which is visible immediately.

    `complete` rather than `read`, for the empty state's sake. A failed read is "done" and still has
    no sessions in it, and "you haven't trained yet" is the one sentence here that a user could act
    on by disbelieving the app.
  */
  const historyRead = useSessionHistoryStore(selectHistoryRead);
  const historyComplete = useSessionHistoryStore(selectHistoryComplete);
  const library = useLibraryStore((state) => state.library);

  // Computed per render, deliberately. All three read the clock: `thisWeekStats` and `sessionsPerWeek`
  // resolve the current week's boundary and `currentStreak` walks back from today, so a cache keyed on
  // the log alone would freeze them at whatever the date was when a session was last written. A modal
  // is short-lived, the log is walked once, and this is not a screen taking keystrokes.
  const weekStats = thisWeekStats(sessions);
  const allTime = historyStats(sessions);
  const streak = currentStreak(sessions);
  const weeks = useMemo(() => sessionsPerWeek(sessions, WEEKS_SHOWN), [sessions]);
  const progress = useMemo(() => exerciseProgress(sessions, WEEKS_SHOWN), [sessions]);

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

        {/* One line instead of a screenful of confident zeros. Split by cause: a read still running
            will resolve on its own and says so, where one that failed will not and must not leave the
            reader waiting for numbers that are never coming. */}
        {!historyComplete ? (
          <ThemedText type="small" themeColor="textSecondary">
            {historyRead ? t('analytics.logUnavailable') : t('history.loading')}
          </ThemedText>
        ) : (
          <>
            <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
              {t('history.thisWeekLabel')}
            </ThemedText>
            <View style={styles.statsRow}>
              <Tile value={String(weekStats.sessions)} label={t('history.sessions')} />
              <Tile value={`${weekStats.hours}h ${weekStats.minutes}m`} label={t('history.time')} />
              {/* "day streak" rather than "streak": it is the one number in this group `currentStreak`
              doesn't scope to the week, so under a THIS WEEK heading a bare "streak" would read as a
              weekly count and a 30-day run would announce itself as "30 · this week". */}
              <Tile value={String(streak)} label={t('history.streak')} />
            </View>

            <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
              {t('history.allTime')}
            </ThemedText>
            <View style={styles.statsRow}>
              <Tile value={String(allTime.sessions)} label={t('history.sessions')} />
              <Tile value={`${allTime.hours}h ${allTime.minutes}m`} label={t('history.time')} />
              <Tile value={String(allTime.sets)} label={t('history.sets')} />
            </View>

            {/*
          Hidden on an empty log rather than drawn as eight flat zeros. A chart of nothing is not a
          reading — it is a shape that says "no data" in the most expensive way available, and the
          tiles above already say it in three numbers. It appears with the first session.
        */}
            {hasHistory && (
              <>
                <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
                  {t('analytics.perWeek', { count: WEEKS_SHOWN })}
                </ThemedText>
                <ThemedView type="backgroundElement" style={[styles.chartCard, { borderColor: theme.border }]}>
                  <WeekBars weeks={weeks} />
                </ThemedView>
              </>
            )}
            {/*
          The half of this screen that answers a question the tiles above cannot. Totals say how much
          you have ever done — which nobody asks twice — where these say whether the number is moving,
          per exercise, which is the reason to open Stats a second time.

          Below the chart rather than above it: turning up is the precondition for getting stronger,
          and a lapse explains a flat row better than a flat row explains itself.
        */}
            <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
              {t('analytics.progressTitle')}
            </ThemedText>

            {progress.length === 0 ? (
              <ThemedText type="small" themeColor="textSecondary">
                {t('analytics.progressEmpty')}
              </ThemedText>
            ) : (
              <>
                <ThemedText type="small" themeColor="textSecondary" style={styles.progressBody}>
                  {t('analytics.progressBody', { count: WEEKS_SHOWN })}
                </ThemedText>
                {progress.map((row, index) => (
                  <View key={row.exerciseId}>
                    {index > 0 && <ListRowSeparator />}
                    <ProgressRow row={row} name={exerciseName(library?.exercises ?? [], row.exerciseId)} />
                  </View>
                ))}
              </>
            )}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
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
  chartCard: {
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.three,
  },
});
