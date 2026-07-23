import type { Exercise, Library, Session, Workout } from '@/domain/types';
import { findExerciseInLibrary } from '@/state/library-store';

function exerciseName(library: Library, id: string): string {
  return findExerciseInLibrary(library, id)?.name ?? id;
}

export function blockChips(workout: Workout, library: Library): string[] {
  return workout.blocks.map((block) => exerciseName(library, block.exerciseId));
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

function estimateBlockSeconds(exercise: Exercise, overrideDurationSec?: number): number {
  switch (exercise.type) {
    case 'hiit':
      return exercise.config.rounds * (exercise.config.workSec + exercise.config.restSec);
    case 'timed_hold':
      return exercise.config.sets * exercise.config.holdSec + (exercise.config.sets - 1) * exercise.config.restSec;
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

export function workoutSummary(workout: Workout, library: Library): string {
  const types = new Set<string>();
  let totalSec = 0;

  for (const block of workout.blocks) {
    const exercise = findExerciseInLibrary(library, block.exerciseId);
    if (!exercise) continue;
    if (exercise.type !== 'rest') types.add(TYPE_LABEL[exercise.type]);
    totalSec += estimateBlockSeconds(exercise, block.configOverride?.durationSec);
  }

  const typeList = [...types];
  const typeLabel = typeList.length === 0 ? 'rest only' : typeList.length === 1 ? typeList[0] : `mixed ${typeList.join(' + ')}`;
  const minutes = Math.max(1, Math.round(totalSec / 60));

  return `${workout.blocks.length} blocks · ${typeLabel} · ~${minutes} min`;
}

function sessionSetCount(session: Session): number {
  return session.entries.reduce((count, entry) => (entry.type === 'rest' ? count : count + entry.sets.length), 0);
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

export type HistoryStats = { sessions: number; hours: number; sets: number };

export function historyStats(sessions: Session[]): HistoryStats {
  const totalMinutes = sessions.reduce((sum, session) => sum + sessionDurationMinutes(session), 0);
  const totalSets = sessions.reduce((sum, session) => sum + sessionSetCount(session), 0);
  return { sessions: sessions.length, hours: Math.round((totalMinutes / 60) * 10) / 10, sets: totalSets };
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

export function historySessionsView(sessions: Session[], library: Library): HistorySessionView[] {
  return sessions.map((session) => {
    const loggedTypes = new Set(session.entries.filter((entry) => entry.type !== 'rest').map((entry) => entry.type));
    const entries: HistorySessionEntryView[] = session.entries
      .filter((entry) => entry.type !== 'rest')
      .map((entry) => {
        const name = exerciseName(library, entry.exercise);
        if (entry.type === 'timed_hold') {
          return { exerciseName: name, summary: entry.sets.map((set) => `${set.holdSec}s`).join(' · ') };
        }
        return { exerciseName: name, summary: `${entry.sets.map((set) => set.reps).join(' · ')} reps` };
      });

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
