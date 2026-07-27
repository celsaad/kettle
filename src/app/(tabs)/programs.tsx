import { router } from 'expo-router';
import { useMemo } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { plural } from '@/domain/format';
import { programWeekNumbers } from '@/domain/program';
import type { Program } from '@/domain/types';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

function weekRangeLabel(program: Program): string {
  const weeks = programWeekNumbers(program);
  if (weeks.length === 0) return 'No weeks';
  const first = weeks[0];
  const last = weeks[weeks.length - 1];
  return first === last ? `Week ${first}` : `Weeks ${first}–${last}`;
}

function detailLabel(program: Program): string | null {
  const count = program.weeks.filter((week) => week.notes || (week.overrides && week.overrides.length > 0)).length;
  if (count === 0) return null;
  return `${plural(count, 'week')} with notes or overrides`;
}

export default function ProgramsScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const library = useLibraryStore((state) => state.library);
  const programs = useMemo(() => library?.programs ?? [], [library]);
  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="subtitle">Programs</ThemedText>
          <Pressable
            onPress={() => router.push('/program-guide')}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="How to write a program"
            style={styles.helpButton}>
            <ThemedText type="heading" themeColor="textSecondary">
              ?
            </ThemedText>
          </Pressable>
        </View>
        <ThemedText themeColor="textSecondary" style={styles.countLabel}>
          {programs.length} program{programs.length === 1 ? '' : 's'}
        </ThemedText>

        <View style={styles.list}>
          {programs.map((program) => {
            const detail = detailLabel(program);
            return (
              <Pressable
                key={program.id}
                onPress={() => router.push({ pathname: '/program-detail', params: { programId: program.id } })}>
                <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                  <View style={styles.cardText}>
                    <ThemedText type="heading">{program.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {weekRangeLabel(program)}
                    </ThemedText>
                    {detail && (
                      <ThemedText type="small" themeColor="textSecondary">
                        {detail}
                      </ThemedText>
                    )}
                  </View>
                  <ThemedText themeColor="textSecondary">{'›'}</ThemedText>
                </ThemedView>
              </Pressable>
            );
          })}
          {programs.length === 0 && (
            <ThemedView type="backgroundElement" style={[styles.emptyState, { borderColor: theme.border }]}>
              <ThemedText type="heading">No programs yet</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.emptyStateBody}>
                Programs are multi-week plans that step through your workouts. Create one with the +
                button, or write one as YAML for finer control (per-week overrides aren't editable
                in-app yet).
              </ThemedText>
              <Pressable
                onPress={() => router.push('/program-guide')}
                style={[styles.emptyStateButton, { borderColor: theme.border }]}>
                <ThemedText type="smallMedium">Writing one as YAML →</ThemedText>
              </Pressable>
            </ThemedView>
          )}
        </View>
      </ScrollView>

      <Pressable
        onPress={() => router.push('/program-editor')}
        accessibilityRole="button"
        accessibilityLabel="New program"
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
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  helpButton: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  countLabel: {
    marginTop: 2,
  },
  list: {
    marginTop: Spacing.three,
    gap: Spacing.two - 3,
  },
  emptyState: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.one + 2,
  },
  emptyStateBody: {
    lineHeight: 18,
  },
  emptyStateButton: {
    alignSelf: 'flex-start',
    borderRadius: 10,
    borderWidth: 1,
    paddingVertical: Spacing.one + 2,
    paddingHorizontal: Spacing.two + 2,
    marginTop: 2,
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
  pressed: {
    opacity: 0.7,
  },
});
