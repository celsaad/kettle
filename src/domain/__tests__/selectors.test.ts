// Mocked so the week boundary is driven by the test rather than the machine's locale; the real one
// reads the device calendar via expo-localization.
const mockFirstWeekdayIndex = jest.fn(() => 1);
jest.mock('@/i18n', () => ({
  firstWeekdayIndex: () => mockFirstWeekdayIndex(),
  currentLocale: () => 'en',
}));

import { changeLanguage } from 'i18next';

import {
  currentStreak,
  exerciseHistory,
  historySessionsView,
  historyStats,
  nextWeekAfter,
  personalBestFor,
  previousSetFor,
  recentSessionsView,
  sessionEntryResult,
  sessionRecords,
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
    await changeLanguage('pt');
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

describe('sessionRecords', () => {
  const exercises: Library['exercises'] = [
    { id: 'bench', name: 'Bench Press', type: 'reps', config: { sets: 3, targetRepsMin: 5, restSec: 120 } },
    { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 8, restSec: 90 } },
    { id: 'plank', name: 'Plank', type: 'timed_hold', config: { sets: 3, holdSecMin: 45, restSec: 60 } },
    { id: 'burpees', name: 'Burpees', type: 'amrap', config: { timeCapSec: 300 } },
  ];

  const benchAt = (startedAt: string, weightKg: number, reps = 5): Session =>
    makeSession({
      startedAt,
      id: `sess-${startedAt}-${weightKg}`,
      entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps, weightKg, restTakenSec: 120 }] }],
    });

  it('reports nothing when there is no log to beat', () => {
    expect(sessionRecords(benchAt('2026-07-24T09:00:00.000Z', 100), [], exercises)).toEqual([]);
  });

  /**
   * A record has to beat something. Without this every exercise in a new user's first week reads as a
   * PR, and the badge means nothing by session three.
   */
  it('does not call a first-ever entry a record', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 100);
    const otherExercise = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 12, restTakenSec: 90 }] }],
    });

    expect(sessionRecords(today, [otherExercise], exercises)).toEqual([]);
  });

  // Regression: a `>=` comparison here celebrates repeating last week's top set as progress.
  it('does not call a tie a record', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 100);
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [before], exercises)).toEqual([]);
  });

  it('reports the heaviest set once it beats the log', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 105);
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    const records = sessionRecords(today, [before], exercises);
    expect(records).toHaveLength(1);
    expect(records[0]).toMatchObject({
      kind: 'heaviestSet',
      exerciseId: 'bench',
      exerciseName: 'Bench Press',
      weightKg: 105,
      reps: 5,
    });
  });

  /**
   * Bodyweight sets carry no `weightKg` at all (`commitCurrentStep` writes `|| undefined`), so they
   * compete on reps. Reporting a `heaviestSet` of 0 kg here would be the wrong record and a wrong
   * number.
   */
  it('judges a bodyweight exercise on reps, not on a zero load', () => {
    const today = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'pullups',
          type: 'reps',
          sets: [
            { reps: 14, restTakenSec: 90 },
            { reps: 9, restTakenSec: 90 },
          ],
        },
      ],
    });
    const before = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-before',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 12, restTakenSec: 90 }] }],
    });

    expect(sessionRecords(today, [before], exercises)).toEqual([
      { kind: 'mostReps', exerciseId: 'pullups', exerciseName: 'Pull-ups', reps: 14 },
    ]);
  });

  it('reports the longest hold and the most amrap rounds', () => {
    const today = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        { exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 75, restTakenSec: 60 }] },
        { exercise: 'burpees', type: 'amrap', roundsCompleted: 9 },
      ],
    });
    const before = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-before',
      entries: [
        { exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 60, restTakenSec: 60 }] },
        { exercise: 'burpees', type: 'amrap', roundsCompleted: 8 },
      ],
    });

    expect(sessionRecords(today, [before], exercises).map((record) => record.kind)).toEqual(['longestHold', 'mostRounds']);
  });

  /**
   * `hiit` rounds and `emom` minutes are bounded by the exercise's own config, so "more than last
   * time" reports a workout edit rather than an achievement. Cardio is out until distance and
   * duration have comparability rules of their own.
   */
  it('has no record for hiit, emom or cardio', () => {
    const today = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        { exercise: 'sprints', type: 'hiit', roundsCompleted: 12 },
        { exercise: 'kb', type: 'emom', minutes: [{ reps: 10 }, { reps: 10 }] },
        { exercise: 'row', type: 'cardio', durationSec: 900, distanceMeters: 4000 },
      ],
    });
    const before = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-before',
      entries: [
        { exercise: 'sprints', type: 'hiit', roundsCompleted: 8 },
        { exercise: 'kb', type: 'emom', minutes: [{ reps: 5 }] },
        { exercise: 'row', type: 'cardio', durationSec: 600, distanceMeters: 2000 },
      ],
    });

    expect(sessionRecords(today, [before], exercises)).toEqual([]);
  });

  /**
   * An abandoned session is not part of the log the rest of the app reports on (`exerciseHistory`
   * skips it too), so a heavy set inside one must not be able to suppress a real record.
   */
  it('ignores unfinished sessions when deciding what was beaten', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 105);
    const abandoned = makeSession({
      startedAt: '2026-07-20T09:00:00.000Z',
      id: 'sess-abandoned',
      endedAt: null,
      entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 200, restTakenSec: 120 }] }],
    });
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [abandoned, before], exercises)).toHaveLength(1);
  });

  // The caller reads both the finished session and the log from the same store, so the session is in
  // its own `priorSessions`. Left in, every record would be compared against itself and never beat it.
  it('excludes the session being judged from its own comparison', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 105);
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [today, before], exercises)).toHaveLength(1);
  });

  // Two blocks of the same exercise are two entries under two member keys. One PR, not two.
  it('reports one record per exercise even when it was logged in two blocks', () => {
    const today = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        { exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 105, restTakenSec: 120 }] },
        { exercise: 'bench', type: 'reps', sets: [{ reps: 3, weightKg: 110, restTakenSec: 120 }] },
      ],
    });
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [before], exercises)).toEqual([
      expect.objectContaining({ kind: 'heaviestSet', weightKg: 110, reps: 3 }),
    ]);
  });

  // Epley across the whole entry: 90 x 8 projects higher than the heavier 100 x 1 single beside it.
  it('estimates the 1RM from the best set, not necessarily the heaviest', () => {
    const today = makeSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'bench',
          type: 'reps',
          sets: [
            { reps: 1, weightKg: 100, restTakenSec: 120 },
            { reps: 8, weightKg: 90, restTakenSec: 120 },
          ],
        },
      ],
    });
    const before = benchAt('2026-07-17T09:00:00.000Z', 95);

    const [record] = sessionRecords(today, [before], exercises);
    expect(record).toMatchObject({ kind: 'heaviestSet', weightKg: 100, reps: 1 });
    if (record.kind !== 'heaviestSet') throw new Error('expected a heaviestSet record');
    expect(record.oneRepMaxKg).toBeCloseTo(114, 5);
  });
});

describe('previousSetFor', () => {
  const benchSession = (startedAt: string, sets: { reps: number; weightKg?: number }[]): Session =>
    makeSession({
      startedAt,
      id: `sess-${startedAt}`,
      entries: [{ exercise: 'bench', type: 'reps', sets: sets.map((set) => ({ ...set, restTakenSec: 120 })) }],
    });

  it('has nothing to show on an empty log', () => {
    expect(previousSetFor([], 'bench', 1)).toBeNull();
  });

  it('has nothing to show for an exercise never logged', () => {
    expect(previousSetFor([benchSession('2026-07-17T09:00:00.000Z', [{ reps: 5, weightKg: 100 }])], 'squat', 1)).toBeNull();
  });

  // Set 3 shows set 3 of last time, not a summary of the whole entry — the number you are about to
  // try to beat is the one from the same position in the session.
  it('matches on set index', () => {
    const session = benchSession('2026-07-17T09:00:00.000Z', [
      { reps: 8, weightKg: 100 },
      { reps: 7, weightKg: 100 },
      { reps: 5, weightKg: 95 },
    ]);

    expect(previousSetFor([session], 'bench', 3)).toEqual({ kind: 'reps', reps: 5, weightKg: 95 });
  });

  // You did four sets today but only three last time: the honest answer for set 4 is what you were
  // lifting by the end of that session, not nothing at all.
  it('falls back to the last set when the previous entry was shorter', () => {
    const session = benchSession('2026-07-17T09:00:00.000Z', [
      { reps: 8, weightKg: 100 },
      { reps: 6, weightKg: 100 },
    ]);

    expect(previousSetFor([session], 'bench', 4)).toEqual({ kind: 'reps', reps: 6, weightKg: 100 });
  });

  // Absent, not 0 — `commitCurrentStep` writes `weightKg: … || undefined`, and the set row reads the
  // difference to choose between "60 kg × 8" and a bodyweight line.
  it('carries a bodyweight set with no weight rather than a zero one', () => {
    const session = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 12, restTakenSec: 90 }] }],
    });

    expect(previousSetFor([session], 'pullups', 1)).toEqual({ kind: 'reps', reps: 12, weightKg: undefined });
  });

  it('reads a hold set', () => {
    const session = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [
        {
          exercise: 'plank',
          type: 'timed_hold',
          sets: [
            { holdSec: 60, restTakenSec: 60 },
            { holdSec: 45, restTakenSec: 60 },
          ],
        },
      ],
    });

    expect(previousSetFor([session], 'plank', 2)).toEqual({ kind: 'hold', holdSec: 45 });
  });

  // The newest *finished* one. An abandoned session is not part of the log the app reports on, and
  // letting one answer "last time" would quote a set from a workout the user walked out of.
  it('skips unfinished sessions and takes the most recent finished one', () => {
    const abandoned = makeSession({
      startedAt: '2026-07-23T09:00:00.000Z',
      id: 'sess-abandoned',
      endedAt: null,
      entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 1, weightKg: 200, restTakenSec: 120 }] }],
    });
    const older = benchSession('2026-07-17T09:00:00.000Z', [{ reps: 5, weightKg: 100 }]);

    expect(previousSetFor([abandoned, older], 'bench', 1)).toEqual({ kind: 'reps', reps: 5, weightKg: 100 });
  });

  it('has nothing to show for interval work, which has no per-set number to carry', () => {
    const session = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'burpees', type: 'amrap', roundsCompleted: 9 }],
    });

    expect(previousSetFor([session], 'burpees', 1)).toBeNull();
  });
});

describe('personalBestFor', () => {
  it('has no best for an exercise never logged', () => {
    expect(personalBestFor([], 'bench')).toEqual({
      heaviestSetKg: undefined,
      mostReps: undefined,
      longestHoldSec: undefined,
    });
  });

  it('reports the highest value across every finished session', () => {
    const sessions = [
      makeSession({
        startedAt: '2026-07-24T09:00:00.000Z',
        id: 'a',
        entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 95, restTakenSec: 120 }] }],
      }),
      makeSession({
        startedAt: '2026-07-17T09:00:00.000Z',
        id: 'b',
        entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 105, restTakenSec: 120 }] }],
      }),
    ];

    expect(personalBestFor(sessions, 'bench').heaviestSetKg).toBe(105);
  });

  // The same split entryBest makes: a bodyweight exercise competes on reps, a loaded one on load. The
  // marker and the completion screen have to agree about this, which is why they share the traversal.
  it('keeps the loaded and bodyweight bests apart', () => {
    const session = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 14, restTakenSec: 90 }] }],
    });

    expect(personalBestFor([session], 'pullups')).toEqual({
      heaviestSetKg: undefined,
      mostReps: 14,
      longestHoldSec: undefined,
    });
  });

  it('ignores unfinished sessions', () => {
    const abandoned = makeSession({
      startedAt: '2026-07-23T09:00:00.000Z',
      id: 'sess-abandoned',
      endedAt: null,
      entries: [{ exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 300, restTakenSec: 60 }] }],
    });
    const finished = makeSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-finished',
      entries: [{ exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 60, restTakenSec: 60 }] }],
    });

    expect(personalBestFor([abandoned, finished], 'plank').longestHoldSec).toBe(60);
  });
});

/**
 * A session with no workout behind it — what an ad-hoc session writes. This layer has been ready for
 * it since `workoutNameFor` learned to return null, which is why #55 needed no changes here; these
 * pin that, so a later "simplification" of the null path fails loudly instead of crashing History.
 */
describe('a session with no workout', () => {
  const library: Library = { version: 1, exercises: [], workouts: [], programs: [] };
  const adHoc = makeSession({
    startedAt: '2026-07-24T09:00:00.000Z',
    workout: null,
    endedAt: '2026-07-24T09:45:00.000Z',
    entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 60, restTakenSec: 120 }] }],
  });

  it('renders in Recent under the translated stand-in', () => {
    expect(recentSessionsView([adHoc], library)[0].workoutName).toBe('Ad-hoc session');
  });

  it('renders in History under the same stand-in', () => {
    expect(historySessionsView([adHoc], library)[0].workoutName).toBe('Ad-hoc session');
  });

  it('counts towards the stats like any other session', () => {
    expect(historyStats([adHoc])).toEqual({ sessions: 1, hours: 0, minutes: 45, sets: 1 });
  });

  // The stand-in is a translated string, not a hardcoded English one — `formatSessionName` owns it.
  it('translates the stand-in', async () => {
    await changeLanguage('pt');
    expect(recentSessionsView([adHoc], library)[0].workoutName).toBe('Sessão avulsa');
  });
});

/**
 * What the session editor (#56) is for, from the other end: a correction has to move every number
 * derived from the log, not just the row the user typed into. Each of these reads the *edited* entry
 * through a different derived path — the History summary, the volume chart, the stat tiles — because
 * an edit that only showed up in one of them would look like the correction hadn't taken.
 */
describe('a corrected entry, downstream', () => {
  const library: Library = {
    version: 1,
    exercises: [{ id: 'back-squat', name: 'Back Squat', type: 'reps', config: { sets: 3, targetRepsMin: 8, restSec: 90 } }],
    workouts: [{ id: 'w', name: 'Leg day', blocks: [] }],
    programs: [],
  };
  const logged = (sets: { reps: number; weightKg?: number }[]): SessionEntry => ({
    exercise: 'back-squat',
    type: 'reps',
    sets: sets.map((set) => ({ ...set, restTakenSec: 90 })),
  });

  const before = makeSession({ startedAt: '2026-07-29T09:00:00.000Z', entries: [logged([{ reps: 8 }, { reps: 8 }])] });
  // The same session with set 2 corrected from 8 reps to 5, which is what `editEntry` writes.
  const after = { ...before, entries: [logged([{ reps: 8 }, { reps: 5 }])] };

  it('changes the summary History renders', () => {
    expect(historySessionsView([before], library)[0].entries[0].summary).toBe('8 · 8 reps');
    expect(historySessionsView([after], library)[0].entries[0].summary).toBe('8 · 5 reps');
  });

  it('changes the entry result the summary is built from', () => {
    expect(sessionEntryResult(after.entries[0])).toEqual({ kind: 'reps', reps: [8, 5] });
  });

  it('changes the volume the chart plots', () => {
    expect(exerciseHistory([before], 'back-squat')[0].volume).toBe(16);
    expect(exerciseHistory([after], 'back-squat')[0].volume).toBe(13);
  });

  // Removing a set has to move the set count too, or the tiles keep counting work that was taken out.
  it('changes the set count in the stat tiles when a set is removed', () => {
    const removed = { ...before, entries: [logged([{ reps: 8 }])] };
    expect(historyStats([before]).sets).toBe(2);
    expect(historyStats([removed]).sets).toBe(1);
  });

  // A loaded entry's volume is reps × weight rather than reps, so correcting the load moves it too.
  it('changes the volume when the load is what was wrong', () => {
    const light = makeSession({ startedAt: '2026-07-29T09:00:00.000Z', entries: [logged([{ reps: 5, weightKg: 60 }])] });
    const heavy = { ...light, entries: [logged([{ reps: 5, weightKg: 100 }])] };

    expect(exerciseHistory([light], 'back-squat')[0].volume).toBe(300);
    expect(exerciseHistory([heavy], 'back-squat')[0].volume).toBe(500);
  });
});
