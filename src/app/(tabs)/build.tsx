import { router } from 'expo-router';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { formatWorkoutShape, plural } from '@/domain/format';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { workoutShape } from '@/state/selectors';
import { useLibraryStore } from '@/state/library-store';

export default function BuildScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const library = useLibraryStore((state) => state.library);

  if (!library) return null;

  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">Workouts</ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.countLabel}>
          {plural(library.workouts.length, 'workout')}
        </ThemedText>

        {library.workouts.length === 0 ? (
          <ThemedView type="backgroundElement" style={[styles.empty, { borderColor: theme.border }]}>
            <ThemedText type="heading">No workouts yet</ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.emptyBody}>
              Build one from exercises in your library.
            </ThemedText>
          </ThemedView>
        ) : (
          <View style={styles.list}>
            {library.workouts.map((workout) => (
              <ThemedView key={workout.id} type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                <Pressable
                  onPress={() => router.push({ pathname: '/workout-editor', params: { id: workout.id } })}
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
                  style={({ pressed }) => [styles.startButton, { backgroundColor: theme.accentSoft }, pressed && styles.pressed]}>
                  <View style={[styles.playTriangle, { borderLeftColor: theme.accent }]} />
                </Pressable>
              </ThemedView>
            ))}
          </View>
        )}
      </ScrollView>

      <Pressable
        onPress={() => router.push('/workout-editor')}
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
