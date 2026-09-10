// Mocked so the week boundary is driven by the test rather than the machine's locale; the real one
// reads the device calendar via expo-localization.
const mockFirstWeekdayIndex = jest.fn(() => 1);
jest.mock('@/i18n', () => ({
  firstWeekdayIndex: () => mockFirstWeekdayIndex(),
  currentLocale: () => 'en',
}));

import type { Session, SessionEntry } from '@/domain/types';
import {
  calendarLevel,
  currentStreak,
  historyStats,
  thisWeekStats,
  trainingCalendar,
} from '@/state/selectors/history-stats';
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

/**
 * The Stats screen's training calendar.
 *
 * `now` is injected rather than faked with timers — the windowing is the thing under test, and a
 * parameter says so more clearly than a system clock does. The fixture is a Wednesday so a session sits
 * mid-week whichever weekday the calendar starts on, rather than on a boundary the first-weekday rule
 * would move — except where the first weekday is the thing under test, and then it is set explicitly.
 */
describe('trainingCalendar', () => {
  const wednesday = new Date(2026, 7, 12, 12, 0, 0); // Wed 12 Aug 2026
  const setFirstWeekday = (day: number) => mockFirstWeekdayIndex.mockReturnValue(day);

  /** A finished session starting at `start` and lasting `minutes`. */
  const lasting = (start: Date, minutes: number, id = start.toISOString()): Session =>
    aSession({
      id,
      startedAt: start.toISOString(),
      endedAt: new Date(start.getTime() + minutes * 60_000).toISOString(),
    });

  const onDay = (daysBefore: number, hour = 9) => {
    const date = new Date(wednesday);
    date.setDate(date.getDate() - daysBefore);
    date.setHours(hour, 0, 0, 0);
    return date;
  };

  afterEach(() => setFirstWeekday(1));

  it('returns the weeks asked for, oldest first, seven consecutive days each', () => {
    const weeks = trainingCalendar([], 4, wednesday);

    expect(weeks).toHaveLength(4);
    expect(weeks[0].weekStart.getTime()).toBeLessThan(weeks[3].weekStart.getTime());
    for (const week of weeks) {
      expect(week.days).toHaveLength(7);
      expect(week.days[0].date.toDateString()).toBe(week.weekStart.toDateString());
    }
    const allDays = weeks.flatMap((week) => week.days.map((day) => day.date));
    for (let index = 1; index < allDays.length; index += 1) {
      const expected = new Date(allDays[index - 1]);
      expected.setDate(expected.getDate() + 1);
      expect(allDays[index].toDateString()).toBe(expected.toDateString());
    }
  });

  it('starts each row on Monday when the calendar says so', () => {
    setFirstWeekday(1);

    expect(trainingCalendar([], 1, wednesday)[0].days[0].date.getDay()).toBe(1);
  });

  it('starts each row on Sunday when the calendar says so', () => {
    setFirstWeekday(0);

    expect(trainingCalendar([], 1, wednesday)[0].days[0].date.getDay()).toBe(0);
  });

  // A double day is one cell, but two sessions: the cell's shade is the day's total time, and the row's
  // count is what the THIS WEEK tile counts.
  it('sums a day with two sessions into one cell, and counts both', () => {
    const weeks = trainingCalendar([lasting(onDay(0, 7), 20, 'a'), lasting(onDay(0, 18), 25, 'b')], 1, wednesday);
    const today = weeks[0].days.find((day) => day.isToday)!;

    expect(today).toMatchObject({ sessions: 2, minutes: 45, level: 3 });
    expect(weeks[0]).toMatchObject({ sessions: 2, minutes: 45 });
  });

  it('shades by fixed minute buckets', () => {
    expect([0, 29, 30, 44, 45, 120].map((minutes) => calendarLevel(1, minutes))).toEqual([1, 1, 2, 2, 3, 3]);
    expect(calendarLevel(0, 0)).toBe(0);
  });

  // An unfinished session has no duration. Shading its day as rest would say you didn't turn up.
  it('shades a day with only an unfinished session as trained', () => {
    const unfinished = aSession({ startedAt: onDay(1).toISOString(), endedAt: null });
    const day = trainingCalendar([unfinished], 1, wednesday)[0].days.find((each) => each.sessions === 1);

    expect(day).toMatchObject({ minutes: 0, level: 1 });
  });

  it('flags today, and the days after it as still to come', () => {
    setFirstWeekday(1);
    const days = trainingCalendar([], 1, wednesday)[0].days;

    expect(days.map((day) => day.isToday)).toEqual([false, false, true, false, false, false, false]);
    expect(days.map((day) => day.isFuture)).toEqual([false, false, false, true, true, true, true]);
  });

  // The THIS WEEK tile and the calendar's last row show the same week, so they must count it the same
  // way — including a session that was never finished.
  it('counts the current week the way the THIS WEEK tile does', () => {
    jest.useFakeTimers();
    jest.setSystemTime(wednesday);
    const sessions = [
      lasting(onDay(0), 30, 'a'),
      lasting(onDay(1), 40, 'b'),
      aSession({ id: 'c', startedAt: onDay(2).toISOString(), endedAt: null }),
      lasting(onDay(9), 30, 'd'),
    ];

    expect(trainingCalendar(sessions, 4, wednesday).at(-1)!.sessions).toBe(thisWeekStats(sessions).sessions);
  });

  it('ignores anything older than the window', () => {
    const weeks = trainingCalendar([lasting(onDay(70), 30)], 4, wednesday);

    expect(weeks.every((week) => week.sessions === 0)).toBe(true);
  });

  // Every cell is a whole calendar day even when the window crosses a DST change, where a day is 23 or
  // 25 hours and millisecond stepping would slide a cell onto the wrong date. Bites on CI, where TZ is
  // set; on a DST-free machine it passes either way.
  it('keeps every day at local midnight across a DST change', () => {
    const weeks = trainingCalendar([], 12, new Date(2026, 10, 18, 12, 0, 0)); // spans the Nov change
    const days = weeks.flatMap((week) => week.days);

    expect(days.every((day) => day.date.getHours() === 0)).toBe(true);
    expect(new Set(days.map((day) => day.date.toDateString())).size).toBe(days.length);
  });
});
