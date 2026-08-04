/**
 * The pure half of the session runner: the step model and the workout → step-list expansion.
 *
 * Split out of use-session-runner.ts so it can be imported without dragging in that hook's native
 * dependencies (expo-audio, expo-haptics, notifications) — those made these otherwise-pure functions
 * untestable, since merely importing them initialised native modules. No behaviour changed in the move.
 */
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

function expandExercise(
  exercise: Exercise,
  blockIndex: number,
  memberKey: string,
  configOverride?: BlockConfigOverride,
  visit?: CircuitVisit,
): RunnerStep[] {
  switch (exercise.type) {
    case 'timed_hold': {
      const sets = visit ? 1 : exercise.config.sets;
      // A 0 (or negative) end would auto-advance on the very first tick, skipping the hold outright —
      // and nothing validates a program week's override config, so `hold_sec_min: 0` reaches here from
      // both the override schema (a free record of numbers) and the in-app override editor (no
      // validateConfig). Degrading to a no-auto-end hold keeps the set runnable by hand, which is what
      // it did before holds could end themselves; vanishing mid-workout would not be a degrade.
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
      return steps;
    }
    case 'reps': {
      const sets = visit ? 1 : exercise.config.sets;
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
        if (i < exercise.config.rounds - 1 && exercise.config.restSec > 0) {
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
      return steps;
    }
    case 'emom': {
      const steps: RunnerStep[] = [];
      // `totalMinutes` is the block's total *duration*, not its interval count — those only coincide
      // for a literal every-minute EMOM. Looping `totalMinutes` times at `intervalSec` each meant a
      // 30-second interval over 10 minutes ran for 5, while estimateExerciseSeconds (selectors.ts)
      // reported the honest `totalMinutes * 60`; the two silently disagreed for any interval but 60s.
      // Clamped to at least one so an interval longer than the whole block still runs once rather
      // than making the exercise vanish into the "nothing to run" path.
      const intervalCount = Math.max(1, Math.floor((exercise.config.totalMinutes * 60) / exercise.config.intervalSec));
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
  return expandExercise(exercise, blockIndex, memberKey);
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
      members.forEach(({ member, memberIndex, exercise }, i) => {
        const memberKey = `${blockIndex}:${memberIndex}`;
        // Shared by the member's own steps and by the rest that follows it, which belongs to the work
        // it follows rather than to what comes next.
        const at = positionAt(round, i);
        steps.push(
          ...expandExercise(exercise, blockIndex, memberKey, member.configOverride, {
            index: round + 1,
            total: block.rounds,
          }).map((step): RunnerStep => ({ ...step, circuit: at })),
        );
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
      });
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

  return steps;
}
