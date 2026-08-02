import { router } from 'expo-router';
import { useMemo } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SortPills } from '@/components/sort-pills';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { formatWorkoutShape } from '@/domain/format';
import { lastTrainedByWorkout, sortForList } from '@/domain/list-sort';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { workoutShape } from '@/state/selectors';
import { useLibraryStore } from '@/state/library-store';
import { useListSort, usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

export default function BuildScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const { t } = useTranslation();
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const sort = useListSort('workouts');
  const setListSort = usePreferencesStore((state) => state.setListSort);

  // Both memos run before the `!library` bail-out below, since hooks can't be conditional. `recent` is
  // the only order that reads the log at all, so the map is built only when it's the one in effect —
  // this walks every session ever logged, and Build is a screen you land on to start training.
  const lastTrained = useMemo(
    () => (sort === 'recent' ? lastTrainedByWorkout(sessions) : new Map<string, string>()),
    [sessions, sort],
  );
  const workouts = useMemo(() => sortForList(library?.workouts ?? [], sort, lastTrained), [library, sort, lastTrained]);

  if (!library) return null;

  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('build.title')}</ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.countLabel}>
          {t('build.workoutCount', { count: workouts.length })}
        </ThemedText>

        {/* Hidden at one item and at none: there is nothing to order, and a control that changes
            nothing when pressed is worse than an absent one. */}
        {workouts.length > 1 && <SortPills sort={sort} onSelect={(next) => setListSort('workouts', next)} />}

        {workouts.length === 0 ? (
          <ThemedView type="backgroundElement" style={[styles.empty, { borderColor: theme.border }]}>
            <ThemedText type="heading">{t('build.emptyTitle')}</ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.emptyBody}>
              {t('build.emptyBody')}
            </ThemedText>
          </ThemedView>
        ) : (
          <View style={styles.list}>
            {workouts.map((workout) => (
              <ThemedView key={workout.id} type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                <Pressable
                  onPress={() => router.push({ pathname: '/workout-editor', params: { id: workout.id } })}
                  accessibilityRole="button"
                  style={styles.cardTextArea}>
                  <View style={styles.cardText}>
                    <ThemedText type="heading">{workout.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {formatWorkoutShape(workoutShape(workout, library.exercises))}
                    </ThemedText>
                  </View>
                </Pressable>

                <Pressable
                  onPress={() => router.push({ pathname: '/session', params: { workoutId: workout.id } })}
                  hitSlop={8}
                  accessibilityRole="button"
                  accessibilityLabel={t('build.startAccessibility', { name: workout.name })}
                  style={({ pressed }) => [
                    styles.startButton,
                    { backgroundColor: theme.accentSoft },
                    pressed && styles.pressed,
                  ]}>
                  <View style={[styles.playTriangle, { borderLeftColor: theme.accent }]} />
                </Pressable>
              </ThemedView>
            ))}
          </View>
        )}
      </ScrollView>

      <Pressable
        onPress={() => router.push('/workout-editor')}
        accessibilityRole="button"
        accessibilityLabel={t('build.newWorkout')}
        style={({ pressed }) => [styles.fab, { backgroundColor: fabColor }, pressed && styles.pressed]}>
        <ThemedText type="title" style={[styles.fabPlus, { color: theme.onAccent }]}>
          +
        </ThemedText>
      </Pressable>
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
  countLabel: {
    marginTop: 2,
  },
  pressed: {
    opacity: 0.7,
  },
  empty: {
    marginTop: Spacing.three,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.three,
    gap: 4,
  },
  emptyBody: {},
  list: {
    marginTop: Spacing.three,
    gap: Spacing.two - 3,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
  },
  cardTextArea: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  cardText: {
    flex: 1,
    gap: 2,
  },
  startButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  playTriangle: {
    width: 0,
    height: 0,
    marginLeft: 2,
    borderTopWidth: 6,
    borderBottomWidth: 6,
    borderLeftWidth: 9,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
  },
  fab: {
    position: 'absolute',
    right: Spacing.three,
    bottom: Spacing.four,
    width: 52,
    height: 52,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabPlus: {
    fontSize: 26,
    lineHeight: 28,
  },
});
