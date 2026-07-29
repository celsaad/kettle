import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { ExerciseType } from '@/domain/types';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useUnitSystem } from '@/state/preferences-store';
import { exportLibrary } from '@/storage/export';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

const FILTERS: { labelKey: string; type: ExerciseType | 'all' }[] = [
  { labelKey: 'library.filterAll', type: 'all' },
  { labelKey: 'library.filterHiit', type: 'hiit' },
  { labelKey: 'library.filterReps', type: 'reps' },
  { labelKey: 'library.filterHold', type: 'timed_hold' },
];

export default function LibraryScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<ExerciseType | 'all'>('all');
  const library = useLibraryStore((state) => state.library);
  const exercises = useMemo(() => library?.exercises.filter((exercise) => exercise.type !== 'rest') ?? [], [library]);

  const filtered = useMemo(() => {
    return exercises.filter((exercise) => {
      const matchesFilter = filter === 'all' || exercise.type === filter;
      const matchesQuery = exercise.name.toLowerCase().includes(query.trim().toLowerCase());
      return matchesFilter && matchesQuery;
    });
  }, [exercises, filter, query]);

  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="subtitle">{t('library.title')}</ThemedText>
          <View style={styles.headerActions}>
            <Pressable onPress={() => exportLibrary().catch(() => {})} hitSlop={8}>
              <ThemedText type="smallMedium" themeColor="textSecondary">
                {t('common.export')}
              </ThemedText>
            </Pressable>
            <Pressable onPress={() => router.push('/import')} hitSlop={8}>
              <ThemedText type="smallMedium" themeColor="accentText">
                {t('common.import')}
              </ThemedText>
            </Pressable>
          </View>
        </View>
        <ThemedText themeColor="textSecondary" style={styles.countLabel}>
          {t('library.exerciseCount', { count: filtered.length })}
        </ThemedText>

        <View style={[styles.searchBar, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
          <ThemedText themeColor="textSecondary">⌕</ThemedText>
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder={t('library.searchPlaceholder')}
            accessibilityLabel={t('library.searchPlaceholder')}
            placeholderTextColor={theme.textSecondary}
            style={[styles.searchInput, { color: theme.text }]}
          />
        </View>

        <View style={styles.filterRow}>
          {FILTERS.map((item) => {
            const active = item.type === filter;
            return (
              <Pressable
                key={item.labelKey}
                onPress={() => setFilter(item.type)}
                style={[
                  styles.filterPill,
                  active
                    ? { backgroundColor: theme.text }
                    : { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
                ]}>
                <ThemedText type="small" style={{ color: active ? theme.onAccent : theme.textSecondary }}>
                  {t(item.labelKey)}
                </ThemedText>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.list}>
          {filtered.map((exercise) => (
            <Pressable
              key={exercise.id}
              onPress={() => router.push({ pathname: '/exercise-editor', params: { id: exercise.id } })}>
              <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
                <View style={styles.cardText}>
                  <ThemedText type="heading">{exercise.name}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {exerciseSummary(exercise, unitSystem)}
                  </ThemedText>
                </View>
                <ExerciseBadge type={exercise.type} />
              </ThemedView>
            </Pressable>
          ))}
        </View>
      </ScrollView>

      <Pressable
        onPress={() => router.push('/exercise-editor')}
        accessibilityRole="button"
        accessibilityLabel={t('library.newExercise')}
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
    alignItems: 'baseline',
  },
  headerActions: {
    flexDirection: 'row',
    gap: Spacing.two + 2,
  },
  countLabel: {
    marginTop: 2,
  },
  pressed: {
    opacity: 0.7,
  },
  searchBar: {
    marginTop: Spacing.three,
    height: 44,
    borderRadius: 14,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.two + 6,
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
  },
  filterRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three,
  },
  filterPill: {
    paddingHorizontal: Spacing.three - 3,
    paddingVertical: 7,
    borderRadius: 999,
  },
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
