import { estimatedOneRepMaxKg } from '@/domain/one-rm';
import type { Exercise, Session, SessionEntry } from '@/domain/types';
import { exerciseName } from '@/state/selectors/exercise-lookup';

/**
 * A personal record set by the session that just finished. Structured, not a sentence —
 * `session-complete.tsx` renders it, and the weight variant has to reach `toDisplayWeight` before it
 * can be a string at all. `exerciseName` is the user's own and renders verbatim.
 */
export type SessionRecord =
  | {
      kind: 'heaviestSet';
      exerciseId: string;
      exerciseName: string;
      weightKg: number;
      reps: number;
      /** Best estimate across the whole entry, which is not always the heaviest set — see entryBest. */
      oneRepMaxKg: number | null;
    }
  | { kind: 'mostReps'; exerciseId: string; exerciseName: string; reps: number }
  | { kind: 'longestHold'; exerciseId: string; exerciseName: string; holdSec: number }
  | { kind: 'mostRounds'; exerciseId: string; exerciseName: string; rounds: number };

type RecordKind = SessionRecord['kind'];

/**
 * One set of a previously logged entry, as data — `session-reps.tsx` / `session-hold.tsx` render it,
 * and the weight can't become a string until it has reached the user's display unit.
 */
export type PreviousSet = { kind: 'reps'; reps: number; weightKg?: number } | { kind: 'hold'; holdSec: number };

type EntryBest = { kind: RecordKind; value: number; reps: number; oneRepMaxKg: number | null } | null;

/**
 * The single number a logged entry puts up for a record, and which record it competes for.
 *
 * Only four of the seven entry types compete, and the omissions are deliberate rather than pending:
 * `hiit` rounds and `emom` minutes are both bounded by the exercise's own config, so "more than last
 * time" there reports that the user edited the workout, not that they did more. `cardio` genuinely
 * has records, but comparing distance across two different routes (or duration across two different
 * distances) needs rules this doesn't have, so it stays out rather than shipping half-answered.
 *
 * The reps split is on whether a load was logged at all, not on its size: `commitCurrentStep` writes
 * `weightKg: … || undefined`, so a bodyweight set has the key *absent* rather than 0 (the same
 * distinction `entryVolume` makes). A bodyweight exercise competes on reps, a loaded one on load —
 * and because the two are separate kinds, adding load to a previously-bodyweight exercise finds no
 * baseline of its own kind and correctly reports nothing.
 */
function entryBest(entry: SessionEntry): EntryBest {
  switch (entry.type) {
    case 'reps': {
      if (entry.sets.length === 0) return null;
      const loaded = entry.sets.flatMap((set) =>
        set.weightKg !== undefined && set.weightKg > 0 ? [{ weightKg: set.weightKg, reps: set.reps }] : [],
      );
      if (loaded.length === 0) {
        return { kind: 'mostReps', value: Math.max(...entry.sets.map((set) => set.reps)), reps: 0, oneRepMaxKg: null };
      }
      const heaviest = loaded.reduce((best, set) => (set.weightKg > best.weightKg ? set : best));
      // Across every set, not just the heaviest one: 90 kg × 8 projects higher than 100 kg × 1, and
      // quoting the estimate of a set that wasn't the best one would be the wrong number twice over.
      const oneRepMaxKg = loaded.reduce<number | null>((best, set) => {
        const estimate = estimatedOneRepMaxKg(set.weightKg, set.reps);
        return estimate !== null && (best === null || estimate > best) ? estimate : best;
      }, null);
      return { kind: 'heaviestSet', value: heaviest.weightKg, reps: heaviest.reps, oneRepMaxKg };
    }
    case 'timed_hold':
      if (entry.sets.length === 0) return null;
      return {
        kind: 'longestHold',
        value: Math.max(...entry.sets.map((set) => set.holdSec)),
        reps: 0,
        oneRepMaxKg: null,
      };
    case 'amrap':
      return { kind: 'mostRounds', value: entry.roundsCompleted, reps: 0, oneRepMaxKg: null };
    case 'hiit':
    case 'emom':
    case 'cardio':
    case 'rest':
      return null;
  }
}

function recordKey(exerciseId: string, kind: RecordKind): string {
  // Serialized rather than joined on a separator: exercise ids come out of the user's hand-written
  // YAML, so any character picked as a separator is one two different ids could collide on.
  return JSON.stringify([exerciseId, kind]);
}

/**
 * The highest value each exercise has ever put up, per record kind, across the finished sessions in
 * `sessions`. One traversal, shared by the completion screen's `sessionRecords` and by the runner's
 * live marker, so "best so far" has exactly one definition — including which entry types compete at
 * all (see `entryBest`) and the rule that an unfinished session doesn't count.
 */
function bestByExerciseAndKind(sessions: Session[], excludeSessionId?: string): Map<string, number> {
  const best = new Map<string, number>();
  for (const session of sessions) {
    if (!session.endedAt || session.id === excludeSessionId) continue;
    for (const entry of session.entries) {
      const entryValue = entryBest(entry);
      if (!entryValue) continue;
      const key = recordKey(entry.exercise, entryValue.kind);
      const seen = best.get(key);
      if (seen === undefined || entryValue.value > seen) best.set(key, entryValue.value);
    }
  }
  return best;
}

/** Absent rather than 0 for a kind never logged: "no best yet" is not "a best of nothing". */
export type PersonalBest = { heaviestSetKg?: number; mostReps?: number; longestHoldSec?: number };

/**
 * One exercise's best-ever values, for the runner's live "this beats your best" marker.
 *
 * Same traversal and same rules as `sessionRecords` — a loaded exercise is judged on load and a
 * bodyweight one on reps, unfinished sessions are skipped — so the marker on the set row and the
 * record on the completion screen can never disagree about what counts.
 */
export function personalBestFor(sessions: Session[], exerciseId: string): PersonalBest {
  const best = bestByExerciseAndKind(sessions);
  return {
    heaviestSetKg: best.get(recordKey(exerciseId, 'heaviestSet')),
    mostReps: best.get(recordKey(exerciseId, 'mostReps')),
    longestHoldSec: best.get(recordKey(exerciseId, 'longestHold')),
  };
}

/**
 * What was logged for `exerciseId` on this set number, the last time it was trained — the "last time:
 * 60 kg × 8" the runner puts on the set row.
 *
 * Matched on **set index**, so set 3 shows set 3 of last time rather than a summary of the whole
 * entry; a previous entry that was shorter falls back to its last set, which is the honest answer to
 * "what was I lifting by then". Newest-first traversal with the same skips as `exerciseHistory`:
 * unfinished sessions and `rest` entries are not part of the log the app reports on.
 *
 * Only `reps` and `timed_hold` have per-set values to show. Interval work is answered by its own
 * screens, and there is no per-set number to carry across sessions.
 */
export function previousSetFor(sessions: Session[], exerciseId: string, setIndex: number): PreviousSet | null {
  for (const session of sessions) {
    if (!session.endedAt) continue;
    for (const entry of session.entries) {
      if (entry.exercise !== exerciseId) continue;
      if (entry.type === 'reps') {
        const set = entry.sets[setIndex - 1] ?? entry.sets.at(-1);
        return set ? { kind: 'reps', reps: set.reps, weightKg: set.weightKg } : null;
      }
      if (entry.type === 'timed_hold') {
        const set = entry.sets[setIndex - 1] ?? entry.sets.at(-1);
        return set ? { kind: 'hold', holdSec: set.holdSec } : null;
      }
    }
  }
  return null;
}

/**
 * What `session` beat, judged against every *earlier finished* session in `priorSessions`.
 *
 * Two rules that between them decide what a record means here, both chosen deliberately:
 *
 * - **A tie is not a record.** Strictly greater, so repeating last week's top set is not celebrated as
 *   if it were progress.
 * - **A first-ever entry is not a record.** Beating something is the whole content of the claim, and
 *   without this every exercise in a new user's first week lights up and the badge means nothing by
 *   session three.
 *
 * Unfinished sessions are skipped on the same grounds as `exerciseHistory`: they are not part of the
 * log the rest of the app reports on, and counting one would let an abandoned session suppress a real
 * record. `session` itself is skipped if it appears in `priorSessions`, since the caller reads both
 * from the same store.
 */
export function sessionRecords(session: Session, priorSessions: Session[], exercises: Exercise[]): SessionRecord[] {
  const bestBefore = bestByExerciseAndKind(priorSessions, session.id);

  // One record per exercise+kind even when the session logged the same exercise in two blocks, which
  // is two entries under two member keys — otherwise the completion screen reports the same PR twice.
  const records = new Map<string, SessionRecord>();
  for (const entry of session.entries) {
    const best = entryBest(entry);
    if (!best) continue;
    const key = recordKey(entry.exercise, best.kind);
    const previous = bestBefore.get(key);
    if (previous === undefined || best.value <= previous) continue;
    const already = records.get(key);
    if (already && bestOf(already) >= best.value) continue;

    const identity = { exerciseId: entry.exercise, exerciseName: exerciseName(exercises, entry.exercise) };
    switch (best.kind) {
      case 'heaviestSet':
        records.set(key, {
          kind: 'heaviestSet',
          ...identity,
          weightKg: best.value,
          reps: best.reps,
          oneRepMaxKg: best.oneRepMaxKg,
        });
        break;
      case 'mostReps':
        records.set(key, { kind: 'mostReps', ...identity, reps: best.value });
        break;
      case 'longestHold':
        records.set(key, { kind: 'longestHold', ...identity, holdSec: best.value });
        break;
      case 'mostRounds':
        records.set(key, { kind: 'mostRounds', ...identity, rounds: best.value });
        break;
    }
  }

  return [...records.values()];
}

/** The number an already-collected record is holding, for the same-exercise-twice comparison above. */
function bestOf(record: SessionRecord): number {
  switch (record.kind) {
    case 'heaviestSet':
      return record.weightKg;
    case 'mostReps':
      return record.reps;
    case 'longestHold':
      return record.holdSec;
    case 'mostRounds':
      return record.rounds;
  }
}
