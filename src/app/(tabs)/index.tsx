import { router } from 'expo-router';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { FirstRunCard } from '@/components/first-run-card';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { formatWorkoutShape } from '@/domain/format';
import { formatFullDate } from '@/i18n/format';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { blockChips, currentStreak, nextUpView, recentSessionsView, thisWeekStats, workoutShape } from '@/state/selectors';

export { RouteErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/**
 * How many block chips the Next-up card shows before it summarises the rest.
 *
 * A real workout produces around twenty of these — one per block, plus one per circuit member — and
 * they wrap, so an honest full list pushed `Start session` below the fold and behind the tab bar. A
 * new user then had to scroll to find the app's primary action, on the screen that opens first.
 *
 * Eight is where two rows of chips end at a typical phone width, which is enough to tell one workout
 * from another at a glance — which is all this row is for; the exact contents are one tap away in the
 * runner. The cap is applied here rather than in `blockChips`, which keeps returning the whole list:
 * the selector describes the workout, and how much of it fits on a card is this screen's problem.
 */
const VISIBLE_CHIP_LIMIT = 8;

export default function TodayScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  // Computed per render, not at module scope. It used to be a module-level const, which froze it at
  // first import — leave the app open past midnight and "Today" showed yesterday's date.
  const dateLabel = formatFullDate(new Date());
  const library = useLibraryStore((state) => state.library);
  const sessions = useSessionHistoryStore((state) => state.sessions);

  const nextUp = library ? nextUpView(library, sessions) : null;
  const chips = nextUp ? blockChips(nextUp.workout, nextUp.exercises) : [];
  const visibleChips = chips.slice(0, VISIBLE_CHIP_LIMIT);
  const hiddenChipCount = chips.length - visibleChips.length;
  const summary = nextUp ? formatWorkoutShape(workoutShape(nextUp.workout, nextUp.exercises)) : '';
  const recentSessions = library ? recentSessionsView(sessions, library) : [];
  const streak = currentStreak(sessions);
  const weekStats = thisWeekStats(sessions);

  // Never having finished a session is what "new here" actually means — it survives a reinstall's
  // seeded library and doesn't need a flag persisted anywhere, so web (which can't persist at all)
  // gets the same behaviour as native for free. It goes for good the moment the first session lands.
  //
  // Suppressed when there's nothing to run: the empty state below is itself a single clear
  // instruction, and two competing instruction blocks is worse than either alone. Step one would be
  // pointing at a workout that isn't there.
  const isFirstRun = sessions.length === 0 && nextUp !== null;

  // Only the store still hydrating. A null `nextUp` is a different thing entirely — a library with no
  // workouts, which is reachable by deleting the seeded ones — and gets the empty card below. This
  // screen used to return null for that too, so clearing your library blanked the home tab: no
  // wordmark, no settings button, no way to find out why. Build already had the empty state; Today
  // didn't, and a blank home tab reads as a crash rather than as an empty library.
  if (!library) return null;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <ThemedText type="title" style={styles.wordmark}>
            Kettle
          </ThemedText>
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

        <View style={styles.todayHeading}>
          <ThemedText type="subtitle">{t('today.heading')}</ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.dateLabel}>
            {dateLabel}
          </ThemedText>
        </View>

        <View style={styles.statsRow}>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">{streak}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('today.dayStreak')}
            </ThemedText>
          </ThemedView>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">{weekStats.sessions}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('today.thisWeek')}
            </ThemedText>
          </ThemedView>
          <ThemedView type="backgroundElement" style={[styles.statCard, { borderColor: theme.border }]}>
            <ThemedText type="heading">
              {weekStats.hours}h {weekStats.minutes}m
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('today.thisWeek')}
            </ThemedText>
          </ThemedView>
        </View>

        {isFirstRun && <FirstRunCard />}

        {nextUp ? (
          <ThemedView type="backgroundElement" style={[styles.nextUpCard, { borderColor: theme.border }]}>
            <ThemedText type="label" themeColor="accentText">
              {nextUp.weekNumber !== null
                ? t('today.nextUpWeek', {
                    week: `${t('programs.week', { n: nextUp.weekNumber })}${nextUp.weekDay ? ` · ${nextUp.weekDay}` : ''}`,
                  })
                : t('today.nextUp')}
            </ThemedText>
            <ThemedText type="subtitle" style={styles.workoutName}>
              {nextUp.workout.name}
            </ThemedText>
            <View style={styles.chipRow}>
              {visibleChips.map((chip, index) => (
                <View
                  key={`${chip.name}-${index}`}
                  style={[styles.chip, { backgroundColor: chip.isRest ? theme.backgroundSelected : theme.accentSoft }]}>
                  <ThemedText type="small" themeColor={chip.isRest ? 'textSecondary' : 'accentText'}>
                    {chip.name}
                  </ThemedText>
                </View>
              ))}
              {/* A `View`, not a `Pressable`: it opens nothing, and neither do the chips beside it.
                  An arrow or a tap target here would advertise a detail screen that doesn't exist.
                  Styled like a rest chip rather than like an exercise, so it reads as part of the
                  row's chrome instead of as another movement in the workout. */}
              {hiddenChipCount > 0 && (
                <View style={[styles.chip, { backgroundColor: theme.backgroundSelected }]}>
                  <ThemedText type="small" themeColor="textSecondary">
                    {t('today.moreBlocks', { count: hiddenChipCount })}
                  </ThemedText>
                </View>
              )}
            </View>
            <ThemedText themeColor="textSecondary" style={styles.summaryLine}>
              {summary}
            </ThemedText>
            {nextUp.weekNotes && (
              <ThemedText type="small" themeColor="textSecondary" style={styles.summaryLine}>
                {nextUp.weekNotes}
              </ThemedText>
            )}
            <Pressable
              onPress={() => router.push({ pathname: '/session', params: nextUp.sessionParams })}
              accessibilityRole="button"
              style={({ pressed }) => [styles.startButton, { backgroundColor: theme.accent }, pressed && styles.pressed]}>
              <View style={[styles.playTriangle, { borderLeftColor: theme.onAccent }]} />
              <ThemedText type="heading" style={{ color: theme.onAccent }}>
                {t('today.startSession')}
              </ThemedText>
            </Pressable>
          </ThemedView>
        ) : (
          <ThemedView type="backgroundElement" style={[styles.nextUpCard, { borderColor: theme.border }]}>
            <ThemedText type="subtitle">{t('today.emptyTitle')}</ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.emptyBody}>
              {t('today.emptyBody')}
            </ThemedText>
            {/*
              Straight to the editor rather than to the Build tab: it's the same action Build's own
              FAB takes, and it needs no cross-tab navigation. The editor can create exercises inline,
              so this works even when the library was cleared out entirely.
            */}
            <Pressable
              onPress={() => router.push('/workout-editor')}
              accessibilityRole="button"
              style={({ pressed }) => [styles.startButton, { backgroundColor: theme.accent }, pressed && styles.pressed]}>
              <ThemedText type="heading" style={{ color: theme.onAccent }}>
                {t('build.newWorkout')}
              </ThemedText>
            </Pressable>
          </ThemedView>
        )}

        {/*
          Outside the card's conditional on purpose, so it shows in both branches. The empty-library
          state is where it earns its place: with no workouts at all you can still train, which the
          "New workout" button above can't offer without a detour through the editor.

          Text-weight rather than a second filled button — when there is a workout queued, that stays
          the primary action.
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

        {/*
          The heading goes with its list rather than standing alone: before this it rendered
          unconditionally, so a fresh install (and the empty library above) showed a "RECENT" label
          with nothing under it — which reads as content that failed to load.
        */}
        {recentSessions.length > 0 && (
          <ThemedText type="label" themeColor="textSecondary" style={styles.sectionLabel}>
            {t('today.recent')}
          </ThemedText>
        )}
        <View style={styles.recentList}>
          {recentSessions.map((session) => (
            <ThemedView key={session.id} type="backgroundElement" style={[styles.recentRow, { borderColor: theme.border }]}>
              {/*
                No trailing arrow: these rows aren't interactive (there's no per-session detail
                screen to open), and an arrow on a non-tappable row just reads as a broken button —
                same reason it came off SessionNextCard.
              */}
              <View style={styles.recentRowText}>
                <ThemedText type="heading">{session.workoutName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {session.dateLabel} · {session.durationLabel} · {session.setsLabel}
                </ThemedText>
              </View>
            </ThemedView>
          ))}
        </View>
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
  wordmark: {
    fontSize: 22,
  },
  settingsButton: {
    width: 36,
    height: 36,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
  todayHeading: {
    marginTop: Spacing.four,
  },
  statsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three,
  },
  statCard: {
    flex: 1,
    borderRadius: 14,
    borderWidth: 1,
    padding: Spacing.two + 4,
  },
  dateLabel: {
    marginTop: 2,
  },
  nextUpCard: {
    marginTop: Spacing.three,
    borderRadius: 22,
    padding: Spacing.three,
    borderWidth: 1,
  },
  workoutName: {
    marginTop: Spacing.one,
  },
  emptyBody: {
    marginTop: Spacing.one,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.one,
    marginTop: Spacing.three,
  },
  chip: {
    paddingHorizontal: Spacing.two,
    paddingVertical: 5,
    borderRadius: 8,
  },
  summaryLine: {
    marginTop: Spacing.three,
  },
  startButton: {
    marginTop: Spacing.three,
    // minHeight, not height: a fixed one clips the label at large accessibility text sizes, and this
    // style now carries the empty state's button too.
    minHeight: 56,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  playTriangle: {
    width: 0,
    height: 0,
    borderTopWidth: 8,
    borderBottomWidth: 8,
    borderLeftWidth: 13,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
  },
  /**
   * Outlined rather than filled, and sized to match the card's own Start button.
   *
   * It shipped as a bare text link and was genuinely easy to miss on a real device — floating between
   * the card and RECENT, it read as a caption rather than a control. An outline gives it an edge to
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
  sectionLabel: {
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
  },
  recentList: {
    gap: Spacing.two,
  },
  recentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
  },
  recentRowText: {
    flex: 1,
    gap: 2,
  },
});
