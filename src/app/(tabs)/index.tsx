import { router } from 'expo-router';
import { memo, useCallback, useDeferredValue, useMemo, useState } from 'react';
import { FlatList, Platform, Pressable, StyleSheet, View, type ListRenderItemInfo } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { FirstRunCard } from '@/components/first-run-card';
import { NextUpCard } from '@/components/next-up-card';
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
import { workoutShape } from '@/state/selectors/workout-shape';
import { nextUpView } from '@/state/selectors/next-up';
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
          {/* The list's rows are the only place a workout name can be read back unambiguously: the
              next-up card in the header renders a name too, and which workout it picks rotates by
              calendar day when no program is active. A test asserting list order off the visible text
              would quietly depend on today's date. */}
          <ThemedText type="heading" testID="workout-card-name">
            {workout.name}
          </ThemedText>
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

/**
 * The home tab: what to run next, and everything there is to run.
 *
 * This was two tabs — `Today` (a next-up card, three stat tiles and a copy of History's first five
 * rows) and `Build` (the workout list). They merged because only one of the three things Today did
 * was its own: the stats and the recent list were a smaller, worse copy of the History tab, while the
 * next-up card is the *only* path that resolves which program week is due and starts a session with
 * `programId`/`week`/`day` so that week's overrides actually apply. Starting a workout from the list
 * below passes a bare `workoutId` and gets none of that, which is why the answer was to merge the two
 * rather than to drop the tab.
 *
 * Five tabs became four, and the app opens on the screen where you pick something and start it.
 */
export default function WorkoutsScreen() {
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
  // in effect — this walks every session ever logged, and this is a screen you land on to train.
  const lastTrained = useMemo(
    () => (sort === 'recent' ? lastTrainedByWorkout(sessions) : new Map<string, string>()),
    [sessions, sort],
  );
  // Sorted after filtering: ordering the whole library to then throw most of it away is work nobody
  // sees.
  const workouts = useMemo(() => sortForList(matching, sort, lastTrained), [matching, sort, lastTrained]);

  // Memoised: this screen owns the search box, so it re-renders on every keystroke — twice, since
  // `useDeferredValue` renders the urgent pass and then the deferred one. `nextUpView` walks the whole
  // session log, sorts the active program's weeks and runs `resolveWorkoutForWeek`, none of which
  // depends on what's being typed. The old Today screen could get away with calling it bare because it
  // had no text input on it; this one cannot, and leaving it bare would undo exactly what the
  // `deferredQuery` note above is for.
  const nextUp = useMemo(() => (library ? nextUpView(library, sessions) : null), [library, sessions]);
  // Only a workout card has something to point at; a rest day has no Start button.
  const queued = nextUp?.kind === 'workout' ? nextUp : null;

  // Never having finished a session is what "new here" actually means — it survives a reinstall's
  // seeded library and doesn't need a flag persisted anywhere, so web (which can't persist at all)
  // gets the same behaviour as native for free. It goes for good the moment the first session lands.
  //
  // Gated on `queued`, **not** on `nextUp`: step one reads "Start the workout below", and a rest card
  // has no workout and no Start button under it. A program whose first slot is `rest_day: true` puts
  // a brand-new user in exactly that state, so this distinction is reachable rather than theoretical.
  // Suppressed on an empty library for the related reason — the list's own empty state is already a
  // single clear instruction, and two competing instruction blocks is worse than either alone.
  const isFirstRun = sessions.length === 0 && queued !== null;

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
        would silently eat every keystroke in the search box that lives up there.
      */}
      <FlatList
        data={workouts}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ItemSeparatorComponent={Separator}
        contentContainerStyle={styles.scrollContent}
        ListHeaderComponent={
          <View style={styles.listHeader}>
            {/*
              The settings gear lives here because this screen is the app's only route to `/settings`
              — it came across with the merge and has nowhere else to be. The `Kettle` wordmark that
              used to sit above it did not: it cost a whole row on the screen that now has to fit a
              card *and* a list, and the web build still shows one in its tab bar.
            */}
            <View style={styles.titleRow}>
              <ThemedText type="subtitle">{t('build.title')}</ThemedText>
              <Pressable
                onPress={() => router.push('/settings')}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel={t('common.settings')}
                style={({ pressed }) => [
                  styles.settingsButton,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                  pressed && styles.pressed,
                ]}>
                <ThemedText themeColor="textSecondary">⚙</ThemedText>
              </Pressable>
            </View>
            <ThemedText themeColor="textSecondary" style={styles.countLabel}>
              {t('build.workoutCount', { count: workouts.length })}
            </ThemedText>

            {isFirstRun && <FirstRunCard />}

            {/* Nothing queued means an empty library, and the list's own empty state below says so
                better than a second card would — so the card simply doesn't render rather than
                duplicating the message above it. */}
            {nextUp && <NextUpCard nextUp={nextUp} />}

            {/*
              Outside the card's conditional on purpose. The empty-library state is where it earns its
              place: with no workouts at all you can still train, which nothing else on this screen
              offers without a detour through the editor.

              Text-weight rather than a second filled button — when there is a workout queued, that
              stays the primary action.
            */}
            <Pressable
              onPress={() => router.push({ pathname: '/session', params: { adhoc: '1' } })}
              accessibilityRole="button"
              style={({ pressed }) => [
                styles.emptySessionButton,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                pressed && styles.pressed,
              ]}>
              <ThemedText type="heading" themeColor="accentText" style={styles.emptySessionGlyph}>
                +
              </ThemedText>
              <ThemedText type="heading" themeColor="accentText">
                {t('today.startEmpty')}
              </ThemedText>
            </Pressable>

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
  titleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  settingsButton: {
    width: 36,
    height: 36,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
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
  /**
   * Outlined rather than filled, and sized to match the next-up card's own Start button.
   *
   * It shipped as a bare text link and was genuinely easy to miss on a real device — floating between
   * the card and the list, it read as a caption rather than a control. An outline gives it an edge to
   * recognise as tappable while leaving the filled accent button above it unambiguously primary,
   * which is the point of the hierarchy rather than a compromise on it.
   */
  emptySessionButton: {
    marginTop: Spacing.three,
    // minHeight, not height: a fixed one clips the label at large accessibility text sizes.
    minHeight: 56,
    borderRadius: 16,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  emptySessionGlyph: {
    // The glyph is decorative — the label beside it already names the control — so it carries no
    // separate accessible text.
    lineHeight: 22,
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
