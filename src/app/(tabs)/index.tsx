import { router } from 'expo-router';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { formatWorkoutShape } from '@/domain/format';
import { formatFullDate } from '@/i18n/format';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { blockChips, currentStreak, nextUpView, recentSessionsView, thisWeekStats, workoutShape } from '@/state/selectors';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

export default function TodayScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  // Computed per render, not at module scope. It used to be a module-level const, which froze it at
  // first import — leave the app open past midnight and "Today" showed yesterday's date.
  const dateLabel = formatFullDate(new Date());
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);

  const nextUp = library ? nextUpView(library, sessions) : null;
  const chips = nextUp ? blockChips(nextUp.workout, nextUp.exercises) : [];
  const summary = nextUp ? formatWorkoutShape(workoutShape(nextUp.workout, nextUp.exercises)) : '';
  const recentSessions = library ? recentSessionsView(sessions, library) : [];
  const streak = currentStreak(sessions);
  const weekStats = thisWeekStats(sessions);

  if (!nextUp) return null;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="title" style={styles.wordmark}>
            Kettle
          </ThemedText>
          <Pressable
            onPress={() => router.push('/settings')}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel={t('common.settings')}
            style={({ pressed }) => [
              styles.settingsButton,
              { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              pressed && styles.pressed,
            ]}>
            <ThemedText themeColor="textSecondary">⚙</ThemedText>
          </Pressable>
        </View>

        <View style={styles.todayHeading}>
          <ThemedText type="subtitle">{t('today.heading')}</ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.dateLabel}>
            {dateLabel}
          </ThemedText>
        </View>

        <View style={styles.statsRow}>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">{streak}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('today.dayStreak')}
            </ThemedText>
          </ThemedView>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">{weekStats.sessions}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('today.thisWeek')}
            </ThemedText>
          </ThemedView>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">
              {weekStats.hours}h {weekStats.minutes}m
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('today.thisWeek')}
            </ThemedText>
          </ThemedView>
        </View>

        <ThemedView type="backgroundElement" style={[styles.nextUpCard, { borderColor: theme.border }]}>
          <ThemedText type="label" themeColor="accentText">
            {nextUp.weekNumber !== null
              ? t('today.nextUpWeek', {
                  week: `${t('programs.week', { n: nextUp.weekNumber })}${nextUp.weekDay ? ` · ${nextUp.weekDay}` : ''}`,
                })
              : t('today.nextUp')}
          </ThemedText>
          <ThemedText type="subtitle" style={styles.workoutName}>
            {nextUp.workout.name}
          </ThemedText>
          <View style={styles.chipRow}>
            {chips.map((chip, index) => (
              <View
                key={`${chip.name}-${index}`}
                style={[styles.chip, { backgroundColor: chip.isRest ? theme.backgroundSelected : theme.accentSoft }]}>
                <ThemedText type="small" themeColor={chip.isRest ? 'textSecondary' : 'accentText'}>
                  {chip.name}
                </ThemedText>
              </View>
            ))}
          </View>
          <ThemedText themeColor="textSecondary" style={styles.summaryLine}>
            {summary}
          </ThemedText>
          {nextUp.weekNotes && (
            <ThemedText type="small" themeColor="textSecondary" style={styles.summaryLine}>
              {nextUp.weekNotes}
            </ThemedText>
          )}
          <Pressable
            onPress={() => router.push({ pathname: '/session', params: nextUp.sessionParams })}
            accessibilityRole="button"
            style={({ pressed }) => [styles.startButton, { backgroundColor: theme.accent }, pressed && styles.pressed]}>
            <View style={[styles.playTriangle, { borderLeftColor: theme.onAccent }]} />
            <ThemedText type="heading" style={{ color: theme.onAccent }}>
              {t('today.startSession')}
            </ThemedText>
          </Pressable>
        </ThemedView>

        <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
          {t('today.recent')}
        </ThemedText>
        <View style={styles.recentList}>
          {recentSessions.map((session) => (
            <ThemedView key={session.id} type="backgroundElement" style={[styles.recentRow, { borderColor: theme.border }]}>
              {/*
                No trailing arrow: these rows aren't interactive (there's no per-session detail
                screen to open), and an arrow on a non-tappable row just reads as a broken button —
                same reason it came off SessionNextCard.
              */}
              <View style={styles.recentRowText}>
                <ThemedText type="heading">{session.workoutName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {session.dateLabel} · {session.durationLabel} · {session.setsLabel}
                </ThemedText>
              </View>
            </ThemedView>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
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
    paddingTop: Platform.select({ web: Spacing.six, default: Spacing.two }),
    paddingBottom: Spacing.six,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  wordmark: {
    fontSize: 22,
  },
  settingsButton: {
    width: 36,
    height: 36,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
  todayHeading: {
    marginTop: Spacing.four,
  },
  statsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three,
  },
  statCard: {
    flex: 1,
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.two + 4,
  },
  dateLabel: {
    marginTop: 2,
  },
  nextUpCard: {
    marginTop: Spacing.three,
    borderRadius: 22,
    padding: Spacing.three,
    borderWidth: 1,
  },
  workoutName: {
    marginTop: Spacing.one,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.one,
    marginTop: Spacing.three,
  },
  chip: {
    paddingHorizontal: Spacing.two,
    paddingVertical: 5,
    borderRadius: 8,
  },
  summaryLine: {
    marginTop: Spacing.three,
  },
  startButton: {
    marginTop: Spacing.three,
    height: 56,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  playTriangle: {
    width: 0,
    height: 0,
    borderTopWidth: 8,
    borderBottomWidth: 8,
    borderLeftWidth: 13,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
  },
  sectionLabel: {
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
  },
  recentList: {
    gap: Spacing.two,
  },
  recentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
  },
  recentRowText: {
    flex: 1,
    gap: 2,
  },
});
