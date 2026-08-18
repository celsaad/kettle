import { router, useLocalSearchParams } from 'expo-router';
import { useMemo } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ListHeaderRule, ListRow, ListRowSeparator } from '@/components/list-row';
import { ModalHeader } from '@/components/modal-header';
import { RowStartButton } from '@/components/row-start-button';
import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import type { Library, ProgramOverride, ProgramWeek, Workout } from '@/domain/types';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

export function overrideLines(override: ProgramOverride, library: Library, workout: Workout | undefined): string[] {
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
  const { t } = useTranslation();
  const { programId } = useLocalSearchParams<{ programId?: string }>();
  const library = useLibraryStore((state) => state.library);

  const program = useMemo(() => library?.programs.find((candidate) => candidate.id === programId), [library, programId]);

  const weeks: ProgramWeek[] = useMemo(() => {
    if (!program) return [];
    // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
    // oxlint-disable-next-line unicorn/no-array-sort
    return [...program.weeks].sort((a, b) => a.week - b.week);
  }, [program]);

  const close = () => router.back();

  const startWeek = (week: ProgramWeek) => {
    if (!program) return;
    router.push({ pathname: '/session', params: { programId: program.id, week: String(week.week), day: week.day } });
  };

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {!program && (
          <ThemedText type="small" themeColor="textSecondary">
            {t('programDetail.notFound')}
          </ThemedText>
        )}

        {program && (
          <>
            <View style={styles.titleRow}>
              <ThemedText type="subtitle" style={styles.titleText}>
                {program.name}
              </ThemedText>
              <Pressable
                onPress={() => router.push({ pathname: '/program-editor', params: { id: program.id } })}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel={t('programDetail.edit')}
                style={styles.editButton}>
                <ThemedText type="heading" themeColor="textSecondary">
                  ✎
                </ThemedText>
              </Pressable>
            </View>

            {/* Starts the list the way every tab screen's does — without it the weeks begin under the
                program's title with nothing marking where the heading stops. */}
            <ListHeaderRule />

            <View style={styles.list}>
              {weeks.map((week, index) => {
                const workout = week.restDay
                  ? undefined
                  : library?.workouts.find((candidate) => candidate.id === week.workoutId);
                const overrideText = week.restDay
                  ? []
                  : (week.overrides ?? []).flatMap((override) => overrideLines(override, library!, workout));

                const weekLabel = `${t('programs.week', { n: week.week })}${week.day ? ` · ${week.day}` : ''}`;

                return (
                  <View key={`${week.week}-${week.day ?? ''}`}>
                    {/* Between neighbours only, never above the first — this is a `map` rather than a
                        `FlatList`, so there is no `ItemSeparatorComponent` to do it. */}
                    {index > 0 && <ListRowSeparator />}

                    <ListRow>
                      <View style={styles.weekText}>
                        <ThemedText type="heading">{weekLabel}</ThemedText>
                        <ThemedText type="small" themeColor="textSecondary">
                          {week.restDay ? t('programDetail.restDay') : (workout?.name ?? week.workoutId)}
                        </ThemedText>

                        {week.notes && (
                          <ThemedText type="small" themeColor="textSecondary" style={styles.notes}>
                            {week.notes}
                          </ThemedText>
                        )}

                        {overrideText.length > 0 && (
                          <View style={styles.overrides}>
                            {overrideText.map((line, lineIndex) => (
                              <ThemedText key={lineIndex} type="small" themeColor="textSecondary">
                                {line}
                              </ThemedText>
                            ))}
                          </View>
                        )}
                      </View>

                      {/* A rest week has nothing to start, so it gets no control at all rather than a
                          disabled one — its own "Rest day" line is the whole content.

                          The label has to say *which* week, unlike the full-width button this
                          replaced: that one sat inside a card whose heading named the week, so "Start
                          this day" was unambiguous in context. A row of identical unnamed play
                          triangles is not, and this is the only text a screen reader gets. */}
                      {!week.restDay && (
                        <RowStartButton
                          onPress={() => startWeek(week)}
                          accessibilityLabel={t('programDetail.startAccessibility', { week: weekLabel })}
                        />
                      )}
                    </ListRow>
                  </View>
                );
              })}
            </View>

            <Pressable
              onPress={close}
              accessibilityRole="button"
              style={[styles.closeButton, { borderColor: theme.border }]}>
              <ThemedText type="heading" themeColor="textSecondary">
                {t('common.close')}
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
    paddingBottom: Spacing.four,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  titleText: {
    flex: 1,
  },
  editButton: {
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // The gap belongs to `ListHeaderRule` above, on the heading's side of the line.
  list: {},
  weekText: {
    flex: 1,
    gap: 2,
  },
  notes: {
    fontStyle: 'italic',
  },
  overrides: {
    gap: 2,
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
