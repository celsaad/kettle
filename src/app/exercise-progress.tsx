import { router, useLocalSearchParams } from 'expo-router';
import { useMemo } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ListHeaderRule, ListRow, ListRowSeparator } from '@/components/list-row';
import { ModalHeader } from '@/components/modal-header';
import { ProgressChart } from '@/components/progress-chart';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { formatProgressDelta, formatProgressReading } from '@/domain/format';
import { useProgressReading } from '@/hooks/use-progress-reading';
import { useTheme } from '@/hooks/use-theme';
import { formatFullDate, formatMonthDay } from '@/i18n/format';
import { exerciseName } from '@/state/selectors/exercise-lookup';
import { exerciseProgress, TREND_WEEKS, type ExerciseProgress } from '@/state/selectors/exercise-progress';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/**
 * One exercise's progress, opened by tapping its row in Getting stronger: where it is now, how far it
 * moved, a chart of every session in the window, and the sessions themselves.
 *
 * **It reads the row it was opened from.** Same selector, same window (`TREND_WEEKS`), same measure,
 * the same new-best rule — so the number on the row and the number at the top of this screen are one
 * reading, and there is no second definition of "getting stronger" to drift. The cost is that it
 * can't show more than the row can: there is no longer window here, and no chart for an exercise the
 * row leaves out (interval work, fixed-length holds). Both would need the row to change first.
 *
 * **No exercise picker.** The design this came from had one, for a version of this that lived on the
 * Stats screen itself; opened from a row, the row already chose.
 */
export default function ExerciseProgressScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const { exerciseId } = useLocalSearchParams<{ exerciseId?: string }>();
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const library = useLibraryStore((state) => state.library);

  const progress = useMemo(() => exerciseProgress(sessions, library?.exercises ?? [], TREND_WEEKS), [sessions, library]);
  const row = [...progress.movers, ...progress.steady].find((candidate) => candidate.exerciseId === exerciseId);

  return (
    // SafeAreaView with a `top` edge, like every modal route: `ModalHeader` carries the modal's own top
    // spacing but not the device's.
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={() => router.back()} />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* User data, verbatim — and shown even when there is nothing to chart, so the empty line below
            is about something rather than floating on its own. */}
        {exerciseId ? <ThemedText type="subtitle">{exerciseName(library?.exercises ?? [], exerciseId)}</ThemedText> : null}

        {row ? (
          <ProgressDetail row={row} />
        ) : (
          // Reached only by a stale route: the row it was opened from has since gone, say because the
          // exercise's last session in the window was deleted from History while this stayed open.
          <ThemedText type="small" themeColor="textSecondary" style={styles.notFound}>
            {t('exerciseProgress.notFound', { count: TREND_WEEKS })}
          </ThemedText>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function ProgressDetail({ row }: { row: ExerciseProgress }) {
  const theme = useTheme();
  const { t } = useTranslation();
  const reading = useProgressReading(row.kind);
  const sign = Math.sign(row.delta);
  const first = row.sessions[0];
  const last = row.sessions.at(-1)!;

  // The same window the selector measured, so the axis starts where the row's "since" does. Computed
  // here rather than returned by the selector because it is only a drawing's extent: a few
  // milliseconds between the two clocks move nothing a reader can see.
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - TREND_WEEKS * 7);

  // Newest first for the list — the most recent session is the one a reader came to check. A copy,
  // reversed; the spread is the copy oxlint can't see through (decision log: no `toReversed`).
  // oxlint-disable-next-line unicorn/no-array-reverse
  const newestFirst = [...row.sessions].reverse();

  return (
    <>
      <View style={styles.headline}>
        <ThemedText type="heading">{formatProgressReading(reading(row.latest))}</ThemedText>
        {/* No red/green here either — the Stats row's rule, for the Stats row's reason: a dip is
            information, not a failure. */}
        <ThemedText type="smallMedium" themeColor={sign === 0 ? 'textSecondary' : 'accentText'}>
          {formatProgressDelta(reading(Math.abs(row.delta)), sign)}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {t('exerciseProgress.since', { date: formatMonthDay(new Date(first.at)) })}
        </ThemedText>
      </View>

      <ThemedView type="backgroundElement" style={[styles.chartCard, { borderColor: theme.border }]}>
        <ProgressChart
          sessions={row.sessions}
          from={from}
          to={to}
          firstLabel={formatProgressReading(reading(first.value))}
          lastLabel={formatProgressReading(reading(last.value))}
        />
        {/* The key is for the dots, so it's only drawn when there are dots other than the latest's. */}
        {row.sessions.some((session) => session.newBest) && (
          <View style={styles.key}>
            <View style={[styles.keyDot, { backgroundColor: theme.accent }]} />
            <ThemedText type="small" themeColor="textSecondary">
              {t('exerciseProgress.newBest')}
            </ThemedText>
          </View>
        )}
      </ThemedView>

      <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
        {t('exerciseProgress.sessionsTitle', { count: TREND_WEEKS })}
      </ThemedText>
      <ListHeaderRule />
      {newestFirst.map((session, index) => (
        <View key={session.at}>
          {index > 0 && <ListRowSeparator />}
          <ListRow>
            <ThemedText type="small" themeColor="textSecondary" style={styles.sessionDate}>
              {formatFullDate(new Date(session.at))}
            </ThemedText>
            <ThemedText type="smallMedium">
              {formatProgressReading(reading(session.value))}
              {session.newBest && (
                <ThemedText type="small" themeColor="accentText">
                  {' '}
                  {t('exerciseProgress.newBestTag')}
                </ThemedText>
              )}
            </ThemedText>
          </ListRow>
        </View>
      ))}
    </>
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
  notFound: {
    marginTop: Spacing.three,
  },
  headline: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'baseline',
    columnGap: Spacing.two,
    marginTop: Spacing.three,
    marginBottom: Spacing.three,
  },
  chartCard: {
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.three,
  },
  key: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one + 2,
    marginTop: Spacing.two,
  },
  keyDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  sectionLabel: {
    marginTop: Spacing.four,
  },
  sessionDate: {
    flex: 1,
  },
});
