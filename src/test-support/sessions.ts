import type { Session } from '@/domain/types';

/**
 * Fixture builder for the session log, alongside `library.ts`'s builders for the library side.
 *
 * `startedAt` is required rather than defaulted because every selector that reads the log keys off it —
 * a session with a stand-in date would make a streak or week-boundary test pass for the wrong reason.
 * `endedAt` defaults to `startedAt` so the session counts as *finished*: the record and history
 * selectors all skip unfinished sessions, and a fixture that silently didn't count would read as a bug
 * in the selector. Pass `endedAt: null` to get the unfinished case on purpose.
 */
export function aSession(overrides: Partial<Session> & { startedAt: string }): Session {
  return {
    version: 1,
    id: `sess-${overrides.startedAt}`,
    workout: 'w',
    program: null,
    programWeek: null,
    programDay: null,
    endedAt: overrides.startedAt,
    entries: [],
    ...overrides,
  };
}
