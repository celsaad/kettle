import type { Exercise, Library, Session, SessionEntry, Workout, WorkoutBlock } from '@/domain/types';
import { findExerciseInLibrary } from '@/state/library-store';

function exerciseName(library: Library, id: string): string {
  return findExerciseInLibrary(library, id)?.name ?? id;
}

export function blockChips(workout: Workout, library: Library): string[] {
  return workout.blocks.flatMap((block) =>
    block.kind === 'circuit'
      ? block.members.map((member) => exerciseName(library, member.exerciseId))
      : [exerciseName(library, block.exerciseId)],
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

function estimateBlockSeconds(block: WorkoutBlock, library: Library): number {
  if (block.kind === 'exercise') {
    const exercise = findExerciseInLibrary(library, block.exerciseId);
    if (!exercise) return 0;
    return estimateExerciseSeconds(exercise, block.configOverride?.durationSec);
  }

  const members = block.members
    .map((member) => findExerciseInLibrary(library, member.exerciseId))
    .filter((exercise): exercise is Exercise => !!exercise);
  const restBetweenExercises = block.restBetweenExercisesSec ?? 0;
  const restBetweenRounds = block.restBetweenRoundsSec ?? 0;

  const roundSeconds =
    members.reduce((sum, exercise) => sum + memberVisitSeconds(exercise), 0) +
    Math.max(0, members.length - 1) * restBetweenExercises;

  return block.rounds * roundSeconds + Math.max(0, block.rounds - 1) * restBetweenRounds;
}

function blockTypes(block: WorkoutBlock, library: Library): Exercise['type'][] {
  if (block.kind === 'exercise') {
    const exercise = findExerciseInLibrary(library, block.exerciseId);
    return exercise ? [exercise.type] : [];
  }
  return block.members
    .map((member) => findExerciseInLibrary(library, member.exerciseId)?.type)
    .filter((type): type is Exercise['type'] => !!type);
}

export function workoutSummary(workout: Workout, library: Library): string {
  const types = new Set<string>();
  let totalSec = 0;

  for (const block of workout.blocks) {
    for (const type of blockTypes(block, library)) {
      if (type !== 'rest') types.add(TYPE_LABEL[type]);
    }
    totalSec += estimateBlockSeconds(block, library);
  }

  const typeList = [...types];
  const typeLabel = typeList.length === 0 ? 'rest only' : typeList.length === 1 ? typeList[0] : `mixed ${typeList.join(' + ')}`;
  const minutes = Math.max(1, Math.round(totalSec / 60));

  return `${workout.blocks.length} blocks · ${typeLabel} · ~${minutes} min`;
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

function sessionEntrySummary(entry: SessionEntry): string {
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

export function historySessionsView(sessions: Session[], library: Library): HistorySessionView[] {
  return sessions.map((session) => {
    const loggedTypes = new Set(session.entries.filter((entry) => entry.type !== 'rest').map((entry) => entry.type));
    const entries: HistorySessionEntryView[] = session.entries
      .filter((entry) => entry.type !== 'rest')
      .map((entry) => ({ exerciseName: exerciseName(library, entry.exercise), summary: sessionEntrySummary(entry) }));

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
