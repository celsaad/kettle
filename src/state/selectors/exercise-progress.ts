import type { Exercise, Session } from '@/domain/types';
import { entryBest, recordKey, type RecordKind } from '@/state/selectors/records';

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
  /**
   * `latest` is the best this exercise has ever put up on its measure, **and** that best was set
   * inside the window by beating an earlier session. Both halves matter: a record set this month and
   * then fallen back from is not "best ever" beside today's lower number, and a best set months ago and
   * merely matched today is not news. Beating is judged by `sessionRecords`' rules — strictly greater,
   * and a first-ever entry beats nothing — so the row and the completion screen agree on what a record
   * is.
   */
  bestEver: boolean;
  /**
   * The sessions behind `points`, oldest first: when each was, what it put up, and whether it set a new
   * best by beating everything logged before it — the same test `bestEver` applies to the latest. What
   * the exercise's own progress screen charts and lists, so that screen and this row are one reading.
   */
  sessions: ProgressSession[];
};

export type ProgressSession = { at: string; value: number; newBest: boolean };

/**
 * The Stats screen's "Getting stronger" section, already split the way it renders.
 *
 * `movers` changed over the window and are the section; `steady` didn't, and fold into one row.
 * `fixedHoldsLeftOut` counts what would have been a row but can't move at all — see
 * `isFixedTargetHold` — so the screen can say they were left out rather than let them look forgotten.
 */
export type ProgressView = {
  movers: ExerciseProgress[];
  steady: ExerciseProgress[];
  fixedHoldsLeftOut: number;
};

/**
 * A timed hold the runner ends by itself at one fixed target, which makes its logged time a copy of
 * its config rather than a measurement.
 *
 * The runner ends a hold at `holdSecMax ?? holdSecMin` and clamps the logged `holdSec` to that end
 * (`use-session-runner.ts`), so a 45s stretch logs 45s every time it is finished. It can go down, by
 * ending early, and never up — the same reason `entryBest` leaves `hiit` and `emom` out, and on a
 * mobility-heavy log it was most of the screen: a dozen rows reading "no change" forever.
 *
 * A **range** stays in, since climbing toward the top of it is real progress, and so does a
 * max-effort hold, which has no end at all. A range whose top equals its bottom does not: the schema
 * and `validateConfig` both accept `hold_sec_max == hold_sec_min`, and the runner ends it at the same
 * second either way, so it is a fixed hold written differently.
 *
 * Judged on the exercise's **current** config, because the log doesn't record the config a session
 * ran under. So an exercise switched from fixed to max-effort brings its capped history with it — a
 * trend that corrects itself as new sessions land, rather than a reason to invent a config history.
 *
 * Here rather than in `entryBest`, which would otherwise be the natural home: records and the runner's
 * live marker can't fire on a fixed hold anyway (nothing beats a full hold, and a tie is not a record),
 * so moving the rule there would thread the library into the runner for no change in behaviour.
 */
function isFixedTargetHold(exercise: Exercise): boolean {
  if (exercise.type !== 'timed_hold') return false;
  const { holdSecMin, holdSecMax } = exercise.config;
  return holdSecMin !== undefined && holdSecMin > 0 && (holdSecMax === undefined || holdSecMax === holdSecMin);
}

/**
 * How far back Getting stronger looks, and the exercise progress screen a row opens with it — one
 * constant, so a row and the chart behind it can never be measuring different windows.
 *
 * Eight weeks. Four is two or three sessions of most exercises, which is not a trend; eight is two
 * months — long enough for a number to move, short enough to still be about what you're doing now.
 */
export const TREND_WEEKS = 8;

/**
 * Per-exercise progress over the last `weeks` weeks.
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
 * On top of those, fixed-target holds are left out — see `isFixedTargetHold` — which needs the
 * library, and is the one rule here that `entryBest` doesn't share.
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
 * **Movers are ordered by how far they moved, relative to where they started** — gains first, then
 * dips. Relative so that +3 reps on 5 outranks +2.5 kg on 60, which is what "most improved" means; the
 * floor of 1 keeps a hold that started at 0s from dividing by zero. Steady rows keep the old
 * most-recently-trained order, since with no change to rank there is nothing better to sort on.
 *
 * `now` is a parameter for the same reason `trainingCalendar` and `nextUpView` take one: the rule is
 * testable without mocking the clock, and the caller owns the clock.
 */
export function exerciseProgress(
  sessions: Session[],
  exercises: Exercise[],
  weeks: number,
  now: Date = new Date(),
): ProgressView {
  // setDate() rather than subtracting weeks × 7 × 86_400_000: a window spanning a DST change is an
  // hour short, and near midnight that silently drops the oldest session out of range. Same hazard
  // `trainingCalendar` and `currentStreak` handle the same way.
  const windowStart = new Date(now);
  windowStart.setDate(windowStart.getDate() - weeks * 7);

  const fixedHolds = new Set(exercises.filter(isFixedTargetHold).map((exercise) => exercise.id));

  // The whole log, not just the window: whether a value is "best ever" depends on everything before
  // the window too. Oldest first, since a record is a comparison with what came earlier, and the store
  // keeps sessions newest first. Unfinished sessions are skipped for the same reason every other
  // selector skips them: they are abandoned or mid-flight, and the log the app reports on is the
  // finished one.
  const finished = sessions
    .filter((session) => session.endedAt && new Date(session.startedAt) <= now)
    // Sorting the array `filter` just made, which nothing else holds a reference to.
    // oxlint-disable-next-line unicorn/no-array-sort
    .sort((a, b) => a.startedAt.localeCompare(b.startedAt));

  type Point = { at: string; kind: RecordKind; value: number; newBest: boolean };
  const windowed = new Map<string, Point[]>();
  // Per exercise and kind: the best so far, and when it was last raised by beating an earlier session.
  const records = new Map<string, { best: number; raisedAt: string | null }>();

  for (const session of finished) {
    // One value per exercise and kind per session, at the better of them: a workout that logs the
    // same exercise in two blocks writes two entries, and they are one session's work, not two points.
    const inSession = new Map<string, { exercise: string; kind: RecordKind; value: number }>();
    for (const entry of session.entries) {
      const best = entryBest(entry);
      if (!best) continue;
      const key = recordKey(entry.exercise, best.kind);
      const seen = inSession.get(key);
      if (!seen || best.value > seen.value) {
        inSession.set(key, { exercise: entry.exercise, kind: best.kind, value: best.value });
      }
    }

    const inWindow = new Date(session.startedAt) >= windowStart;
    for (const [key, { exercise, kind, value }] of inSession) {
      const record = records.get(key);
      const newBest = record !== undefined && value > record.best;
      if (!record) records.set(key, { best: value, raisedAt: null });
      else if (newBest) records.set(key, { best: value, raisedAt: session.startedAt });

      if (!inWindow) continue;
      const point = { at: session.startedAt, kind, value, newBest };
      const seen = windowed.get(exercise);
      if (seen) seen.push(point);
      else windowed.set(exercise, [point]);
    }
  }

  const rows: ExerciseProgress[] = [];
  let fixedHoldsLeftOut = 0;
  for (const [exerciseId, points] of windowed) {
    const kind = points.at(-1)!.kind;
    const ofKind = points.filter((point) => point.kind === kind);
    if (ofKind.length < 2) continue;
    // Counted only past the two-session floor, so the note the screen shows is about rows that would
    // otherwise have been there.
    if (fixedHolds.has(exerciseId)) {
      fixedHoldsLeftOut += 1;
      continue;
    }

    const values = ofKind.map((point) => point.value);
    const latest = values.at(-1)!;
    const record = records.get(recordKey(exerciseId, kind))!;
    rows.push({
      exerciseId,
      kind,
      points: values,
      latest,
      delta: latest - values[0],
      lastTrainedAt: ofKind.at(-1)!.at,
      bestEver: latest === record.best && record.raisedAt !== null && new Date(record.raisedAt) >= windowStart,
      sessions: ofKind.map(({ at, value, newBest }) => ({ at, value, newBest })),
    });
  }

  // Ties break on recency, then on the id so the order is stable rather than dependent on Map
  // insertion, which follows session order.
  const byRecency = (a: ExerciseProgress, b: ExerciseProgress) =>
    b.lastTrainedAt.localeCompare(a.lastTrainedAt) || a.exerciseId.localeCompare(b.exerciseId);
  const relative = (row: ExerciseProgress) => Math.abs(row.delta) / Math.max(row.points[0], 1);

  const movers = rows
    .filter((row) => row.delta !== 0)
    // Both sorts are over arrays `filter` just made.
    // oxlint-disable-next-line unicorn/no-array-sort
    .sort((a, b) => {
      if (a.delta > 0 !== b.delta > 0) return a.delta > 0 ? -1 : 1;
      return relative(b) - relative(a) || byRecency(a, b);
    });
  // oxlint-disable-next-line unicorn/no-array-sort
  const steady = rows.filter((row) => row.delta === 0).sort(byRecency);

  return { movers, steady, fixedHoldsLeftOut };
}
