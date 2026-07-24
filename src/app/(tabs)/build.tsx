import { router } from 'expo-router';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { workoutSummary } from '@/state/selectors';
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
          {library.workouts.length} workouts
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
              <Pressable key={workout.id} onPress={() => router.push({ pathname: '/workout-editor', params: { id: workout.id } })}>
                <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                  <View style={styles.cardText}>
                    <ThemedText type="heading">{workout.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {workoutSummary(workout, library.exercises)}
                    </ThemedText>
                  </View>
                  <ThemedText themeColor="textSecondary">›</ThemedText>
                </ThemedView>
              </Pressable>
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
  cardText: {
    flex: 1,
    gap: 2,
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
