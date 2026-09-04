import { StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * One line on the count-in, for someone who has never finished a session: you can stop watching the
 * screen. `FirstRunCard` already makes this claim on the Workouts tab ("Timers and audio cues run it,
 * hands-free"); this is the moment it becomes checkable, and the only moment where acting on it costs
 * nothing — the count-in is three seconds of standing still, where every other runner screen is time
 * the user is meant to be lifting.
 *
 * **It reads the session log itself, and that is the load-bearing detail rather than a style choice.**
 * `session.tsx`'s `CompletedSession` carries the note: subscribing to `sessions` anywhere above the
 * runner re-renders it on every logged set, since each one writes through the store — the exact cost
 * the runner's refs exist to avoid. So the subscription cannot be lifted into `SessionScreen` and
 * passed down as a prop. It is safe here twice over: this component is a leaf, and the whole count-in
 * unmounts before `ActiveSession` ever mounts.
 *
 * Who sees it is derived, not persisted — `sessions.length === 0`, the same test the Workouts tab's
 * first-run card uses. A "seen" flag would mean a new file, a new store and a web build with nowhere
 * to write it, to answer a question the log already answers. The count-in runs before the runner
 * creates a session, so the count is still zero on the very session this describes.
 *
 * **The wording says "hear", not "called out"** — an earlier draft promised each step would be named
 * aloud, which is true only with a screen reader running. `use-session-announcements.ts` speaks step
 * identity through `announceForAccessibility` and is a no-op otherwise; what everyone else gets is
 * `use-session-sounds.ts`'s three dings — a tick on a countdown's last three seconds and a distinct
 * ding on an exercise change. So the sentence promises the cues that actually fire.
 *
 * **Muted sessions see nothing.** `sessionSounds` defaults on, but a user who turned it off would get
 * a hint promising sound that no longer plays, on the one screen with no way to argue back. Cheaper
 * to stay quiet than to write a second sentence for a case where the advice doesn't hold.
 *
 * Web sees it on every session, knowingly: there is no persistence there, so `listSessions` always
 * comes back empty and the count never leaves zero. Same trade as step 3 of `FirstRunCard`, recorded
 * for the same reason — web is what `docs/verifying-in-the-browser.md` drives, and an unexplained
 * always-on hint there is a bug report waiting to be written.
 */
export function FirstSessionHint() {
  const { t } = useTranslation();
  const hasHistory = useSessionHistoryStore((state) => state.sessions.length > 0);
  const soundsOn = usePreferencesStore((state) => state.preferences.sessionSounds);

  if (hasHistory || !soundsOn) return null;

  return (
    <ThemedText type="small" style={styles.hint}>
      {t('session.countdown.firstTimeHint')}
    </ThemedText>
  );
}

const styles = StyleSheet.create({
  // Plain text rather than the bordered card this started as. The count-in is a three-line
  // composition — label, workout name, numeral — and a filled or bordered box below the numeral reads
  // as a fourth object competing with the one thing the screen exists to show. No new color either:
  // `textSecondary` on this background is the pairing the "GET READY" label above already uses.
  hint: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
    marginTop: Spacing.two,
    maxWidth: 280,
    lineHeight: 20,
  },
});
