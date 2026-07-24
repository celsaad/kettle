import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type {
  BlockConfigOverride,
  EmomMinuteLog,
  Exercise,
  RepsSetLog,
  Session,
  SessionEntry,
  TimedHoldSetLog,
  Workout,
} from '@/domain/types';
import { cancelNotification, requestNotificationPermissions, scheduleRestCompleteNotification } from '@/hooks/safe-notifications';
import { useSessionSounds } from '@/hooks/use-session-sounds';
import { useSessionHistoryStore } from '@/state/session-history-store';

export type IntervalVariant = 'hiit' | 'emom' | 'amrap' | 'cardio';

export type RunnerStep =
  | {
      kind: 'hold';
      blockIndex: number;
      memberKey: string;
      exerciseId: string;
      exerciseName: string;
      holdTargetSec: number;
      holdTargetMaxSec?: number;
      setIndex: number;
      setTotal: number;
      notes?: string;
    }
  | {
      kind: 'reps';
      blockIndex: number;
      memberKey: string;
      exerciseId: string;
      exerciseName: string;
      targetReps: number;
      targetRepsMax?: number;
      setIndex: number;
      setTotal: number;
      notes?: string;
    }
  | {
      kind: 'interval';
      blockIndex: number;
      memberKey: string;
      exerciseId: string;
      exerciseName: string;
      variant: IntervalVariant;
      targetSec: number;
      /** true = count up with no auto-advance (cardio with no configured duration); false = countdown, auto-advances. */
      countUp: boolean;
      setIndex: number;
      setTotal: number;
      targetReps?: number;
      cardioDistanceMeters?: number;
      notes?: string;
    }
  // `standalone` distinguishes a dedicated Rest workout-block (its own logged session entry) from
  // inter-set/inter-round/inter-exercise rest folded into (or discarded after) the surrounding work.
  | { kind: 'rest'; blockIndex: number; memberKey: string; exerciseId: string; standalone: boolean; seconds: number };

function expandExercise(
  exercise: Exercise,
  blockIndex: number,
  memberKey: string,
  configOverride?: BlockConfigOverride,
  // Set when expanding a circuit member: the circuit's own `rounds` is what repeats the member, not
  // the member's own `sets` — so each visit is exactly one set, and no inter-set rest is inserted
  // (rest between visits is the circuit's own rest_between_exercises_sec/rest_between_rounds_sec).
  setsOverride?: number,
): RunnerStep[] {
  switch (exercise.type) {
    case 'timed_hold': {
      const sets = setsOverride ?? exercise.config.sets;
      const steps: RunnerStep[] = [];
      for (let i = 0; i < sets; i++) {
        steps.push({
          kind: 'hold',
          blockIndex,
          memberKey,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          holdTargetSec: exercise.config.holdSecMin,
          holdTargetMaxSec: exercise.config.holdSecMax,
          setIndex: i + 1,
          setTotal: sets,
          notes: exercise.notes,
        });
        if (setsOverride === undefined && i < sets - 1) {
          steps.push({ kind: 'rest', blockIndex, memberKey, exerciseId: exercise.id, standalone: false, seconds: exercise.config.restSec });
        }
      }
      return steps;
    }
    case 'reps': {
      const sets = setsOverride ?? exercise.config.sets;
      const steps: RunnerStep[] = [];
      for (let i = 0; i < sets; i++) {
        steps.push({
          kind: 'reps',
          blockIndex,
          memberKey,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          targetReps: exercise.config.targetRepsMin,
          targetRepsMax: exercise.config.targetRepsMax,
          setIndex: i + 1,
          setTotal: sets,
          notes: exercise.notes,
        });
        if (setsOverride === undefined && i < sets - 1) {
          steps.push({ kind: 'rest', blockIndex, memberKey, exerciseId: exercise.id, standalone: false, seconds: exercise.config.restSec });
        }
      }
      return steps;
    }
    case 'hiit': {
      const steps: RunnerStep[] = [];
      for (let i = 0; i < exercise.config.rounds; i++) {
        steps.push({
          kind: 'interval',
          blockIndex,
          memberKey,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          variant: 'hiit',
          targetSec: exercise.config.workSec,
          countUp: false,
          setIndex: i + 1,
          setTotal: exercise.config.rounds,
          notes: exercise.notes,
        });
        if (i < exercise.config.rounds - 1) {
          steps.push({ kind: 'rest', blockIndex, memberKey, exerciseId: exercise.id, standalone: false, seconds: exercise.config.restSec });
        }
      }
      return steps;
    }
    case 'emom': {
      const steps: RunnerStep[] = [];
      for (let i = 0; i < exercise.config.totalMinutes; i++) {
        steps.push({
          kind: 'interval',
          blockIndex,
          memberKey,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          variant: 'emom',
          targetSec: exercise.config.intervalSec,
          countUp: false,
          setIndex: i + 1,
          setTotal: exercise.config.totalMinutes,
          targetReps: exercise.config.targetReps,
          notes: exercise.notes,
        });
      }
      return steps;
    }
    case 'amrap':
      return [
        {
          kind: 'interval',
          blockIndex,
          memberKey,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          variant: 'amrap',
          targetSec: exercise.config.timeCapSec,
          countUp: false,
          setIndex: 1,
          setTotal: 1,
          notes: exercise.notes,
        },
      ];
    case 'cardio': {
      const hasDuration = exercise.config.durationSec !== undefined;
      return [
        {
          kind: 'interval',
          blockIndex,
          memberKey,
          exerciseId: exercise.id,
          exerciseName: exercise.name,
          variant: 'cardio',
          targetSec: exercise.config.durationSec ?? 0,
          countUp: !hasDuration,
          setIndex: 1,
          setTotal: 1,
          cardioDistanceMeters: exercise.config.distanceMeters,
          notes: exercise.notes,
        },
      ];
    }
    case 'rest':
      return [
        {
          kind: 'rest',
          blockIndex,
          memberKey,
          exerciseId: exercise.id,
          standalone: true,
          seconds: configOverride?.durationSec ?? exercise.config.durationSec,
        },
      ];
  }
}

/** Exported so callers (session.tsx) can check for a zero-step workout before ever starting a session. */
export function buildSteps(workout: Workout, exercises: Exercise[]): RunnerStep[] {
  const steps: RunnerStep[] = [];

  workout.blocks.forEach((block, blockIndex) => {
    if (block.kind === 'exercise') {
      const exercise = exercises.find((candidate) => candidate.id === block.exerciseId);
      if (!exercise) return;
      steps.push(...expandExercise(exercise, blockIndex, `${blockIndex}`, block.configOverride));
      return;
    }

    // Circuit: true round-robin (A,B,C,A,B,C,...) — each member's memberKey stays stable across
    // rounds so its sets accumulate into one session entry per exercise, not one per round.
    const members = block.members
      .map((member, memberIndex) => ({ member, memberIndex, exercise: exercises.find((candidate) => candidate.id === member.exerciseId) }))
      .filter((entry): entry is typeof entry & { exercise: Exercise } => !!entry.exercise);
    if (members.length === 0) return;

    for (let round = 0; round < block.rounds; round++) {
      members.forEach(({ member, memberIndex, exercise }, i) => {
        const memberKey = `${blockIndex}:${memberIndex}`;
        steps.push(...expandExercise(exercise, blockIndex, memberKey, member.configOverride, 1));
        const isLastMember = i === members.length - 1;
        if (!isLastMember && block.restBetweenExercisesSec) {
          steps.push({
            kind: 'rest',
            blockIndex,
            memberKey: `${blockIndex}:circuit-rest`,
            exerciseId: exercise.id,
            standalone: false,
            seconds: block.restBetweenExercisesSec,
          });
        }
      });
      const isLastRound = round === block.rounds - 1;
      if (!isLastRound && block.restBetweenRoundsSec) {
        steps.push({
          kind: 'rest',
          blockIndex,
          memberKey: `${blockIndex}:circuit-rest`,
          exerciseId: members[0].exercise.id,
          standalone: false,
          seconds: block.restBetweenRoundsSec,
        });
      }
    }
  });

  return steps;
}

type LastCommitBuffer = 'sets' | 'hiit' | 'emom';

// Forces a compile error at every call site below if RunnerStep/IntervalVariant grows a case that
// isn't handled — a new exercise type silently falling through to "no undo support" is a data-loss
// bug, not just a missing feature, so this is enforced rather than left to a comment.
function assertNever(value: never): never {
  throw new Error(`Unhandled RunnerStep/variant case: ${JSON.stringify(value)}`);
}

/**
 * Which pending buffer (if any) a step's `commitCurrentStep` call touches. Used by `goPrev()` to
 * undo precisely one level: the most recent `advance()`, no further.
 */
function bufferForStep(step: RunnerStep): LastCommitBuffer | null {
  switch (step.kind) {
    case 'hold':
    case 'reps':
      return 'sets';
    case 'interval':
      switch (step.variant) {
        case 'hiit':
          return 'hiit';
        case 'emom':
          return 'emom';
        case 'amrap':
        case 'cardio':
          return null;
        default:
          return assertNever(step.variant);
      }
    case 'rest':
      return null;
    default:
      return assertNever(step);
  }
}

/** True for step kinds whose commit calls `logEntry` directly rather than going through a pending buffer + flush. */
function isDirectLogStep(step: RunnerStep): boolean {
  switch (step.kind) {
    case 'hold':
    case 'reps':
      return false;
    case 'interval':
      switch (step.variant) {
        case 'amrap':
        case 'cardio':
          return true;
        case 'hiit':
        case 'emom':
          return false;
        default:
          return assertNever(step.variant);
      }
    case 'rest':
      return step.standalone;
    default:
      return assertNever(step);
  }
}

/**
 * A record of what the most recent `advance()` committed, precise enough for `goPrev()` to reverse
 * it exactly one level deep (a second `goPrev()` without an intervening `advance()` reverses nothing
 * further — see `goPrev` below).
 *
 * - `pending`: the commit only pushed/incremented a not-yet-flushed buffer entry for `memberKey`
 *   (member didn't change, so no flush ran) — undo pops/decrements it back off.
 * - `entry`: the commit produced a `SessionEntry` that's already been written to the session file,
 *   either via `flushMember` (buffer is the buffer it was built from) or a direct one-shot `logEntry`
 *   call (amrap/cardio/standalone rest — buffer is `null`, nothing to restore into a buffer).
 */
type LastCommit =
  | { kind: 'pending'; resultingIndex: number; memberKey: string; buffer: LastCommitBuffer }
  | { kind: 'entry'; resultingIndex: number; memberKey: string; buffer: LastCommitBuffer | null };

export type RestPreview = { label: string; detail: string } | null;

function formatHoldTarget(step: Extract<RunnerStep, { kind: 'hold' }>): string {
  return step.holdTargetMaxSec ? `${step.holdTargetSec}–${step.holdTargetMaxSec}s` : `${step.holdTargetSec}s`;
}

function formatRepsTarget(step: Extract<RunnerStep, { kind: 'reps' }>): string {
  return step.targetRepsMax ? `${step.targetReps}–${step.targetRepsMax}` : `${step.targetReps}`;
}

function previewFor(step: RunnerStep | undefined): RestPreview {
  if (!step) return null;
  if (step.kind === 'hold') {
    return { label: step.exerciseName, detail: `hold · set ${step.setIndex} of ${step.setTotal} · target ${formatHoldTarget(step)}` };
  }
  if (step.kind === 'reps') {
    return { label: step.exerciseName, detail: `reps · set ${step.setIndex} of ${step.setTotal} · target ${formatRepsTarget(step)}` };
  }
  if (step.kind === 'interval') {
    const progress = step.setTotal > 1 ? `round ${step.setIndex} of ${step.setTotal}` : `${step.targetSec}s`;
    return { label: step.exerciseName, detail: `${step.variant} · ${progress}` };
  }
  return null;
}

export function useSessionRunner(
  workout: Workout,
  exercises: Exercise[],
  programId: string | null,
  onComplete: () => void,
) {
  const steps = useMemo(() => buildSteps(workout, exercises), [workout, exercises]);
  const [stepIndex, setStepIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [holdElapsedSec, setHoldElapsedSec] = useState(0);
  const [restRemainingSec, setRestRemainingSec] = useState(0);
  const [restTargetSec, setRestTargetSec] = useState(0);
  const [reps, setReps] = useState(0);
  const [rpe, setRpe] = useState(8);
  const [roundsCompleted, setRoundsCompleted] = useState(0);
  const [extraReps, setExtraReps] = useState(0);

  const step = steps[stepIndex];
  const isCountdownStep = step?.kind === 'rest' || (step?.kind === 'interval' && !step.countUp);

  const { playTick, playExerciseChange } = useSessionSounds();

  const startSession = useSessionHistoryStore((state) => state.startSession);
  const logEntry = useSessionHistoryStore((state) => state.logEntry);
  const removeLastEntry = useSessionHistoryStore((state) => state.removeLastEntry);
  const completeSession = useSessionHistoryStore((state) => state.completeSession);

  const sessionRef = useRef<Session | null>(null);
  const pendingSetsRef = useRef<Record<string, (TimedHoldSetLog | RepsSetLog)[]>>({});
  const pendingHiitRoundsRef = useRef<Record<string, number>>({});
  const pendingEmomMinutesRef = useRef<Record<string, EmomMinuteLog[]>>({});
  // What the last advance() committed — consumed and cleared by goPrev() to undo exactly one level.
  const lastCommitRef = useRef<LastCommit | null>(null);
  const repsRef = useRef(reps);
  const rpeRef = useRef(rpe);
  const roundsCompletedRef = useRef(roundsCompleted);
  const extraRepsRef = useRef(extraReps);
  repsRef.current = reps;
  rpeRef.current = rpe;
  roundsCompletedRef.current = roundsCompleted;
  extraRepsRef.current = extraReps;

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
    sessionRef.current = startSession(workout.id, programId);
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
    const countdownTarget = step?.kind === 'rest' ? step.seconds : step?.kind === 'interval' && !step.countUp ? step.targetSec : 0;
    restTargetSecRef.current = countdownTarget;
    setHoldElapsedSec(0);
    setReps(0);
    setRoundsCompleted(0);
    setExtraReps(0);
    setRestRemainingSec(countdownTarget);
    setRestTargetSec(countdownTarget);
  }

  // Records `current`'s own contribution (the set/round/minute just finished, or the rest just
  // taken) into the pending-by-member buffers, or directly into the session for one-shot entries.
  const commitCurrentStep = useCallback(
    (current: RunnerStep) => {
      if (!sessionRef.current) return;
      if (current.kind === 'hold') {
        const sets = pendingSetsRef.current[current.memberKey] ?? [];
        sets.push({ holdSec: computeElapsedSec(), restTakenSec: 0 });
        pendingSetsRef.current[current.memberKey] = sets;
      } else if (current.kind === 'reps') {
        const sets = pendingSetsRef.current[current.memberKey] ?? [];
        sets.push({ reps: repsRef.current, rpe: rpeRef.current, restTakenSec: 0 });
        pendingSetsRef.current[current.memberKey] = sets;
      } else if (current.kind === 'interval') {
        if (current.variant === 'hiit') {
          pendingHiitRoundsRef.current[current.memberKey] = (pendingHiitRoundsRef.current[current.memberKey] ?? 0) + 1;
        } else if (current.variant === 'emom') {
          const minutes = pendingEmomMinutesRef.current[current.memberKey] ?? [];
          minutes.push({ reps: repsRef.current || undefined });
          pendingEmomMinutesRef.current[current.memberKey] = minutes;
        } else if (current.variant === 'amrap') {
          sessionRef.current = logEntry(sessionRef.current, {
            exercise: current.exerciseId,
            type: 'amrap',
            roundsCompleted: roundsCompletedRef.current,
            extraReps: extraRepsRef.current || undefined,
          });
        } else if (current.variant === 'cardio') {
          sessionRef.current = logEntry(sessionRef.current, {
            exercise: current.exerciseId,
            type: 'cardio',
            durationSec: computeElapsedSec(),
            distanceMeters: current.cardioDistanceMeters,
          });
        }
      } else if (current.kind === 'rest') {
        const takenSec = computeElapsedSec();
        if (current.standalone) {
          sessionRef.current = logEntry(sessionRef.current, {
            exercise: current.exerciseId,
            type: 'rest',
            restTakenSec: takenSec,
          });
        } else {
          const sets = pendingSetsRef.current[current.memberKey];
          if (sets && sets.length > 0) sets[sets.length - 1].restTakenSec = takenSec;
        }
      }
    },
    [computeElapsedSec, logEntry],
  );

  // Flushes whatever's pending for a member into the session. Looks at the buffers directly
  // (rather than the current step's kind) so it also works when the active step is the member's
  // interleaved rest — at most one member has unflushed data at a time, so this is unambiguous.
  const flushMember = useCallback(
    (memberKey: string, exerciseId: string) => {
      if (!sessionRef.current) return;

      const sets = pendingSetsRef.current[memberKey];
      if (sets && sets.length > 0) {
        const entry: SessionEntry =
          'holdSec' in sets[0]
            ? { exercise: exerciseId, type: 'timed_hold', sets: sets as TimedHoldSetLog[] }
            : { exercise: exerciseId, type: 'reps', sets: sets as RepsSetLog[] };
        sessionRef.current = logEntry(sessionRef.current, entry);
      }
      delete pendingSetsRef.current[memberKey];

      const roundsDone = pendingHiitRoundsRef.current[memberKey];
      if (roundsDone !== undefined) {
        sessionRef.current = logEntry(sessionRef.current, { exercise: exerciseId, type: 'hiit', roundsCompleted: roundsDone });
        delete pendingHiitRoundsRef.current[memberKey];
      }

      const minutes = pendingEmomMinutesRef.current[memberKey];
      if (minutes !== undefined) {
        sessionRef.current = logEntry(sessionRef.current, { exercise: exerciseId, type: 'emom', minutes });
        delete pendingEmomMinutesRef.current[memberKey];
      }
    },
    [logEntry],
  );

  const advance = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});

    setStepIndex((index) => {
      const current = steps[index];
      const next = steps[index + 1];
      const nextIndex = index + 1;

      if (current && sessionRef.current) {
        const buffer = bufferForStep(current);
        const directLog = isDirectLogStep(current);
        commitCurrentStep(current);
        const leavingMember = !next || next.memberKey !== current.memberKey;
        if (leavingMember) flushMember(current.memberKey, current.exerciseId);
        // A distinct cue from the plain countdown tick, so a change of exercise is audible even
        // without looking at the screen — but not for every set/round within the same exercise.
        if (leavingMember && next) playExerciseChange();

        // Track exactly what this advance() committed so goPrev() can undo it precisely (one level
        // only — see the LastCommit type doc).
        if (directLog) {
          lastCommitRef.current = { kind: 'entry', resultingIndex: nextIndex, memberKey: current.memberKey, buffer: null };
        } else if (buffer) {
          lastCommitRef.current = leavingMember
            ? { kind: 'entry', resultingIndex: nextIndex, memberKey: current.memberKey, buffer }
            : { kind: 'pending', resultingIndex: nextIndex, memberKey: current.memberKey, buffer };
        } else {
          // Non-standalone rest: mutates the already-pending last set's restTakenSec in place rather
          // than pushing/appending anything new, so redoing it later just overwrites that field again
          // — nothing to track or undo.
          lastCommitRef.current = null;
        }
      } else {
        lastCommitRef.current = null;
      }

      if (nextIndex >= steps.length) {
        if (sessionRef.current) sessionRef.current = completeSession(sessionRef.current);
        onComplete();
        return index;
      }
      return nextIndex;
    });
  }, [steps, onComplete, commitCurrentStep, flushMember, completeSession, playExerciseChange]);

  useEffect(() => {
    if (!step || paused) return;
    const id = setInterval(() => {
      if (step.kind === 'hold' || (step.kind === 'interval' && step.countUp)) {
        setHoldElapsedSec(computeElapsedSec());
      } else if (isCountdownStep) {
        const remaining = Math.max(0, restTargetSecRef.current - computeElapsedSec());
        setRestRemainingSec(remaining);
        if (remaining <= 0) advance();
        else if (remaining <= 3) playTick();
      }
    }, 1000);
    return () => clearInterval(id);
  }, [step, paused, isCountdownStep, advance, computeElapsedSec, playTick]);

  // Recompute from wall-clock timestamps on foreground return, and catch up an auto-advancing
  // countdown that fully elapsed while backgrounded — JS timers are throttled/suspended in the background.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active' || !step || paused) return;
      if (step.kind === 'hold' || (step.kind === 'interval' && step.countUp)) {
        setHoldElapsedSec(computeElapsedSec());
      } else if (isCountdownStep) {
        const remaining = Math.max(0, restTargetSecRef.current - computeElapsedSec());
        if (remaining <= 0) advance();
        else setRestRemainingSec(remaining);
      }
    });
    return () => subscription.remove();
  }, [step, paused, isCountdownStep, advance, computeElapsedSec]);

  // Local-notification fallback so the rest-complete cue still fires if the app is backgrounded.
  useEffect(() => {
    if (!step || !isCountdownStep || paused) return;
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
  }, [step, isCountdownStep, paused, computeElapsedSec, workout.name]);

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

  // Undoes exactly what the most recent advance() committed, if goPrev() is called immediately after
  // it (i.e. we're stepping back onto the step that commit belongs to) — one level deep only. A
  // second goPrev() in a row (no intervening advance()) finds lastCommitRef already cleared and just
  // moves the index, same as today.
  const goPrev = useCallback(() => {
    setStepIndex((index) => {
      const commit = lastCommitRef.current;
      if (commit && commit.resultingIndex === index && sessionRef.current) {
        if (commit.kind === 'pending') {
          const buffer = commit.buffer;
          switch (buffer) {
            case 'sets': {
              const sets = pendingSetsRef.current[commit.memberKey];
              if (sets && sets.length > 0) {
                sets.pop();
                if (sets.length === 0) delete pendingSetsRef.current[commit.memberKey];
              }
              break;
            }
            case 'hiit': {
              const count = pendingHiitRoundsRef.current[commit.memberKey];
              if (count !== undefined) {
                if (count > 1) pendingHiitRoundsRef.current[commit.memberKey] = count - 1;
                else delete pendingHiitRoundsRef.current[commit.memberKey];
              }
              break;
            }
            case 'emom': {
              const minutes = pendingEmomMinutesRef.current[commit.memberKey];
              if (minutes && minutes.length > 0) {
                minutes.pop();
                if (minutes.length === 0) delete pendingEmomMinutesRef.current[commit.memberKey];
              }
              break;
            }
            default:
              assertNever(buffer);
          }
        } else {
          const entries = sessionRef.current.entries;
          const lastEntry = entries[entries.length - 1];
          if (lastEntry) {
            sessionRef.current = removeLastEntry(sessionRef.current);
            // A multi-set hold/reps member (or a multi-round hiit/multi-minute emom flush) only had
            // its *last* contribution added by this commit — restore the rest back into the pending
            // buffer rather than discarding the whole flushed entry. `buffer` is null for direct-log
            // entries (amrap/cardio/standalone rest), which are single-shot and have nothing to restore.
            const buffer = commit.buffer;
            switch (buffer) {
              case 'sets':
                if ((lastEntry.type === 'timed_hold' || lastEntry.type === 'reps') && lastEntry.sets.length > 1) {
                  pendingSetsRef.current[commit.memberKey] = lastEntry.sets.slice(0, -1);
                }
                break;
              case 'hiit':
                if (lastEntry.type === 'hiit' && lastEntry.roundsCompleted > 1) {
                  pendingHiitRoundsRef.current[commit.memberKey] = lastEntry.roundsCompleted - 1;
                }
                break;
              case 'emom':
                if (lastEntry.type === 'emom' && lastEntry.minutes.length > 1) {
                  pendingEmomMinutesRef.current[commit.memberKey] = lastEntry.minutes.slice(0, -1);
                }
                break;
              case null:
                break;
              default:
                assertNever(buffer);
            }
          }
        }
        lastCommitRef.current = null;
      }
      return Math.max(0, index - 1);
    });
  }, [removeLastEntry]);

  // Ends the session on demand: the in-progress step is committed and its member flushed first,
  // so the current set/round (and anything already logged) is saved rather than discarded.
  const finishSession = useCallback(() => {
    if (step && sessionRef.current) {
      commitCurrentStep(step);
      flushMember(step.memberKey, step.exerciseId);
    }
    if (sessionRef.current) sessionRef.current = completeSession(sessionRef.current);
    onComplete();
  }, [step, commitCurrentStep, flushMember, completeSession, onComplete]);

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
    roundsCompleted,
    setRoundsCompleted,
    extraReps,
    setExtraReps,
    nextPreview: step?.kind === 'rest' ? previewFor(steps[stepIndex + 1]) : null,
    doneSet: advance,
    logSet: advance,
    skipRest: advance,
    logInterval: advance,
    addRestSeconds,
    goPrev,
    finishSession,
  };
}
