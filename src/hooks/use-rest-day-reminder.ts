import { t } from 'i18next';
import { useEffect } from 'react';

import { REST_DAY_REMINDER_DAYS, REST_DAY_REMINDER_HOUR } from '@/domain/preferences';
import { cancelRestDayReminder, requestNotificationPermissions, scheduleRestDayReminder } from '@/hooks/safe-notifications';
import { usePreferencesStore } from '@/state/preferences-store';
import { selectHistoryComplete, useSessionHistoryStore } from '@/state/session-history-store';

/**
 * When the nudge should land: `REST_DAY_REMINDER_DAYS` calendar days after the last session, at a
 * fixed local hour.
 *
 * Steps with `setDate()` rather than adding 48h in milliseconds, the same fix `currentStreak` carries:
 * a fixed 24-hour day lands on the wrong date across a DST boundary. Exported for its own test — the
 * arithmetic is the part worth pinning, and it's the part a caller can't see.
 */
export function restDayReminderAt(lastSessionStartedAt: string): Date {
  const at = new Date(lastSessionStartedAt);
  at.setDate(at.getDate() + REST_DAY_REMINDER_DAYS);
  at.setHours(REST_DAY_REMINDER_HOUR, 0, 0, 0);
  return at;
}

/**
 * Keeps the opt-in rest-day reminder in step with the log. Mounted once, from the root layout's
 * `Navigation`.
 *
 * That layout used to render only after all three stores had hydrated, and this hook was written
 * against that ordering. It no longer holds: session history is hydrated but not gated on, so this
 * now mounts while the log is still being read. Waiting for `status` is not a refinement of the old
 * behaviour but a restoration of it — without the wait, an empty log reads as "never trained", which
 * takes the `!lastSessionStartedAt` branch and **cancels the pending reminder** on every cold start,
 * rescheduling only once the read lands. An app killed inside that window loses the notification with
 * nothing to say so.
 *
 * It is a *rest-day* nudge rather than a daily alarm on purpose: there is only ever one pending
 * notification and it sits a couple of days past the most recent session, so finishing a session
 * pushes it back out and someone training regularly never sees it. A repeating daily trigger was the
 * obvious alternative and was rejected — it fires whether or not you trained, which is the guilt-trip
 * this feature is explicitly not.
 *
 * Both inputs are narrow selectors: the session store is rewritten on every logged set, and reading
 * the whole array here would re-render the router on each one.
 *
 * Local notifications only. Nothing here transmits, and nothing may be added that does — the Play
 * listing declares zero data collected (`AGENTS.md` → "Platform constraints").
 */
export function useRestDayReminder(): void {
  const enabled = usePreferencesStore((state) => state.preferences.restDayReminder);
  // Sessions are newest-first (session-files.ts), so the head is the most recent one — including one
  // still in progress, whose start is already the right anchor.
  const lastSessionStartedAt = useSessionHistoryStore((state) => state.sessions[0]?.startedAt ?? null);
  // `complete`, not `read`. This hook reasons about the *absence* of sessions — no log means "never
  // trained", which cancels the pending reminder — and a failed read produces exactly that absence
  // without it being true. On `error` the honest move is to leave whatever is already scheduled
  // alone, which is what the early return below does.
  const historyComplete = useSessionHistoryStore(selectHistoryComplete);

  useEffect(() => {
    // Nothing is known yet, so nothing is decided yet: leave whatever is already scheduled alone.
    if (!historyComplete) return;
    if (!enabled || !lastSessionStartedAt) {
      cancelRestDayReminder();
      return;
    }

    const at = restDayReminderAt(lastSessionStartedAt);
    // Already past — the user is opening the app now, which is the thing the reminder exists to
    // prompt. Firing one immediately would be a notification about something they're already doing.
    if (at.getTime() <= Date.now()) {
      cancelRestDayReminder();
      return;
    }

    requestNotificationPermissions();
    scheduleRestDayReminder(
      t('session.notification.reminderTitle'),
      t('session.notification.reminderBody', { count: REST_DAY_REMINDER_DAYS }),
      at,
    );
  }, [enabled, lastSessionStartedAt, historyComplete]);
}
