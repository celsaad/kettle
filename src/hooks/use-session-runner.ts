import * as Haptics from 'expo-haptics';
import { t } from 'i18next';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type { EmomMinuteLog, Exercise, RepsSetLog, Session, SessionEntry, TimedHoldSetLog, Workout } from '@/domain/types';
import {
  cancelNotification,
  requestNotificationPermissions,
  scheduleRestCompleteNotification,
} from '@/hooks/safe-notifications';
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
 * Which of the member's accumulating logs (if any) a step's `commitCurrentStep` call grows. Used by
 * `goPrev()` to undo precisely one level: the most recent `advance()`, no further.
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

/** True for step kinds whose commit logs a one-shot entry rather than growing a member's accumulating log. */
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
 * Every commit reaches the session file (see `persistMember`), so what varies is *how* to take it
 * back, not whether anything was written:
 *
 * - `buffer` set: the commit grew that member's accumulating log, so undo shrinks it back by one and
 *   rewrites the member's entry — or retracts the entry entirely if that was its first contribution.
 * - `buffer: null`: a one-shot `logEntry` (amrap/cardio/standalone rest), which has no accumulating
 *   buffer behind it — undo just removes the entry it appended.
 */
type LastCommit = { resultingIndex: number; memberKey: string; exerciseId: string; buffer: LastCommitBuffer | null };

export type RestPreview = { label: string; detail: string } | null;

function formatHoldTarget(step: Extract<RunnerStep, { kind: 'hold' }>): string {
  return step.holdTargetMaxSec ? `${step.holdTargetSec}–${step.holdTargetMaxSec}s` : `${step.holdTargetSec}s`;
}

function formatRepsTarget(step: Extract<RunnerStep, { kind: 'reps' }>): string {
  return step.targetRepsMax ? `${step.targetReps}–${step.targetRepsMax}` : `${step.targetReps}`;
}

function previewFor(step: RunnerStep | undefined): RestPreview {
  if (!step) return null;
  // `label` stays the user's own exercise name — never translated.
  if (step.kind === 'hold') {
    const detail = t('preview.hold', { index: step.setIndex, total: step.setTotal, target: formatHoldTarget(step) });
    return { label: step.exerciseName, detail };
  }
  if (step.kind === 'reps') {
    const detail = t('preview.reps', { index: step.setIndex, total: step.setTotal, target: formatRepsTarget(step) });
    return { label: step.exerciseName, detail };
  }
  if (step.kind === 'interval') {
    const progress =
      step.setTotal > 1
        ? t('preview.round', { index: step.setIndex, total: step.setTotal })
        : t('preview.seconds', { n: step.targetSec });
    return { label: step.exerciseName, detail: t('preview.interval', { variant: step.variant, progress }) };
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

  const { playTick, playExerciseChange, playMilestone } = useSessionSounds();

  /**
   * Which step index the milestone chime has already sounded for. Both triggers are threshold
   * conditions ("elapsed past the target", "past halfway") that stay true for the rest of the step, so
   * the 1Hz tick would otherwise re-fire them every second. Keyed on the step index rather than reset
   * on change, so it's correct even if the ticking effect re-runs mid-step (pause/resume rebuilds it).
   */
  const milestoneFiredForStepRef = useRef(-1);
  const fireMilestone = useCallback(() => {
    if (milestoneFiredForStepRef.current === stepIndexRef.current) return;
    milestoneFiredForStepRef.current = stepIndexRef.current;
    playMilestone();
  }, [playMilestone]);

  const startSession = useSessionHistoryStore((state) => state.startSession);
  const logEntry = useSessionHistoryStore((state) => state.logEntry);
  const replaceEntry = useSessionHistoryStore((state) => state.replaceEntry);
  const removeLastEntry = useSessionHistoryStore((state) => state.removeLastEntry);
  const completeSession = useSessionHistoryStore((state) => state.completeSession);

  const sessionRef = useRef<Session | null>(null);
  /**
   * Each member's accumulating log — the sets/rounds/minutes done so far, keyed by `memberKey` so a
   * circuit member's visits across rounds gather into one entry rather than one per round.
   *
   * These are a working copy, not a buffer of unwritten work: every mutation is followed by a
   * `persistMember` call that rewrites the member's session entry, so nothing lives only here between
   * sets. They're kept for the whole session rather than cleared when a member finishes — the entry
   * they'd be rebuilt from is on disk anyway, and holding them means an undo doesn't have to
   * reconstruct a member's earlier sets from what it just retracted.
   */
  const memberSetsRef = useRef<Record<string, (TimedHoldSetLog | RepsSetLog)[]>>({});
  const memberHiitRoundsRef = useRef<Record<string, number>>({});
  const memberEmomMinutesRef = useRef<Record<string, EmomMinuteLog[]>>({});
  /**
   * Where each member's entry sits in `session.entries`, so a later set rewrites that entry instead of
   * appending another. Entries are only ever appended at the end or removed from the end (see
   * `goPrev`), so a recorded index stays valid for the life of the session.
   */
  const entryIndexRef = useRef<Record<string, number>>({});
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
    const countdownTarget =
      step?.kind === 'rest' ? step.seconds : step?.kind === 'interval' && !step.countUp ? step.targetSec : 0;
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
      const priorSets = memberSetsRef.current[step.memberKey];
      const lastLogged = priorSets?.at(-1);
      const carried = lastLogged && 'reps' in lastLogged ? lastLogged.weightKg : undefined;
      setWeightKg(carried ?? step.targetWeightKg ?? 0);
    }
    setRoundsCompleted(0);
    setExtraReps(0);
    setRestRemainingSec(countdownTarget);
    setRestTargetSec(countdownTarget);
  }

  /** The entry a member's accumulated log currently amounts to, or null if it has logged nothing yet. */
  const entryForMember = useCallback((memberKey: string, exerciseId: string): SessionEntry | null => {
    const sets = memberSetsRef.current[memberKey];
    if (sets && sets.length > 0) {
      return 'holdSec' in sets[0]
        ? { exercise: exerciseId, type: 'timed_hold', sets: sets as TimedHoldSetLog[] }
        : { exercise: exerciseId, type: 'reps', sets: sets as RepsSetLog[] };
    }

    const roundsDone = memberHiitRoundsRef.current[memberKey];
    if (roundsDone !== undefined) return { exercise: exerciseId, type: 'hiit', roundsCompleted: roundsDone };

    const minutes = memberEmomMinutesRef.current[memberKey];
    if (minutes && minutes.length > 0) return { exercise: exerciseId, type: 'emom', minutes };

    return null;
  }, []);

  /**
   * Write-through: puts the member's log on disk as it stands, appending its entry the first time and
   * rewriting that same entry every time after.
   *
   * This is what §7.2 asks for and what the old flush-when-the-member-finishes design didn't deliver:
   * sets sat in memory until the whole exercise was done, so a crash three sets into a four-set hold
   * wrote nothing for it. The cost is one file write per set instead of one per exercise, on a file
   * that was already rewritten whole on every append — and it's this session's own file, never the
   * rest of history (§5.2 note 3).
   */
  const persistMember = useCallback(
    (memberKey: string, exerciseId: string) => {
      const session = sessionRef.current;
      if (!session) return;
      const entry = entryForMember(memberKey, exerciseId);
      if (!entry) return;

      const index = entryIndexRef.current[memberKey];
      if (index === undefined) {
        entryIndexRef.current[memberKey] = session.entries.length;
        sessionRef.current = logEntry(session, entry);
      } else {
        sessionRef.current = replaceEntry(session, index, entry);
      }
    },
    [entryForMember, logEntry, replaceEntry],
  );

  // Records `current`'s own contribution (the set/round/minute just finished, or the rest just
  // taken) into its member's log and straight through to the session file, or logs a one-shot entry
  // for the step kinds that have no accumulating log behind them.
  const commitCurrentStep = useCallback(
    (current: RunnerStep) => {
      if (!sessionRef.current) return;
      if (current.kind === 'hold') {
        const sets = memberSetsRef.current[current.memberKey] ?? [];
        sets.push({ holdSec: computeElapsedSec(), restTakenSec: 0 });
        memberSetsRef.current[current.memberKey] = sets;
        persistMember(current.memberKey, current.exerciseId);
      } else if (current.kind === 'reps') {
        const sets = memberSetsRef.current[current.memberKey] ?? [];
        // `|| undefined` so bodyweight (0) stays absent from the log rather than recording a 0 kg load —
        // entryVolume distinguishes the two, summing reps×weight only when a weight is actually present.
        sets.push({
          reps: repsRef.current,
          weightKg: weightKgRef.current || undefined,
          rpe: rpeRef.current,
          restTakenSec: 0,
        });
        memberSetsRef.current[current.memberKey] = sets;
        persistMember(current.memberKey, current.exerciseId);
      } else if (current.kind === 'interval') {
        if (current.variant === 'hiit') {
          memberHiitRoundsRef.current[current.memberKey] = (memberHiitRoundsRef.current[current.memberKey] ?? 0) + 1;
          persistMember(current.memberKey, current.exerciseId);
        } else if (current.variant === 'emom') {
          const minutes = memberEmomMinutesRef.current[current.memberKey] ?? [];
          minutes.push({ reps: repsRef.current || undefined });
          memberEmomMinutesRef.current[current.memberKey] = minutes;
          persistMember(current.memberKey, current.exerciseId);
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
          // Attributes the rest to the set it followed. The set is already on disk without it, so the
          // entry has to be rewritten — otherwise `rest_taken_sec` would be the one field that only
          // survived if the exercise ran to completion.
          const sets = memberSetsRef.current[current.memberKey];
          if (sets && sets.length > 0) {
            sets[sets.length - 1].restTakenSec = takenSec;
            persistMember(current.memberKey, current.exerciseId);
          }
        }
      }
    },
    [computeElapsedSec, logEntry, persistMember],
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

        // A distinct cue from the plain countdown tick, so a change of exercise is audible even
        // without looking at the screen — but not for every set/round within the same exercise. In a
        // circuit this is true on every hand-off, since the member comes back next round; nothing
        // about *writing* hangs off it any more, now that each set writes itself.
        const changingExercise = !next || next.memberKey !== current.memberKey;
        if (changingExercise && next) playExerciseChange();

        // Track exactly what this advance() committed so goPrev() can undo it precisely (one level
        // only — see the LastCommit type doc).
        const commitContext = { resultingIndex: nextIndex, memberKey: current.memberKey, exerciseId: current.exerciseId };
        if (directLog) {
          lastCommitRef.current = { ...commitContext, buffer: null };
        } else if (buffer) {
          lastCommitRef.current = { ...commitContext, buffer };
        } else {
          // Non-standalone rest: overwrites the previous set's restTakenSec rather than adding
          // anything, so redoing it later just overwrites that field again — nothing to undo.
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
  }, [steps, onComplete, commitCurrentStep, completeSession, playExerciseChange]);

  useEffect(() => {
    if (!step || paused) return;
    const id = setInterval(() => {
      if (step.kind === 'hold' || (step.kind === 'interval' && step.countUp)) {
        const elapsed = computeElapsedSec();
        setHoldElapsedSec(elapsed);
        // Holds count *up* with the target as a marker, so without this nothing marks the moment you
        // actually reach it — the one thing worth knowing with your eyes shut. Fires at the bottom of
        // a range target, since that's the point the set counts.
        if (step.kind === 'hold' && elapsed >= step.holdTargetSec) fireMilestone();
      } else if (isCountdownStep) {
        const remaining = Math.max(0, restTargetSecRef.current - computeElapsedSec());
        setRestRemainingSec(remaining);
        if (remaining <= 0) advance();
        else {
          // Halfway through a HIIT work interval — the point you'd otherwise have to look up to pace
          // yourself. Only hiit: its rest intervals already get the 3-2-1 ticks, and emom/amrap are
          // either too short for a midpoint to mean anything or better served by their own cues.
          if (step.kind === 'interval' && step.variant === 'hiit' && remaining <= step.targetSec / 2) {
            fireMilestone();
          }
          if (remaining <= 3) playTick();
        }
      }
    }, 1000);
    return () => clearInterval(id);
  }, [step, paused, isCountdownStep, advance, computeElapsedSec, playTick, fireMilestone]);

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
        const buffer = commit.buffer;
        switch (buffer) {
          case 'sets': {
            const sets = memberSetsRef.current[commit.memberKey];
            if (sets && sets.length > 0) sets.pop();
            break;
          }
          case 'hiit': {
            const count = memberHiitRoundsRef.current[commit.memberKey];
            if (count !== undefined) {
              if (count > 1) memberHiitRoundsRef.current[commit.memberKey] = count - 1;
              else delete memberHiitRoundsRef.current[commit.memberKey];
            }
            break;
          }
          case 'emom': {
            const minutes = memberEmomMinutesRef.current[commit.memberKey];
            if (minutes && minutes.length > 0) minutes.pop();
            break;
          }
          // A one-shot entry (amrap/cardio/standalone rest) with no accumulating log behind it: the
          // commit appended it and nothing has been appended since, so removing the last entry
          // removes exactly that one.
          case null:
            sessionRef.current = removeLastEntry(sessionRef.current);
            break;
          default:
            assertNever(buffer);
        }

        if (buffer !== null) {
          // Rewrite what the member has left, or retract its entry if that set was all it had. The
          // retraction can only ever hit the *last* entry: this undoes the most recent commit, so
          // whatever it appended is still the newest thing in the session.
          if (entryForMember(commit.memberKey, commit.exerciseId)) {
            persistMember(commit.memberKey, commit.exerciseId);
          } else {
            sessionRef.current = removeLastEntry(sessionRef.current);
            // Forget where its entry was, so redoing the set appends a fresh one instead of trying to
            // rewrite an index that no longer holds it.
            delete entryIndexRef.current[commit.memberKey];
          }
        }
        lastCommitRef.current = null;
      }

      const prevIndex = Math.max(0, index - 1);
      stepIndexRef.current = prevIndex;
      setStepIndex(prevIndex);
    }
  }, [removeLastEntry, entryForMember, persistMember]);

  // Ends the session on demand: the in-progress step is committed first, so the current set/round
  // (and everything already logged) is saved rather than discarded.
  const finishSession = useCallback(() => {
    if (step && sessionRef.current) commitCurrentStep(step);
    if (sessionRef.current) sessionRef.current = completeSession(sessionRef.current);
    onComplete();
  }, [step, commitCurrentStep, completeSession, onComplete]);

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
    // Whether a rest step actually follows, which the reps button names ("Log set → Rest"). It used
    // to be safe to assume one always did; back-to-back sets (rest_sec: 0) emit no rest step at all,
    // so the label has to ask rather than promise.
    restFollows: steps[stepIndex + 1]?.kind === 'rest',
    doneSet: advance,
    logSet: advance,
    skipRest: advance,
    logInterval: advance,
    addRestSeconds,
    goPrev,
    finishSession,
  };
}
