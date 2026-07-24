import { router, useLocalSearchParams } from 'expo-router';
import { useKeepAwake } from 'expo-keep-awake';
import { useCallback, useMemo, useState } from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SessionCountdown } from '@/components/session-countdown';
import { SessionHold } from '@/components/session-hold';
import { SessionInterval } from '@/components/session-interval';
import { SessionProgressDots } from '@/components/session-progress-dots';
import { SessionReps } from '@/components/session-reps';
import { SessionRest } from '@/components/session-rest';
import { ThemedText } from '@/components/themed-text';
import { RunnerColors, MaxContentWidth, Spacing } from '@/constants/theme';
import { resolveWorkoutForWeek } from '@/domain/program';
import type { Exercise, Workout } from '@/domain/types';
import { useSessionRunner } from '@/hooks/use-session-runner';
import { useLibraryStore } from '@/state/library-store';

export default function SessionScreen() {
  useKeepAwake();
  const onComplete = useCallback(() => router.back(), []);
  const library = useLibraryStore((state) => state.library);
  const { workoutId, programId, week } = useLocalSearchParams<{ workoutId?: string; programId?: string; week?: string }>();
  const [started, setStarted] = useState(false);

  const resolved = useMemo(() => {
    if (!library) return null;
    if (workoutId) {
      const found = library.workouts.find((candidate) => candidate.id === workoutId);
      return found ? { workout: found, exercises: library.exercises, programId: null } : null;
    }
    if (programId && week) {
      const program = library.programs.find((candidate) => candidate.id === programId);
      const weekNumber = Number(week);
      if (!program || Number.isNaN(weekNumber)) return null;
      const resolvedWeek = resolveWorkoutForWeek(program, weekNumber, library);
      return resolvedWeek ? { ...resolvedWeek, programId } : null;
    }
    return { workout: library.workouts[0], exercises: library.exercises, programId: null };
  }, [library, workoutId, programId, week]);

  const workout = resolved?.workout;
  const exercises = resolved?.exercises ?? [];

  if (!workout) return null;

  if (!started) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <SessionCountdown workoutName={workout.name} onDone={() => setStarted(true)} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <ActiveSession workout={workout} exercises={exercises} programId={resolved?.programId ?? null} onComplete={onComplete} />
  );
}

function ActiveSession({
  workout,
  exercises,
  programId,
  onComplete,
}: {
  workout: Workout;
  exercises: Exercise[];
  programId: string | null;
  onComplete: () => void;
}) {
  const runner = useSessionRunner(workout, exercises, programId, onComplete);
  const { step } = runner;

  const confirmFinish = useCallback(() => {
    Alert.alert('Finish session?', 'Your progress, including the current set, will be saved.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Finish', style: 'destructive', onPress: runner.finishSession },
    ]);
  }, [runner.finishSession]);

  if (!step) return null;

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
      <View style={styles.content}>
        <View style={styles.header}>
          <ThemedText type="small" style={styles.workoutName}>
            {runner.workoutName}
          </ThemedText>
          <View style={styles.headerRight}>
            <SessionProgressDots total={runner.blockTotal} activeIndex={runner.blockIndex} />
            <Pressable onPress={confirmFinish} hitSlop={8}>
              <ThemedText type="code" style={styles.finishLabel}>
                Finish
              </ThemedText>
            </Pressable>
          </View>
        </View>

        {step.kind === 'hold' && (
          <SessionHold
            exerciseName={step.exerciseName}
            setIndex={step.setIndex}
            setTotal={step.setTotal}
            targetSec={step.holdTargetSec}
            targetMaxSec={step.holdTargetMaxSec}
            elapsedSec={runner.holdElapsedSec}
            paused={runner.paused}
            notes={step.notes}
            onTogglePause={runner.setPaused}
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
            targetRepsMax={step.targetRepsMax}
            reps={runner.reps}
            onChangeReps={runner.setReps}
            rpe={runner.rpe}
            onChangeRpe={runner.setRpe}
            notes={step.notes}
            onPrev={runner.goPrev}
            onLogSet={runner.logSet}
          />
        )}

        {step.kind === 'interval' && (
          <SessionInterval
            exerciseName={step.exerciseName}
            variant={step.variant}
            setIndex={step.setIndex}
            setTotal={step.setTotal}
            targetSec={step.targetSec}
            countUp={step.countUp}
            elapsedSec={runner.holdElapsedSec}
            remainingSec={runner.restRemainingSec}
            targetReps={step.targetReps}
            cardioDistanceMeters={step.cardioDistanceMeters}
            notes={step.notes}
            paused={runner.paused}
            onTogglePause={runner.setPaused}
            reps={runner.reps}
            onChangeReps={runner.setReps}
            roundsCompleted={runner.roundsCompleted}
            onChangeRoundsCompleted={runner.setRoundsCompleted}
            extraReps={runner.extraReps}
            onChangeExtraReps={runner.setExtraReps}
            onPrev={runner.goPrev}
            onDone={runner.logInterval}
          />
        )}

        {step.kind === 'rest' && (
          <SessionRest
            secondsRemaining={runner.restRemainingSec}
            totalSeconds={runner.restTargetSec}
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
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  finishLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
});
