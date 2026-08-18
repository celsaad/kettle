import { router } from 'expo-router';
import { useMemo } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { formatWorkoutShape } from '@/domain/format';
import { useTheme } from '@/hooks/use-theme';
import type { NextUpView } from '@/state/selectors/next-up';
import { workoutShape } from '@/state/selectors/workout-shape';

/**
 * What the Workouts tab queues up: the next slot of the active program, or a scheduled rest day.
 *
 * **It shows no block chips.** It used to open with a wrapping row of them, capped first at eight and
 * then at six, and on a real device that row was most of the reason this card filled the screen — the
 * workout list it introduces started two rows from the bottom. The chips were also close to redundant:
 * the summary line directly beneath said "5 blocks · reps · ~10 min", which is the same claim in one
 * line and one that stays true for a twenty-block session. What the chips added over that was the
 * movement *names*, and those are one tap away in the runner, on the screen where you need them.
 *
 * Everything that can still grow is capped by `numberOfLines` rather than by a `maxHeight` on the
 * card. A fixed height would clip its own contents at large accessibility text sizes — the same trap
 * that keeps every touch target on `minHeight` — whereas line caps shrink the text budget and leave
 * the card free to grow with the reader's font.
 */
export function NextUpCard({ nextUp }: { nextUp: NextUpView }) {
  return nextUp.kind === 'rest' ? <RestDayCard rest={nextUp} /> : <QueuedWorkoutCard queued={nextUp} />;
}

/*
  A scheduled rest day. No shape summary and deliberately no Start button — the program says
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
    <View style={styles.card}>
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
    </View>
  );
}

function QueuedWorkoutCard({ queued }: { queued: Extract<NextUpView, { kind: 'workout' }> }) {
  const theme = useTheme();
  const { t } = useTranslation();

  // Memoised for the same reason `WorkoutCard` memoises its own: this card is the header of a list
  // whose search box re-renders the whole screen on every keystroke, and `workoutShape` walks every
  // block of the workout (and every member of every circuit). It does not depend on the query.
  const summary = useMemo(
    () => formatWorkoutShape(workoutShape(queued.workout, queued.exercises)),
    [queued.workout, queued.exercises],
  );

  return (
    <View style={styles.card}>
      <ThemedText type="label" themeColor="accentText">
        {queued.weekNumber !== null
          ? t('today.nextUpWeek', {
              week: `${t('programs.week', { n: queued.weekNumber })}${queued.weekDay ? ` · ${queued.weekDay}` : ''}`,
            })
          : t('today.nextUp')}
      </ThemedText>
      {/* User data, so its length is theirs to decide, and two lines names any workout. */}
      <ThemedText type="subtitle" style={styles.workoutName} numberOfLines={2}>
        {queued.workout.name}
      </ThemedText>
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
    </View>
  );
}

const styles = StyleSheet.create({
  /**
   * No fill, no border, no radius — this is the page's heading, not a box on it.
   *
   * It reads as primary from the type scale and from the one accent fill inside it, which is what
   * marks the primary action on every screen in the app. The frame was redundant with that, and once
   * the list below became rows it was the only drawn box left on the screen, which made it read as
   * something left behind rather than as something privileged.
   *
   * Dropping the padding is the half that actually mattered on device: it sat *on top of* the
   * screen's own 16px inset, so every line in here started 32px from the edge while every workout
   * name below started at 16px. The card looked narrower than the list it introduced.
   */
  card: {
    marginTop: Spacing.three,
  },
  workoutName: {
    marginTop: Spacing.one,
  },
  emptyBody: {
    marginTop: Spacing.one,
  },
  /**
   * `Spacing.two`, not `Spacing.three`. The label, name, summary and week notes are one continuous
   * reading — "week 2 · Tuesday / Foundations · Push / 5 blocks · reps · ~13 min" — and spacing them
   * like separate sections cost three full gaps on the tallest element of the screen the workout list
   * has to fit under. The gap above the Start button stays at `Spacing.three`: that one separates the
   * text from the action, which is a boundary worth drawing.
   */
  summaryLine: {
    marginTop: Spacing.two,
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
