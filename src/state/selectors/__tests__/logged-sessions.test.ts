import type { SessionEntry } from '@/domain/types';
import { loggedSessions } from '@/state/selectors/logged-sessions';
import { aSession } from '@/test-support/sessions';

const skipped: SessionEntry = { exercise: 'pullups', type: 'reps', sets: [{ reps: 0, restTakenSec: 0 }] };
const done: SessionEntry = { exercise: 'dips', type: 'reps', sets: [{ reps: 8, restTakenSec: 0 }] };

describe('loggedSessions', () => {
  it('hides a finished session with no work left in it', () => {
    const allSkipped = aSession({ startedAt: '2026-07-24T09:00:00.000Z', entries: [skipped] });
    const kept = aSession({ startedAt: '2026-07-25T09:00:00.000Z', entries: [skipped, done] });

    const logged = loggedSessions([kept, allSkipped]);

    expect(logged.map((session) => session.id)).toEqual([kept.id]);
    expect(logged[0].entries).toEqual([done]);
  });

  // An in-flight session starts with no entries at all; hiding it would hide the session being run.
  it('keeps an unfinished session even while it is empty', () => {
    const running = aSession({ startedAt: '2026-07-24T09:00:00.000Z', endedAt: null });
    expect(loggedSessions([running])).toEqual([running]);
  });

  // Zustand selectors return this directly, and a fresh array per call re-renders every subscriber.
  it('returns the same array for the same input', () => {
    const sessions = [aSession({ startedAt: '2026-07-24T09:00:00.000Z', entries: [done] })];
    expect(loggedSessions(sessions)).toBe(loggedSessions(sessions));
  });
});
