import { router } from 'expo-router';
import { memo, useCallback, useDeferredValue, useMemo, useState } from 'react';
import { FlatList, Platform, Pressable, StyleSheet, View, type ListRenderItemInfo } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { NoResults } from '@/components/no-results';
import { SearchBar } from '@/components/search-bar';
import { SortPills } from '@/components/sort-pills';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { formatWorkoutShape } from '@/domain/format';
import { lastTrainedByWorkout, sortForList } from '@/domain/list-sort';
import type { Exercise, Workout } from '@/domain/types';
import { useAppTheme } from '@/hooks/theme-context';
import { useTheme } from '@/hooks/use-theme';
import { workoutShape } from '@/state/selectors';
import { useLibraryStore } from '@/state/library-store';
import { useListSort, usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/**
 * One card, memoised, and defined at module level so its identity is stable across renders — a
 * component declared inside the screen is a new type every render, which remounts every row and makes
 * the memo worse than useless.
 *
 * It navigates by itself rather than taking `onOpen`/`onStart` props, which is what keeps its props
 * down to two values that don't change: a new lambda per row per render would defeat `memo` at the
 * first prop comparison. `exercises` comes straight off the library, so its identity only changes when
 * the library does.
 */
const WorkoutCard = memo(function WorkoutCard({ workout, exercises }: { workout: Workout; exercises: Exercise[] }) {
  const theme = useTheme();
  const { t } = useTranslation();
  const shape = useMemo(() => formatWorkoutShape(workoutShape(workout, exercises)), [workout, exercises]);

  return (
    <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
      <Pressable
        onPress={() => router.push({ pathname: '/workout-editor', params: { id: workout.id } })}
        accessibilityRole="button"
        style={styles.cardTextArea}>
        <View style={styles.cardText}>
          <ThemedText type="heading">{workout.name}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {shape}
          </ThemedText>
        </View>
      </Pressable>

      <Pressable
        onPress={() => router.push({ pathname: '/session', params: { workoutId: workout.id } })}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel={t('build.startAccessibility', { name: workout.name })}
        style={({ pressed }) => [styles.startButton, { backgroundColor: theme.accentSoft }, pressed && styles.pressed]}>
        <View style={[styles.playTriangle, { borderLeftColor: theme.accent }]} />
      </Pressable>
    </ThemedView>
  );
});

/** Reproduces the `gap` the old `styles.list` had, which a FlatList's cells don't inherit. */
function Separator() {
  return <View style={styles.separator} />;
}

const keyExtractor = (workout: Workout) => workout.id;

export default function BuildScreen() {
  const theme = useTheme();
  const { scheme } = useAppTheme();
  const { t } = useTranslation();
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const sort = useListSort('workouts');
  const setListSort = usePreferencesStore((state) => state.setListSort);
  const [query, setQuery] = useState('');
  /*
    The input keeps the immediate value so typing is never held back; only the filtering below runs on
    the deferred one. A timer-based debounce was the alternative and would have added its full delay to
    every list, including the seed library where filtering is already instant — this adds nothing until
    the work is actually slow enough to notice, and then yields to the keystroke instead of blocking it.
  */
  const deferredQuery = useDeferredValue(query);

  // Every hook here runs before the `!library` bail-out below, since hooks can't be conditional.
  const all = library?.workouts ?? [];
  const matching = useMemo(() => {
    const needle = deferredQuery.trim().toLowerCase();
    if (!needle) return all;
    return all.filter((workout) => workout.name.toLowerCase().includes(needle));
    // `all` is a fresh array literal whenever the library is null, so the library itself is the honest
    // dependency — depending on `all` would re-filter on every render of an unhydrated screen.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [library, deferredQuery]);

  // `recent` is the only order that reads the log at all, so the map is built only when it's the one
  // in effect — this walks every session ever logged, and Build is a screen you land on to train.
  const lastTrained = useMemo(
    () => (sort === 'recent' ? lastTrainedByWorkout(sessions) : new Map<string, string>()),
    [sessions, sort],
  );
  // Sorted after filtering: ordering the whole library to then throw most of it away is work nobody
  // sees.
  const workouts = useMemo(() => sortForList(matching, sort, lastTrained), [matching, sort, lastTrained]);

  const exercises = library?.exercises;
  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<Workout>) => <WorkoutCard workout={item} exercises={exercises ?? []} />,
    [exercises],
  );

  if (!library) return null;

  const fabColor = scheme === 'dark' ? theme.accent : theme.text;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      {/*
        The chrome is passed as an *element*, never as an inline `() => <Header/>`. An inline arrow is
        a new component type on every render, so React unmounts and remounts the whole header — which
        costs nothing visible today and will silently eat every keystroke once a text input lives up
        there.
      */}
      <FlatList
        data={workouts}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ItemSeparatorComponent={Separator}
        contentContainerStyle={styles.scrollContent}
        ListHeaderComponent={
          <View style={styles.listHeader}>
            <ThemedText type="subtitle">{t('build.title')}</ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.countLabel}>
              {t('build.workoutCount', { count: workouts.length })}
            </ThemedText>

            {/* Keyed off the whole library rather than what's visible, so neither control disappears
                mid-search and moves the list under the finger that's typing. */}
            {all.length > 0 && (
              <SearchBar value={query} onChangeText={setQuery} placeholder={t('build.searchPlaceholder')} />
            )}

            {/* Hidden at one item and at none: there is nothing to order, and a control that changes
                nothing when pressed is worse than an absent one. */}
            {all.length > 1 && <SortPills sort={sort} onSelect={(next) => setListSort('workouts', next)} />}
          </View>
        }
        /*
          Two different empty states, and telling them apart is the point. "No workouts yet — build one
          from exercises in your library" is the right thing to say on a fresh install and exactly the
          wrong thing to say to someone with forty workouts who mistyped one.
        */
        ListEmptyComponent={
          all.length > 0 ? (
            <NoResults query={deferredQuery} />
          ) : (
            <ThemedView type="backgroundElement" style={[styles.empty, { borderColor: theme.border }]}>
              <ThemedText type="heading">{t('build.emptyTitle')}</ThemedText>
              <ThemedText themeColor="textSecondary" style={styles.emptyBody}>
                {t('build.emptyBody')}
              </ThemedText>
            </ThemedView>
          )
        }
      />

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
  // No `marginTop` any more: the header below carries the gap that `styles.list` used to, and both
  // would have stacked into double the space above the empty card.
  empty: {
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.three,
    gap: 4,
  },
  emptyBody: {},
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
