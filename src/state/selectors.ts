import { resolveWorkoutForWeek } from '@/domain/program';
import type { Exercise, Library, Program, ProgramWeek, Session, SessionEntry, Workout, WorkoutBlock } from '@/domain/types';

function findExercise(exercises: Exercise[], id: string): Exercise | undefined {
  return exercises.find((exercise) => exercise.id === id);
}

function exerciseName(exercises: Exercise[], id: string): string {
  return findExercise(exercises, id)?.name ?? id;
}

export function blockChips(workout: Workout, exercises: Exercise[]): string[] {
  return workout.blocks.flatMap((block) =>
    block.kind === 'circuit'
      ? block.members.map((member) => exerciseName(exercises, member.exerciseId))
      : [exerciseName(exercises, block.exerciseId)],
  );
}

const TYPE_LABEL: Record<Exercise['type'], string> = {
  hiit: 'hiit',
  emom: 'emom',
  amrap: 'amrap',
  reps: 'reps',
  timed_hold: 'hold',
  cardio: 'cardio',
  rest: 'rest',
};

function estimateExerciseSeconds(exercise: Exercise, overrideDurationSec?: number): number {
  switch (exercise.type) {
    case 'hiit':
      return exercise.config.rounds * (exercise.config.workSec + exercise.config.restSec);
    case 'timed_hold':
      return exercise.config.sets * exercise.config.holdSecMin + (exercise.config.sets - 1) * exercise.config.restSec;
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
      return exercise.config.holdSecMin;
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

export function workoutSummary(workout: Workout, exercises: Exercise[]): string {
  const types = new Set<string>();
  let totalSec = 0;

  for (const block of workout.blocks) {
    for (const type of blockTypes(block, exercises)) {
      if (type !== 'rest') types.add(TYPE_LABEL[type]);
    }
    totalSec += estimateBlockSeconds(block, exercises);
  }

  const typeList = [...types];
  const typeLabel = typeList.length === 0 ? 'rest only' : typeList.length === 1 ? typeList[0] : `mixed ${typeList.join(' + ')}`;
  const minutes = Math.max(1, Math.round(totalSec / 60));

  return `${workout.blocks.length} blocks · ${typeLabel} · ~${minutes} min`;
}

export type NextUpView = {
  workout: Workout;
  exercises: Exercise[];
  weekLabel: string | null;
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
    weekLabel: null,
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
  const lastProgram = lastProgramSession && library.programs.find((candidate) => candidate.id === lastProgramSession.program);
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
function nextWeekAfter(program: Program, sessions: Session[]): ProgramWeek {
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
    weekLabel: `Week ${targetWeek.week}${targetWeek.day ? ` · ${targetWeek.day}` : ''}`,
    weekNotes: targetWeek.notes ?? null,
    sessionParams: { programId: program.id, week: String(targetWeek.week), day: targetWeek.day },
  };
}

function sessionSetCount(session: Session): number {
  return session.entries.reduce((count, entry) => {
    if (entry.type === 'reps' || entry.type === 'timed_hold') return count + entry.sets.length;
    if (entry.type === 'rest') return count;
    return count + 1;
  }, 0);
}

function sessionDurationMinutes(session: Session): number {
  if (!session.endedAt) return 0;
  const ms = new Date(session.endedAt).getTime() - new Date(session.startedAt).getTime();
  return Math.max(0, Math.round(ms / 60000));
}

function workoutNameFor(session: Session, library: Library): string {
  if (!session.workout) return 'Ad-hoc session';
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
    workoutName: workoutNameFor(session, library),
    dateLabel: new Date(session.startedAt).toLocaleDateString('en-US', { weekday: 'short' }),
    durationLabel: `${sessionDurationMinutes(session)} min`,
    setsLabel: `${sessionSetCount(session)} sets`,
  }));
}

export type HistoryStats = { sessions: number; hours: number; sets: number; minutes: number };

export function historyStats(sessions: Session[]): HistoryStats {
  const totalMinutes = sessions.reduce((sum, session) => sum + sessionDurationMinutes(session), 0);
  const totalSets = sessions.reduce((sum, session) => sum + sessionSetCount(session), 0);
  return { 
    sessions: sessions.length, 
    hours: Math.round((totalMinutes / 60) * 10) / 10, 
    minutes: totalMinutes % 60, 
    sets: totalSets };
}

function startOfWeek(date: Date): Date {
  const start = new Date(date);
  const day = start.getDay(); // 0 = Sunday .. 6 = Saturday
  const diffToMonday = day === 0 ? -6 : 1 - day;
  start.setDate(start.getDate() + diffToMonday);
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

  const oneDayMs = 86_400_000;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const yesterday = new Date(today.getTime() - oneDayMs);

  if (!activeDays.has(today.toDateString()) && !activeDays.has(yesterday.toDateString())) return 0;

  let streak = 0;
  let cursor = activeDays.has(today.toDateString()) ? today : yesterday;
  while (activeDays.has(cursor.toDateString())) {
    streak += 1;
    cursor = new Date(cursor.getTime() - oneDayMs);
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

export function sessionEntrySummary(entry: SessionEntry): string {
  switch (entry.type) {
    case 'timed_hold':
      return entry.sets.map((set) => `${set.holdSec}s`).join(' · ');
    case 'reps':
      return `${entry.sets.map((set) => set.reps).join(' · ')} reps`;
    case 'hiit':
      return `${entry.roundsCompleted} rounds`;
    case 'emom': {
      const totalReps = entry.minutes.reduce((sum, minute) => sum + (minute.reps ?? 0), 0);
      const repsNote = totalReps > 0 ? ` · ${totalReps} reps` : '';
      return `${entry.minutes.length} min${repsNote}`;
    }
    case 'amrap':
      return entry.extraReps ? `${entry.roundsCompleted} rounds + ${entry.extraReps} reps` : `${entry.roundsCompleted} rounds`;
    case 'cardio': {
      const parts: string[] = [];
      if (entry.durationSec !== undefined) parts.push(`${entry.durationSec}s`);
      if (entry.distanceMeters !== undefined) parts.push(`${entry.distanceMeters} m`);
      return parts.join(' · ');
    }
    case 'rest':
      return `${entry.restTakenSec}s`;
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
        dateLabel: new Date(session.startedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        summary: sessionEntrySummary(entry),
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
      .map((entry) => ({ exerciseName: exerciseName(library.exercises, entry.exercise), summary: sessionEntrySummary(entry) }));

    const startedAt = new Date(session.startedAt);
    return {
      id: session.id,
      day: startedAt.getDate(),
      month: startedAt.toLocaleDateString('en-US', { month: 'short' }).toUpperCase(),
      workoutName: workoutNameFor(session, library),
      durationLabel: `${sessionDurationMinutes(session)} min`,
      setsLabel: `${sessionSetCount(session)} sets`,
      mixed: loggedTypes.size > 1,
      entries,
    };
  });
}
