import { StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

/**
 * One line on the count-in, for someone with nothing in their session log yet: you can stop watching
 * the screen. `FirstRunCard` already makes this claim on the Workouts tab ("Timers and audio cues run it,
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
 * **That test is "has a session on record", not "has finished one", and the difference is real.**
 * `use-session-runner.ts` calls `startSession` on mount, before a single set exists, and backing out
 * leaves that session in the log with no entries — so someone who abandons their first count-in has a
 * count of 1 and never sees this again. Deliberately not corrected here: every first-run gate in the
 * app tests the same bare length (`index.tsx`, `import.tsx`, `FirstRunCard`), while every *selector*
 * over the log filters on `endedAt`. Matching the first-run family is the point, and one gate quietly
 * using the selectors' rule would be a divergence nothing names. If this wants fixing it wants fixing
 * in all four at once. The cost is small either way: the abandoned run is one the hint was on screen
 * for.
 *
 * **The wording says "hear", not "called out"** — an earlier draft promised each step would be named
 * aloud, which is true only with a screen reader running. `use-session-announcements.ts` speaks step
 * identity through `announceForAccessibility` and is a no-op otherwise; what everyone else gets is
 * `use-session-sounds.ts`'s three dings — a tick on a countdown's last three seconds and a distinct
 * ding on an exercise change. So the sentence promises the cues that actually fire.
 *
 * **It is not announced, and that is a decision rather than an oversight.** `session.tsx` says of this
 * same screen that a banner here "is gone before it is read and invisible to a screen reader", which
 * is why the over-length warning got its own screen and a control instead. That reasoning holds for a
 * warning whose loss corrupts the log; it is answered differently here, three ways. Announcing it
 * would land three seconds before the first step's own announcement, and
 * `use-session-announcements.ts` exists to protect exactly that one — its state-vs-ref note records
 * the first utterance being dropped "at exactly the moment identity matters most, the start of a
 * session", so putting a nicety in front of it trades identity for advice. The sentence is also the
 * most redundant thing in the app for this audience: a screen reader gets every step spoken by name,
 * so it learns within one step that Kettle talks, where a sighted user may never connect a ding to
 * anything. And `accessibilityLiveRegion` is Android-only, so the "cheap" version is half a platform
 * with iOS in flight. Nothing is lost by missing this line; if that ever stops being true, it wants
 * the truncation screen's treatment — its own moment and a control — not a live region here.
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
  //
  // No `maxFontSizeMultiplier`, deliberately — capping body text is the thing the `minHeight` rule
  // exists to avoid, and the numeral's cap is for decoration. The open question is the other end: at
  // the largest text sizes this wraps to several lines on a `flex: 1` screen with no ScrollView, so it
  // could push the count-in's own content out of view. If it does, the fix is `SessionNextCard`'s —
  // drop the optional element above a font scale — not shrink the text of the people who set it.

  hint: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
    marginTop: Spacing.two,
    maxWidth: 280,
    lineHeight: 20,
  },
});
