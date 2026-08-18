import { formatEntryResult, formatSessionDuration, formatSessionName, formatSetCount } from '@/domain/format';
import type { Library, Session } from '@/domain/types';
import { formatMonthBadge, formatMonthDay } from '@/i18n/format';
import { exerciseName } from '@/state/selectors/exercise-lookup';
import {
  entryVolume,
  sessionDurationMinutes,
  sessionEntryResult,
  sessionSetCount,
  workoutNameFor,
} from '@/state/selectors/session-summary';

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
