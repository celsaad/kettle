import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type {
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

// The step model and workout→steps expansion live in session-steps.ts so they can be tested without
// this file's native imports. Re-exported here because they're part of the runner's public surface.
export type { IntervalVariant, RunnerStep } from '@/hooks/session-steps';
export { buildSteps } from '@/hooks/session-steps';

import type { RunnerStep } from '@/hooks/session-steps';
import { buildSteps } from '@/hooks/session-steps';

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

/**
 * What's coming up after the current step — skips a single immediately-following rest step (whether
 * inter-set or a standalone Rest block) so it always previews the next real work, not "Rest". This is
 * the same lookahead a rest screen always did (its own next step is essentially never itself rest, so
 * previewFor(steps[index + 1]) already landed on real work) — generalized so an exercise screen, whose
 * very next step is usually its own trailing rest, gets the same "what's actually next" preview.
 */
function upcomingPreview(steps: RunnerStep[], index: number): RestPreview {
  const next = steps[index + 1];
  return previewFor(next?.kind === 'rest' ? steps[index + 2] : next);
}

export function useSessionRunner(
  workout: Workout,
  exercises: Exercise[],
  programId: string | null,
  programWeek: number | null,
  programDay: string | null,
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
  /** 0 means bodyweight — `weightKg` is optional on RepsSetLog, and 0 is logged as absent, not as a load of zero. */
  const [weightKg, setWeightKg] = useState(0);
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
  /**
   * The authoritative "where are we now" for advance()/goPrev().
   *
   * Those used to read the index from inside a setStepIndex updater, which meant every commit,
   * flush, logEntry and completeSession call ran *inside* that updater. React is free to re-invoke an
   * updater (StrictMode's dev double-invoke, or a concurrent render being replayed), and here that
   * would duplicate session entries and file writes — a data bug, not just wasted work. Reading the
   * index from a ref lets the side effects run once, in the event handler, with the updater gone.
   *
   * Advanced eagerly by both functions so two calls landing in the same tick (the ticking interval and
   * the foreground catch-up can both fire advance()) see the updated position rather than repeating one.
   */
  const stepIndexRef = useRef(stepIndex);
  stepIndexRef.current = stepIndex;
  const repsRef = useRef(reps);
  const rpeRef = useRef(rpe);
  const weightKgRef = useRef(weightKg);
  const roundsCompletedRef = useRef(roundsCompleted);
  const extraRepsRef = useRef(extraReps);
  repsRef.current = reps;
  rpeRef.current = rpe;
  weightKgRef.current = weightKg;
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
    sessionRef.current = startSession(workout.id, programId, programWeek, programDay);
    // Runs once per mounted workout: session should exist before any set can be logged.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Reset per-step transient state whenever the active step changes (adjusting state during
  // render on a key change, rather than in an effect — see https://react.dev/learn/you-might-not-need-an-effect).
  // Seeded to -1 (never a real stepIndex) rather than stepIndex itself, so this also fires on the very
  // first render: seeding it with stepIndex made the first step's target look identical to "no change",
  // so restTargetSecRef stayed at its useRef(0) default for a countdown-type first step (hiit/emom/amrap,
  // or a leading standalone rest block) — the ticking effect then saw remaining <= 0 almost immediately
  // and auto-advanced before the timer ever really ran.
  const [resetForStepIndex, setResetForStepIndex] = useState(-1);
  if (resetForStepIndex !== stepIndex) {
    setResetForStepIndex(stepIndex);
    const now = Date.now();
    phaseStartedAtRef.current = now;
    pausedAtRef.current = paused ? now : null;
    pausedMsRef.current = 0;
    const countdownTarget = step?.kind === 'rest' ? step.seconds : step?.kind === 'interval' && !step.countUp ? step.targetSec : 0;
    restTargetSecRef.current = countdownTarget;
    setHoldElapsedSec(0);
    // Reps start at the set's target, not 0: hitting the target is the common case, so counting up
    // from zero one tap at a time made the expected outcome the most expensive one to record. Reps
    // re-seed from the target every set rather than carrying the last set's value, because varying
    // reps between sets is the normal thing being logged — unlike load, below.
    setReps(step?.kind === 'reps' ? step.targetReps : 0);
    // Load carries across sets of the same exercise: whatever was actually lifted on the previous set
    // is the best default for the next one, and snapping back to the configured target every set would
    // make any mid-workout adjustment need re-entering. Falls back to the target on the first set of
    // an exercise, since there's nothing logged yet to carry.
    if (step?.kind === 'reps') {
      const priorSets = pendingSetsRef.current[step.memberKey];
      const lastLogged = priorSets?.at(-1);
      const carried = lastLogged && 'reps' in lastLogged ? lastLogged.weightKg : undefined;
      setWeightKg(carried ?? step.targetWeightKg ?? 0);
    }
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
        // `|| undefined` so bodyweight (0) stays absent from the log rather than recording a 0 kg load —
        // entryVolume distinguishes the two, summing reps×weight only when a weight is actually present.
        sets.push({ reps: repsRef.current, weightKg: weightKgRef.current || undefined, rpe: rpeRef.current, restTakenSec: 0 });
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

    {
      const index = stepIndexRef.current;
      const current = steps[index];
      const next = steps[index + 1];
      const nextIndex = index + 1;

      if (current && sessionRef.current) {
        const buffer = bufferForStep(current);
        const directLog = isDirectLogStep(current);
        commitCurrentStep(current);

        // Two different questions, and conflating them was a bug. Flushing asks "is this member
        // finished for the whole workout?" — in a circuit the member comes back next round, and
        // flushing on the immediate hand-off wrote one entry *per round* instead of accumulating its
        // sets into one, contradicting the intent stated in session-steps.ts's expansion. The audio
        // cue asks the narrower "are we moving to a different exercise right now?", which is still
        // true on every circuit hand-off.
        const changingExercise = !next || next.memberKey !== current.memberKey;
        const memberDone = !steps.slice(nextIndex).some((later) => later.memberKey === current.memberKey);
        if (memberDone) flushMember(current.memberKey, current.exerciseId);
        // A distinct cue from the plain countdown tick, so a change of exercise is audible even
        // without looking at the screen — but not for every set/round within the same exercise.
        if (changingExercise && next) playExerciseChange();

        // Track exactly what this advance() committed so goPrev() can undo it precisely (one level
        // only — see the LastCommit type doc).
        if (directLog) {
          lastCommitRef.current = { kind: 'entry', resultingIndex: nextIndex, memberKey: current.memberKey, buffer: null };
        } else if (buffer) {
          lastCommitRef.current = memberDone
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
        return;
      }

      stepIndexRef.current = nextIndex;
      setStepIndex(nextIndex);
    }
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
    // restTargetSec is in the deps as a *change signal*, not because the body reads it: the remaining
    // time comes from restTargetSecRef, which no dependency tracks. Without it, "+30s" mutated the ref
    // while every dep stayed equal, so the effect never re-ran and the notification still fired at the
    // original end time. Both places that move the ref also set this state, so it's a faithful signal.
  }, [step, isCountdownStep, paused, restTargetSec, computeElapsedSec, workout.name]);

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
    {
      const index = stepIndexRef.current;
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

      const prevIndex = Math.max(0, index - 1);
      stepIndexRef.current = prevIndex;
      setStepIndex(prevIndex);
    }
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
    weightKg,
    setWeightKg,
    roundsCompleted,
    setRoundsCompleted,
    extraReps,
    setExtraReps,
    nextPreview: upcomingPreview(steps, stepIndex),
    doneSet: advance,
    logSet: advance,
    skipRest: advance,
    logInterval: advance,
    addRestSeconds,
    goPrev,
    finishSession,
  };
}
