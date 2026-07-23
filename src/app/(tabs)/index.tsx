import { router } from 'expo-router';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { blockChips, recentSessionsView, workoutSummary } from '@/state/selectors';

const today = new Date();
const dateLabel = today.toLocaleDateString('en-US', {
  weekday: 'long',
  month: 'short',
  day: 'numeric',
});

export default function TodayScreen() {
  const theme = useTheme();
  const { scheme, toggle } = useAppTheme();
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);

  const workout = library?.workouts[0];
  const chips = workout && library ? blockChips(workout, library) : [];
  const summary = workout && library ? workoutSummary(workout, library) : '';
  const recentSessions = library ? recentSessionsView(sessions, library) : [];

  if (!workout) return null;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="title" style={styles.wordmark}>
            Kettle
          </ThemedText>
          <Pressable
            onPress={toggle}
            style={({ pressed }) => [
              styles.syncButton,
              { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              pressed && styles.pressed,
            ]}>
            <ThemedText themeColor="textSecondary">{scheme === 'dark' ? '☀' : '☾'}</ThemedText>
          </Pressable>
        </View>

        <View style={styles.todayHeading}>
          <ThemedText type="subtitle">Today</ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.dateLabel}>
            {dateLabel}
          </ThemedText>
        </View>

        <ThemedView type="backgroundElement" style={[styles.nextUpCard, { borderColor: theme.border }]}>
          <ThemedText type="label" themeColor="accentText">
            NEXT UP
          </ThemedText>
          <ThemedText type="subtitle" style={styles.workoutName}>
            {workout.name}
          </ThemedText>
          <View style={styles.chipRow}>
            {chips.map((chip, index) => (
              <View
                key={`${chip}-${index}`}
                style={[
                  styles.chip,
                  { backgroundColor: chip === 'Rest' ? theme.backgroundSelected : theme.accentSoft },
                ]}>
                <ThemedText
                  type="small"
                  themeColor={chip === 'Rest' ? 'textSecondary' : 'accentText'}>
                  {chip}
                </ThemedText>
              </View>
            ))}
          </View>
          <ThemedText themeColor="textSecondary" style={styles.summaryLine}>
            {summary}
          </ThemedText>
          <Pressable
            onPress={() => router.push('/session')}
            style={({ pressed }) => [styles.startButton, { backgroundColor: theme.accent }, pressed && styles.pressed]}>
            <View style={[styles.playTriangle, { borderLeftColor: theme.onAccent }]} />
            <ThemedText type="heading" style={{ color: theme.onAccent }}>
              Start session
            </ThemedText>
          </Pressable>
        </ThemedView>

        <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
          RECENT
        </ThemedText>
        <View style={styles.recentList}>
          {recentSessions.map((session) => (
            <ThemedView
              key={session.id}
              type="backgroundElement"
              style={[styles.recentRow, { borderColor: theme.border }]}>
              <View style={styles.recentRowText}>
                <ThemedText type="heading">{session.workoutName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {session.dateLabel} · {session.durationLabel} · {session.setsLabel}
                </ThemedText>
              </View>
              <ThemedText themeColor="textSecondary">→</ThemedText>
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
  syncButton: {
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
