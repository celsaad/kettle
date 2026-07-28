import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo } from 'react-native';

/**
 * Speaks the runner's state changes to a screen reader.
 *
 * The naive version — an `accessibilityLiveRegion` on the numeral — would announce once per second,
 * roughly sixty interruptions a minute, talking over the user mid-set. It would also be reading out
 * the one thing they don't need: the *timing* is already carried eyes-free by the audio cues.
 *
 * What's missing without sight is **identity** — which exercise, which set, how many left. So this
 * announces transitions only: a new step, and nothing in between. Roughly five utterances per
 * exercise instead of ninety.
 *
 * Gated on a screen reader actually running. `announceForAccessibility` is a no-op otherwise, but the
 * check keeps the runner from doing per-step work for the ~99% of sessions where nobody is listening.
 */
export function useSessionAnnouncements(announcement: string | null) {
  // State, not a ref: `isScreenReaderEnabled()` resolves asynchronously, and the first step's
  // announcement arrives before it does. Held in a ref, that first announcement was silently dropped
  // — at exactly the moment identity matters most, the start of a session. As state, resolving the
  // check re-runs the effect below and the pending announcement still gets spoken.
  const [enabled, setEnabled] = useState(false);
  const lastSpokenRef = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    AccessibilityInfo.isScreenReaderEnabled().then((value) => {
      if (!cancelled) setEnabled(value);
    });
    const subscription = AccessibilityInfo.addEventListener('screenReaderChanged', setEnabled);
    return () => {
      cancelled = true;
      subscription.remove();
    };
  }, []);

  useEffect(() => {
    if (!announcement || !enabled) return;
    // Deduped because the effect re-runs whenever the derived string is rebuilt, which happens on
    // every tick — only a genuine change is worth speaking.
    if (lastSpokenRef.current === announcement) return;
    lastSpokenRef.current = announcement;
    AccessibilityInfo.announceForAccessibility(announcement);
  }, [announcement, enabled]);
}
