import { useCallback, useEffect, useMemo, useState } from 'react';

import type { Exercise, Workout } from '@/domain/types';

export type RunnerStep =
  | { kind: 'hold'; blockIndex: number; exerciseName: string; holdTargetSec: number; setIndex: number; setTotal: number }
  | { kind: 'reps'; blockIndex: number; exerciseName: string; targetReps: number; setIndex: number; setTotal: number }
  | { kind: 'rest'; blockIndex: number; seconds: number };

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
          exerciseName: exercise.name,
          holdTargetSec: exercise.config.holdSec,
          setIndex: i + 1,
          setTotal: exercise.config.sets,
        });
        if (i < exercise.config.sets - 1) {
          steps.push({ kind: 'rest', blockIndex, seconds: exercise.config.restSec });
        }
      }
    } else if (exercise.type === 'reps') {
      for (let i = 0; i < exercise.config.sets; i++) {
        steps.push({
          kind: 'reps',
          blockIndex,
          exerciseName: exercise.name,
          targetReps: exercise.config.targetReps,
          setIndex: i + 1,
          setTotal: exercise.config.sets,
        });
        if (i < exercise.config.sets - 1) {
          steps.push({ kind: 'rest', blockIndex, seconds: exercise.config.restSec });
        }
      }
    } else if (exercise.type === 'rest') {
      steps.push({ kind: 'rest', blockIndex, seconds: block.configOverride?.durationSec ?? exercise.config.durationSec });
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
  const [reps, setReps] = useState(0);
  const [rpe, setRpe] = useState(8);

  const step = steps[stepIndex];

  // Reset per-step transient state whenever the active step changes (adjusting state during
  // render on a key change, rather than in an effect — see https://react.dev/learn/you-might-not-need-an-effect).
  const [resetForStepIndex, setResetForStepIndex] = useState(stepIndex);
  if (resetForStepIndex !== stepIndex) {
    setResetForStepIndex(stepIndex);
    setHoldElapsedSec(0);
    setReps(0);
    setRestRemainingSec(step?.kind === 'rest' ? step.seconds : 0);
  }

  const advance = useCallback(() => {
    setStepIndex((index) => {
      const nextIndex = index + 1;
      if (nextIndex >= steps.length) {
        onComplete();
        return index;
      }
      return nextIndex;
    });
  }, [steps.length, onComplete]);

  useEffect(() => {
    if (!step || paused) return;
    const id = setInterval(() => {
      if (step.kind === 'hold') {
        setHoldElapsedSec((sec) => sec + 1);
      } else if (step.kind === 'rest') {
        setRestRemainingSec((sec) => {
          if (sec <= 1) {
            advance();
            return 0;
          }
          return sec - 1;
        });
      }
    }, 1000);
    return () => clearInterval(id);
  }, [step, paused, advance]);

  const goPrev = useCallback(() => {
    setStepIndex((index) => Math.max(0, index - 1));
  }, []);

  const addRestSeconds = useCallback((amount: number) => {
    setRestRemainingSec((sec) => Math.max(0, sec + amount));
  }, []);

  return {
    step,
    stepIndex,
    totalSteps: steps.length,
    blockIndex: step?.blockIndex ?? 0,
    blockTotal: workout.blocks.length,
    workoutName: workout.name,
    paused,
    setPaused,
    holdElapsedSec,
    restRemainingSec,
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
