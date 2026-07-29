import { router } from 'expo-router';
import { useMemo } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { programWeekNumbers } from '@/domain/program';
import type { Program } from '@/domain/types';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

function weekRangeLabel(program: Program, t: TFunction): string {
  const weeks = programWeekNumbers(program);
  if (weeks.length === 0) return t('programs.noWeeks');
  const first = weeks[0];
  const last = weeks[weeks.length - 1];
  return first === last ? t('programs.week', { n: first }) : t('programs.weeksRange', { first, last });
}

function detailLabel(program: Program, t: TFunction): string | null {
  const count = program.weeks.filter((week) => week.notes || (week.overrides && week.overrides.length > 0)).length;
  if (count === 0) return null;
  return t('programs.weeksWithNotes', { count });
}

export default function ProgramsScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const { t } = useTranslation();
  const library = useLibraryStore((state) => state.library);
  const programs = useMemo(() => library?.programs ?? [], [library]);
  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="subtitle">{t('programs.title')}</ThemedText>
          <Pressable
            onPress={() => router.push('/program-guide')}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel={t('programs.helpLabel')}
            style={styles.helpButton}>
            <ThemedText type="heading" themeColor="textSecondary">
              ?
            </ThemedText>
          </Pressable>
        </View>
        <ThemedText themeColor="textSecondary" style={styles.countLabel}>
          {t('programs.count', { count: programs.length })}
        </ThemedText>

        <View style={styles.list}>
          {programs.map((program) => {
            const detail = detailLabel(program, t);
            return (
              <Pressable
                key={program.id}
                onPress={() => router.push({ pathname: '/program-detail', params: { programId: program.id } })}>
                <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                  <View style={styles.cardText}>
                    <ThemedText type="heading">{program.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {weekRangeLabel(program, t)}
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
              <ThemedText type="heading">{t('programs.emptyTitle')}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.emptyStateBody}>
                {t('programs.emptyBody')}
              </ThemedText>
              <Pressable
                onPress={() => router.push('/program-guide')}
                style={[styles.emptyStateButton, { borderColor: theme.border }]}>
                <ThemedText type="smallMedium">{t('programs.emptyYamlLink')}</ThemedText>
              </Pressable>
            </ThemedView>
          )}
        </View>
      </ScrollView>

      <Pressable
        onPress={() => router.push('/program-editor')}
        accessibilityRole="button"
        accessibilityLabel={t('programs.newProgram')}
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
