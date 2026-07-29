import { router, useLocalSearchParams } from 'expo-router';
import type { ErrorBoundaryProps } from 'expo-router';
import { useKeepAwake } from 'expo-keep-awake';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ErrorFallback } from '@/components/error-fallback';
import { SessionComplete } from '@/components/session-complete';
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
import { useSessionAnnouncements } from '@/hooks/use-session-announcements';
import { buildSteps, useSessionRunner } from '@/hooks/use-session-runner';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * The one screen where a render throw costs data, so its boundary does more than apologise.
 *
 * Every logged set reaches the session file as it happens (§7.2), so all of them survive a throw — but
 * the runner writes `ended_at` only when the whole workout does, and a session without it counts as
 * zero minutes in every stat tile and is skipped outright by `exerciseHistory`. Those entries would sit
 * on disk and appear nowhere in the app. React has already unmounted the runner and the ref holding
 * that `Session` by the time this renders, which is why the id is tracked in the store rather than
 * passed down here.
 *
 * What this *can't* salvage, and the copy is careful not to claim: the set being performed right now,
 * which has no logged value until the user advances off it.
 *
 * Deliberately no retry, unlike the shared boundaries: re-rendering this route restarts it from the
 * countdown, and mounting the runner again calls `startSession` — a second session file for one
 * workout, with the first one's sets stranded in it. "Go to History" instead, where the salvaged
 * session is the top row.
 */
export function ErrorBoundary({ error }: ErrorBoundaryProps) {
  const { t } = useTranslation();
  const abandonActiveSession = useSessionHistoryStore((state) => state.abandonActiveSession);

  useEffect(() => {
    abandonActiveSession();
  }, [abandonActiveSession]);

  return (
    <ErrorFallback
      title={t('errorBoundary.session.title')}
      body={t('errorBoundary.session.body')}
      error={error}
      primary={{ label: t('errorBoundary.session.toHistory'), onPress: () => router.dismissTo('/history') }}
    />
  );
}

export default function SessionScreen() {
  // suppressDeactivateWarnings is the library's own escape hatch for this exact race, and without it
  // leaving this screen always threw. useKeepAwake's cleanup calls deactivateKeepAwake() with no
  // .catch() unless the flag is set; on web, activate() only records the tag *after* awaiting
  // navigator.wakeLock.request(), so any unmount that beats that promise — which is every unmount
  // here, since finishing/closing navigates immediately — leaves deactivate with no tag to release
  // and it throws ERR_KEEP_AWAKE_TAG_INVALID ("The wake lock with tag _r_N_ has not activated yet")
  // as an unhandled rejection. The flag makes that path .catch(() => {}) instead. Nothing is leaked:
  // the browser releases a screen wake lock on its own when the page/tab goes away.
  useKeepAwake(undefined, { suppressDeactivateWarnings: true });
  const { t } = useTranslation();
  const [completed, setCompleted] = useState(false);
  // Doesn't navigate back directly — advance()/finishSession() in use-session-runner.ts already call
  // this the moment the workout is done (naturally or via "Finish"), and by then the session has
  // already saved. Showing a completion screen first (rather than snapping straight back to wherever
  // the session was started from) gives that moment somewhere to land instead of just vanishing.
  const onComplete = useCallback(() => setCompleted(true), []);
  const library = useLibraryStore((state) => state.library);
  const { workoutId, programId, week, day } = useLocalSearchParams<{
    workoutId?: string;
    programId?: string;
    week?: string;
    day?: string;
  }>();
  const [started, setStarted] = useState(false);

  const resolved = useMemo(() => {
    if (!library) return null;
    if (workoutId) {
      const found = library.workouts.find((candidate) => candidate.id === workoutId);
      return found ? { workout: found, exercises: library.exercises, programId: null, week: null, day: null } : null;
    }
    if (programId && week) {
      const program = library.programs.find((candidate) => candidate.id === programId);
      const weekNumber = Number(week);
      if (!program || Number.isNaN(weekNumber)) return null;
      const resolvedWeek = resolveWorkoutForWeek(program, weekNumber, library, day);
      return resolvedWeek ? { ...resolvedWeek, programId, week: weekNumber, day: day ?? null } : null;
    }
    return { workout: library.workouts[0], exercises: library.exercises, programId: null, week: null, day: null };
  }, [library, workoutId, programId, week, day]);

  const workout = resolved?.workout;
  // resolved is already memoized (stable reference unless its own deps change), so memoizing off it
  // rather than `resolved?.exercises ?? []` directly keeps `exercises` from getting a fresh array
  // identity every render while resolved is null (before library loads).
  const exercises = useMemo(() => resolved?.exercises ?? [], [resolved]);
  // Computed unconditionally (hooks can't follow the early returns below) so a workout that resolves
  // to zero runnable steps — no blocks, or every block's exercise has sets/rounds/minutes at 0 — can be
  // caught before ever showing the pre-session countdown, instead of leaving the user on a blank screen
  // with nothing to tap once the countdown finishes.
  const steps = useMemo(() => (workout ? buildSteps(workout, exercises) : []), [workout, exercises]);

  if (!workout) return null;

  if (steps.length === 0) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <View style={styles.emptyState}>
            <ThemedText type="subtitle" style={styles.emptyStateTitle}>
              {t('session.nothingToRun.title')}
            </ThemedText>
            <ThemedText type="small" style={styles.emptyStateBody}>
              {t('session.nothingToRun.body', { name: workout.name })}
            </ThemedText>
            <Pressable
              onPress={() => router.back()}
              style={styles.emptyStateButton}
              accessibilityRole="button"
              accessibilityLabel={t('common.close')}>
              <ThemedText type="heading" style={styles.emptyStateButtonLabel}>
                {t('common.close')}
              </ThemedText>
            </Pressable>
          </View>
        </View>
      </SafeAreaView>
    );
  }

  if (!started) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <SessionCountdown workoutName={workout.name} onDone={() => setStarted(true)} />
        </View>
      </SafeAreaView>
    );
  }

  if (completed) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top', 'bottom', 'left', 'right']}>
        <View style={styles.content}>
          <SessionComplete workoutName={workout.name} onDone={() => router.back()} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <ActiveSession
      workout={workout}
      exercises={exercises}
      programId={resolved?.programId ?? null}
      programWeek={resolved?.week ?? null}
      programDay={resolved?.day ?? null}
      onComplete={onComplete}
    />
  );
}

function ActiveSession({
  workout,
  exercises,
  programId,
  programWeek,
  programDay,
  onComplete,
}: {
  workout: Workout;
  exercises: Exercise[];
  programId: string | null;
  programWeek: number | null;
  programDay: string | null;
  onComplete: () => void;
}) {
  const runner = useSessionRunner(workout, exercises, programId, programWeek, programDay, onComplete);
  const { t } = useTranslation();

  /**
   * One sentence per step, rebuilt only when the step itself changes — the hook dedupes, but keeping
   * this out of the tick path means the string isn't reassembled sixty times a minute either.
   * Names come from the user's library, so they're interpolated rather than translated.
   */
  const announcement = useMemo(() => {
    const current = runner.step;
    if (!current) return null;
    switch (current.kind) {
      case 'hold':
        return t('session.announce.hold', {
          name: current.exerciseName,
          index: current.setIndex,
          total: current.setTotal,
          target: current.holdTargetSec,
        });
      case 'reps':
        return t('session.announce.reps', {
          name: current.exerciseName,
          index: current.setIndex,
          total: current.setTotal,
          target: current.targetReps,
        });
      case 'interval':
        return t('session.announce.interval', {
          name: current.exerciseName,
          index: current.setIndex,
          total: current.setTotal,
          seconds: current.targetSec,
        });
      case 'rest':
        return t('session.announce.rest', { seconds: current.seconds });
    }
  }, [runner.step, t]);

  useSessionAnnouncements(announcement);
  const { step } = runner;

  const confirmFinish = useCallback(() => {
    Alert.alert(t('session.finish.confirmTitle'), t('session.finish.confirmBody'), [
      { text: t('common.cancel'), style: 'cancel' },
      { text: t('session.finish.confirmAction'), style: 'destructive', onPress: runner.finishSession },
    ]);
  }, [runner.finishSession, t]);

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
            <Pressable
              onPress={confirmFinish}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel={t('session.finish.confirmTitle')}>
              <ThemedText type="code" style={styles.finishLabel}>
                {t('session.finish.label')}
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
            next={runner.nextPreview}
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
            weightKg={runner.weightKg}
            onChangeWeightKg={runner.setWeightKg}
            notes={step.notes}
            next={runner.nextPreview}
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
            next={runner.nextPreview}
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
            onPrev={runner.goPrev}
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
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  emptyStateTitle: {
    color: RunnerColors.text,
  },
  emptyStateBody: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
  },
  emptyStateButton: {
    marginTop: Spacing.two,
    height: 52,
    minWidth: 160,
    borderRadius: 15,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyStateButtonLabel: {
    color: RunnerColors.background,
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
