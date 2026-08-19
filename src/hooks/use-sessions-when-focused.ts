import { useRef } from 'react';
import { useIsFocused } from 'expo-router';

import type { Session } from '@/domain/types';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * The log and whether it has finished loading, always from the same moment.
 *
 * Returned together rather than read separately because a frozen `sessions` beside a live `ready` is
 * a state that never existed: a screen that blurred before the read landed would see an empty log
 * *and* be told the read was done, and would render the "week 1 to a six-week-in user" card that
 * gating on readiness exists to prevent — correcting only once the screen was focused again.
 */
export type SessionsSnapshot = { sessions: Session[]; ready: boolean };

/**
 * The session log, held at its last value while the calling screen is not the one on screen.
 *
 * `session` is a `presentation: 'modal'` route, so all four tab screens stay **mounted** underneath
 * it for the entire workout. Every `logEntry` rebuilds `sessions` with `.map()`, giving the array a
 * fresh identity, which invalidated the `useMemo` around `nextUpView` and `historySessionsView` on
 * screens nobody was looking at. Both walk the whole log, and both ran once per set — on the JS
 * thread, during the one flow whose defining requirement is timer reliability.
 *
 * Focus rather than `activeSessionId` is the signal on purpose. Gating on "a session is live" reads
 * as the more direct test, but a modal can be swiped away with its session still running, and that
 * version would then leave the visible tabs frozen for as long as the user left it that way — a live
 * session missing from History with nothing to explain it. Focus is true whenever the screen is
 * actually being read, which is the only time its derived views need to be current, and it recovers
 * on its own the moment the user comes back.
 *
 * Writing the ref during render is deliberate and idempotent: the same render inputs always produce
 * the same stored value, so a re-run of the render costs nothing and changes nothing.
 */
export function useSessionsWhenFocused(): SessionsSnapshot {
  const sessions = useSessionHistoryStore((state) => state.sessions);
  const ready = useSessionHistoryStore((state) => state.status === 'ready' || state.status === 'error');
  const isFocused = useIsFocused();
  const held = useRef<SessionsSnapshot>({ sessions, ready });
  if (isFocused) held.current = { sessions, ready };
  return held.current;
}
