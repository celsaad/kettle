import { resolveWorkoutForWeek } from '@/domain/program';
import type { Exercise, Library, Program, ProgramWeek, Session, Workout } from '@/domain/types';

/** Where the "start this" button on either card points. */
export type SessionParams = { workoutId: string } | { programId: string; week: string; day?: string };

/**
 * What the home screen queues up. A union because a rest day has no workout, no exercises and nothing
 * to start — modelling it as a `NextUpView` with null fields would put the burden of noticing on every
 * reader of `.workout`, and the card is the one place that must not get this wrong.
 *
 * Week fields are structured rather than a sentence: the view composes the label so it can be
 * translated, and the `day` inside it is user data that renders verbatim.
 */
export type NextUpView =
  | {
      kind: 'workout';
      workout: Workout;
      exercises: Exercise[];
      weekNumber: number | null;
      weekDay: string | null;
      weekNotes: string | null;
      sessionParams: SessionParams;
    }
  | {
      kind: 'rest';
      weekNumber: number;
      weekDay: string | null;
      weekNotes: string | null;
      /** The next slot that actually runs something, for the card's "train anyway" escape hatch. */
      skipTo: SessionParams | null;
    };

/**
 * Seeded by the day (not Math.random) so the pick is stable across re-renders within the same
 * day instead of flickering on every unrelated state change. This is the seam for a future
 * recency-aware heuristic (e.g. suggest a full-body workout after a long layoff) — for now it
 * just avoids always landing on the same workout.
 */
function workoutOfTheDay(workouts: Workout[]): Workout {
  const dayIndex = Math.floor(Date.now() / 86_400_000);
  return workouts[dayIndex % workouts.length];
}

function flatFallback(library: Library): NextUpView | null {
  if (library.workouts.length === 0) return null;
  const workout = workoutOfTheDay(library.workouts);
  return {
    kind: 'workout',
    workout,
    exercises: library.exercises,
    weekNumber: null,
    weekDay: null,
    weekNotes: null,
    sessionParams: { workoutId: workout.id },
  };
}

/**
 * The program the user is currently following: whichever program the most recently *started*
 * session was run under (sessions are already sorted newest-first). Falls back to the first
 * library program when no session has ever been tied to one (e.g. a fresh install).
 */
function activeProgram(library: Library, sessions: Session[]): Program | undefined {
  const lastProgramSession = sessions.find((session) => session.program);
  const lastProgram =
    lastProgramSession && library.programs.find((candidate) => candidate.id === lastProgramSession.program);
  return lastProgram ?? library.programs[0];
}

/**
 * The rotation order: by week number, and **within one week number, the order the weeks were
 * written**. Same order `program-detail.tsx` lists them in.
 *
 * The days of a week are ordered by nothing but their position in the file, which is load-bearing
 * rather than lazy: `day` is free user text, so there is no ordering to read out of it. This used to
 * break ties on `day.localeCompare`, which sorted a week alphabetically — `Monday`…`Sunday`, the
 * obvious way to write a calendar week, ran *Friday → Monday → Saturday → Sunday → Thursday →
 * Tuesday → Wednesday*, and numbered labels broke past `Day 9`. The seed library worked around it by
 * labelling days `Day 1`…`Day 7`; an outside author got no warning and no way to discover the rule,
 * and `program-detail.tsx` displayed the honest file order the whole time.
 *
 * Relies on `Array.prototype.sort` being **stable** (required since ES2019, honoured by Hermes) to
 * leave equal week numbers in input order — so the comparator deliberately has no tiebreak, and
 * adding one back would reintroduce the bug.
 */
function sortedProgramWeeks(program: Program): ProgramWeek[] {
  // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
  // oxlint-disable-next-line unicorn/no-array-sort
  return [...program.weeks].sort((a, b) => a.week - b.week);
}

/** The most recent session tracked against this program's weeks, or null. `sessions` is newest-first. */
function lastTrackedSession(program: Program, sessions: Session[]): Session | null {
  return sessions.find((session) => session.program === program.id && session.programWeek != null) ?? null;
}

/** Index into `sortedProgramWeeks` of the slot that comes after the last tracked session. */
function nextWeekIndexAfter(sortedWeeks: ProgramWeek[], program: Program, sessions: Session[]): number {
  const lastSession = lastTrackedSession(program, sessions);
  const lastIndex = lastSession
    ? sortedWeeks.findIndex((week) => week.week === lastSession.programWeek && (week.day ?? null) === lastSession.programDay)
    : -1;
  return lastIndex === -1 ? 0 : (lastIndex + 1) % sortedWeeks.length;
}

/**
 * The week right after whichever one the most recent tracked-progress session was actually for
 * (`sessions` is newest-first, so `.find` naturally gets it) — not a count of completed sessions,
 * which drifts from reality the moment a week is redone or a different week is started directly
 * (program-detail.tsx's per-week "Start this week" lets you start any week, not just the suggested
 * one). No matching session (brand new program, or every session predates week-tracking) starts from
 * the beginning; reaching the end wraps back to the start, so finishing a program restarts it.
 */
export function nextWeekAfter(program: Program, sessions: Session[]): ProgramWeek {
  const sortedWeeks = sortedProgramWeeks(program);
  return sortedWeeks[nextWeekIndexAfter(sortedWeeks, program, sessions)];
}

/** How many rest slots sit in a row from `startIndex`, wrapping. Capped at the length: an all-rest
 *  program (which the schema refuses on import but the in-app editor can still build) would otherwise
 *  spin forever. */
function restRunLength(sortedWeeks: ProgramWeek[], startIndex: number): number {
  let run = 0;
  while (run < sortedWeeks.length && sortedWeeks[(startIndex + run) % sortedWeeks.length].restDay) run += 1;
  return run;
}

/**
 * Whole calendar days from `startedAt` to `now`, both taken at local midnight.
 *
 * Rounds the millisecond difference rather than flooring it, which is what makes it survive DST: the
 * two midnights either side of a boundary are 23 or 25 hours apart, and flooring 23/24 reports zero
 * days for a day that genuinely passed. The same hazard `currentStreak` and `restDayReminderAt`
 * handle, with the same reasoning and a different arithmetic.
 */
function calendarDaysBetween(startedAt: string, now: Date): number {
  const from = new Date(startedAt);
  from.setHours(0, 0, 0, 0);
  const to = new Date(now);
  to.setHours(0, 0, 0, 0);
  return Math.round((to.getTime() - from.getTime()) / 86_400_000);
}

/**
 * Picks what the home screen's "Next up" card shows: the next slot of the active program (see
 * nextWeekAfter). Falls back to a library workout when there's no program to follow, or when the
 * program's next slot names a workout that no longer exists.
 *
 * **How a rest day clears itself.** Nothing about a rest day is ever logged, so the pointer arithmetic
 * above — which is derived entirely from the log — would pin this card to the rest slot forever. What
 * unpins it is elapsed calendar days: the day you train, the rest slot that follows is what's next; the
 * day after, it's the rest day itself; the day after that, it's spent. In one line, with `R`
 * consecutive rest slots:
 *
 *     restsServed = max(0, daysSince(lastSession) - 1)
 *
 * and the card shows rest slot `restsServed + 1` until `restsServed` reaches `R`. So two rest slots
 * take two days, and a week written out in full behaves like a week.
 *
 * A persisted "rest day done" flag was the obvious alternative and was rejected: web can't persist
 * anything, and a user who never taps the button is stuck. The card's "train anyway" link (`skipTo`)
 * is the escape hatch instead, so nobody is ever blocked by this arithmetic disagreeing with their
 * actual week.
 *
 * `now` is a parameter rather than a `new Date()` inside, so the rule is testable without mocking the
 * clock — the same split `serializeSessionArchiveYaml` uses. The caller owns the clock.
 */
export function nextUpView(library: Library, sessions: Session[], now: Date = new Date()): NextUpView | null {
  const program = activeProgram(library, sessions);
  if (!program || program.weeks.length === 0) return flatFallback(library);

  const sortedWeeks = sortedProgramWeeks(program);
  const startIndex = nextWeekIndexAfter(sortedWeeks, program, sessions);
  const paramsFor = (week: ProgramWeek): SessionParams => ({
    programId: program.id,
    week: String(week.week),
    day: week.day,
  });

  const restRun = restRunLength(sortedWeeks, startIndex);
  if (restRun > 0) {
    const trainingWeek = restRun < sortedWeeks.length ? sortedWeeks[(startIndex + restRun) % sortedWeeks.length] : null;
    const lastSession = lastTrackedSession(program, sessions);
    // No session ever tracked against this program means no anchor to count from, so a program that
    // opens on a rest day shows that rest day until something is logged. "Train anyway" is the way
    // out, and the first session logged gives every later day a real anchor.
    const elapsed = lastSession ? calendarDaysBetween(lastSession.startedAt, now) : 0;
    const restsServed = Math.max(0, elapsed - 1);

    if (trainingWeek === null || restsServed < restRun) {
      const restWeek = sortedWeeks[(startIndex + Math.min(restsServed, restRun - 1)) % sortedWeeks.length];
      return {
        kind: 'rest',
        weekNumber: restWeek.week,
        weekDay: restWeek.day ?? null,
        weekNotes: restWeek.notes ?? null,
        skipTo: trainingWeek ? paramsFor(trainingWeek) : null,
      };
    }
  }

  const targetWeek = sortedWeeks[(startIndex + restRun) % sortedWeeks.length];
  const resolved = resolveWorkoutForWeek(program, targetWeek.week, library, targetWeek.day);
  if (!resolved) return flatFallback(library);

  return {
    kind: 'workout',
    workout: resolved.workout,
    exercises: resolved.exercises,
    weekNumber: targetWeek.week,
    weekDay: targetWeek.day ?? null,
    weekNotes: targetWeek.notes ?? null,
    sessionParams: paramsFor(targetWeek),
  };
}
