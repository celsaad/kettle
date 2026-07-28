// Mocked so the week boundary is driven by the test rather than the machine's locale; the real one
// reads the device calendar via expo-localization.
const mockFirstWeekdayIndex = jest.fn(() => 1);
jest.mock('@/i18n', () => ({
  firstWeekdayIndex: () => mockFirstWeekdayIndex(),
  currentLocale: () => 'en',
}));

import i18next from 'i18next';

import {
  currentStreak,
  exerciseHistory,
  historySessionsView,
  historyStats,
  nextWeekAfter,
  recentSessionsView,
  sessionEntryResult,
  thisWeekStats,
} from '@/state/selectors';
import type { EntryResult } from '@/domain/format';
import type { Library, Program, Session, SessionEntry } from '@/domain/types';

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

/**
 * `sets` is the same private count behind History's and Today's tiles and both "N sets" labels, so
 * it's asserted through `historyStats` rather than exported for the test.
 *
 * Regression: interval entries have no `sets` array and used to count as a flat 1 apiece, which made
 * a 16-round HIIT worth the same as one hold. Every tile reading this under-reported for anyone
 * training mostly in intervals.
 */
describe('historyStats sets', () => {
  const setsIn = (entries: SessionEntry[]) =>
    historyStats([makeSession({ startedAt: '2026-07-24T09:00:00.000Z', entries })]).sets;

  it('counts one set per logged set for reps and holds', () => {
    expect(
      setsIn([
        {
          exercise: 'pullups',
          type: 'reps',
          sets: [
            { reps: 8, restTakenSec: 90 },
            { reps: 6, restTakenSec: 0 },
          ],
        },
        { exercise: 'lsit', type: 'timed_hold', sets: [{ holdSec: 20, restTakenSec: 0 }] },
      ]),
    ).toBe(3);
  });

  it('counts each completed round of a HIIT or AMRAP entry', () => {
    expect(setsIn([{ exercise: 'burpees', type: 'hiit', roundsCompleted: 8 }])).toBe(8);
    expect(setsIn([{ exercise: 'chipper', type: 'amrap', roundsCompleted: 5, extraReps: 3 }])).toBe(5);
  });

  it('counts each interval of an EMOM entry', () => {
    expect(setsIn([{ exercise: 'swings', type: 'emom', minutes: [{ reps: 10 }, { reps: 10 }, {}] }])).toBe(3);
  });

  it('counts cardio as one effort and rest as none', () => {
    expect(setsIn([{ exercise: 'row', type: 'cardio', durationSec: 600, distanceMeters: 2000 }])).toBe(1);
    expect(setsIn([{ exercise: 'rest', type: 'rest', restTakenSec: 90 }])).toBe(0);
  });

  it('sums a mixed session across all of them', () => {
    expect(
      setsIn([
        {
          exercise: 'pullups',
          type: 'reps',
          sets: [
            { reps: 8, restTakenSec: 90 },
            { reps: 6, restTakenSec: 0 },
          ],
        },
        { exercise: 'rest', type: 'rest', restTakenSec: 60 },
        { exercise: 'burpees', type: 'hiit', roundsCompleted: 8 },
        { exercise: 'swings', type: 'emom', minutes: [{ reps: 10 }, { reps: 10 }] },
      ]),
    ).toBe(12);
  });

  // Nothing performed is nothing counted — an abandoned interval block shouldn't inflate the tile
  // the way a flat 1 per entry did.
  it('counts a round-less interval entry as no sets', () => {
    expect(setsIn([{ exercise: 'burpees', type: 'hiit', roundsCompleted: 0 }])).toBe(0);
  });
});

/**
 * The two session-list views feed History and Today, and both used to assemble their labels here as
 * English template literals — untranslated on both screens, and pluralised by concatenation, so a
 * one-set session read "1 sets". They now go through `domain/format.ts`, which is what makes the `pt`
 * case below possible at all: against `en` a hardcoded literal and a `t()` call render identically.
 */
describe('session list labels', () => {
  const library: Library = {
    version: 1,
    exercises: [{ id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } }],
    workouts: [{ id: 'push-day', name: 'Push day', blocks: [] }],
    programs: [],
  };

  const oneSet = makeSession({
    startedAt: '2026-07-24T09:00:00.000Z',
    endedAt: '2026-07-24T09:12:00.000Z',
    workout: 'push-day',
    entries: [{ exercise: 'lsit', type: 'timed_hold', sets: [{ holdSec: 20, restTakenSec: 0 }] }],
  });

  it('pluralises the set count instead of concatenating it', () => {
    const [single] = historySessionsView([oneSet], library);
    expect(single.setsLabel).toBe('1 set');
    expect(single.durationLabel).toBe('12 min');

    const twoSets = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      endedAt: '2026-07-24T09:12:00.000Z',
      entries: [
        {
          exercise: 'lsit',
          type: 'timed_hold',
          sets: [
            { holdSec: 20, restTakenSec: 0 },
            { holdSec: 18, restTakenSec: 0 },
          ],
        },
      ],
    });
    expect(historySessionsView([twoSets], library)[0].setsLabel).toBe('2 sets');
    expect(recentSessionsView([oneSet], library)[0].setsLabel).toBe('1 set');
  });

  it('names a session after its workout, falling back to a label when it was started ad-hoc', () => {
    expect(historySessionsView([oneSet], library)[0].workoutName).toBe('Push day');
    const adHoc = makeSession({ startedAt: '2026-07-24T09:00:00.000Z', workout: null });
    expect(historySessionsView([adHoc], library)[0].workoutName).toBe('Ad-hoc session');
    expect(recentSessionsView([adHoc], library)[0].workoutName).toBe('Ad-hoc session');
  });

  // The workout is user data from their own YAML, so it stays as written whatever the locale is.
  it('translates every label it owns, and none of the user data', async () => {
    await i18next.changeLanguage('pt');
    const [session] = historySessionsView([oneSet], library);
    expect(session.setsLabel).toBe('1 série');
    expect(session.durationLabel).toBe('12 min');
    expect(session.workoutName).toBe('Push day');

    const adHoc = makeSession({ startedAt: '2026-07-24T09:00:00.000Z', workout: null });
    expect(recentSessionsView([adHoc], library)[0].workoutName).toBe('Sessão avulsa');
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

// The week's first day comes from the device calendar rather than a hardcoded Monday, so these drive
// it explicitly instead of depending on whatever locale the test machine reports. Both conventions are
// covered because the whole point of the change is that either can be correct: Brazil and the US start
// on Sunday, most of Europe on Monday, and the old hardcoding silently measured a different seven days
// than the calendar the user reads.
describe('thisWeekStats', () => {
  const setFirstWeekday = (day: number) => mockFirstWeekdayIndex.mockReturnValue(day);

  it('starts the week on Monday when the calendar says so', () => {
    setFirstWeekday(1);
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 26, 15, 0, 0)); // Sunday 2026-07-26; week began Mon 2026-07-20
    const sessions = [
      makeSession({ startedAt: new Date(2026, 6, 20, 8, 0, 0).toISOString() }), // Mon, in
      makeSession({ startedAt: new Date(2026, 6, 26, 10, 0, 0).toISOString() }), // Sun, in
      makeSession({ startedAt: new Date(2026, 6, 19, 20, 0, 0).toISOString() }), // previous Sun, out
    ];
    expect(thisWeekStats(sessions).sessions).toBe(2);
  });

  it('starts the week on Sunday when the calendar says so', () => {
    setFirstWeekday(0);
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 26, 15, 0, 0)); // Sunday 2026-07-26 is now the week's *first* day
    const sessions = [
      makeSession({ startedAt: new Date(2026, 6, 26, 10, 0, 0).toISOString() }), // today, in
      makeSession({ startedAt: new Date(2026, 6, 20, 8, 0, 0).toISOString() }), // last Mon, now out
      makeSession({ startedAt: new Date(2026, 6, 19, 20, 0, 0).toISOString() }), // previous Sun, out
    ];
    expect(thisWeekStats(sessions).sessions).toBe(1);
  });

  it('excludes the prior week once the boundary day arrives', () => {
    setFirstWeekday(1);
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
        {
          exercise: 'e',
          type: 'timed_hold',
          sets: [
            { holdSec: 20, restTakenSec: 60 },
            { holdSec: 15, restTakenSec: 0 },
          ],
        },
        { kind: 'holds', holdSecs: [20, 15] },
      ],
      [
        {
          exercise: 'e',
          type: 'reps',
          sets: [
            { reps: 10, restTakenSec: 60 },
            { reps: 8, restTakenSec: 0 },
          ],
        },
        { kind: 'reps', reps: [10, 8] },
      ],
      [
        { exercise: 'e', type: 'hiit', roundsCompleted: 4 },
        { kind: 'rounds', rounds: 4 },
      ],
      [
        { exercise: 'e', type: 'emom', minutes: [{ reps: 3 }, { reps: 2 }] },
        { kind: 'intervals', intervals: 2, totalReps: 5 },
      ],
      // No reps logged at all reports none, rather than a misleading zero.
      [
        { exercise: 'e', type: 'emom', minutes: [{}, {}] },
        { kind: 'intervals', intervals: 2, totalReps: undefined },
      ],
      [
        { exercise: 'e', type: 'amrap', roundsCompleted: 7, extraReps: 4 },
        { kind: 'rounds', rounds: 7, extraReps: 4 },
      ],
      [
        { exercise: 'e', type: 'amrap', roundsCompleted: 7 },
        { kind: 'rounds', rounds: 7, extraReps: undefined },
      ],
      [
        { exercise: 'e', type: 'cardio', durationSec: 480, distanceMeters: 2000 },
        { kind: 'cardio', durationSec: 480, distanceMeters: 2000 },
      ],
      [
        { exercise: 'e', type: 'rest', restTakenSec: 90 },
        { kind: 'rest', restTakenSec: 90 },
      ],
    ];
    for (const [entry, expected] of cases) expect(sessionEntryResult(entry)).toEqual(expected);
  });
});

describe('exerciseHistory volume', () => {
  it('weights reps volume by load only when a set actually carries weight', () => {
    const withWeight = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'bench',
          type: 'reps',
          sets: [
            { reps: 5, weightKg: 60, restTakenSec: 90 },
            { reps: 5, weightKg: 60, restTakenSec: 90 },
          ],
        },
      ],
    });
    const bodyweight = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'pushups',
          type: 'reps',
          sets: [
            { reps: 10, restTakenSec: 45 },
            { reps: 8, restTakenSec: 0 },
          ],
        },
      ],
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
