import { router } from 'expo-router';
import { memo, useCallback, useDeferredValue, useMemo, useState } from 'react';
import { FlatList, Platform, Pressable, StyleSheet, View, type ListRenderItemInfo } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ExerciseBadge, exerciseSummary } from '@/components/exercise-badge';
import { NoResults } from '@/components/no-results';
import { SearchBar } from '@/components/search-bar';
import { SortPills } from '@/components/sort-pills';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { lastTrainedByExercise, sortForList } from '@/domain/list-sort';
import { ExerciseType, type Exercise } from '@/domain/types';
import type { UnitSystem } from '@/domain/units';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useListSort, usePreferencesStore, useUnitSystem } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { exportLibrary } from '@/storage/export';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

const FILTERS: { labelKey: string; type: ExerciseType | 'all' }[] = [
  { labelKey: 'library.filterAll', type: 'all' },
  { labelKey: 'library.filterHiit', type: 'hiit' },
  { labelKey: 'library.filterReps', type: 'reps' },
  { labelKey: 'library.filterHold', type: 'timed_hold' },
];

/**
 * Memoised and module-level for the same reasons as Build's `WorkoutCard`; see the note there. This is
 * the one that matters most of the four: a library grows without bound, and it's about to gain a
 * search box whose every keystroke re-renders this screen.
 */
const ExerciseCard = memo(function ExerciseCard({ exercise, unitSystem }: { exercise: Exercise; unitSystem: UnitSystem }) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={() => router.push({ pathname: '/exercise-editor', params: { id: exercise.id } })}
      accessibilityRole="button">
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
  );
});

/** Reproduces the `gap` the old `styles.list` had, which a FlatList's cells don't inherit. */
function Separator() {
  return <View style={styles.separator} />;
}

const keyExtractor = (exercise: Exercise) => exercise.id;

export default function LibraryScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<ExerciseType | 'all'>('all');
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const sort = useListSort('exercises');
  const setListSort = usePreferencesStore((state) => state.setListSort);
  const exercises = useMemo(() => library?.exercises.filter((exercise) => exercise.type !== 'rest') ?? [], [library]);
  // Immediate value in the input, deferred value in the filter — see the note in (tabs)/index.tsx.
  const deferredQuery = useDeferredValue(query);

  const filtered = useMemo(() => {
    const needle = deferredQuery.trim().toLowerCase();
    return exercises.filter((exercise) => {
      const matchesFilter = filter === 'all' || exercise.type === filter;
      const matchesQuery = exercise.name.toLowerCase().includes(needle);
      return matchesFilter && matchesQuery;
    });
  }, [exercises, filter, deferredQuery]);

  // Only `recent` reads the log, and reading it means walking every session ever logged — see Build.
  const lastTrained = useMemo(
    () => (sort === 'recent' ? lastTrainedByExercise(sessions) : new Map<string, string>()),
    [sessions, sort],
  );
  // Sorted after filtering, not before: the two are independent, and ordering the whole library to
  // then throw most of it away is work nobody sees.
  const visible = useMemo(() => sortForList(filtered, sort, lastTrained), [filtered, sort, lastTrained]);

  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<Exercise>) => <ExerciseCard exercise={item} unitSystem={unitSystem} />,
    [unitSystem],
  );

  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      {/* The header is an element, not an inline `() => <Header/>` — see the note in (tabs)/index.tsx. That
          matters here and not only in principle: the search box below lives in it, and a remounted
          header loses focus on every keystroke. */}
      <FlatList
        data={visible}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ItemSeparatorComponent={Separator}
        contentContainerStyle={styles.scrollContent}
        ListHeaderComponent={
          <View style={styles.listHeader}>
            <View style={styles.header}>
              <ThemedText type="subtitle">{t('library.title')}</ThemedText>
              <View style={styles.headerActions}>
                <Pressable onPress={() => exportLibrary().catch(() => {})} accessibilityRole="button" hitSlop={8}>
                  <ThemedText type="smallMedium" themeColor="textSecondary">
                    {t('common.export')}
                  </ThemedText>
                </Pressable>
                <Pressable onPress={() => router.push('/import')} accessibilityRole="button" hitSlop={8}>
                  <ThemedText type="smallMedium" themeColor="accentText">
                    {t('common.import')}
                  </ThemedText>
                </Pressable>
              </View>
            </View>
            <ThemedText themeColor="textSecondary" style={styles.countLabel}>
              {t('library.exerciseCount', { count: visible.length })}
            </ThemedText>

            <SearchBar value={query} onChangeText={setQuery} placeholder={t('library.searchPlaceholder')} />

            <View style={styles.filterRow}>
              {FILTERS.map((item) => {
                const active = item.type === filter;
                return (
                  <Pressable
                    key={item.labelKey}
                    onPress={() => setFilter(item.type)}
                    accessibilityRole="button"
                    accessibilityState={{ selected: active }}
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

            {/* Keyed off the whole library rather than what's currently visible: tied to `visible` the
                control would vanish mid-search the moment a query narrowed things to one result,
                moving the list under the finger that's typing. */}
            {exercises.length > 1 && <SortPills sort={sort} onSelect={(next) => setListSort('exercises', next)} />}
          </View>
        }
        /*
          No first-run half here, unlike Build and Programs: a library with no exercises at all is only
          reachable by deleting every seeded one, and Library has never had a card for it. This adds
          the case that a search *creates* — a blank body under intact chrome, which reads as a broken
          screen rather than as "nothing matched".
        */
        ListEmptyComponent={exercises.length > 0 ? <NoResults query={deferredQuery} /> : null}
      />

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
  // What `styles.list`'s `marginTop` and `gap` became once the list stopped being one `View`.
  listHeader: {
    marginBottom: Spacing.three,
  },
  separator: {
    height: Spacing.two - 3,
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
