import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

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
        GET READY
      </ThemedText>
      <ThemedText type="numeral" style={styles.numeral}>
        {count}
      </ThemedText>
      <ThemedText type="small" style={styles.workoutName}>
        {workoutName}
      </ThemedText>
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
    fontSize: 140,
    lineHeight: 140,
    color: RunnerColors.accent,
  },
  workoutName: {
    color: RunnerColors.textSecondary,
  },
});
