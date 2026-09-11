/**
 * What's left in a running workout, for the Coming up sheet (docs/up-next-plan.md).
 *
 * Pure, and next to `session-steps.ts` rather than inside the runner for the same reason that module
 * exists: importing the runner initialises expo-audio, and this has to be testable without it.
 *
 * Built from the **live step list, never from `workout.blocks`**. The list is what the runner will
 * actually run, so a swap, an added set, a program override, an ad-hoc queue and a workout cut short
 * by `MaxStepsPerWorkout` all come out right without being special-cased here. Reading the workout
 * instead would describe what was authored, which stops being the same thing on the first swap.
 *
 * Returns descriptors, never strings: names are the user's own and render verbatim, and everything
 * else is the sheet's to translate.
 */
import type { CircuitPosition, RunnerStep } from '@/hooks/session-steps';

/** A step that is work rather than rest — what the sheet formats a target from, as the Next card does. */
export type WorkStep = Exclude<RunnerStep, { kind: 'rest' }>;

type CircuitWorkStep = WorkStep & { circuit: CircuitPosition };

/** What "2 more …" counts: sets for reps and holds, rounds for HIIT, minutes for EMOM; null for one-shot work. */
export type WorkUnit = 'set' | 'round' | 'minute' | null;

/** One circuit member's visit, carried by its first step so the sheet can show the target. */
export type UpcomingMember = { memberKey: string; step: WorkStep };

export type UpcomingItem =
  /**
   * A non-circuit exercise. `started` is whether the step on screen belongs to it — the difference
   * between "2 more sets" and "2 sets".
   */
  | { kind: 'exercise'; memberKey: string; step: WorkStep; left: number; unit: WorkUnit; started: boolean }
  /** A standalone Rest block. Inter-set and circuit rest are never listed. */
  | { kind: 'rest'; seconds: number }
  /** The first round with work left in the circuit you're in, each remaining member spelled out. */
  | { kind: 'round'; blockIndex: number; round: number; rounds: number; members: UpcomingMember[] }
  /** The rounds after that one, collapsed. */
  | { kind: 'moreRounds'; blockIndex: number; count: number; names: string[] }
  /** A circuit you haven't reached, collapsed to one item. */
  | { kind: 'circuit'; blockIndex: number; rounds: number; names: string[] };

function unitOf(step: WorkStep): WorkUnit {
  if (step.kind === 'reps' || step.kind === 'hold') return 'set';
  if (step.variant === 'hiit') return 'round';
  if (step.variant === 'emom') return 'minute';
  return null;
}

/**
 * One entry per member, in the order they first appear. The folding matters for `hiit` and `emom`
 * members, which run their own interval count on *every* visit (see CircuitVisit in session-steps.ts):
 * without it an 8-round HIIT member would be listed eight times in one round.
 */
function distinctMembers(steps: CircuitWorkStep[]): UpcomingMember[] {
  const seen = new Set<string>();
  const members: UpcomingMember[] = [];
  for (const step of steps) {
    if (seen.has(step.memberKey)) continue;
    seen.add(step.memberKey);
    members.push({ memberKey: step.memberKey, step });
  }
  return members;
}

/**
 * One circuit's remaining steps, as the sheet shows them (option C in the plan).
 *
 * Rounds are read off `step.circuit.round`, **never counted from a member's steps** — the same
 * per-visit interval count that `distinctMembers` folds would turn three visits of an 8-round HIIT
 * into "24 more". And they are the rounds *present in the list*, not the circuit's configured total,
 * so a truncated workout lists exactly what the runner will run before it stops.
 */
function circuitItems(group: RunnerStep[], current: RunnerStep | undefined): UpcomingItem[] {
  const blockIndex = group[0].blockIndex;
  // Whether the step on screen is inside this circuit — its work, or a rest between its members.
  const visit =
    current?.circuit && current.blockIndex === blockIndex
      ? { memberKey: current.memberKey, round: current.circuit.round }
      : null;

  const work = group.filter(
    (step): step is CircuitWorkStep =>
      step.kind !== 'rest' &&
      step.circuit !== undefined &&
      // The rest of the visit in progress — a HIIT member's remaining intervals — is what the NOW row
      // is showing, not the next member.
      !(visit && step.memberKey === visit.memberKey && step.circuit.round === visit.round),
  );
  if (work.length === 0) return [];

  const rounds = [...new Set(work.map((step) => step.circuit.round))];
  const namesIn = (round: number) =>
    distinctMembers(work.filter((step) => step.circuit.round === round)).map((member) => member.step.exerciseName);

  if (!visit) return [{ kind: 'circuit', blockIndex, rounds: rounds.length, names: namesIn(rounds[0]) }];

  // "First round with work left" rather than "the current round": on the rest between rounds, or on the
  // last member of one, the current round has nothing left, and the next round is what's coming.
  const items: UpcomingItem[] = [
    {
      kind: 'round',
      blockIndex,
      round: rounds[0],
      rounds: work[0].circuit.rounds,
      members: distinctMembers(work.filter((step) => step.circuit.round === rounds[0])),
    },
  ];
  if (rounds.length > 1) {
    items.push({ kind: 'moreRounds', blockIndex, count: rounds.length - 1, names: namesIn(rounds[1]) });
  }
  return items;
}

/**
 * Everything after the step on screen, grouped the way the workout was written.
 *
 * Never throws, because a throw here is a throw in the session screen's render, and that screen's
 * error boundary ends the workout. An index past the end — how a parked ad-hoc session looks — or an
 * empty list is simply nothing left.
 */
export function upcomingOutline(steps: RunnerStep[], stepIndex: number): UpcomingItem[] {
  const current: RunnerStep | undefined = steps[stepIndex];
  const items: UpcomingItem[] = [];
  let i = Math.max(stepIndex + 1, 0);

  while (i < steps.length) {
    const step = steps[i];

    // A circuit's steps are contiguous as a block, rests included (both kinds carry a position), so
    // the whole block is taken at once and handed over.
    if (step.circuit) {
      let end = i;
      while (end < steps.length && steps[end].circuit && steps[end].blockIndex === step.blockIndex) end++;
      items.push(...circuitItems(steps.slice(i, end), current));
      i = end;
      continue;
    }

    i++;
    if (step.kind === 'rest') {
      if (step.standalone) items.push({ kind: 'rest', seconds: step.seconds });
      continue;
    }

    // Grouped by `memberKey`, not `blockIndex`: a swap issues the substitute a new key inside the same
    // block, and it should be listed under its own name. Inter-set rest pushes nothing, so a member's
    // sets stay adjacent here even though they aren't in the list.
    const last = items.at(-1);
    if (last?.kind === 'exercise' && last.memberKey === step.memberKey) {
      items[items.length - 1] = { ...last, left: last.left + 1 };
      continue;
    }
    items.push({
      kind: 'exercise',
      memberKey: step.memberKey,
      step,
      left: 1,
      unit: unitOf(step),
      // Also true while resting between its sets: an inter-set rest carries its exercise's key.
      started: current !== undefined && !current.circuit && current.memberKey === step.memberKey,
    });
  }

  return items;
}
