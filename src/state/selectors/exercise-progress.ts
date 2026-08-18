import type { Session } from '@/domain/types';
import { entryBest, type RecordKind } from '@/state/selectors/records';

/**
 * One exercise's trend over a window: what it is measured on, every value it put up, and the change
 * from the first of them to the last.
 *
 * `points` is oldest-first, one per session, and is what the sparkline draws. `delta` is deliberately
 * last-minus-first rather than best-minus-worst: the question is "am I moving", and a single good day
 * in the middle of a plateau is not movement.
 */
export type ExerciseProgress = {
  exerciseId: string;
  /** What this exercise competes on — see `entryBest`, which decides it. */
  kind: RecordKind;
  points: number[];
  latest: number;
  delta: number;
  /** ISO `startedAt` of the most recent session in the window, for ordering. */
  lastTrainedAt: string;
};

/**
 * Per-exercise progress over the last `weeks` weeks, most recently trained first.
 *
 * **The measure is not this module's decision.** `entryBest` already owns "what counts as doing more"
 * for the completion screen's records and the runner's live best-marker, and it is reused here so the
 * three can never disagree — including its omissions, which are the reason this screen does not claim
 * to cover everything you did:
 *
 * - `hiit` rounds and `emom` minutes are bounded by the exercise's own config, so a rise there means
 *   the workout was edited, not that more was done.
 * - `cardio` has real records, but comparing distance across two routes needs rules the app doesn't
 *   have.
 *
 * **One kind per exercise, taken from its most recent session.** A bodyweight exercise competes on
 * reps and a loaded one on load, so an exercise that gained a dumbbell partway through the window has
 * points of both kinds — and a delta across them would be arithmetic on two different units. The
 * older kind's points are dropped rather than converted, which reports the shorter honest trend
 * instead of a longer invented one.
 *
 * **Two sessions minimum.** One session in the window is not a trend, and a row reading `+0` next to
 * a single bar says nothing except that the screen wanted another row.
 *
 * `now` is a parameter for the same reason `sessionsPerWeek` and `nextUpView` take one: the rule is
 * testable without mocking the clock, and the caller owns the clock.
 */
export function exerciseProgress(sessions: Session[], weeks: number, now: Date = new Date()): ExerciseProgress[] {
  // setDate() rather than subtracting weeks × 7 × 86_400_000: a window spanning a DST change is an
  // hour short, and near midnight that silently drops the oldest session out of range. Same hazard
  // `sessionsPerWeek` and `currentStreak` handle the same way.
  const windowStart = new Date(now);
  windowStart.setDate(windowStart.getDate() - weeks * 7);

  type Entry = { at: string; kind: RecordKind; value: number };
  const byExercise = new Map<string, Entry[]>();

  for (const session of sessions) {
    // Unfinished sessions are skipped for the same reason every other selector skips them: they are
    // abandoned or mid-flight, and the log the app reports on is the finished one.
    if (!session.endedAt) continue;
    const startedAt = new Date(session.startedAt);
    if (startedAt < windowStart || startedAt > now) continue;

    for (const entry of session.entries) {
      const best = entryBest(entry);
      if (!best) continue;
      const seen = byExercise.get(entry.exercise);
      const point = { at: session.startedAt, kind: best.kind, value: best.value };
      if (seen) seen.push(point);
      else byExercise.set(entry.exercise, [point]);
    }
  }

  const rows: ExerciseProgress[] = [];
  for (const [exerciseId, entries] of byExercise) {
    // Oldest first, which is both the reading order of the sparkline and what makes `at(-1)` the most
    // recent session below. Sorting a copy is unnecessary — `entries` is built here and owned here.
    // oxlint-disable-next-line unicorn/no-array-sort
    entries.sort((a, b) => a.at.localeCompare(b.at));

    const kind = entries.at(-1)!.kind;
    const ofKind = entries.filter((entry) => entry.kind === kind);
    if (ofKind.length < 2) continue;

    const points = ofKind.map((entry) => entry.value);
    const latest = points.at(-1)!;
    rows.push({
      exerciseId,
      kind,
      points,
      latest,
      delta: latest - points[0],
      lastTrainedAt: ofKind.at(-1)!.at,
    });
  }

  // Most recently trained first: what you are working on now is what you came to check. Ties break on
  // the id so the order is stable rather than dependent on Map insertion, which follows session order.
  // oxlint-disable-next-line unicorn/no-array-sort
  rows.sort((a, b) => b.lastTrainedAt.localeCompare(a.lastTrainedAt) || a.exerciseId.localeCompare(b.exerciseId));
  return rows;
}
