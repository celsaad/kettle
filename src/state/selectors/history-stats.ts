import type { Session } from '@/domain/types';
import { firstWeekdayIndex } from '@/i18n';
import { sessionDurationMinutes, sessionSetCount } from '@/state/selectors/session-summary';

export type HistoryStats = { sessions: number; hours: number; sets: number; minutes: number };

/**
 * `hours`/`minutes` are the two halves of one "1h 30m" reading, so both must be whole. `hours` used to
 * be `round(totalMinutes / 60 * 10) / 10` — a fractional *total* — while `minutes` was already a
 * remainder, and both renderers print them side by side: 90 minutes came out as "1.5h 30m", double-
 * counting the half hour. Only ever showed up above the hour mark, which is why short test sessions
 * ("0h 0m") never caught it.
 */
export function historyStats(sessions: Session[]): HistoryStats {
  const totalMinutes = sessions.reduce((sum, session) => sum + sessionDurationMinutes(session), 0);
  const totalSets = sessions.reduce((sum, session) => sum + sessionSetCount(session), 0);
  return {
    sessions: sessions.length,
    hours: Math.floor(totalMinutes / 60),
    minutes: totalMinutes % 60,
    sets: totalSets,
  };
}

/**
 * Start of the user's current week, honouring their calendar's first weekday rather than assuming
 * Monday. The Monday assumption is right for most of Europe and wrong for the US, Canada, Japan and
 * much of Latin America — "this week" silently measured a different window than the calendar the user
 * reads, and the discrepancy is invisible until the boundary day.
 */
function startOfWeek(date: Date): Date {
  const start = new Date(date);
  const firstDay = firstWeekdayIndex(); // 0 = Sunday .. 6 = Saturday
  const diff = (start.getDay() - firstDay + 7) % 7;
  start.setDate(start.getDate() - diff);
  start.setHours(0, 0, 0, 0);
  return start;
}

/**
 * historyStats scoped to the current calendar week (local time, starting on whichever weekday the
 * user's calendar does — see startOfWeek) — same aggregation, just a pre-filtered input.
 */
export function thisWeekStats(sessions: Session[]): HistoryStats {
  const weekStart = startOfWeek(new Date());
  return historyStats(sessions.filter((session) => new Date(session.startedAt) >= weekStart));
}

/** One bar of the analytics screen's breakdown: a week, and how many sessions landed in it. */
export type WeekTally = { weekStart: Date; sessions: number };

/**
 * Sessions per calendar week for the last `weeks` weeks, **oldest first** — the reading order of the
 * chart it feeds.
 *
 * Every week in the window is present even when it has no sessions, which is the whole point: a gap
 * is the most informative bar on a consistency chart, and silently omitting empty weeks would compress
 * a month off training into a chart that looks unbroken. `startOfWeek` decides where a week begins, so
 * this agrees with `thisWeekStats` about which sessions are "this week" rather than inventing a second
 * definition.
 *
 * `now` is a parameter for the same reason `nextUpView` takes one: the rule is then testable without
 * mocking the clock, and the caller owns the clock. Counting only — no duration or set totals — since
 * the question this answers is "am I turning up", and a bar chart can carry exactly one measure.
 */
export function sessionsPerWeek(sessions: Session[], weeks: number, now: Date = new Date()): WeekTally[] {
  const currentWeekStart = startOfWeek(now);

  // Counts down so the array comes out oldest-first without a reverse: the arithmetic still walks
  // backwards from the one week whose boundary is known, but the loop visits the oldest offset first.
  // (`toReversed` is the lint rule's suggested fix and is off the table for the same reason `toSorted`
  // is — see the decision log.)
  const tallies: WeekTally[] = [];
  for (let index = weeks - 1; index >= 0; index -= 1) {
    // setDate() rather than subtracting 7 × 86_400_000: a week spanning a DST change is 167 or 169
    // hours, and fixed-millisecond arithmetic drifts an hour each time until it crosses a midnight
    // and lands in the wrong week. Same hazard `currentStreak` and `calendarDaysBetween` handle.
    const weekStart = new Date(currentWeekStart);
    weekStart.setDate(weekStart.getDate() - index * 7);
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 7);

    const count = sessions.filter((session) => {
      const startedAt = new Date(session.startedAt);
      return startedAt >= weekStart && startedAt < weekEnd;
    }).length;

    tallies.push({ weekStart, sessions: count });
  }

  return tallies;
}

/**
 * Consecutive calendar days with at least one session, walking back from today. Today not having a
 * session yet doesn't break the streak (the day isn't over) — only a gap of a full day or more does.
 */
export function currentStreak(sessions: Session[]): number {
  const activeDays = new Set(sessions.map((session) => new Date(session.startedAt).toDateString()));
  if (activeDays.size === 0) return 0;

  // Steps by calendar day rather than by 86_400_000ms. Subtracting a fixed 24 hours lands on the wrong
  // day across a DST boundary — on a 23-hour day it skips back two days, silently truncating a real
  // streak, and on a 25-hour day it stays on the same one. setDate() moves a whole day whatever that
  // day's length is.
  const previousDay = (date: Date): Date => {
    const previous = new Date(date);
    previous.setDate(previous.getDate() - 1);
    return previous;
  };

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const yesterday = previousDay(today);

  if (!activeDays.has(today.toDateString()) && !activeDays.has(yesterday.toDateString())) return 0;

  let streak = 0;
  let cursor = activeDays.has(today.toDateString()) ? today : yesterday;
  while (activeDays.has(cursor.toDateString())) {
    streak += 1;
    cursor = previousDay(cursor);
  }
  return streak;
}
