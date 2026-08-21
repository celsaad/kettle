/**
 * The pure half of the session runner: the step model and the workout → step-list expansion.
 *
 * Split out of use-session-runner.ts so it can be imported without dragging in that hook's native
 * dependencies (expo-audio, expo-haptics, notifications) — those made these otherwise-pure functions
 * untestable, since merely importing them initialised native modules. No behaviour changed in the move.
 */
import { emomIntervalCount } from '@/domain/schema';
import type { BlockConfigOverride, Exercise, Workout } from '@/domain/types';

export type IntervalVariant = 'hiit' | 'emom' | 'amrap' | 'cardio';

/**
 * Where a step sits inside its circuit block: which round, and which exercise of the round-robin.
 * Absent on every step outside a circuit, which is what the runner tests to know it's in one.
 *
 * Attached during expansion rather than derived later, because the member numbering is over the
 * *resolved* members — a circuit naming an exercise the library no longer has drops that member, so
 * neither `block.members.length` nor a member's index in it survives. A step carries a `blockIndex`
 * and nothing else about its block, so this is the last place the numbering is knowable.
 *
 * Rest steps take the position of the work they *follow*, not the work they lead to: mid-circuit, the
 * honest answer to "where am I" is what's been done.
 */
export type CircuitPosition = {
  round: number;
  rounds: number;
  /** 1-based, over the members that resolved to an exercise. */
  member: number;
  memberTotal: number;
};

export type RunnerStep =
  | {
      kind: 'hold';
      blockIndex: number;
      memberKey: string;
      /** Set only inside a circuit block — see CircuitPosition. */
      circuit?: CircuitPosition;
      exerciseId: string;
      exerciseName: string;
      /** The mark to cross, not the finish line — see `holdEndSec`. Absent on a max-effort hold. */
      holdTargetSec?: number;
      holdTargetMaxSec?: number;
      /**
       * When the hold ends by itself: the top of the range (`holdSecMax ?? holdSecMin`), or undefined
       * for a max-effort hold, which counts up until the Done button ends it.
       *
       * Precomputed here rather than derived in the runner for the same reason `countUp` is, a few
       * cases down: one place decides what shape of step the config describes.
       */
      holdEndSec?: number;
      setIndex: number;
      setTotal: number;
      notes?: string;
    }
  | {
      kind: 'reps';
      blockIndex: number;
      memberKey: string;
      /** Set only inside a circuit block — see CircuitPosition. */
      circuit?: CircuitPosition;
      exerciseId: string;
      exerciseName: string;
      targetReps: number;
      targetRepsMax?: number;
      targetWeightKg?: number;
      setIndex: number;
      setTotal: number;
      notes?: string;
    }
  | {
      kind: 'interval';
      blockIndex: number;
      memberKey: string;
      /** Set only inside a circuit block — see CircuitPosition. */
      circuit?: CircuitPosition;
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
  //
  // Non-standalone rest is only emitted when it's longer than zero. A zero-second rest step isn't
  // free: the runner shows the rest screen, then `remaining <= 0` fires on the very next tick, so
  // back-to-back sets got a flash of rest UI, the completion chime and a scheduled "Rest complete"
  // notification between every one of them — the thing that made a `rest_sec: 0` superset feel
  // broken. Standalone rest is exempt: it's a logged entry, and the schema permits `duration_sec: 0`.
  | {
      kind: 'rest';
      blockIndex: number;
      memberKey: string;
      /** Set only inside a circuit block — see CircuitPosition. */
      circuit?: CircuitPosition;
      exerciseId: string;
      standalone: boolean;
      seconds: number;
    };

/**
 * Where a circuit member's single visit sits in the circuit's rounds. Set only when expanding a
 * circuit member: the circuit's own `rounds` is what repeats the member, not the member's own
 * `sets` — so each visit is exactly one set, and no inter-set rest is inserted (rest between visits
 * is the circuit's own rest_between_exercises_sec/rest_between_rounds_sec).
 *
 * It carries the round position rather than just suppressing the loop because that position is the
 * only honest thing to put on the step: a member visited once per round across 3 rounds is doing 3
 * sets of that exercise, and reporting the literal `1 of 1` of a single visit left the runner
 * saying "Set 1 of 1" on every round with no sense of progress through the circuit.
 */
type CircuitVisit = { index: number; total: number };

/**
 * Ceilings on what one expansion may allocate, held here as well as in `schema.ts`.
 *
 * Dropping the spread-push fixed the crash but left a worse residual than it first looked. A `hiit`
 * or `emom` circuit member ignores the visit flag and runs its own full round count *once per visit*
 * (see CircuitVisit), so a 500-round circuit of two 500-round `hiit` members — every number of it
 * schema-legal — expands to 999,999 steps. Measured: ~1s and ~117MB of heap, synchronously, inside a
 * render-time `useMemo`. On a phone that is an OOM or an ANR, not a slow workout, and calling it
 * "slow" was the wrong read: `push(...arr)` at least failed fast.
 *
 * `MaxStepsPerExercise` sits above everything the schema admits on its own — the worst single
 * exercise is 500 sets with a rest between each, 999 steps — so it only ever fires on a path that
 * skipped validation.
 *
 * `MaxStepsPerWorkout` is the one that can truncate something importable, because neither `blocks`
 * nor a circuit's `exercises` has an upper bound and no per-exercise ceiling bounds their sum. It is
 * set where it is on measured cost: ~120 bytes a step, so 20,000 steps is ~2.4MB and a few
 * milliseconds, while sitting roughly sixty times above the longest workout anyone would write (a
 * four-hour session at 30s a step is under 500). Truncating is a poor degrade and it is still the
 * better one — the alternative on the only libraries that reach it is the app dying mid-render.
 */
const MaxStepsPerExercise = 2000;
export const MaxStepsPerWorkout = 20_000;

/**
 * The step list, and whether either ceiling cut it short — so the session screen can say so before
 * the countdown, rather than the workout ending in the middle and being logged as finished.
 */
export type StepBuildResult = { steps: RunnerStep[]; truncated: boolean };

/** `count`, clamped to what one exercise may expand into. See MaxStepsPerExercise. */
function boundedCount(count: number): number {
  return Math.min(Math.max(0, Math.floor(count)), MaxStepsPerExercise);
}

/**
 * Expands one exercise, reporting whether `MaxStepsPerExercise` cut it short.
 *
 * Reported rather than inferred from the length, because a per-exercise clamp is invisible at the
 * workout level: `sets: 3000` clamps to 2000 and produces 3999 steps, far under
 * `MaxStepsPerWorkout` — so a length test says "not truncated" while the session runs two thirds of
 * the sets and is logged as finished.
 */
function expandExercise(
  exercise: Exercise,
  blockIndex: number,
  memberKey: string,
  configOverride?: BlockConfigOverride,
  visit?: CircuitVisit,
): { steps: RunnerStep[]; truncated: boolean } {
  // Wrapped so the clamp reports itself as it happens, rather than being recomputed by a second copy
  // of the same arithmetic that could drift from this one.
  let truncated = false;
  const bounded = (count: number): number => {
    const next = boundedCount(count);
    if (next < count) truncated = true;
    return next;
  };
  switch (exercise.type) {
    case 'timed_hold': {
      const sets = visit ? 1 : bounded(exercise.config.sets);
      // A 0 (or negative) end would auto-advance on the very first tick, skipping the hold outright.
      // Both paths that could produce one are gated now — applyExerciseOverride re-validates a merged
      // override and the in-app override editor runs validateConfig — but the degrade stays: it keeps
      // the set runnable by hand, which is what it did before holds could end themselves, and a hold
      // vanishing mid-workout would not be a degrade.
      const configuredEnd = exercise.config.holdSecMax ?? exercise.config.holdSecMin;
      const holdEndSec = configuredEnd !== undefined && configuredEnd > 0 ? configuredEnd : undefined;
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
          holdEndSec,
          setIndex: visit?.index ?? i + 1,
          setTotal: visit?.total ?? sets,
          notes: exercise.notes,
        });
        if (!visit && i < sets - 1 && exercise.config.restSec > 0) {
          steps.push({
            kind: 'rest',
            blockIndex,
            memberKey,
            exerciseId: exercise.id,
            standalone: false,
            seconds: exercise.config.restSec,
          });
        }
      }
      return { steps, truncated };
    }
    case 'reps': {
      const sets = visit ? 1 : bounded(exercise.config.sets);
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
          targetWeightKg: exercise.config.targetWeightKg,
          setIndex: visit?.index ?? i + 1,
          setTotal: visit?.total ?? sets,
          notes: exercise.notes,
        });
        if (!visit && i < sets - 1 && exercise.config.restSec > 0) {
          steps.push({
            kind: 'rest',
            blockIndex,
            memberKey,
            exerciseId: exercise.id,
            standalone: false,
            seconds: exercise.config.restSec,
          });
        }
      }
      return { steps, truncated };
    }
    case 'hiit': {
      const steps: RunnerStep[] = [];
      const rounds = bounded(exercise.config.rounds);
      for (let i = 0; i < rounds; i++) {
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
          setTotal: rounds,
          notes: exercise.notes,
        });
        if (i < rounds - 1 && exercise.config.restSec > 0) {
          steps.push({
            kind: 'rest',
            blockIndex,
            memberKey,
            exerciseId: exercise.id,
            standalone: false,
            seconds: exercise.config.restSec,
          });
        }
      }
      return { steps, truncated };
    }
    case 'emom': {
      const steps: RunnerStep[] = [];
      // `totalMinutes` is the block's total *duration*, not its interval count — those only coincide
      // for a literal every-minute EMOM. Looping `totalMinutes` times at `intervalSec` each meant a
      // 30-second interval over 10 minutes ran for 5, while estimateExerciseSeconds (selectors.ts)
      // reported the honest `totalMinutes * 60`; the two silently disagreed for any interval but 60s.
      // Clamped to at least one so an interval longer than the whole block still runs once rather
      // than making the exercise vanish into the "nothing to run" path.
      // Floored *before* `bounded`, because the floor is not a clamp: an interval that doesn't divide
      // the block evenly leaves a partial one that was never going to run, and reporting that as
      // truncation put the "too long to run in full" screen in front of ordinary workouts —
      // `interval_sec: 45, total_minutes: 10` is 13⅓ intervals and 13 is the honest answer.
      const intervalCount = bounded(
        Math.max(1, emomIntervalCount(exercise.config.intervalSec, exercise.config.totalMinutes)),
      );
      for (let i = 0; i < intervalCount; i++) {
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
          setTotal: intervalCount,
          targetReps: exercise.config.targetReps,
          notes: exercise.notes,
        });
      }
      return { steps, truncated };
    }
    case 'amrap':
      return {
        truncated,
        steps: [
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
        ],
      };
    case 'cardio': {
      const hasDuration = exercise.config.durationSec !== undefined;
      return {
        truncated,
        steps: [
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
        ],
      };
    }
    case 'rest':
      return {
        truncated,
        steps: [
          {
            kind: 'rest',
            blockIndex,
            memberKey,
            exerciseId: exercise.id,
            standalone: true,
            seconds: configOverride?.durationSec ?? exercise.config.durationSec,
          },
        ],
      };
  }
}

// --- Mid-session mutation ---
//
// These live here rather than in the runner so they can be tested without pulling in expo-audio and
// the native modules, which is the same reason `buildSteps` does. They are pure: a new array out, the
// input untouched.
//
// **Circuit members are not a legal target and the caller is responsible for that.** A circuit
// member's setIndex/setTotal is its position in the circuit's *rounds* (see CircuitVisit above), and
// its steps are not contiguous in the list — "one more set" there means "one more round", which is a
// different operation on a different object. The runner checks the block kind before offering the
// control; nothing here can tell the difference from a step alone.

/** A member's own work steps, in list order — the ones "Set 3 of 4" counts. */
function setStepIndices(steps: RunnerStep[], memberKey: string): number[] {
  return steps.flatMap((step, index) =>
    step.memberKey === memberKey && (step.kind === 'reps' || step.kind === 'hold') ? [index] : [],
  );
}

/** How many sets of this member the list currently holds. */
export function setStepsForMember(steps: RunnerStep[], memberKey: string): number {
  return setStepIndices(steps, memberKey).length;
}

/**
 * Renumbers one member's set steps to 1..n. Both mutations end here, because `setIndex`/`setTotal`
 * are baked into each step and drive the display *and* `previewFor` — a list that isn't renumbered
 * reads "Set 3 of 3" on the fourth set.
 */
function renumber(steps: RunnerStep[], memberKey: string): RunnerStep[] {
  const indices = setStepIndices(steps, memberKey);
  const total = indices.length;
  const position = new Map(indices.map((stepIndex, i) => [stepIndex, i + 1]));
  return steps.map((step, index) => {
    const setIndex = position.get(index);
    return setIndex === undefined ? step : { ...step, setIndex, setTotal: total };
  });
}

/**
 * One more set of `memberKey`, appended after its current last one.
 *
 * The trailing rest is **cloned from one of the member's own rest steps** rather than rebuilt from
 * config, which is what makes the back-to-back case fall out instead of needing to be remembered: an
 * exercise authored `rest_sec: 0` emits no rest steps at all (see the note on the `rest` variant
 * above), so there is nothing to clone and the new set correctly gets none — and `restFollows` in the
 * runner keeps telling the truth about the log button's label.
 */
export function addSetForMember(steps: RunnerStep[], memberKey: string): RunnerStep[] {
  const indices = setStepIndices(steps, memberKey);
  const lastIndex = indices.at(-1);
  if (lastIndex === undefined) return steps;

  const template = steps[lastIndex];
  const rest = steps.find((step) => step.kind === 'rest' && step.memberKey === memberKey && !step.standalone);

  const inserted: RunnerStep[] = rest ? [{ ...rest }, { ...template }] : [{ ...template }];
  const next = [...steps.slice(0, lastIndex + 1), ...inserted, ...steps.slice(lastIndex + 1)];
  return renumber(next, memberKey);
}

/**
 * One fewer set of `memberKey`, removing its last one and the rest that led into it.
 *
 * Never drops the member's only set. Whether it may drop the *last* one is the caller's call, not
 * this function's: the runner holds the floor (what has been logged, plus the set in progress),
 * because only it knows what has already reached the session file.
 */
export function dropLastSetForMember(steps: RunnerStep[], memberKey: string): RunnerStep[] {
  const indices = setStepIndices(steps, memberKey);
  if (indices.length <= 1) return steps;

  const lastIndex = indices[indices.length - 1];
  const before = steps[lastIndex - 1];
  const dropsRest = before?.kind === 'rest' && before.memberKey === memberKey && !before.standalone;
  const from = dropsRest ? lastIndex - 1 : lastIndex;

  const next = [...steps.slice(0, from), ...steps.slice(lastIndex + 1)];
  return renumber(next, memberKey);
}

/**
 * One exercise's steps on its own, outside any workout — what a mid-session substitute expands to.
 *
 * A thin wrapper over the same `expandExercise` the workout expansion uses, so a swapped-in exercise
 * is built exactly the way it would have been had the workout named it in the first place: same rest
 * interleaving, same target fields, same notes.
 */
export function buildStepsForExercise(exercise: Exercise, blockIndex: number, memberKey: string): RunnerStep[] {
  // The clamp flag is dropped here on purpose: a mid-session substitute is sized by the sets it
  // *replaces* (see swapExerciseForMember), so the new exercise's own count is trimmed to fit anyway
  // and "was it clamped" is not a question about the workout the user is running.
  return expandExercise(exercise, blockIndex, memberKey).steps;
}

/**
 * Substitutes `exercise` for whatever is left of `memberKey`, from `fromIndex` onward.
 *
 * The substitute gets the **remaining set count**, not its own configured one: three sets left means
 * three sets of the new exercise. This is a substitution inside the workout rather than a rewrite of
 * it, so the block keeps its shape — while the reps, load, hold and rest targets all come from the new
 * exercise, which is what makes it a different exercise rather than a rename.
 *
 * Sized by reusing the part-1 mutations rather than reimplementing the trimming, which is why the
 * `rest_sec: 0` rule and the renumbering hold here for free.
 *
 * The caller supplies `newMemberKey` and must never reuse the old one: every accumulating log in the
 * runner is keyed by it, and reissuing a key for work already logged would make the substitute's sets
 * grow the *original* exercise's session entry.
 */
export function swapExerciseForMember(
  steps: RunnerStep[],
  memberKey: string,
  fromIndex: number,
  exercise: Exercise,
  newMemberKey: string,
): RunnerStep[] {
  const remaining = setStepIndices(steps, memberKey).filter((index) => index >= fromIndex).length;
  if (remaining === 0) return steps;

  const memberIndices = steps.flatMap((step, index) => (step.memberKey === memberKey ? [index] : []));
  const lastIndex = memberIndices[memberIndices.length - 1];
  // Non-circuit members are contiguous, which is what makes "the rest of this member" a slice at all —
  // the runner is what refuses to offer this inside a circuit.
  if (lastIndex === undefined || lastIndex < fromIndex) return steps;

  const blockIndex = steps[fromIndex].blockIndex;
  let replacement = buildStepsForExercise(exercise, blockIndex, newMemberKey);
  if (replacement.length === 0) return steps;
  while (setStepsForMember(replacement, newMemberKey) > remaining) {
    replacement = dropLastSetForMember(replacement, newMemberKey);
  }
  while (setStepsForMember(replacement, newMemberKey) < remaining) {
    replacement = addSetForMember(replacement, newMemberKey);
  }

  return [...steps.slice(0, fromIndex), ...replacement, ...steps.slice(lastIndex + 1)];
}

/**
 * The whole workout, expanded, plus whether either ceiling cut it short.
 *
 * `buildSteps` below is the plain-array form that almost everything wants; the session screen takes
 * this one, because it is the only caller that has to tell the user the workout will stop early.
 */
export function buildStepsWithLimits(workout: Workout, exercises: Exercise[]): StepBuildResult {
  const steps: RunnerStep[] = [];
  let truncated = false;

  workout.blocks.forEach((block, blockIndex) => {
    // Checked per block rather than per push, so a truncated workout is made of whole blocks. `blocks`
    // has no upper bound in the schema, so this is reachable without a circuit at all.
    if (steps.length >= MaxStepsPerWorkout) {
      truncated = true;
      return;
    }
    if (block.kind === 'exercise') {
      const exercise = exercises.find((candidate) => candidate.id === block.exerciseId);
      if (!exercise) return;
      // Appended one at a time rather than spread as arguments: `push(...arr)` passes every element as
      // a separate argument and throws `RangeError` past the engine's argument limit, which turned a
      // large set count into a crash before the session screen's first render instead of a long workout.
      const expanded = expandExercise(exercise, blockIndex, `${blockIndex}`, block.configOverride);
      if (expanded.truncated) truncated = true;
      for (const step of expanded.steps) steps.push(step);
      return;
    }

    // Circuit: true round-robin (A,B,C,A,B,C,...) — each member's memberKey stays stable across
    // rounds so its sets accumulate into one session entry per exercise, not one per round.
    const members = block.members
      .map((member, memberIndex) => ({
        member,
        memberIndex,
        exercise: exercises.find((candidate) => candidate.id === member.exerciseId),
      }))
      .filter((entry): entry is typeof entry & { exercise: Exercise } => !!entry.exercise);
    if (members.length === 0) return;

    // Numbered over `members` (the resolved ones), not `block.members` — see CircuitPosition.
    const positionAt = (roundIndex: number, slot: number): CircuitPosition => ({
      round: roundIndex + 1,
      rounds: block.rounds,
      member: slot + 1,
      memberTotal: members.length,
    });

    for (let round = 0; round < block.rounds; round++) {
      // Both loops check, and both matter: rounds are what a `hiit` member multiplies, and `exercises`
      // has no upper bound either, so one round of a huge circuit is unbounded on its own. A `for`
      // loop over the members rather than `forEach`, which cannot stop. Overshoot is one member visit,
      // which `boundedCount` caps.
      if (steps.length >= MaxStepsPerWorkout) {
        truncated = true;
        break;
      }
      for (let i = 0; i < members.length; i++) {
        if (steps.length >= MaxStepsPerWorkout) {
          truncated = true;
          break;
        }
        const { member, memberIndex, exercise } = members[i];
        const memberKey = `${blockIndex}:${memberIndex}`;
        // Shared by the member's own steps and by the rest that follows it, which belongs to the work
        // it follows rather than to what comes next.
        const at = positionAt(round, i);
        // One at a time, for the same reason as the exercise block above.
        const visit = expandExercise(exercise, blockIndex, memberKey, member.configOverride, {
          index: round + 1,
          total: block.rounds,
        });
        if (visit.truncated) truncated = true;
        for (const step of visit.steps) steps.push({ ...step, circuit: at });
        const isLastMember = i === members.length - 1;
        if (!isLastMember && block.restBetweenExercisesSec) {
          steps.push({
            kind: 'rest',
            blockIndex,
            memberKey: `${blockIndex}:circuit-rest`,
            circuit: at,
            exerciseId: exercise.id,
            standalone: false,
            seconds: block.restBetweenExercisesSec,
          });
        }
      }
      const isLastRound = round === block.rounds - 1;
      if (!isLastRound && block.restBetweenRoundsSec) {
        steps.push({
          kind: 'rest',
          blockIndex,
          memberKey: `${blockIndex}:circuit-rest`,
          circuit: positionAt(round, members.length - 1),
          exerciseId: members[0].exercise.id,
          standalone: false,
          seconds: block.restBetweenRoundsSec,
        });
      }
    }
  });

  return { steps, truncated };
}

/** Exported so callers (session.tsx) can check for a zero-step workout before ever starting a session. */
export function buildSteps(workout: Workout, exercises: Exercise[]): RunnerStep[] {
  return buildStepsWithLimits(workout, exercises).steps;
}
