import { router } from 'expo-router';
import { useMemo } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { formatWorkoutShape } from '@/domain/format';
import { useTheme } from '@/hooks/use-theme';
import type { NextUpView } from '@/state/selectors/next-up';
import { blockChips, workoutShape } from '@/state/selectors/workout-shape';

/**
 * How many block chips the card shows before it summarises the rest.
 *
 * A real workout produces around twenty of these — one per block, plus one per circuit member — and
 * they wrap, so an honest full list pushed `Start session` below the fold and behind the tab bar. A
 * new user then had to scroll to find the app's primary action, on the screen that opens first.
 *
 * Two constraints now, which is why it's six rather than the eight it shipped at. The original one:
 * enough chips to tell one workout from another at a glance, which is all this row is for — the exact
 * contents are one tap away in the runner. The one the tab merge added: this card is the list header
 * on a screen whose whole point is the workout list underneath it, so the card has to end somewhere
 * near the fold or the list it introduces is never on screen with it.
 *
 * The cap is applied here rather than in `blockChips`, which keeps returning the whole list: the
 * selector describes the workout, and how much of it fits on a card is this component's problem.
 */
const VISIBLE_CHIP_LIMIT = 6;

/**
 * What the Workouts tab queues up: the next slot of the active program, or a scheduled rest day.
 *
 * Everything that can grow without limit is capped by `numberOfLines` rather than by a `maxHeight` on
 * the card. A fixed height would clip its own contents at large accessibility text sizes — the same
 * trap that keeps every touch target on `minHeight` — whereas line caps shrink the text budget and
 * leave the card free to grow with the reader's font.
 */
export function NextUpCard({ nextUp }: { nextUp: NextUpView }) {
  return nextUp.kind === 'rest' ? <RestDayCard rest={nextUp} /> : <QueuedWorkoutCard queued={nextUp} />;
}

/*
  A scheduled rest day. No chips, no shape summary and deliberately no Start button — the program says
  today runs nothing, and a filled primary action would argue with that. The escape hatch is a
  text-weight "Train anyway" that jumps to the next slot which does run something, and "Start an empty
  session" below the card stays available as it always is.
*/
function RestDayCard({ rest }: { rest: Extract<NextUpView, { kind: 'rest' }> }) {
  const theme = useTheme();
  const { t } = useTranslation();
  // Hoisted out of the JSX so the narrowing survives into the onPress closure, which it wouldn't as a
  // property read off `rest`.
  const skipTo = rest.skipTo;

  return (
    <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
      <ThemedText type="label" themeColor="textSecondary">
        {t('today.restDayWeek', {
          week: `${t('programs.week', { n: rest.weekNumber })}${rest.weekDay ? ` · ${rest.weekDay}` : ''}`,
        })}
      </ThemedText>
      <ThemedText type="subtitle" style={styles.workoutName}>
        {t('today.restDayTitle')}
      </ThemedText>
      {/* The copy deliberately makes no claim about *when*. This card is what's next rather than what
          today is: it appears the moment you finish the session before it (elapsed is 0, so nothing is
          owed yet), and a run of consecutive rest slots — which both seeded programs ship at the end
          of every week — means the next workout is not necessarily tomorrow. "Nothing scheduled today,
          back tomorrow" was false in both cases, and the day it does mean is already named in the
          label above. */}
      <ThemedText themeColor="textSecondary" style={styles.emptyBody}>
        {t('today.restDayBody')}
      </ThemedText>
      {/* The week's own note — user data, rendered verbatim. Often the only thing that distinguishes an
          active recovery day from a full day off. Capped at two lines: it's unbounded free text, and
          it's the one field here that can push the workout list off the screen on its own. */}
      {rest.weekNotes && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.summaryLine} numberOfLines={2}>
          {rest.weekNotes}
        </ThemedText>
      )}
      {skipTo && (
        <Pressable
          onPress={() => router.push({ pathname: '/session', params: skipTo })}
          accessibilityRole="button"
          style={({ pressed }) => [styles.restSkipButton, { borderColor: theme.border }, pressed && styles.pressed]}>
          <ThemedText type="heading" themeColor="accentText">
            {t('today.restDaySkip')}
          </ThemedText>
        </Pressable>
      )}
    </ThemedView>
  );
}

function QueuedWorkoutCard({ queued }: { queued: Extract<NextUpView, { kind: 'workout' }> }) {
  const theme = useTheme();
  const { t } = useTranslation();

  // Same reasoning as `WorkoutCard`'s in the Workouts screen, and it applies harder here: this card is
  // the header of a list whose search box re-renders the whole screen on every keystroke, and both of
  // these walk every block of the workout (and every member of every circuit). Neither depends on the
  // query, so neither should run again for it.
  const { visibleChips, hiddenChipCount } = useMemo(() => {
    const chips = blockChips(queued.workout, queued.exercises);
    return {
      visibleChips: chips.slice(0, VISIBLE_CHIP_LIMIT),
      hiddenChipCount: Math.max(0, chips.length - VISIBLE_CHIP_LIMIT),
    };
  }, [queued.workout, queued.exercises]);
  const summary = useMemo(
    () => formatWorkoutShape(workoutShape(queued.workout, queued.exercises)),
    [queued.workout, queued.exercises],
  );

  return (
    <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
      <ThemedText type="label" themeColor="accentText">
        {queued.weekNumber !== null
          ? t('today.nextUpWeek', {
              week: `${t('programs.week', { n: queued.weekNumber })}${queued.weekDay ? ` · ${queued.weekDay}` : ''}`,
            })
          : t('today.nextUp')}
      </ThemedText>
      {/* User data, so its length is theirs to decide — two lines names any workout and caps the one
          field above the chips that could otherwise grow. */}
      <ThemedText type="subtitle" style={styles.workoutName} numberOfLines={2}>
        {queued.workout.name}
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
        {/* A `View`, not a `Pressable`: it opens nothing, and neither do the chips beside it. An arrow
            or a tap target here would advertise a detail screen that doesn't exist. Styled like a rest
            chip rather than like an exercise, so it reads as part of the row's chrome instead of as
            another movement in the workout. */}
        {hiddenChipCount > 0 && (
          <View style={[styles.chip, { backgroundColor: theme.backgroundSelected }]}>
            <ThemedText type="small" themeColor="textSecondary">
              {t('today.moreBlocks', { count: hiddenChipCount })}
            </ThemedText>
          </View>
        )}
      </View>
      {/* Two, not one. `formatWorkoutShape` composes a fixed three-part string, so one line looked
          like plenty — and isn't: a mixed workout reads "5 blocks · mixed: reps + hold + hiit · ~13
          min" and a phone-width single line drops the tail, which is the estimated duration. Losing
          the last component of a three-part summary is worse than the row of text it saves, and the
          duration is the part you actually decide on. Bounded either way, since nothing here is user
          text. */}
      <ThemedText themeColor="textSecondary" style={styles.summaryLine} numberOfLines={2}>
        {summary}
      </ThemedText>
      {/* Unbounded user text, capped for the same reason as the rest card's. */}
      {queued.weekNotes && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.summaryLine} numberOfLines={2}>
          {queued.weekNotes}
        </ThemedText>
      )}
      <Pressable
        onPress={() => router.push({ pathname: '/session', params: queued.sessionParams })}
        accessibilityRole="button"
        style={({ pressed }) => [styles.startButton, { backgroundColor: theme.accent }, pressed && styles.pressed]}>
        <View style={[styles.playTriangle, { borderLeftColor: theme.onAccent }]} />
        <ThemedText type="heading" style={{ color: theme.onAccent }}>
          {t('today.startSession')}
        </ThemedText>
      </Pressable>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  card: {
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
    // minHeight, not height: a fixed one clips the label at large accessibility text sizes.
    minHeight: 56,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  /**
   * Outlined and no play triangle, unlike the Start button it sits where. The rest card's whole claim
   * is that today runs nothing; a filled accent button would contradict the sentence above it. Same
   * `minHeight` reasoning as the others.
   */
  restSkipButton: {
    marginTop: Spacing.three,
    minHeight: 56,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
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
  pressed: {
    opacity: 0.7,
  },
});
