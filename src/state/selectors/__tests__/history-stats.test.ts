// Mocked so the week boundary is driven by the test rather than the machine's locale; the real one
// reads the device calendar via expo-localization.
const mockFirstWeekdayIndex = jest.fn(() => 1);
jest.mock('@/i18n', () => ({
  firstWeekdayIndex: () => mockFirstWeekdayIndex(),
  currentLocale: () => 'en',
}));

import type { SessionEntry } from '@/domain/types';
import { currentStreak, historyStats, thisWeekStats } from '@/state/selectors/history-stats';
import { aSession } from '@/test-support/sessions';

afterEach(() => {
  jest.useRealTimers();
});

describe('historyStats', () => {
  // Regression: hours/minutes must be the two whole-number halves of one "1h 30m" reading. This used
  // to compute hours as a fractional round(total/60*10)/10, so 90 minutes rendered "1.5h 30m" — the
  // half hour double-counted against minutes. Only showed up above the hour mark.
  it('splits a 90-minute total into whole hours and a remainder, not a fractional hour', () => {
    const session = aSession({ startedAt: '2026-07-24T09:00:00.000Z', endedAt: '2026-07-24T10:30:00.000Z' });
    expect(historyStats([session])).toEqual({ sessions: 1, hours: 1, minutes: 30, sets: 0 });
  });

  it('reports zeroes for no sessions', () => {
    expect(historyStats([])).toEqual({ sessions: 0, hours: 0, minutes: 0, sets: 0 });
  });

  it('reports 0 hours for a sub-hour total', () => {
    const session = aSession({ startedAt: '2026-07-24T09:00:00.000Z', endedAt: '2026-07-24T09:25:00.000Z' });
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
    historyStats([aSession({ startedAt: '2026-07-24T09:00:00.000Z', entries })]).sets;

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

describe('currentStreak', () => {
  it('counts a session logged today only', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    const session = aSession({ startedAt: new Date(2026, 6, 24, 9, 0, 0).toISOString() });
    expect(currentStreak([session])).toBe(1);
  });

  it('does not break the streak when only yesterday has a session and today has none yet', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 8, 0, 0));
    const session = aSession({ startedAt: new Date(2026, 6, 23, 9, 0, 0).toISOString() });
    expect(currentStreak([session])).toBe(1);
  });

  it('counts a consecutive multi-day run', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    const sessions = [
      aSession({ startedAt: new Date(2026, 6, 24, 9, 0, 0).toISOString() }),
      aSession({ startedAt: new Date(2026, 6, 23, 9, 0, 0).toISOString() }),
      aSession({ startedAt: new Date(2026, 6, 22, 9, 0, 0).toISOString() }),
    ];
    expect(currentStreak(sessions)).toBe(3);
  });

  it('stops at a gap rather than counting an older isolated day', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    const sessions = [
      aSession({ startedAt: new Date(2026, 6, 24, 9, 0, 0).toISOString() }),
      // Yesterday (07-23) has no session: the gap should stop the streak before reaching 07-22.
      aSession({ startedAt: new Date(2026, 6, 22, 9, 0, 0).toISOString() }),
    ];
    expect(currentStreak(sessions)).toBe(1);
  });

  it('returns 0 for no sessions at all', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 24, 12, 0, 0));
    expect(currentStreak([])).toBe(0);
  });
});

/**
 * The DST cases need a DST-observing timezone, pinned in jest.setup.js and in the CI workflow.
 * **They only bite where that pin works** — Node ignores TZ on Windows and uses the OS zone, so on a
 * DST-free machine (São Paulo, UTC) these pass whether the day-stepping is correct or not, because
 * there's no transition to cross. Treat a local green as "not broken", and CI as the real check.
 */
describe('currentStreak across a DST transition', () => {
  // US spring-forward 2026 is Sunday 8 March: that day is only 23 hours long. Walking back by a fixed
  // 86_400_000ms from Monday 9th lands on Saturday 7th, skipping Sunday entirely and truncating the
  // streak to 1. Stepping by calendar day keeps all three.
  it('counts every day through a 23-hour spring-forward day', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-03-09T18:00:00-04:00'));

    const sessions = [
      aSession({ startedAt: '2026-03-09T12:00:00-04:00' }),
      aSession({ startedAt: '2026-03-08T12:00:00-05:00' }),
      aSession({ startedAt: '2026-03-07T12:00:00-05:00' }),
    ];

    expect(currentStreak(sessions)).toBe(3);
  });

  // Autumn 2026 falls back on Sunday 1 November, a 25-hour day. A fixed-24h step stalls on the same
  // calendar day, which would double-count it.
  it('counts every day through a 25-hour fall-back day', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-11-02T18:00:00-05:00'));

    const sessions = [
      aSession({ startedAt: '2026-11-02T12:00:00-05:00' }),
      aSession({ startedAt: '2026-11-01T12:00:00-04:00' }),
      aSession({ startedAt: '2026-10-31T12:00:00-04:00' }),
    ];

    expect(currentStreak(sessions)).toBe(3);
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
      aSession({ startedAt: new Date(2026, 6, 20, 8, 0, 0).toISOString() }), // Mon, in
      aSession({ startedAt: new Date(2026, 6, 26, 10, 0, 0).toISOString() }), // Sun, in
      aSession({ startedAt: new Date(2026, 6, 19, 20, 0, 0).toISOString() }), // previous Sun, out
    ];
    expect(thisWeekStats(sessions).sessions).toBe(2);
  });

  it('starts the week on Sunday when the calendar says so', () => {
    setFirstWeekday(0);
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 26, 15, 0, 0)); // Sunday 2026-07-26 is now the week's *first* day
    const sessions = [
      aSession({ startedAt: new Date(2026, 6, 26, 10, 0, 0).toISOString() }), // today, in
      aSession({ startedAt: new Date(2026, 6, 20, 8, 0, 0).toISOString() }), // last Mon, now out
      aSession({ startedAt: new Date(2026, 6, 19, 20, 0, 0).toISOString() }), // previous Sun, out
    ];
    expect(thisWeekStats(sessions).sessions).toBe(1);
  });

  it('excludes the prior week once the boundary day arrives', () => {
    setFirstWeekday(1);
    jest.useFakeTimers();
    jest.setSystemTime(new Date(2026, 6, 27, 9, 0, 0)); // Monday 2026-07-27, a new week
    const sessions = [
      aSession({ startedAt: new Date(2026, 6, 26, 23, 0, 0).toISOString() }), // last week's Sunday
      aSession({ startedAt: new Date(2026, 6, 27, 0, 5, 0).toISOString() }), // this week's Monday
    ];
    expect(thisWeekStats(sessions).sessions).toBe(1);
  });
});
