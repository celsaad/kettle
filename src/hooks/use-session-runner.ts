import * as Haptics from 'expo-haptics';
import { t } from 'i18next';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type { EmomMinuteLog, Exercise, RepsSetLog, Session, SessionEntry, TimedHoldSetLog, Workout } from '@/domain/types';
import {
  cancelNotification,
  requestNotificationPermissions,
  scheduleStepCompleteNotification,
} from '@/hooks/safe-notifications';
import { useSessionSounds } from '@/hooks/use-session-sounds';
import { useLibraryStore } from '@/state/library-store';
import { formatSessionName } from '@/domain/format';

import { personalBestFor, previousSetFor } from '@/state/selectors/records';
import { useSessionHistoryStore } from '@/state/session-history-store';

// The step model and workout→steps expansion live in session-steps.ts so they can be tested without
// this file's native imports. Re-exported here because they're part of the runner's public surface.
export type { CircuitPosition, IntervalVariant, RunnerStep } from '@/hooks/session-steps';
export { buildSteps, buildStepsWithLimits } from '@/hooks/session-steps';

import type { RunnerStep } from '@/hooks/session-steps';
import {
  addSetForMember,
  buildSteps,
  dropLastSetForMember,
  buildStepsForExercise,
  setStepsForMember,
  swapExerciseForMember,
} from '@/hooks/session-steps';

// Forces a compile error in `commitCurrentStep`/`goPrev` below if RunnerStep/IntervalVariant grows a
// case that isn't handled — a new exercise type silently committing nothing, or falling through to "no
// undo support", is a data-loss bug rather than just a missing feature, so this is enforced rather
// than left to a comment.
function assertNever(value: never): never {
  throw new Error(`Unhandled RunnerStep/variant case: ${JSON.stringify(value)}`);
}

/**
 * What one step's commit added to the session, held in `contributionsRef` under that step's own index.
 *
 * **Keyed by step index because a step can be committed more than once.** Stepping back onto a step
 * and redoing it commits it again, and so does Finish pressed from a stepped-back position; before
 * this was keyed, every one of those appended a *second* set/minute/entry for a step that already had
 * one — three sets logged against a two-set plan, or a duplicate amrap entry carrying whatever the
 * screen had been re-seeded to. Recording where the first commit landed is what lets the second
 * overwrite it instead, so committing a step twice means the same as committing it once.
 *
 * The position/`entryIndex` each kind carries is that landing spot. It is also what makes undo safe to
 * refuse: `goPrev` only takes a contribution back when it is still the newest of its kind, since
 * removing one from the middle would leave every later contribution pointing at the wrong slot.
 *
 * Non-standalone rest records nothing — it overwrites the previous set's `restTakenSec` rather than
 * adding anything, so it is already idempotent and has nothing to take back.
 */
type Contribution =
  | { kind: 'sets'; memberKey: string; exerciseId: string; position: number }
  | { kind: 'hiit'; memberKey: string; exerciseId: string; round: number }
  | { kind: 'emom'; memberKey: string; exerciseId: string; position: number }
  | { kind: 'direct'; entryIndex: number };

/**
 * `exerciseId` rides along purely so the preview can show the bundled drawing. It's the step's own
 * id rather than anything derived, which is what keeps the art correct in every language — see the
 * note on `EXERCISE_ART`.
 */
export type RestPreview = { label: string; detail: string; exerciseId: string } | null;

/** Null on a max-effort hold, which has no target to preview — the caller picks a different string. */
function formatHoldTarget(step: Extract<RunnerStep, { kind: 'hold' }>): string | null {
  if (step.holdTargetSec === undefined) return null;
  return step.holdTargetMaxSec ? `${step.holdTargetSec}–${step.holdTargetMaxSec}s` : `${step.holdTargetSec}s`;
}

function formatRepsTarget(step: Extract<RunnerStep, { kind: 'reps' }>): string {
  return step.targetRepsMax ? `${step.targetReps}–${step.targetRepsMax}` : `${step.targetReps}`;
}

function previewFor(step: RunnerStep | undefined): RestPreview {
  if (!step) return null;
  // `label` stays the user's own exercise name — never translated.
  if (step.kind === 'hold') {
    const target = formatHoldTarget(step);
    const detail =
      target === null
        ? t('preview.holdOpen', { index: step.setIndex, total: step.setTotal })
        : t('preview.hold', { index: step.setIndex, total: step.setTotal, target });
    return { label: step.exerciseName, detail, exerciseId: step.exerciseId };
  }
  if (step.kind === 'reps') {
    const detail = t('preview.reps', { index: step.setIndex, total: step.setTotal, target: formatRepsTarget(step) });
    return { label: step.exerciseName, detail, exerciseId: step.exerciseId };
  }
  if (step.kind === 'interval') {
    const progress =
      step.setTotal > 1
        ? t('preview.round', { index: step.setIndex, total: step.setTotal })
        : t('preview.seconds', { n: step.targetSec });
    return {
      label: step.exerciseName,
      detail: t('preview.interval', { variant: step.variant, progress }),
      exerciseId: step.exerciseId,
    };
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
  /**
   * Null for an ad-hoc session — one started with no pre-built workout, which builds its step list as
   * it goes. The data model always allowed it (`Session.workout` is `string | null`); nothing in the
   * UI had ever produced the case.
   */
  workout: Workout | null,
  exercises: Exercise[],
  programId: string | null,
  programWeek: number | null,
  programDay: string | null,
  /**
   * Handed the session it just finished writing. The completion screen needs it to say what was beaten,
   * and it can't read it back off the store: React unmounts this hook and the ref holding it on the
   * same tick, and `completeSession` has already cleared `activeSessionId` by then.
   */
  onComplete: (session: Session | null) => void,
) {
  /**
   * State, not a `useMemo` over `buildSteps` — the list is mutable mid-session now (add/drop set), and
   * a memo would throw those edits away on its next recompute.
   *
   * Losing the automatic rebuild is the point rather than a cost. `session.tsx` subscribes to the
   * library store, so `exercises` got a fresh identity on any library write — including the adopt
   * write-back on the set row — and re-ran `buildSteps` mid-workout. That was harmless (same length,
   * same order, `stepIndex` is its own state) but it is not something a session should be exposed to
   * once the list can be edited.
   */
  const [steps, setSteps] = useState(() => (workout ? buildSteps(workout, exercises) : []));
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

  /**
   * The log as it stood when this session started, for "last time" and the live PR marker.
   *
   * **Snapshotted, never read live.** Every logged set writes through the store (`persistMember` →
   * `logEntry`), so a live read would do two wrong things at once: show the set finished two minutes
   * ago as "last time", and re-render the whole runner on every set. `getState()` is not a hook, so
   * this reads without subscribing, and a lazy initialiser runs it exactly once — during the first
   * render, which is before the `startSession` effect below has prepended the in-flight session.
   * (`previousSetFor` and `personalBestFor` skip unfinished sessions anyway, so the exclusion holds
   * from both ends.)
   */
  const [priorSessions] = useState(() => useSessionHistoryStore.getState().sessions);

  const step = steps[stepIndex];
  /**
   * Drives the *countdown display* — `restRemainingSec` off `stepEndSecRef`. An auto-ending hold
   * deliberately isn't one of these: it still counts up on screen, because the number shown is the
   * number logged as `holdSec` (§12.2).
   */
  const isCountdownStep = step?.kind === 'rest' || (step?.kind === 'interval' && !step.countUp);
  /**
   * Whether the current step ends on its own: the countdown steps above, plus a hold with a target.
   * These are the two that need the background catch-up and the scheduled notification, because both
   * can end while the screen is asleep. A max-effort hold and a counting-up cardio end only when the
   * user says so.
   *
   * This and `isCountdownStep` were one predicate until holds grew an end. Widening that one would
   * have put holds on the countdown *display* as well, which is the single thing they must not join.
   */
  const stepEndsItself = isCountdownStep || (step?.kind === 'hold' && step.holdEndSec !== undefined);

  const { playTick, playExerciseChange, playMilestone, enabled: soundsEnabled } = useSessionSounds();

  /**
   * Which step index the milestone chime has already sounded for. Both triggers are threshold
   * conditions ("elapsed past the target", "past halfway") that stay true for the rest of the step, so
   * the 1Hz tick would otherwise re-fire them every second. A ref rather than state for the reason the
   * whole timing path is: it must be correct even if the ticking effect re-runs mid-step, which
   * pause/resume does.
   *
   * **Cleared by the step-change block below, not left to be overwritten by the next step that
   * fires.** Holding the last index it sounded for is enough to silence a repeat *within* a step and
   * not enough to arm the next visit to one: step back with Prev over anything that doesn't chime —
   * a rest, a reps set — and the ref still names the hold you just left, so redoing that hold passed
   * its threshold in silence. Resetting on identity change means "has *this* visit chimed", which is
   * the question being asked.
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
  /**
   * What each step's commit has already put into the session, keyed by step index — see Contribution.
   * Committing the same step twice overwrites its contribution rather than adding a second one.
   */
  const contributionsRef = useRef<Record<number, Contribution>>({});
  /**
   * The step index the most recent commit belongs to, or null when the last one contributed nothing.
   *
   * This is the *undo window* and nothing else: `goPrev()` takes a contribution back only when it is
   * stepping straight back onto the step that just committed — one level, exactly as before. What
   * changed is that correctness no longer rests on it. Getting back to a step any other way (two
   * Prevs, Finish from a stepped-back position) leaves the contribution standing, and re-committing
   * now overwrites it instead of logging the set a second time.
   */
  const lastCommitIndexRef = useRef<number | null>(null);
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
  /**
   * Set the moment the session is handed to `onComplete`, so nothing can commit into it afterwards.
   *
   * The completion paths are the one place `stepIndexRef` stops being a usable "have we moved on"
   * signal: they return without advancing it (there is nowhere to advance to), so the staleness guard
   * the two timing effects rely on can't see that the session is over. Without this a second
   * `advance()` in the same batch re-committed the *final* step — a 3-round HIIT logged 4 — and called
   * `onComplete` twice. It also makes a double-tapped Finish, and a Finish landing in the same batch as
   * the last auto-advance, no-ops rather than duplicate work.
   */
  const finishedRef = useRef(false);
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
  const stepEndSecRef = useRef(0);

  const computeElapsedSec = useCallback(() => {
    const now = pausedAtRef.current ?? Date.now();
    return Math.max(0, Math.floor((now - phaseStartedAtRef.current - pausedMsRef.current) / 1000));
  }, []);

  useEffect(() => {
    requestNotificationPermissions();
  }, []);

  useEffect(() => {
    // A *real* workout with no blocks still creates nothing — session.tsx catches that case before it
    // gets here. An ad-hoc session has no blocks by definition and must create its file up front:
    // §7.2, never hold a live session only in memory.
    if (workout && workout.blocks.length === 0) return;
    sessionRef.current = startSession(workout?.id ?? null, programId, programWeek, programDay);
    // Runs once per mounted workout: session should exist before any set can be logged.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * Reset per-step transient state whenever the active step changes (adjusting state during render on
   * a key change, rather than in an effect — see https://react.dev/learn/you-might-not-need-an-effect).
   *
   * **Keyed on which step this is, not on where it sits.** `stepIndex` alone was enough while the list
   * was immutable, and became wrong the moment it wasn't: swapping an exercise replaces the step at
   * the *current* index, and adding one to a parked ad-hoc session lands it on the index the runner is
   * already on. Neither moves `stepIndex`, so neither re-seeded — a substitute inherited the replaced
   * exercise's reps and load, and an added exercise's first set inherited the parked state's zero,
   * logging 0 reps. Both shipped, and the browser run for the second is what surfaced them.
   *
   * The identity deliberately stops at member + kind + set number rather than the step object, so the
   * mutations that leave *this* set alone — add set, drop set, both of which rebuild the array —
   * don't snap a rep count the user just dialled in back to the target.
   *
   * Starts at `''`, which no real identity can equal, so this still fires on the very first render.
   * That mattered before for its own reason: seeding it with the initial value made the first step
   * look unchanged, so `stepEndSecRef` stayed at its `useRef(0)` default for a countdown-type first
   * step and the ticking effect auto-advanced before the timer ever ran.
   */
  const stepIdentity = step
    ? `${stepIndex}:${step.memberKey}:${step.kind}:${'setIndex' in step ? step.setIndex : 0}`
    : `${stepIndex}:none`;
  const [resetForStep, setResetForStep] = useState('');
  if (resetForStep !== stepIdentity) {
    setResetForStep(stepIdentity);
    const now = Date.now();
    phaseStartedAtRef.current = now;
    pausedAtRef.current = paused ? now : null;
    pausedMsRef.current = 0;
    // A hold seeds this too, even though it counts *up* on screen: everything downstream that asks
    // "when does this step end" — the background catch-up and the scheduled notification — then reads
    // one field instead of branching on step kind again.
    const endSec =
      step?.kind === 'rest'
        ? step.seconds
        : step?.kind === 'interval' && !step.countUp
          ? step.targetSec
          : step?.kind === 'hold'
            ? (step.holdEndSec ?? 0)
            : 0;
    stepEndSecRef.current = endSec;
    // See the ref's own note: this is what makes it mean "has this visit chimed" rather than "which
    // step chimed last", so a step redone via Prev sounds its milestone again.
    milestoneFiredForStepRef.current = -1;
    setHoldElapsedSec(0);
    // Reps start at the set's target, not 0: hitting the target is the common case, so counting up
    // from zero one tap at a time made the expected outcome the most expensive one to record. Reps
    // re-seed from the target every set rather than carrying the last set's value, because varying
    // reps between sets is the normal thing being logged — unlike load, below.
    //
    // An EMOM minute seeds from its own target for exactly that reason, and used to seed 0 while the
    // step carried a `targetReps` the screen was already showing as the prescription — so the common
    // case cost a tap per rep, and an untouched minute logged nothing at all (`commitCurrentStep`
    // writes `reps || undefined`). `targetReps` stays optional there: an EMOM may prescribe only the
    // interval, and 0 keeps that minute out of the log rather than inventing a count for it.
    setReps(step?.kind === 'reps' || step?.kind === 'interval' ? (step.targetReps ?? 0) : 0);
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
    // Seeded from `endSec` for every kind, holds included, so the state stays a faithful mirror of
    // `stepEndSecRef` at step start — which is exactly what the notification effect leans on as its
    // change signal (see its dependency-array note). The hold screen reads neither of these.
    setRestRemainingSec(endSec);
    setRestTargetSec(endSec);
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

  /**
   * Records `current`'s own contribution — the set/round/minute just finished, or the rest just taken —
   * into its member's log and straight through to the session file, or logs a one-shot entry for the
   * step kinds that have no accumulating log behind them.
   *
   * **Committing the same step twice means the same as committing it once**, which is what `index` is
   * for: the contribution it recorded the first time is overwritten in place rather than joined by a
   * second one. Getting back onto a committed step is not rare — two Prevs, or Finish from a
   * stepped-back position — and appending there logged a set that was performed once as two.
   */
  const commitCurrentStep = useCallback(
    (current: RunnerStep, index: number) => {
      // Captured rather than read through the ref inside the closures below: at most one entry write
      // happens per commit, so this cannot go stale within the call, and it keeps the non-null
      // narrowing that a closure would throw away.
      const session = sessionRef.current;
      if (!session) {
        lastCommitIndexRef.current = null;
        return;
      }
      const previous = contributionsRef.current[index];

      /**
       * Puts a set in the slot this step's earlier commit took, or on the end if it has none yet.
       *
       * `restTakenSec` carries over from the slot rather than resetting to 0: the rest after a set is
       * recorded by the *next* step, so a set redone on its own would otherwise lose it. The bounds
       * and member checks are belt and braces — a position that no longer fits can only mean
       * bookkeeping something else already invalidated, and appending is the safe reading of that.
       */
      const putSet = (set: TimedHoldSetLog | RepsSetLog): Contribution => {
        const { memberKey, exerciseId } = current;
        const sets = memberSetsRef.current[memberKey] ?? [];
        const at =
          previous?.kind === 'sets' && previous.memberKey === memberKey && previous.position < sets.length
            ? previous.position
            : sets.length;
        const carriedRest = sets[at]?.restTakenSec;
        sets[at] = carriedRest === undefined ? set : { ...set, restTakenSec: carriedRest };
        memberSetsRef.current[memberKey] = sets;
        persistMember(memberKey, exerciseId);
        return { kind: 'sets', memberKey, exerciseId, position: at };
      };

      /** The same for a one-shot entry: rewrite the one the first commit appended rather than appending another. */
      const logDirect = (entry: SessionEntry): Contribution => {
        if (previous?.kind === 'direct' && previous.entryIndex < session.entries.length) {
          sessionRef.current = replaceEntry(session, previous.entryIndex, entry);
          return previous;
        }
        sessionRef.current = logEntry(session, entry);
        return { kind: 'direct', entryIndex: session.entries.length };
      };

      // Exhaustive on purpose (see assertNever): a new step kind has to say what it commits, since
      // falling through to "nothing" would lose the work silently.
      const contribution = ((): Contribution | null => {
        switch (current.kind) {
          case 'hold': {
            // Clamped to the hold's own end, which is not the same as the elapsed clock. A hold that ends
            // while the app is backgrounded is only *noticed* on foreground return, so the raw elapsed
            // there is however long you were away — a 25s plank logged 60s in the test that found this.
            // The same clamp covers a throttled tick arriving a second or two late in the foreground.
            // Ending early by hand is unaffected: elapsed is below the end, and the honest number wins.
            const elapsed = computeElapsedSec();
            const holdSec = current.holdEndSec === undefined ? elapsed : Math.min(elapsed, current.holdEndSec);
            return putSet({ holdSec, restTakenSec: 0 });
          }
          case 'reps':
            // `|| undefined` so bodyweight (0) stays absent from the log rather than recording a 0 kg load —
            // entryVolume distinguishes the two, summing reps×weight only when a weight is actually present.
            return putSet({
              reps: repsRef.current,
              weightKg: weightKgRef.current || undefined,
              rpe: rpeRef.current,
              restTakenSec: 0,
            });
          case 'interval':
            switch (current.variant) {
              case 'hiit': {
                const { memberKey, exerciseId } = current;
                // A round already counted is not a second round when the step is redone, and there is
                // nothing to rewrite — the count is the whole entry.
                if (previous?.kind === 'hiit') return previous;
                const round = (memberHiitRoundsRef.current[memberKey] ?? 0) + 1;
                memberHiitRoundsRef.current[memberKey] = round;
                persistMember(memberKey, exerciseId);
                return { kind: 'hiit', memberKey, exerciseId, round };
              }
              case 'emom': {
                const { memberKey, exerciseId } = current;
                const minutes = memberEmomMinutesRef.current[memberKey] ?? [];
                const at =
                  previous?.kind === 'emom' && previous.memberKey === memberKey && previous.position < minutes.length
                    ? previous.position
                    : minutes.length;
                minutes[at] = { reps: repsRef.current || undefined };
                memberEmomMinutesRef.current[memberKey] = minutes;
                persistMember(memberKey, exerciseId);
                return { kind: 'emom', memberKey, exerciseId, position: at };
              }
              case 'amrap':
                return logDirect({
                  exercise: current.exerciseId,
                  type: 'amrap',
                  roundsCompleted: roundsCompletedRef.current,
                  extraReps: extraRepsRef.current || undefined,
                });
              case 'cardio': {
                // Clamped to the configured duration, exactly as a hold is clamped to its end above and for
                // the same reason: a cardio step that ends while the app is backgrounded is only *noticed* on
                // foreground return, so the raw elapsed there is however long you were away — a 60s row
                // logged 600s in the test that found this. Only when it has a duration to be clamped to; a
                // count-up cardio ends when the user says so, and its elapsed is the whole measurement.
                // `restTakenSec` deliberately isn't clamped this way: "how long you rested" really is the
                // time that passed, whereas "how long you rowed" is not.
                const elapsed = computeElapsedSec();
                return logDirect({
                  exercise: current.exerciseId,
                  type: 'cardio',
                  durationSec: current.countUp ? elapsed : Math.min(elapsed, current.targetSec),
                  distanceMeters: current.cardioDistanceMeters,
                });
              }
              default:
                return assertNever(current.variant);
            }
          case 'rest': {
            const takenSec = computeElapsedSec();
            if (current.standalone) {
              return logDirect({ exercise: current.exerciseId, type: 'rest', restTakenSec: takenSec });
            }
            // Attributes the rest to the set it followed — the set the step *before this one*
            // contributed, rather than whatever sits on the end of the member's log. Those are the same
            // set right up until one is redone out of order, at which point the end of the log belongs
            // to a later set and the rest would land on that one instead.
            //
            // The set is already on disk without it, so the entry has to be rewritten — otherwise
            // `rest_taken_sec` would be the one field that only survived if the exercise ran to completion.
            const preceding = contributionsRef.current[index - 1];
            const sets = memberSetsRef.current[current.memberKey];
            const at =
              preceding?.kind === 'sets' && preceding.memberKey === current.memberKey
                ? preceding.position
                : (sets?.length ?? 0) - 1;
            if (sets && at >= 0 && at < sets.length) {
              sets[at].restTakenSec = takenSec;
              persistMember(current.memberKey, current.exerciseId);
            }
            // Nothing to take back: this overwrote a field rather than adding anything, so redoing it
            // simply overwrites it again.
            return null;
          }
          default:
            return assertNever(current);
        }
      })();

      if (contribution) {
        contributionsRef.current[index] = contribution;
        lastCommitIndexRef.current = index;
      } else {
        lastCommitIndexRef.current = null;
      }
    },
    [computeElapsedSec, logEntry, replaceEntry, persistMember],
  );

  const advance = useCallback(() => {
    if (finishedRef.current) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});

    {
      const index = stepIndexRef.current;
      const current = steps[index];
      const next = steps[index + 1];
      const nextIndex = index + 1;

      if (current && sessionRef.current) {
        commitCurrentStep(current, index);

        // A distinct cue from the plain countdown tick, so a change of exercise is audible even
        // without looking at the screen — but not for every set/round within the same exercise. In a
        // circuit this is true on every hand-off, since the member comes back next round; nothing
        // about *writing* hangs off it any more, now that each set writes itself.
        const changingExercise = !next || next.memberKey !== current.memberKey;
        if (changingExercise && next) playExerciseChange();
      } else {
        lastCommitIndexRef.current = null;
      }

      if (nextIndex >= steps.length) {
        // An ad-hoc session parks one past the end rather than completing: what comes next is the
        // user's to decide, and the screen offers Add exercise / Finish there. Parking (rather than
        // clamping) is what makes `addExercise` free — appending puts the new first step at exactly
        // the index this already points to.
        if (!workout) {
          stepIndexRef.current = nextIndex;
          setStepIndex(nextIndex);
          return;
        }
        finishedRef.current = true;
        if (sessionRef.current) sessionRef.current = completeSession(sessionRef.current);
        onComplete(sessionRef.current);
        return;
      }

      stepIndexRef.current = nextIndex;
      setStepIndex(nextIndex);
    }
  }, [steps, workout, onComplete, commitCurrentStep, completeSession, playExerciseChange]);

  useEffect(() => {
    if (!step || paused) return;
    const indexAtSetup = stepIndexRef.current;
    const id = setInterval(() => {
      // This callback decides whether the step has ended from `phaseStartedAtRef`/`stepEndSecRef`,
      // which are re-seeded during render — so between an advance() and that render it is holding the
      // *previous* step's clock and would conclude the new step has expired too. See the note on the
      // AppState effect below for how the two of them get into that state together.
      if (stepIndexRef.current !== indexAtSetup || finishedRef.current) return;
      if (step.kind === 'hold' || (step.kind === 'interval' && step.countUp)) {
        const elapsed = computeElapsedSec();
        setHoldElapsedSec(elapsed);
        if (step.kind === 'hold') {
          const { holdTargetSec: target, holdEndSec: end } = step;
          // Holds count *up* with the target as a marker, so without this nothing marks the moment you
          // actually reach it — the one thing worth knowing with your eyes shut. Fires at the bottom of
          // a range target, since that's the point the set counts.
          //
          // Only when the minimum is genuinely short of the end, though: on a fixed target the mark
          // and the auto-advance land in the same second, and two cues one after the other read as a
          // glitch rather than as two pieces of information.
          if (target !== undefined && elapsed >= target && (end === undefined || target < end)) fireMilestone();
          if (end !== undefined) {
            const remaining = end - elapsed;
            // The same 3-2-1 rest and countdown intervals get. In a hold this is the part that earns
            // its keep: it's how you know to prepare the dismount without looking, which is the whole
            // reason the step ends itself at all.
            if (remaining > 0 && remaining <= 3) playTick();
            if (remaining <= 0) advance();
          }
        }
      } else if (isCountdownStep) {
        const remaining = Math.max(0, stepEndSecRef.current - computeElapsedSec());
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

  // Recompute from wall-clock timestamps on foreground return, and catch up a step that ended while
  // backgrounded — JS timers are throttled/suspended in the background.
  //
  // The hold branch does the catching up as well as the countdown one, and that is not symmetry for
  // its own sake: a hold is run with the phone on the floor and the screen asleep, so "the step ended
  // while we weren't ticking" is its *normal* case rather than an edge one. Without this the timer
  // resumes mid-hold and quietly runs long, which is the exact overrun the feature exists to remove.
  //
  // **Both this and the ticking effect must refuse to act on a step that has already been left**, and
  // that is not a theoretical guard. React Native delivers a backlog of queued native calls to JS in
  // one batch, so on resume a timer callback that came due while away and this AppState event can both
  // run before React re-renders — and neither `phaseStartedAtRef` nor `stepEndSecRef` is re-seeded
  // until it does. The first to run advances; the second, still reading the old step's clock, decides
  // the new step has expired too and advances again, committing a step nobody performed. Advancing
  // `stepIndexRef` eagerly (see its note) stops them repeating one another's commit but not this, which
  // is the worse half: a set logged at 0 reps *and* skipped.
  useEffect(() => {
    const indexAtSetup = stepIndexRef.current;
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active' || !step || paused) return;
      if (stepIndexRef.current !== indexAtSetup || finishedRef.current) return;
      if (step.kind === 'hold' || (step.kind === 'interval' && step.countUp)) {
        const elapsed = computeElapsedSec();
        if (step.kind === 'hold' && step.holdEndSec !== undefined && elapsed >= step.holdEndSec) advance();
        else setHoldElapsedSec(elapsed);
      } else if (isCountdownStep) {
        const remaining = Math.max(0, stepEndSecRef.current - computeElapsedSec());
        if (remaining <= 0) advance();
        else setRestRemainingSec(remaining);
      }
    });
    return () => subscription.remove();
  }, [step, paused, isCountdownStep, advance, computeElapsedSec]);

  // Local-notification fallback so a step that ends itself still cues you when the app is
  // backgrounded — a hold as much as a rest, since both are run with the screen off.
  useEffect(() => {
    if (!step || !stepEndsItself || paused) return;
    let cancelled = false;
    let notificationId: string | null = null;

    const isHold = step.kind === 'hold';
    const remaining = Math.max(1, stepEndSecRef.current - computeElapsedSec());
    scheduleStepCompleteNotification(
      t(isHold ? 'session.notification.holdTitle' : 'session.notification.restTitle'),
      t(isHold ? 'session.notification.holdBody' : 'session.notification.restBody', {
        workout: formatSessionName(workout?.name ?? null),
      }),
      remaining,
      // Same switch that silences the in-app cues. Backgrounded or not, it is the same ding to the
      // user, and `settings.soundsNote` promises the toggle is the only way to quiet them.
      soundsEnabled,
    ).then((id) => {
      if (!id) return;
      if (cancelled) cancelNotification(id);
      else notificationId = id;
    });

    return () => {
      cancelled = true;
      if (notificationId) cancelNotification(notificationId);
    };
    // restTargetSec is in the deps as a *change signal*, not because the body reads it: the remaining
    // time comes from stepEndSecRef, which no dependency tracks. Without it, "+30s" mutated the ref
    // while every dep stayed equal, so the effect never re-ran and the notification still fired at the
    // original end time. Every place that moves the ref also sets this state — including a hold's own
    // seeding — so it stays a faithful signal.
  }, [step, stepEndsItself, paused, restTargetSec, computeElapsedSec, workout?.name, soundsEnabled]);

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

  // Takes back what the most recent advance() committed, if goPrev() is called immediately after it
  // (i.e. we're stepping back onto the step that commit belongs to) — one level deep only. A second
  // goPrev() in a row (no intervening advance()) finds the undo window closed and just moves the index.
  //
  // **That window is a convenience now, not a correctness guard.** It used to be both: a step reached
  // any other way still had its commit standing, and committing it again appended a second set. The
  // commit is idempotent per step index now (see Contribution), so the worst a closed window costs is
  // a set staying in the log until it is redone in place.
  //
  // Deliberately without the `finishedRef` guard advance()/finishSession() carry, because it can't be
  // reached after completion: onComplete swaps SessionScreen over to SessionComplete, which unmounts
  // the runner and every control that could call this. That unmount is the whole invariant — a
  // completion screen rendered *alongside* a live runner would put this back in reach, retracting a
  // commit from a session already written as ended.
  const goPrev = useCallback(() => {
    const index = stepIndexRef.current;
    const committedIndex = lastCommitIndexRef.current;
    const contribution = committedIndex === index - 1 ? contributionsRef.current[index - 1] : undefined;
    if (contribution && sessionRef.current) {
      // Each kind checks that its contribution is still the newest of its kind before taking it back.
      // Within the undo window it always is; the check is what keeps that an invariant of the data
      // rather than of the window, since removing one from the middle would leave every later
      // contribution pointing at the wrong slot.
      switch (contribution.kind) {
        case 'sets': {
          const sets = memberSetsRef.current[contribution.memberKey];
          if (sets && contribution.position === sets.length - 1) sets.pop();
          break;
        }
        case 'hiit': {
          const count = memberHiitRoundsRef.current[contribution.memberKey];
          if (count === contribution.round) {
            if (count > 1) memberHiitRoundsRef.current[contribution.memberKey] = count - 1;
            else delete memberHiitRoundsRef.current[contribution.memberKey];
          }
          break;
        }
        case 'emom': {
          const minutes = memberEmomMinutesRef.current[contribution.memberKey];
          if (minutes && contribution.position === minutes.length - 1) minutes.pop();
          break;
        }
        // A one-shot entry (amrap/cardio/standalone rest) with no accumulating log behind it: nothing
        // has been appended since, so removing the last entry removes exactly that one.
        case 'direct':
          if (contribution.entryIndex === sessionRef.current.entries.length - 1) {
            sessionRef.current = removeLastEntry(sessionRef.current);
          }
          break;
        default:
          assertNever(contribution);
      }

      if (contribution.kind !== 'direct') {
        // Rewrite what the member has left, or retract its entry if that set was all it had. The
        // retraction can only ever hit the *last* entry: this undoes the most recent commit, so
        // whatever it appended is still the newest thing in the session.
        if (entryForMember(contribution.memberKey, contribution.exerciseId)) {
          persistMember(contribution.memberKey, contribution.exerciseId);
        } else {
          sessionRef.current = removeLastEntry(sessionRef.current);
          // Forget where its entry was, so redoing the set appends a fresh one instead of trying to
          // rewrite an index that no longer holds it.
          delete entryIndexRef.current[contribution.memberKey];
        }
      }
      // Gone from the log, so gone from the bookkeeping too — redoing this step contributes afresh
      // rather than overwriting a slot that no longer exists.
      delete contributionsRef.current[index - 1];
      lastCommitIndexRef.current = null;
    }

    const prevIndex = Math.max(0, index - 1);
    stepIndexRef.current = prevIndex;
    setStepIndex(prevIndex);
  }, [removeLastEntry, entryForMember, persistMember]);

  // Ends the session on demand: the in-progress step is committed first, so the current set/round
  // (and everything already logged) is saved rather than discarded.
  const finishSession = useCallback(() => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    // Read through `stepIndexRef` rather than the render's `step`, for the reason its own note gives:
    // an advance() in this same batch has already moved the position but not yet re-rendered, and
    // committing the closure's step there would log the one it just committed a second time.
    //
    // Finishing from a step that has already been committed — stepped back onto, then Finish rather
    // than redone — is the other way this used to double-log, and is now the same overwrite any redo is.
    const index = stepIndexRef.current;
    const current = steps[index];
    if (current && sessionRef.current) commitCurrentStep(current, index);
    if (sessionRef.current) sessionRef.current = completeSession(sessionRef.current);
    onComplete(sessionRef.current);
  }, [steps, commitCurrentStep, completeSession, onComplete]);

  /** What this set looked like last time, matched on set number. Null on interval and rest steps. */
  const previousSet = useMemo(
    () =>
      step?.kind === 'reps' || step?.kind === 'hold' ? previousSetFor(priorSessions, step.exerciseId, step.setIndex) : null,
    [priorSessions, step],
  );

  /**
   * The bar the live marker measures against: the best ever logged, raised by anything better this
   * session has already put up.
   *
   * That second half is what stops three sets at a new top weight all claiming a PR — only the set
   * that actually moves the number does. Keyed on `step`, which is the only thing that changes when a
   * set is committed (`commitCurrentStep` pushes to the ref and advances in the same tick), so reading
   * the ref here recomputes at exactly the right moments.
   */
  const bestToBeat = useMemo(() => {
    if (step?.kind !== 'reps' && step?.kind !== 'hold') return null;
    const best = personalBestFor(priorSessions, step.exerciseId);
    const raise = (a: number | undefined, b: number | undefined) =>
      a === undefined ? b : b === undefined ? a : Math.max(a, b);

    const logged = memberSetsRef.current[step.memberKey] ?? [];
    const holds = logged.flatMap((set) => ('holdSec' in set ? [set.holdSec] : []));
    const loaded = logged.flatMap((set) => ('reps' in set && set.weightKg ? [set.weightKg] : []));
    const bodyweight = logged.flatMap((set) => ('reps' in set && !set.weightKg ? [set.reps] : []));

    return {
      weightKg: raise(best.heaviestSetKg, loaded.length ? Math.max(...loaded) : undefined),
      reps: raise(best.mostReps, bodyweight.length ? Math.max(...bodyweight) : undefined),
      holdSec: raise(best.longestHoldSec, holds.length ? Math.max(...holds) : undefined),
    };
  }, [priorSessions, step]);

  /**
   * Whether what's on screen right now would beat everything ever logged for this exercise.
   *
   * Prospective rather than retrospective: it marks the set *while* you're on it, which needs no
   * transient and no new timing in the runner's tick path — the completion screen owns the after-the-
   * fact celebration. Undefined on either side means nothing to beat, and a first-ever entry is not a
   * record, matching `sessionRecords`. Strictly greater, so a tie isn't one either.
   */
  const beatsPersonalBest = (() => {
    if (!bestToBeat || !step) return false;
    if (step.kind === 'hold') return bestToBeat.holdSec !== undefined && holdElapsedSec > bestToBeat.holdSec;
    // Bodyweight competes on reps, loaded on load — the same split `entryBest` makes, and for the same
    // reason: a bodyweight set logs no `weightKg` at all.
    if (weightKg > 0) return bestToBeat.weightKg !== undefined && weightKg > bestToBeat.weightKg;
    return bestToBeat.reps !== undefined && reps > bestToBeat.reps;
  })();

  /**
   * Takes last time's load as this set's, and writes it back as the exercise's new target so the next
   * session starts there without a trip to the editor.
   *
   * Reps are deliberately not adopted: they re-seed from the target every set (see the step-change
   * block above) because varying reps is the normal thing being logged, and pinning last week's would
   * fight double progression.
   *
   * `getState()` rather than a subscription — the runner has no reason to re-render when the library
   * changes, least of all as a result of its own write. The value is already kilograms, straight off a
   * logged set, so it goes to the store unconverted (see `setTargetWeightKg`).
   */
  const adoptPreviousLoad = useCallback(() => {
    if (step?.kind !== 'reps' || previousSet?.kind !== 'reps' || !previousSet.weightKg) return;
    setWeightKg(previousSet.weightKg);
    useLibraryStore.getState().setTargetWeightKg(step.exerciseId, previousSet.weightKg);
  }, [step, previousSet]);

  /**
   * Whether the set count of the exercise on screen can be changed at all.
   *
   * False inside a circuit: there, `setIndex`/`setTotal` is the member's position in the circuit's
   * *rounds*, and its steps are interleaved with the other members' — so "one more set" means "one
   * more round" of the whole block, which is a different operation this doesn't implement. The block
   * kind is the honest test, and the runner is the only layer that has it (the step carries a
   * `blockIndex`, not a block).
   */
  const inCircuit = step ? workout?.blocks[step.blockIndex]?.kind === 'circuit' : false;
  const isSetStep = step?.kind === 'reps' || step?.kind === 'hold';
  const canAddSet = isSetStep && !inCircuit;
  /**
   * The floor: everything this member has already logged, plus the one being performed. Dropping into
   * that range would mean removing a set that is already in the session file, which is a different
   * and much less welcome operation than "make it three sets".
   */
  const canDropSet =
    canAddSet && step
      ? setStepsForMember(steps, step.memberKey) > (memberSetsRef.current[step.memberKey]?.length ?? 0) + 1
      : false;

  /**
   * Both mutations **must** clear the commit bookkeeping, and that is the single most dangerous line
   * here. Contributions are keyed by index into `steps` (see Contribution), so any edit to the array
   * invalidates every one of them — a stale key sends the next `goPrev()` to undo a commit that now
   * belongs to a different step, retracting a set the user did keep, and would have the next commit
   * overwrite a slot that is no longer the one it wrote.
   *
   * The floor above means neither mutation can touch a step at or before `stepIndex`, so the index
   * itself needs no adjustment.
   */
  const mutateSteps = useCallback((mutate: (current: RunnerStep[]) => RunnerStep[]) => {
    contributionsRef.current = {};
    lastCommitIndexRef.current = null;
    setSteps(mutate);
  }, []);

  const addSet = useCallback(() => {
    if (!step) return;
    const { memberKey } = step;
    mutateSteps((current) => addSetForMember(current, memberKey));
  }, [step, mutateSteps]);

  const dropSet = useCallback(() => {
    if (!step) return;
    const { memberKey } = step;
    mutateSteps((current) => dropLastSetForMember(current, memberKey));
  }, [step, mutateSteps]);

  /**
   * What the exercise on screen could be swapped for: the same type, minus itself.
   *
   * Same type only, because that is what makes "the remaining set count" a coherent thing to give the
   * substitute — a HIIT's rounds are not sets — and it keeps the runner screen from changing kind
   * under someone mid-set. `exercises` is already the program-resolved list, so a week's overrides are
   * applied to the candidates too.
   */
  const swapCandidates = useMemo(() => {
    if (!canAddSet || !step || (step.kind !== 'reps' && step.kind !== 'hold')) return [];
    const current = exercises.find((exercise) => exercise.id === step.exerciseId);
    if (!current) return [];
    return exercises.filter((exercise) => exercise.type === current.type && exercise.id !== current.id);
  }, [canAddSet, step, exercises]);

  const canSwapExercise = swapCandidates.length > 0;

  /**
   * Counts swaps so each one issues a member key nothing has used before.
   *
   * **This is what makes the swap safe**, and it is invariant 1 from the issue: `memberSetsRef`,
   * `memberHiitRoundsRef`, `memberEmomMinutesRef` and `entryIndexRef` are all keyed by `memberKey`.
   * Reissuing the original key would make the substitute's sets grow the *replaced* exercise's entry —
   * bench press sets silently becoming dumbbell press ones in the log. A fresh key means the old
   * entry keeps exactly the sets that were done under it, and the substitute starts its own.
   *
   * `~swap` cannot collide with anything `buildSteps` issues ("0", "0:1").
   */
  const swapCountRef = useRef(0);

  /**
   * How many exercises an ad-hoc session has added, which is both the next `blockIndex` and the
   * next member key's suffix.
   *
   * Same rule as the swap counter above, and for the same reason: a key nothing has used means the
   * new exercise's sets start their own session entry instead of growing someone else's, and its
   * first `persistMember` appends at the end — all `entryIndexRef`'s append-only assumption asks.
   */
  const adhocCountRef = useRef(0);

  /**
   * Appends an exercise to an ad-hoc session, which is the only way one gets any steps at all.
   *
   * Through `mutateSteps` like every other edit, so it inherits the bookkeeping clear — appending
   * doesn't invalidate an index the way a mid-list edit does, but routing every mutation through one
   * place is what stops the next one forgetting.
   */
  const addExercise = useCallback(
    (exerciseId: string) => {
      const exercise = exercises.find((candidate) => candidate.id === exerciseId);
      if (!exercise) return;
      const blockIndex = adhocCountRef.current;
      adhocCountRef.current += 1;
      const memberKey = `adhoc${blockIndex}`;
      mutateSteps((current) => [...current, ...buildStepsForExercise(exercise, blockIndex, memberKey)]);
    },
    [exercises, mutateSteps],
  );

  const swapExercise = useCallback(
    (exerciseId: string) => {
      if (!step) return;
      const exercise = exercises.find((candidate) => candidate.id === exerciseId);
      if (!exercise) return;
      const { memberKey } = step;
      const index = stepIndexRef.current;
      swapCountRef.current += 1;
      const newMemberKey = `${memberKey}~swap${swapCountRef.current}`;
      // Through mutateSteps like every other edit, so it inherits the bookkeeping clear rather than
      // restating it — see the note there on what a stale undo index costs.
      mutateSteps((current) => swapExerciseForMember(current, memberKey, index, exercise, newMemberKey));
    },
    [step, exercises, mutateSteps],
  );

  const addRestSeconds = useCallback(
    (amount: number) => {
      stepEndSecRef.current = Math.max(0, stepEndSecRef.current + amount);
      setRestTargetSec(stepEndSecRef.current);
      setRestRemainingSec(Math.max(0, stepEndSecRef.current - computeElapsedSec()));
    },
    [computeElapsedSec],
  );

  return {
    step,
    stepIndex,
    totalSteps: steps.length,
    blockIndex: step?.blockIndex ?? 0,
    // An ad-hoc session's "blocks" are the exercises added so far, which is what keeps the progress
    // dots meaningful; SessionProgressDots already degrades to nothing at zero.
    blockTotal: workout ? workout.blocks.length : adhocCountRef.current,
    /**
     * Where the current step sits in its circuit, or null outside one. Read straight off the step:
     * a circuit's steps are interleaved rather than contiguous, so "which part of the circuit" isn't
     * derivable from the index alone (see CircuitPosition).
     */
    circuit: step?.circuit ?? null,
    /** Null for an ad-hoc session — `formatSessionName` owns the stand-in, not this layer. */
    workoutName: workout?.name ?? null,
    isAdHoc: !workout,
    addExercise,
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
    previousSet,
    beatsPersonalBest,
    adoptPreviousLoad,
    canAddSet,
    canDropSet,
    addSet,
    dropSet,
    canSwapExercise,
    swapCandidates,
    swapExercise,
    doneSet: advance,
    logSet: advance,
    skipRest: advance,
    logInterval: advance,
    addRestSeconds,
    goPrev,
    finishSession,
  };
}
