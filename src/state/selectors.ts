import { resolveWorkoutForWeek } from '@/domain/program';
import { firstWeekdayIndex } from '@/i18n';
import { formatMonthBadge, formatMonthDay, formatWeekday } from '@/i18n/format';
import type { EntryResult, WorkoutShape } from '@/domain/format';
import { formatEntryResult, formatSessionDuration, formatSessionName, formatSetCount } from '@/domain/format';
import type { Exercise, Library, Program, ProgramWeek, Session, SessionEntry, Workout, WorkoutBlock } from '@/domain/types';

function findExercise(exercises: Exercise[], id: string): Exercise | undefined {
  return exercises.find((exercise) => exercise.id === id);
}

function exerciseName(exercises: Exercise[], id: string): string {
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

export type NextUpView = {
  workout: Workout;
  exercises: Exercise[];
  /** Structured, not a sentence: the view composes the label so it can be translated. */
  weekNumber: number | null;
  weekDay: string | null;
  weekNotes: string | null;
  sessionParams: { workoutId: string } | { programId: string; week: string; day?: string };
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
export function nextWeekAfter(program: Program, sessions: Session[]): ProgramWeek {
  // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
  // oxlint-disable-next-line unicorn/no-array-sort
  const sortedWeeks = [...program.weeks].sort((a, b) => a.week - b.week || (a.day ?? '').localeCompare(b.day ?? ''));
  const lastSession = sessions.find((session) => session.program === program.id && session.programWeek != null);
  const lastIndex = lastSession
    ? sortedWeeks.findIndex((week) => week.week === lastSession.programWeek && (week.day ?? null) === lastSession.programDay)
    : -1;
  return sortedWeeks[lastIndex === -1 ? 0 : (lastIndex + 1) % sortedWeeks.length];
}

/**
 * Picks the workout for the home screen's "Next up" card: the next week of the active program (see
 * nextWeekAfter). Falls back to the first library workout when there's no program to follow.
 */
export function nextUpView(library: Library, sessions: Session[]): NextUpView | null {
  const program = activeProgram(library, sessions);
  if (!program || program.weeks.length === 0) return flatFallback(library);

  const targetWeek = nextWeekAfter(program, sessions);

  const resolved = resolveWorkoutForWeek(program, targetWeek.week, library, targetWeek.day);
  if (!resolved) return flatFallback(library);

  return {
    workout: resolved.workout,
    exercises: resolved.exercises,
    weekNumber: targetWeek.week,
    weekDay: targetWeek.day ?? null,
    weekNotes: targetWeek.notes ?? null,
    sessionParams: { programId: program.id, week: String(targetWeek.week), day: targetWeek.day },
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
      entries,
    };
  });
}
