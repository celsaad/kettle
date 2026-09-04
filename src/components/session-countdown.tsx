import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { FirstSessionHint } from '@/components/first-session-hint';
import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import { useSessionSounds } from '@/hooks/use-session-sounds';

type Props = {
  workoutName: string;
  onDone: () => void;
};

/** Get-ready count-in shown before a session's timers start: 3, 2, 1 — one tick ding per number,
 * then a distinct "exercise change" ding as the first exercise begins. */
export function SessionCountdown({ workoutName, onDone }: Props) {
  const [count, setCount] = useState(3);
  const { t } = useTranslation();
  const { playTick, playExerciseChange } = useSessionSounds();

  useEffect(() => {
    playTick();
    // Only the initial numeral's ding — playTick would re-fire on every render otherwise.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const id = setTimeout(() => {
      if (count === 1) {
        playExerciseChange();
        onDone();
        return;
      }
      setCount((current) => current - 1);
      playTick();
    }, 1000);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [count]);

  return (
    <View style={styles.container}>
      <ThemedText type="code" style={styles.label}>
        {t('session.countdown.getReady')}
      </ThemedText>
      {/*
        The name is the headline and the count is the caption, which is the opposite of how this
        started. A 140px "2" is a number nobody needs: it is gone in a second, it says nothing the
        three ticks haven't, and it was drawn eight times the size of the one thing a reader actually
        checks here — whether the session about to start is the one they meant. User data, so it
        renders verbatim and is free to wrap.
      */}
      <ThemedText type="subtitle" style={styles.workoutName}>
        {workoutName}
      </ThemedText>
      <ThemedText type="numeral" maxFontSizeMultiplier={1.3} style={styles.numeral}>
        {count}
      </ThemedText>
      {/*
        Below the numeral, not above it: the name is what a reader checks here, and pushing it further
        from the top for a line that only ever shows once would trade the screen's steady job for its
        first-run one. Renders nothing for everyone past their first session — see the component.
      */}
      <FirstSessionHint />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  label: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1.4,
  },
  numeral: {
    fontSize: 56,
    lineHeight: 64,
    color: RunnerColors.accent,
  },
  workoutName: {
    color: RunnerColors.text,
    textAlign: 'center',
  },
});
