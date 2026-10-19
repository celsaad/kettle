import { hasLoggedWork, withoutZeroWork } from '@/domain/logged-work';
import type { Session } from '@/domain/types';

const cache = new WeakMap<Session[], Session[]>();

/**
 * The log as every training view should see it: zero sets gone, and a finished session left with no
 * work hidden outright — off History, off the calendar and streaks, and not moving a program on.
 *
 * Filtered on read rather than only at write, because sessions logged before the runner stopped
 * writing zeros are still on disk, and so are hand-edited ones. The files themselves keep what was
 * written: export and backup read the raw `sessions`, since those are the user's own files.
 *
 * Memoized on the array's identity so a zustand selector can return it directly — a fresh array per
 * call would re-render every subscriber on every store update. An in-flight session is never hidden,
 * since it starts out empty by definition; every view skips unfinished sessions anyway.
 */
export function loggedSessions(sessions: Session[]): Session[] {
  const cached = cache.get(sessions);
  if (cached) return cached;
  const result = sessions.flatMap((session) => {
    const cleaned = withoutZeroWork(session);
    return cleaned.endedAt && !hasLoggedWork(cleaned) ? [] : [cleaned];
  });
  cache.set(sessions, result);
  return result;
}
