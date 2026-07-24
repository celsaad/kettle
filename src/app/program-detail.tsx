import { router, useLocalSearchParams } from 'expo-router';
import { useMemo } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { programWeekNumbers } from '@/domain/program';
import type { Library, ProgramOverride, ProgramWeek, Workout } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

function overrideLines(override: ProgramOverride, library: Library, workout: Workout | undefined): string[] {
  if (override.kind === 'exercise') {
    const exercise = library.exercises.find((candidate) => candidate.id === override.exerciseId);
    const name = exercise?.name ?? override.exerciseId;
    return Object.entries(override.config).map(([key, value]) => `${name}: ${key} → ${value}`);
  }
  const block = workout?.blocks.find((candidate) => candidate.kind === 'circuit' && candidate.id === override.blockId);
  const label = block ? 'Circuit' : override.blockId;
  return Object.entries(override.config).map(([key, value]) => `${label}: ${key} → ${value}`);
}

export default function ProgramDetailScreen() {
  const theme = useTheme();
  const { programId } = useLocalSearchParams<{ programId?: string }>();
  const library = useLibraryStore((state) => state.library);

  const program = useMemo(() => library?.programs.find((candidate) => candidate.id === programId), [library, programId]);

  const weeks: ProgramWeek[] = useMemo(() => {
    if (!program) return [];
    const order = programWeekNumbers(program);
    return order.map((weekNumber) => program.weeks.find((week) => week.week === weekNumber)!);
  }, [program]);

  const close = () => router.back();

  const startWeek = (weekNumber: number) => {
    if (!program) return;
    router.push({ pathname: '/session', params: { programId: program.id, week: String(weekNumber) } });
  };

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['bottom', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.grabber, { backgroundColor: theme.border }]} />

        {!program && (
          <ThemedText type="small" themeColor="textSecondary">
            Program not found.
          </ThemedText>
        )}

        {program && (
          <>
            <ThemedText type="subtitle">{program.name}</ThemedText>

            <View style={styles.list}>
              {weeks.map((week) => {
                const workout = library?.workouts.find((candidate) => candidate.id === week.workoutId);
                const overrideText = (week.overrides ?? []).flatMap((override) => overrideLines(override, library!, workout));

                return (
                  <ThemedView key={week.week} type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                    <View style={styles.cardHeader}>
                      <View style={styles.cardHeaderText}>
                        <ThemedText type="heading">Week {week.week}</ThemedText>
                        <ThemedText type="small" themeColor="textSecondary">
                          {workout?.name ?? week.workoutId}
                        </ThemedText>
                      </View>
                    </View>

                    {week.notes && (
                      <ThemedText type="small" themeColor="textSecondary" style={styles.notes}>
                        {week.notes}
                      </ThemedText>
                    )}

                    {overrideText.length > 0 && (
                      <View style={styles.overrides}>
                        {overrideText.map((line, index) => (
                          <ThemedText key={index} type="small" themeColor="textSecondary">
                            {line}
                          </ThemedText>
                        ))}
                      </View>
                    )}

                    <Pressable
                      onPress={() => startWeek(week.week)}
                      style={[styles.startButton, { backgroundColor: theme.accent }]}>
                      <ThemedText type="smallMedium" style={{ color: theme.onAccent }}>
                        Start this week
                      </ThemedText>
                    </Pressable>
                  </ThemedView>
                );
              })}
            </View>

            <Pressable onPress={close} style={[styles.closeButton, { borderColor: theme.border }]}>
              <ThemedText type="heading" themeColor="textSecondary">
                Close
              </ThemedText>
            </Pressable>
          </>
        )}
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
    paddingTop: Spacing.two,
    paddingBottom: Spacing.four,
  },
  grabber: {
    alignSelf: 'center',
    width: 40,
    height: 5,
    borderRadius: 3,
    marginBottom: Spacing.three - 2,
  },
  list: {
    marginTop: Spacing.three,
    gap: Spacing.two,
  },
  card: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
    gap: Spacing.two - 3,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  cardHeaderText: {
    flex: 1,
    gap: 2,
  },
  notes: {
    fontStyle: 'italic',
  },
  overrides: {
    gap: 2,
  },
  startButton: {
    marginTop: Spacing.one,
    height: 44,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeButton: {
    marginTop: Spacing.four,
    height: 52,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
