import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import { ThemedText } from '@/components/themed-text';
import { Spacing, MaxContentWidth } from '@/constants/theme';
import { findExercise, workouts } from '@/constants/mock-data';
import { useTheme } from '@/hooks/use-theme';

const workout = workouts[0];

export default function BuildScreen() {
  const theme = useTheme();

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText themeColor="textSecondary">‹ Cancel</ThemedText>
          <ThemedText type="heading" themeColor="accentText">
            Save
          </ThemedText>
        </View>

        <View style={styles.titleRow}>
          <ThemedText type="subtitle">{workout.name}</ThemedText>
          <ThemedText themeColor="textSecondary">✎</ThemedText>
        </View>
        <ThemedText themeColor="textSecondary" style={styles.blockCount}>
          {workout.blocks.length} blocks
        </ThemedText>

        <View style={styles.list}>
          {workout.blocks.map((block, index) => {
            const exercise = findExercise(block.exerciseId);
            const isRest = exercise.type === 'rest';
            const overrideSec = block.configOverride?.durationSec;
            const summary =
              isRest && overrideSec
                ? `${overrideSec} seconds`
                : exerciseSummary(exercise);

            return (
              <View
                key={`${block.exerciseId}-${index}`}
                style={[
                  styles.row,
                  isRest
                    ? { borderWidth: 1, borderStyle: 'dashed', borderColor: theme.border }
                    : { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
                ]}>
                <ThemedText themeColor="textSecondary" style={styles.dragHandle}>
                  ⣿
                </ThemedText>
                <View style={styles.rowText}>
                  <ThemedText type={isRest ? 'default' : 'heading'}>{exercise.name}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {summary}
                  </ThemedText>
                </View>
                <ExerciseBadge type={exercise.type} overrideLabel={overrideSec ? 'OVERRIDE' : undefined} />
              </View>
            );
          })}
        </View>

        <Pressable
          style={({ pressed }) => [styles.addBlock, { borderColor: theme.border }, pressed && styles.pressed]}>
          <ThemedText type="heading" themeColor="textSecondary">
            + Add block
          </ThemedText>
        </Pressable>
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
  pressed: {
    opacity: 0.7,
  },
  titleRow: {
    marginTop: Spacing.three - 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  blockCount: {
    marginTop: 4,
  },
  list: {
    marginTop: Spacing.three,
    gap: Spacing.two + 2,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    padding: Spacing.two + 6,
  },
  dragHandle: {
    letterSpacing: -2,
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  addBlock: {
    marginTop: Spacing.three,
    height: 48,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
