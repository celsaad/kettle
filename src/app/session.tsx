import { router } from 'expo-router';
import { useCallback } from 'react';
import { StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SessionHold } from '@/components/session-hold';
import { SessionProgressDots } from '@/components/session-progress-dots';
import { SessionReps } from '@/components/session-reps';
import { SessionRest } from '@/components/session-rest';
import { ThemedText } from '@/components/themed-text';
import { RunnerColors, MaxContentWidth, Spacing } from '@/constants/theme';
import { workouts } from '@/constants/mock-data';
import { useSessionRunner } from '@/hooks/use-session-runner';

const workout = workouts[0];

export default function SessionScreen() {
  const onComplete = useCallback(() => router.back(), []);
  const runner = useSessionRunner(workout, onComplete);
  const { step } = runner;

  if (!step) return null;

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
      <View style={styles.content}>
        <View style={styles.header}>
          <ThemedText type="small" style={styles.workoutName}>
            {runner.workoutName}
          </ThemedText>
          <SessionProgressDots total={runner.blockTotal} activeIndex={runner.blockIndex} />
        </View>

        {step.kind === 'hold' && (
          <SessionHold
            exerciseName={step.exerciseName}
            setIndex={step.setIndex}
            setTotal={step.setTotal}
            targetSec={step.holdTargetSec}
            elapsedSec={runner.holdElapsedSec}
            paused={runner.paused}
            onTogglePause={() => runner.setPaused((paused) => !paused)}
            onPrev={runner.goPrev}
            onDone={runner.doneSet}
          />
        )}

        {step.kind === 'reps' && (
          <SessionReps
            exerciseName={step.exerciseName}
            setIndex={step.setIndex}
            setTotal={step.setTotal}
            targetReps={step.targetReps}
            reps={runner.reps}
            onChangeReps={runner.setReps}
            rpe={runner.rpe}
            onChangeRpe={runner.setRpe}
            onPrev={runner.goPrev}
            onLogSet={runner.logSet}
          />
        )}

        {step.kind === 'rest' && (
          <SessionRest
            secondsRemaining={runner.restRemainingSec}
            totalSeconds={step.seconds}
            next={runner.nextPreview}
            onAddSeconds={runner.addRestSeconds}
            onSkip={runner.skipRest}
          />
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: RunnerColors.background,
  },
  content: {
    flex: 1,
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.two,
    paddingBottom: Spacing.three,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.four,
  },
  workoutName: {
    color: RunnerColors.textSecondary,
  },
});
