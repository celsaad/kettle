import { formatEntryResult } from '@/domain/format';
import type { Session } from '@/domain/types';
import { formatMonthDay } from '@/i18n/format';
import { entryVolume, sessionEntryResult } from '@/state/selectors/session-summary';

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
