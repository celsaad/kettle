import { resolveWorkoutForWeek } from '@/domain/program';
import { firstWeekdayIndex } from '@/i18n';
import { formatMonthBadge, formatMonthDay, formatWeekday } from '@/i18n/format';
import type { EntryResult, WorkoutShape } from '@/domain/format';
import { formatEntryResult, formatSessionDuration, formatSessionName, formatSetCount } from '@/domain/format';
import { estimatedOneRepMaxKg } from '@/domain/one-rm';
import type { Exercise, Library, Program, ProgramWeek, Session, SessionEntry, Workout, WorkoutBlock } from '@/domain/types';

function findExercise(exercises: Exercise[], id: string): Exercise | undefined {
  return exercises.find((exercise) => exercise.id === id);
}

export function exerciseName(exercises: Exercise[], id: string): string {
  return findExercise(exercises, id)?.name ?? id;
}

export type BlockChip = { name: string; isRest: boolean };

/**
 * Carries each chip's `isRest` alongside its name. Callers used to style rest chips by comparing the
 * rendered name to the literal `'Rest'` — the user's own exercise name, so renaming that exercise (or
 * authoring one in any other language) silently dropped the styling.
 */
export function blockChips(workout: Workout, exercises: Exercise[]): BlockChip[] {
  const chipFor = (exerciseId: string): BlockChip => ({
    name: exerciseName(exercises, exerciseId),
    isRest: findExercise(exercises, exerciseId)?.type === 'rest',
  });
  return workout.blocks.flatMap((block) =>
    block.kind === 'circuit' ? block.members.map((member) => chipFor(member.exerciseId)) : [chipFor(block.exerciseId)],
  );
}

function estimateExerciseSeconds(exercise: Exercise, overrideDurationSec?: number): number {
  switch (exercise.type) {
    case 'hiit':
      return exercise.config.rounds * (exercise.config.workSec + exercise.config.restSec);
    // Estimated from the top of the range, because that's where the hold now ends itself. A
    // max-effort hold has no end to estimate and contributes only its rest, the same way cardio
    // without a duration contributes 0 below.
    case 'timed_hold':
      return (
        exercise.config.sets * (exercise.config.holdSecMax ?? exercise.config.holdSecMin ?? 0) +
        (exercise.config.sets - 1) * exercise.config.restSec
      );
    case 'reps':
      return exercise.config.sets * exercise.config.restSec;
    case 'emom':
      return exercise.config.totalMinutes * 60;
    case 'amrap':
      return exercise.config.timeCapSec;
    case 'cardio':
      return exercise.config.durationSec ?? 0;
    case 'rest':
      return overrideDurationSec ?? exercise.config.durationSec;
  }
}

/** A member's single per-visit cost within a circuit round: one hold/rep pass, not the exercise's own `sets`. */
function memberVisitSeconds(exercise: Exercise): number {
  switch (exercise.type) {
    case 'timed_hold':
      return exercise.config.holdSecMax ?? exercise.config.holdSecMin ?? 0;
    case 'reps':
      return exercise.config.restSec;
    case 'hiit':
      return exercise.config.rounds * (exercise.config.workSec + exercise.config.restSec);
    case 'emom':
      return exercise.config.totalMinutes * 60;
    case 'amrap':
      return exercise.config.timeCapSec;
    case 'cardio':
      return exercise.config.durationSec ?? 0;
    case 'rest':
      return exercise.config.durationSec;
  }
}

function estimateBlockSeconds(block: WorkoutBlock, exercises: Exercise[]): number {
  if (block.kind === 'exercise') {
    const exercise = findExercise(exercises, block.exerciseId);
    if (!exercise) return 0;
    return estimateExerciseSeconds(exercise, block.configOverride?.durationSec);
  }

  const members = block.members
    .map((member) => findExercise(exercises, member.exerciseId))
    .filter((exercise): exercise is Exercise => !!exercise);
  const restBetweenExercises = block.restBetweenExercisesSec ?? 0;
  const restBetweenRounds = block.restBetweenRoundsSec ?? 0;

  const roundSeconds =
    members.reduce((sum, exercise) => sum + memberVisitSeconds(exercise), 0) +
    Math.max(0, members.length - 1) * restBetweenExercises;

  return block.rounds * roundSeconds + Math.max(0, block.rounds - 1) * restBetweenRounds;
}

function blockTypes(block: WorkoutBlock, exercises: Exercise[]): Exercise['type'][] {
  if (block.kind === 'exercise') {
    const exercise = findExercise(exercises, block.exerciseId);
    return exercise ? [exercise.type] : [];
  }
  return block.members
    .map((member) => findExercise(exercises, member.exerciseId)?.type)
    .filter((type): type is Exercise['type'] => !!type);
}

/** Structured, not a sentence — `formatWorkoutShape` in domain/format.ts renders it. */
export function workoutShape(workout: Workout, exercises: Exercise[]): WorkoutShape {
  const types = new Set<Exercise['type']>();
  let totalSec = 0;

  for (const block of workout.blocks) {
    for (const type of blockTypes(block, exercises)) {
      if (type !== 'rest') types.add(type);
    }
    totalSec += estimateBlockSeconds(block, exercises);
  }

  return {
    blockCount: workout.blocks.length,
    types: [...types],
    estimatedMinutes: Math.max(1, Math.round(totalSec / 60)),
  };
}

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
 * The week right after whichever one the most recent tracked-progress session was actually for
 * (`sessions` is newest-first, so `.find` naturally gets it) — not a count of completed sessions,
 * which drifts from reality the moment a week is redone or a different week is started directly
 * (program-detail.tsx's per-week "Start this week" lets you start any week, not just the suggested
 * one). No matching session (brand new program, or every session predates week-tracking) starts from
 * the beginning; reaching the end wraps back to the start, so finishing a program restarts it.
 */
/** The rotation order: by week number, then by `day` label. File order is not the running order. */
function sortedProgramWeeks(program: Program): ProgramWeek[] {
  // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
  // oxlint-disable-next-line unicorn/no-array-sort
  return [...program.weeks].sort((a, b) => a.week - b.week || (a.day ?? '').localeCompare(b.day ?? ''));
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

/**
 * How many sets one logged entry is worth. Interval work has no `sets` array, and counting each such
 * entry as a flat 1 — which is what this did — made a 20-minute EMOM and a single 30-second hold the
 * same one "set". Every stat tile in History and Today reads this, so the whole volume story
 * under-reported for anyone whose training is mostly intervals.
 *
 * The comparable unit is one interval actually performed: a HIIT or AMRAP round, an EMOM minute —
 * the same numbers `sessionEntryResult` already reports per entry. `cardio` stays at 1 (one
 * continuous effort, not a set count) and `rest` at 0.
 */
function entrySetCount(entry: SessionEntry): number {
  switch (entry.type) {
    case 'timed_hold':
    case 'reps':
      return entry.sets.length;
    case 'hiit':
    case 'amrap':
      return entry.roundsCompleted;
    case 'emom':
      return entry.minutes.length;
    case 'cardio':
      return 1;
    case 'rest':
      return 0;
  }
}

function sessionSetCount(session: Session): number {
  return session.entries.reduce((count, entry) => count + entrySetCount(entry), 0);
}

function sessionDurationMinutes(session: Session): number {
  if (!session.endedAt) return 0;
  const ms = new Date(session.endedAt).getTime() - new Date(session.startedAt).getTime();
  return Math.max(0, Math.round(ms / 60000));
}

/**
 * The workout behind a session, or `null` when it was started ad-hoc — the stand-in label for that
 * case is `formatSessionName`'s to translate, not this layer's to assemble. Falls back to the raw id
 * for a workout that has since been deleted from the library, which is user data either way.
 */
function workoutNameFor(session: Session, library: Library): string | null {
  if (!session.workout) return null;
  return library.workouts.find((workout) => workout.id === session.workout)?.name ?? session.workout;
}

export type RecentSessionView = {
  id: string;
  workoutName: string;
  dateLabel: string;
  durationLabel: string;
  setsLabel: string;
};

export function recentSessionsView(sessions: Session[], library: Library, limit = 5): RecentSessionView[] {
  return sessions.slice(0, limit).map((session) => ({
    id: session.id,
    workoutName: formatSessionName(workoutNameFor(session, library)),
    dateLabel: formatWeekday(new Date(session.startedAt)),
    durationLabel: formatSessionDuration(sessionDurationMinutes(session)),
    setsLabel: formatSetCount(sessionSetCount(session)),
  }));
}

export type HistoryStats = { sessions: number; hours: number; sets: number; minutes: number };

/**
 * `hours`/`minutes` are the two halves of one "1h 30m" reading, so both must be whole. `hours` used to
 * be `round(totalMinutes / 60 * 10) / 10` — a fractional *total* — while `minutes` was already a
 * remainder, and both renderers print them side by side: 90 minutes came out as "1.5h 30m", double-
 * counting the half hour. Only ever showed up above the hour mark, which is why short test sessions
 * ("0h 0m") never caught it.
 */
export function historyStats(sessions: Session[]): HistoryStats {
  const totalMinutes = sessions.reduce((sum, session) => sum + sessionDurationMinutes(session), 0);
  const totalSets = sessions.reduce((sum, session) => sum + sessionSetCount(session), 0);
  return {
    sessions: sessions.length,
    hours: Math.floor(totalMinutes / 60),
    minutes: totalMinutes % 60,
    sets: totalSets,
  };
}

/**
 * Start of the user's current week, honouring their calendar's first weekday rather than assuming
 * Monday. The Monday assumption is right for most of Europe and wrong for the US, Canada, Japan and
 * much of Latin America — "this week" silently measured a different window than the calendar the user
 * reads, and the discrepancy is invisible until the boundary day.
 */
function startOfWeek(date: Date): Date {
  const start = new Date(date);
  const firstDay = firstWeekdayIndex(); // 0 = Sunday .. 6 = Saturday
  const diff = (start.getDay() - firstDay + 7) % 7;
  start.setDate(start.getDate() - diff);
  start.setHours(0, 0, 0, 0);
  return start;
}

/** historyStats scoped to the current calendar week (Monday start, local time) — same aggregation, just a pre-filtered input. */
export function thisWeekStats(sessions: Session[]): HistoryStats {
  const weekStart = startOfWeek(new Date());
  return historyStats(sessions.filter((session) => new Date(session.startedAt) >= weekStart));
}

/**
 * Consecutive calendar days with at least one session, walking back from today. Today not having a
 * session yet doesn't break the streak (the day isn't over) — only a gap of a full day or more does.
 */
export function currentStreak(sessions: Session[]): number {
  const activeDays = new Set(sessions.map((session) => new Date(session.startedAt).toDateString()));
  if (activeDays.size === 0) return 0;

  // Steps by calendar day rather than by 86_400_000ms. Subtracting a fixed 24 hours lands on the wrong
  // day across a DST boundary — on a 23-hour day it skips back two days, silently truncating a real
  // streak, and on a 25-hour day it stays on the same one. setDate() moves a whole day whatever that
  // day's length is.
  const previousDay = (date: Date): Date => {
    const previous = new Date(date);
    previous.setDate(previous.getDate() - 1);
    return previous;
  };

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const yesterday = previousDay(today);

  if (!activeDays.has(today.toDateString()) && !activeDays.has(yesterday.toDateString())) return 0;

  let streak = 0;
  let cursor = activeDays.has(today.toDateString()) ? today : yesterday;
  while (activeDays.has(cursor.toDateString())) {
    streak += 1;
    cursor = previousDay(cursor);
  }
  return streak;
}

export type HistorySessionEntryView = { exerciseName: string; summary: string };

export type HistorySessionView = {
  id: string;
  day: number;
  month: string;
  workoutName: string;
  durationLabel: string;
  setsLabel: string;
  mixed: boolean;
  /**
   * Whether the card offers its edit affordance. False while a session is still running: the runner
   * owns that file and writes through its own copy, so an edit from History would be overwritten by
   * the next set. `session-history-store`'s `editEntry` refuses the same case — this only keeps the
   * screen from offering something that would be declined.
   */
  editable: boolean;
  entries: HistorySessionEntryView[];
};

/**
 * Structured, not a sentence — `formatEntryResult` in domain/format.ts renders it. Collapses seven
 * entry types onto six shapes: hiit and amrap both reduce to rounds, so nothing downstream has to
 * know which produced them.
 */
export function sessionEntryResult(entry: SessionEntry): EntryResult {
  switch (entry.type) {
    case 'timed_hold':
      return { kind: 'holds', holdSecs: entry.sets.map((set) => set.holdSec) };
    case 'reps':
      return { kind: 'reps', reps: entry.sets.map((set) => set.reps) };
    case 'hiit':
      return { kind: 'rounds', rounds: entry.roundsCompleted };
    case 'emom': {
      const totalReps = entry.minutes.reduce((sum, minute) => sum + (minute.reps ?? 0), 0);
      return { kind: 'intervals', intervals: entry.minutes.length, totalReps: totalReps || undefined };
    }
    case 'amrap':
      return { kind: 'rounds', rounds: entry.roundsCompleted, extraReps: entry.extraReps };
    case 'cardio':
      return { kind: 'cardio', durationSec: entry.durationSec, distanceMeters: entry.distanceMeters };
    case 'rest':
      return { kind: 'rest', restTakenSec: entry.restTakenSec };
  }
}

/**
 * A single comparable number per logged entry, for the volume chart — same discriminated switch shape
 * as sessionEntrySummary, just numeric instead of a display string. `rest` is unreachable here since
 * exerciseHistory already filters those out before this is called.
 */
function entryVolume(entry: SessionEntry): number {
  switch (entry.type) {
    case 'timed_hold':
      return entry.sets.reduce((sum, set) => sum + set.holdSec, 0);
    case 'reps': {
      const hasWeight = entry.sets.some((set) => set.weightKg !== undefined);
      return entry.sets.reduce((sum, set) => sum + (hasWeight ? set.reps * (set.weightKg ?? 0) : set.reps), 0);
    }
    case 'hiit':
      return entry.roundsCompleted;
    case 'emom':
      return entry.minutes.reduce((sum, minute) => sum + (minute.reps ?? 0), 0);
    case 'amrap':
      return entry.roundsCompleted;
    case 'cardio':
      return entry.distanceMeters ?? entry.durationSec ?? 0;
    case 'rest':
      return 0;
  }
}

export type ExerciseHistoryEntry = { sessionId: string; dateLabel: string; summary: string; volume: number };

/**
 * The last (up to `limit`) times a given exercise was logged, newest first — `sessions` is already
 * newest-first (session-files.ts's listSessions()/startSession()), so no re-sort needed. Skips
 * unfinished sessions and `rest`-type entries: a rest exercise's own repeat performance isn't
 * meaningful the way a lift's is, so a rest exercise naturally ends up with no history to show.
 */
export function exerciseHistory(sessions: Session[], exerciseId: string, limit = 5): ExerciseHistoryEntry[] {
  const results: ExerciseHistoryEntry[] = [];
  for (const session of sessions) {
    if (!session.endedAt) continue;
    for (const entry of session.entries) {
      if (entry.exercise !== exerciseId || entry.type === 'rest') continue;
      results.push({
        sessionId: session.id,
        dateLabel: formatMonthDay(new Date(session.startedAt)),
        summary: formatEntryResult(sessionEntryResult(entry)),
        volume: entryVolume(entry),
      });
      if (results.length >= limit) return results;
    }
  }
  return results;
}

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

export function historySessionsView(sessions: Session[], library: Library): HistorySessionView[] {
  return sessions.map((session) => {
    const loggedTypes = new Set(session.entries.filter((entry) => entry.type !== 'rest').map((entry) => entry.type));
    const entries: HistorySessionEntryView[] = session.entries
      .filter((entry) => entry.type !== 'rest')
      .map((entry) => ({
        exerciseName: exerciseName(library.exercises, entry.exercise),
        summary: formatEntryResult(sessionEntryResult(entry)),
      }));

    const startedAt = new Date(session.startedAt);
    return {
      id: session.id,
      day: startedAt.getDate(),
      month: formatMonthBadge(startedAt),
      workoutName: formatSessionName(workoutNameFor(session, library)),
      durationLabel: formatSessionDuration(sessionDurationMinutes(session)),
      setsLabel: formatSetCount(sessionSetCount(session)),
      mixed: loggedTypes.size > 1,
      editable: session.endedAt !== null,
      entries,
    };
  });
}
