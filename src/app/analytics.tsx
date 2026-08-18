import { router } from 'expo-router';
import { useMemo } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { WeekBars } from '@/components/week-bars';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { currentStreak, historyStats, sessionsPerWeek, thisWeekStats } from '@/state/selectors/history-stats';
import { useSessionHistoryStore } from '@/state/session-history-store';

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

  // Computed per render, deliberately. All three read the clock: `thisWeekStats` and `sessionsPerWeek`
  // resolve the current week's boundary and `currentStreak` walks back from today, so a cache keyed on
  // the log alone would freeze them at whatever the date was when a session was last written. A modal
  // is short-lived, the log is walked once, and this is not a screen taking keystrokes.
  const weekStats = thisWeekStats(sessions);
  const allTime = historyStats(sessions);
  const streak = currentStreak(sessions);
  const weeks = useMemo(() => sessionsPerWeek(sessions, WEEKS_SHOWN), [sessions]);

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
      </ScrollView>
    </SafeAreaView>
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
  chartCard: {
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.three,
  },
});
