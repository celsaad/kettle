import {
  currentStreak,
  exerciseHistory,
  historyStats,
  nextWeekAfter,
  sessionEntryResult,
  thisWeekStats,
} from '@/state/selectors';
import type { EntryResult } from '@/domain/format';
import type { Program, Session, SessionEntry } from '@/domain/types';

function makeSession(overrides: Partial<Session> & { startedAt: string }): Session {
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

afterEach(() => {
  jest.useRealTimers();
});

describe('historyStats', () => {
  // Regression: hours/minutes must be the two whole-number halves of one "1h 30m" reading. This used
  // to compute hours as a fractional round(total/60*10)/10, so 90 minutes rendered "1.5h 30m" — the
  // half hour double-counted against minutes. Only showed up above the hour mark.
  it('splits a 90-minute total into whole hours and a remainder, not a fractional hour', () => {
    const session = makeSession({ startedAt: '2026-07-24T09:00:00.000Z', endedAt: '2026-07-24T10:30:00.000Z' });
    expect(historyStats([session])).toEqual({ sessions: 1, hours: 1, minutes: 30, sets: 0 });
  });

  it('reports zeroes for no sessions', () => {
    expect(historyStats([])).toEqual({ sessions: 0, hours: 0, minutes: 0, sets: 0 });
  });

  it('reports 0 hours for a sub-hour total', () => {
    const session = makeSession({ startedAt: '2026-07-24T09:00:00.000Z', endedAt: '2026-07-24T09:25:00.000Z' });
    expect(historyStats([session])).toEqual({ sessions: 1, hours: 0, minutes: 25, sets: 0 });
  });
});

describe('currentStreak', () => {
  it('counts a session logged today only', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    const session = makeSession({ startedAt: new Date(2026, 6, 24, 9, 0, 0).toISOString() });
    expect(currentStreak([session])).toBe(1);
  });

  it('does not break the streak when only yesterday has a session and today has none yet', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 8, 0, 0));
    const session = makeSession({ startedAt: new Date(2026, 6, 23, 9, 0, 0).toISOString() });
    expect(currentStreak([session])).toBe(1);
  });

  it('counts a consecutive multi-day run', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    const sessions = [
      makeSession({ startedAt: new Date(2026, 6, 24, 9, 0, 0).toISOString() }),
      makeSession({ startedAt: new Date(2026, 6, 23, 9, 0, 0).toISOString() }),
      makeSession({ startedAt: new Date(2026, 6, 22, 9, 0, 0).toISOString() }),
    ];
    expect(currentStreak(sessions)).toBe(3);
  });

  it('stops at a gap rather than counting an older isolated day', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    const sessions = [
      makeSession({ startedAt: new Date(2026, 6, 24, 9, 0, 0).toISOString() }),
      // Yesterday (07-23) has no session: the gap should stop the streak before reaching 07-22.
      makeSession({ startedAt: new Date(2026, 6, 22, 9, 0, 0).toISOString() }),
    ];
    expect(currentStreak(sessions)).toBe(1);
  });

  it('returns 0 for no sessions at all', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    expect(currentStreak([])).toBe(0);
  });
});

describe('thisWeekStats', () => {
  // startOfWeek treats Monday as the first day of the week, so the boundary sits between Sunday night
  // and Monday morning rather than the Date.getDay() default of Sunday.
  it('includes sessions from Monday through today when today is a Sunday', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 26, 15, 0, 0)); // Sunday 2026-07-26, week started Mon 2026-07-20
    const sessions = [
      makeSession({ startedAt: new Date(2026, 6, 20, 8, 0, 0).toISOString() }), // this week's Monday
      makeSession({ startedAt: new Date(2026, 6, 26, 10, 0, 0).toISOString() }), // this week's Sunday
      makeSession({ startedAt: new Date(2026, 6, 19, 20, 0, 0).toISOString() }), // previous week's Sunday
    ];
    expect(thisWeekStats(sessions).sessions).toBe(2);
  });

  it('excludes last week entirely once the week has rolled over to Monday', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 27, 9, 0, 0)); // Monday 2026-07-27, a new week
    const sessions = [
      makeSession({ startedAt: new Date(2026, 6, 26, 23, 0, 0).toISOString() }), // last week's Sunday
      makeSession({ startedAt: new Date(2026, 6, 27, 0, 5, 0).toISOString() }), // this week's Monday
    ];
    expect(thisWeekStats(sessions).sessions).toBe(1);
  });
});

describe('nextWeekAfter', () => {
  const program: Program = {
    id: 'prog',
    name: 'Prog',
    weeks: [
      { week: 1, workoutId: 'full' },
      { week: 2, workoutId: 'full' },
      { week: 3, workoutId: 'full' },
    ],
  };

  it('starts at the first week when there is no prior session', () => {
    expect(nextWeekAfter(program, []).week).toBe(1);
  });

  it('picks the next week after the most recent tracked session', () => {
    const sessions = [makeSession({ startedAt: 't', program: 'prog', programWeek: 1, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(2);
  });

  it('wraps back to the first week after the last one', () => {
    const sessions = [makeSession({ startedAt: 't', program: 'prog', programWeek: 3, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(1);
  });

  it('falls back to the first week when the referenced week no longer exists in the program', () => {
    const sessions = [makeSession({ startedAt: 't', program: 'prog', programWeek: 99, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(1);
  });

  it('ignores a session belonging to a different program', () => {
    const sessions = [makeSession({ startedAt: 't', program: 'other-prog', programWeek: 2, programDay: null })];
    expect(nextWeekAfter(program, sessions).week).toBe(1);
  });
});

// Asserts the descriptor, not the sentence: rendering is format.test.ts's job, and assertions on
// prose are exactly what i18n would invalidate. hiit and amrap both collapse onto `rounds` — the
// renderer never needs to know which produced it.
describe('sessionEntryResult', () => {
  it('describes every entry type structurally', () => {
    const cases: [SessionEntry, EntryResult][] = [
      [
        { exercise: 'e', type: 'timed_hold', sets: [{ holdSec: 20, restTakenSec: 60 }, { holdSec: 15, restTakenSec: 0 }] },
        { kind: 'holds', holdSecs: [20, 15] },
      ],
      [
        { exercise: 'e', type: 'reps', sets: [{ reps: 10, restTakenSec: 60 }, { reps: 8, restTakenSec: 0 }] },
        { kind: 'reps', reps: [10, 8] },
      ],
      [{ exercise: 'e', type: 'hiit', roundsCompleted: 4 }, { kind: 'rounds', rounds: 4 }],
      [
        { exercise: 'e', type: 'emom', minutes: [{ reps: 3 }, { reps: 2 }] },
        { kind: 'intervals', intervals: 2, totalReps: 5 },
      ],
      // No reps logged at all reports none, rather than a misleading zero.
      [{ exercise: 'e', type: 'emom', minutes: [{}, {}] }, { kind: 'intervals', intervals: 2, totalReps: undefined }],
      [
        { exercise: 'e', type: 'amrap', roundsCompleted: 7, extraReps: 4 },
        { kind: 'rounds', rounds: 7, extraReps: 4 },
      ],
      [{ exercise: 'e', type: 'amrap', roundsCompleted: 7 }, { kind: 'rounds', rounds: 7, extraReps: undefined }],
      [
        { exercise: 'e', type: 'cardio', durationSec: 480, distanceMeters: 2000 },
        { kind: 'cardio', durationSec: 480, distanceMeters: 2000 },
      ],
      [{ exercise: 'e', type: 'rest', restTakenSec: 90 }, { kind: 'rest', restTakenSec: 90 }],
    ];
    for (const [entry, expected] of cases) expect(sessionEntryResult(entry)).toEqual(expected);
  });
});

describe('exerciseHistory volume', () => {
  it('weights reps volume by load only when a set actually carries weight', () => {
    const withWeight = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 60, restTakenSec: 90 }, { reps: 5, weightKg: 60, restTakenSec: 90 }] }],
    });
    const bodyweight = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'pushups', type: 'reps', sets: [{ reps: 10, restTakenSec: 45 }, { reps: 8, restTakenSec: 0 }] }],
    });
    expect(exerciseHistory([withWeight], 'bench')[0].volume).toBe(600);
    expect(exerciseHistory([bodyweight], 'pushups')[0].volume).toBe(18);
  });

  it('uses distance over duration for cardio, falling back to duration when distance is absent', () => {
    const withDistance = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'row', type: 'cardio', durationSec: 480, distanceMeters: 2000 }],
    });
    const durationOnly = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'row', type: 'cardio', durationSec: 480 }],
    });
    expect(exerciseHistory([withDistance], 'row')[0].volume).toBe(2000);
    expect(exerciseHistory([durationOnly], 'row')[0].volume).toBe(480);
  });
});
