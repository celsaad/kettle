import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type { Exercise, RepsSetLog, Session, SessionEntry, TimedHoldSetLog, Workout } from '@/domain/types';
import { cancelNotification, requestNotificationPermissions, scheduleRestCompleteNotification } from '@/hooks/safe-notifications';
import { useSessionHistoryStore } from '@/state/session-history-store';

export type RunnerStep =
  | {
      kind: 'hold';
      blockIndex: number;
      exerciseId: string;
      exerciseName: string;
      holdTargetSec: number;
      setIndex: number;
      setTotal: number;
    }
  | {
      kind: 'reps';
      blockIndex: number;
      exerciseId: string;
      exerciseName: string;
      targetReps: number;
      setIndex: number;
      setTotal: number;
    }
  // `standalone` distinguishes a dedicated Rest workout-block (its own logged session entry) from
  // the inter-set rest bundled inside a hold/reps block (folded into that set's restTakenSec).
  | { kind: 'rest'; blockIndex: number; exerciseId: string; standalone: boolean; seconds: number };

function buildSteps(workout: Workout, exercises: Exercise[]): RunnerStep[] {
  const steps: RunnerStep[] = [];

  workout.blocks.forEach((block, blockIndex) => {
    const exercise = exercises.find((candidate) => candidate.id === block.exerciseId);
    if (!exercise) return;

    if (exercise.type === 'timed_hold') {
      for (let i = 0; i < exercise.config.sets; i++) {
        steps.push({
          kind: 'hold',
          blockIndex,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          holdTargetSec: exercise.config.holdSec,
          setIndex: i + 1,
          setTotal: exercise.config.sets,
        });
        if (i < exercise.config.sets - 1) {
          steps.push({ kind: 'rest', blockIndex, exerciseId: exercise.id, standalone: false, seconds: exercise.config.restSec });
        }
      }
    } else if (exercise.type === 'reps') {
      for (let i = 0; i < exercise.config.sets; i++) {
        steps.push({
          kind: 'reps',
          blockIndex,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          targetReps: exercise.config.targetReps,
          setIndex: i + 1,
          setTotal: exercise.config.sets,
        });
        if (i < exercise.config.sets - 1) {
          steps.push({ kind: 'rest', blockIndex, exerciseId: exercise.id, standalone: false, seconds: exercise.config.restSec });
        }
      }
    } else if (exercise.type === 'rest') {
      steps.push({
        kind: 'rest',
        blockIndex,
        exerciseId: exercise.id,
        standalone: true,
        seconds: block.configOverride?.durationSec ?? exercise.config.durationSec,
      });
    }
  });

  return steps;
}

export type RestPreview = { label: string; detail: string } | null;

function previewFor(step: RunnerStep | undefined): RestPreview {
  if (!step) return null;
  if (step.kind === 'hold') {
    return { label: step.exerciseName, detail: `hold · set ${step.setIndex} of ${step.setTotal} · target ${step.holdTargetSec}s` };
  }
  if (step.kind === 'reps') {
    return { label: step.exerciseName, detail: `reps · set ${step.setIndex} of ${step.setTotal} · target ${step.targetReps}` };
  }
  return null;
}

export function useSessionRunner(workout: Workout, exercises: Exercise[], onComplete: () => void) {
  const steps = useMemo(() => buildSteps(workout, exercises), [workout, exercises]);
  const [stepIndex, setStepIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [holdElapsedSec, setHoldElapsedSec] = useState(0);
  const [restRemainingSec, setRestRemainingSec] = useState(0);
  const [restTargetSec, setRestTargetSec] = useState(0);
  const [reps, setReps] = useState(0);
  const [rpe, setRpe] = useState(8);

  const step = steps[stepIndex];

  const startSession = useSessionHistoryStore((state) => state.startSession);
  const logEntry = useSessionHistoryStore((state) => state.logEntry);
  const completeSession = useSessionHistoryStore((state) => state.completeSession);

  const sessionRef = useRef<Session | null>(null);
  const pendingSetsRef = useRef<Record<number, (TimedHoldSetLog | RepsSetLog)[]>>({});
  const repsRef = useRef(reps);
  const rpeRef = useRef(rpe);
  repsRef.current = reps;
  rpeRef.current = rpe;

  // Wall-clock timing state (§7.1): the current phase's timing is anchored to real timestamps, not
  // accumulated setInterval ticks, so it stays correct across throttling and backgrounding.
  const phaseStartedAtRef = useRef(Date.now());
  const pausedAtRef = useRef<number | null>(null);
  const pausedMsRef = useRef(0);
  const restTargetSecRef = useRef(0);

  const computeElapsedSec = useCallback(() => {
    const now = pausedAtRef.current ?? Date.now();
    return Math.max(0, Math.floor((now - phaseStartedAtRef.current - pausedMsRef.current) / 1000));
  }, []);

  useEffect(() => {
    requestNotificationPermissions();
  }, []);

  useEffect(() => {
    if (workout.blocks.length === 0) return;
    sessionRef.current = startSession(workout.id);
    // Runs once per mounted workout: session should exist before any set can be logged.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Reset per-step transient state whenever the active step changes (adjusting state during
  // render on a key change, rather than in an effect — see https://react.dev/learn/you-might-not-need-an-effect).
  const [resetForStepIndex, setResetForStepIndex] = useState(stepIndex);
  if (resetForStepIndex !== stepIndex) {
    setResetForStepIndex(stepIndex);
    const now = Date.now();
    phaseStartedAtRef.current = now;
    pausedAtRef.current = paused ? now : null;
    pausedMsRef.current = 0;
    restTargetSecRef.current = step?.kind === 'rest' ? step.seconds : 0;
    setHoldElapsedSec(0);
    setReps(0);
    setRestRemainingSec(step?.kind === 'rest' ? step.seconds : 0);
    setRestTargetSec(restTargetSecRef.current);
  }

  const advance = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});

    setStepIndex((index) => {
      const current = steps[index];
      const next = steps[index + 1];

      if (current && sessionRef.current) {
        if (current.kind === 'hold') {
          const sets = pendingSetsRef.current[current.blockIndex] ?? [];
          sets.push({ holdSec: computeElapsedSec(), restTakenSec: 0 });
          pendingSetsRef.current[current.blockIndex] = sets;
        } else if (current.kind === 'reps') {
          const sets = pendingSetsRef.current[current.blockIndex] ?? [];
          sets.push({ reps: repsRef.current, rpe: rpeRef.current, restTakenSec: 0 });
          pendingSetsRef.current[current.blockIndex] = sets;
        } else if (current.kind === 'rest') {
          const takenSec = computeElapsedSec();
          if (current.standalone) {
            sessionRef.current = logEntry(sessionRef.current, {
              exercise: current.exerciseId,
              type: 'rest',
              restTakenSec: takenSec,
            });
          } else {
            const sets = pendingSetsRef.current[current.blockIndex];
            if (sets && sets.length > 0) sets[sets.length - 1].restTakenSec = takenSec;
          }
        }

        if ((current.kind === 'hold' || current.kind === 'reps') && (!next || next.blockIndex !== current.blockIndex)) {
          const sets = pendingSetsRef.current[current.blockIndex];
          if (sets && sets.length > 0) {
            const entry: SessionEntry =
              current.kind === 'hold'
                ? { exercise: current.exerciseId, type: 'timed_hold', sets: sets as TimedHoldSetLog[] }
                : { exercise: current.exerciseId, type: 'reps', sets: sets as RepsSetLog[] };
            sessionRef.current = logEntry(sessionRef.current, entry);
          }
          delete pendingSetsRef.current[current.blockIndex];
        }
      }

      const nextIndex = index + 1;
      if (nextIndex >= steps.length) {
        if (sessionRef.current) sessionRef.current = completeSession(sessionRef.current);
        onComplete();
        return index;
      }
      return nextIndex;
    });
  }, [steps, onComplete, computeElapsedSec, logEntry, completeSession]);

  useEffect(() => {
    if (!step || paused) return;
    const id = setInterval(() => {
      if (step.kind === 'hold') {
        setHoldElapsedSec(computeElapsedSec());
      } else if (step.kind === 'rest') {
        const remaining = Math.max(0, restTargetSecRef.current - computeElapsedSec());
        setRestRemainingSec(remaining);
        if (remaining <= 0) advance();
      }
    }, 1000);
    return () => clearInterval(id);
  }, [step, paused, advance, computeElapsedSec]);

  // Recompute from wall-clock timestamps on foreground return, and catch up an auto-advancing rest
  // that fully elapsed while backgrounded — JS timers are throttled/suspended in the background.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active' || !step || paused) return;
      if (step.kind === 'hold') {
        setHoldElapsedSec(computeElapsedSec());
      } else if (step.kind === 'rest') {
        const remaining = Math.max(0, restTargetSecRef.current - computeElapsedSec());
        if (remaining <= 0) advance();
        else setRestRemainingSec(remaining);
      }
    });
    return () => subscription.remove();
  }, [step, paused, advance, computeElapsedSec]);

  // Local-notification fallback so the rest-complete cue still fires if the app is backgrounded.
  useEffect(() => {
    if (!step || step.kind !== 'rest' || paused) return;
    let cancelled = false;
    let notificationId: string | null = null;

    const remaining = Math.max(1, restTargetSecRef.current - computeElapsedSec());
    scheduleRestCompleteNotification('Rest complete', `${workout.name} · back to work`, remaining).then((id) => {
      if (!id) return;
      if (cancelled) cancelNotification(id);
      else notificationId = id;
    });

    return () => {
      cancelled = true;
      if (notificationId) cancelNotification(notificationId);
    };
  }, [step, paused, computeElapsedSec, workout.name]);

  const togglePause = useCallback(() => {
    setPaused((wasPaused) => {
      const now = Date.now();
      if (!wasPaused) {
        pausedAtRef.current = now;
      } else if (pausedAtRef.current != null) {
        pausedMsRef.current += now - pausedAtRef.current;
        pausedAtRef.current = null;
      }
      return !wasPaused;
    });
  }, []);

  const goPrev = useCallback(() => {
    setStepIndex((index) => Math.max(0, index - 1));
  }, []);

  const addRestSeconds = useCallback(
    (amount: number) => {
      restTargetSecRef.current = Math.max(0, restTargetSecRef.current + amount);
      setRestTargetSec(restTargetSecRef.current);
      setRestRemainingSec(Math.max(0, restTargetSecRef.current - computeElapsedSec()));
    },
    [computeElapsedSec],
  );

  return {
    step,
    stepIndex,
    totalSteps: steps.length,
    blockIndex: step?.blockIndex ?? 0,
    blockTotal: workout.blocks.length,
    workoutName: workout.name,
    paused,
    setPaused: togglePause,
    holdElapsedSec,
    restRemainingSec,
    restTargetSec,
    reps,
    setReps,
    rpe,
    setRpe,
    nextPreview: step?.kind === 'rest' ? previewFor(steps[stepIndex + 1]) : null,
    doneSet: advance,
    logSet: advance,
    skipRest: advance,
    addRestSeconds,
    goPrev,
  };
}
