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

/**
 * The minutes at which a trained day's shade steps up. Exported so the legend interpolates the same
 * numbers the shading uses, rather than restating them in three locale bundles where they could drift.
 */
export const CALENDAR_MINUTES = { mid: 30, long: 45 } as const;

/** 0 is a day with no session; 1–3 are under 30 minutes, 30–44, and 45 or more. */
export type CalendarLevel = 0 | 1 | 2 | 3;

export type CalendarDay = {
  /** The day's local midnight. */
  date: Date;
  sessions: number;
  minutes: number;
  level: CalendarLevel;
  isToday: boolean;
  isFuture: boolean;
};

/** One row of the Stats screen's training calendar. */
export type CalendarWeek = { weekStart: Date; sessions: number; minutes: number; days: CalendarDay[] };

/**
 * Fixed buckets rather than a scale relative to the user's own longest day: fixed is what a legend can
 * name, and a relative scale would re-shade a whole month after one long session. The cost is that
 * minutes aren't effort, so a long stretching session shades like a hard one — the log has nothing
 * better to measure effort by.
 *
 * A trained day is never level 0, even at zero minutes. An unfinished session has no duration, and
 * shading it as rest would say you didn't turn up on a day you did.
 */
export function calendarLevel(sessions: number, minutes: number): CalendarLevel {
  if (sessions === 0) return 0;
  if (minutes < CALENDAR_MINUTES.mid) return 1;
  if (minutes < CALENDAR_MINUTES.long) return 2;
  return 3;
}

/**
 * The last `weeks` calendar weeks, **oldest first**, each as seven days — the rows of the Stats
 * screen's training calendar.
 *
 * Built on `startOfWeek`, so it agrees with `thisWeekStats` about where a week begins, and it counts
 * sessions the way that does too — unfinished ones included — so the THIS WEEK tile and the calendar's
 * last row can never disagree about the week they both show. Every day is present whether or not you
 * trained: a gap is the most informative cell on a consistency chart, and leaving one out would draw a
 * lapse as an unbroken run.
 *
 * Counts down so the array comes out oldest-first without a reverse: the arithmetic walks backwards
 * from the one week whose boundary is known, but the loop visits the oldest offset first. (`toReversed`
 * is the lint rule's suggested fix and is off the table for the same reason `toSorted` is — see the
 * decision log.)
 *
 * `now` is a parameter for the same reason `nextUpView` takes one: the rule is testable without
 * mocking the clock, and the caller owns the clock.
 */
export function trainingCalendar(sessions: Session[], weeks: number, now: Date = new Date()): CalendarWeek[] {
  const byDay = new Map<string, { sessions: number; minutes: number }>();
  for (const session of sessions) {
    const key = new Date(session.startedAt).toDateString();
    const day = byDay.get(key) ?? { sessions: 0, minutes: 0 };
    day.sessions += 1;
    day.minutes += sessionDurationMinutes(session);
    byDay.set(key, day);
  }

  const todayKey = now.toDateString();
  const currentWeekStart = startOfWeek(now);
  const calendar: CalendarWeek[] = [];
  for (let index = weeks - 1; index >= 0; index -= 1) {
    // setDate() for both steps rather than adding milliseconds: a week spanning a DST change is 167 or
    // 169 hours, and fixed-ms arithmetic drifts until a day boundary crosses midnight. Same hazard
    // `currentStreak` and `exerciseProgress` handle.
    const weekStart = new Date(currentWeekStart);
    weekStart.setDate(weekStart.getDate() - index * 7);

    const days: CalendarDay[] = [];
    for (let offset = 0; offset < 7; offset += 1) {
      const date = new Date(weekStart);
      date.setDate(date.getDate() + offset);
      const logged = byDay.get(date.toDateString()) ?? { sessions: 0, minutes: 0 };
      // Compared as date strings rather than timestamps: where DST starts at midnight (Brazil did, until
      // 2019) that day has no 00:00, and a timestamp comparison against "today at midnight" misses.
      const isToday = date.toDateString() === todayKey;
      days.push({
        date,
        ...logged,
        level: calendarLevel(logged.sessions, logged.minutes),
        isToday,
        isFuture: !isToday && date > now,
      });
    }

    calendar.push({
      weekStart,
      sessions: days.reduce((sum, day) => sum + day.sessions, 0),
      minutes: days.reduce((sum, day) => sum + day.minutes, 0),
      days,
    });
  }

  return calendar;
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
