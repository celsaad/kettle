import { setAudioModeAsync, useAudioPlayer } from 'expo-audio';
import { useCallback, useEffect } from 'react';

import { usePreferencesStore } from '@/state/preferences-store';

const tickSource = require('../../assets/sounds/tick.wav');
const exerciseChangeSource = require('../../assets/sounds/exercise-change.wav');
const milestoneSource = require('../../assets/sounds/milestone.wav');

/**
 * Session audio cues: a short "tick" ding for the 3-2-1 approach on any countdown (the pre-session
 * get-ready count-in, and every rest/interval countdown's final 3 seconds), a distinct "exercise
 * change" ding for when the runner actually moves to a different exercise, and a rising two-note
 * "milestone" chime for reaching a point *within* a step — a HIIT interval's halfway mark, or a hold
 * hitting its target. A player each (rather than one reused) so no cue can cut another off.
 *
 * The milestone chime rises where the tick is flat, because the two carry opposite meanings: a tick
 * says "about to end", a milestone says "keep going, you're partway". One sound serves both milestone
 * cases — which of the two you're hearing is never ambiguous, since you're either mid-interval or
 * mid-hold, and a third distinct sound would be more to learn for no added information.
 *
 * **The three files are level-matched on purpose** (~0.30 RMS, 0.5 peak). The milestone shipped as a
 * decaying bell at 0.09 RMS — a real ~11dB below the other two — and got reported as "doesn't fire",
 * because mid-HIIT or mid-hold you are breathing hard with the phone on the floor. It fires; it was
 * inaudible. A cue you are meant to hear without looking has to hold its level, so these are
 * flat-topped tones with a short release, not chimes that ring out. Measure a replacement against
 * tick.wav before assuming it is loud enough.
 *
 * **The mute preference is read here and nowhere else.** The runner fires these from the tick effect,
 * from `advance()` and from the countdown component, so a check at each call site is a check that
 * gets forgotten the next time a cue is added; gating the three callbacks means a muted session is
 * muted by construction. The players stay loaded either way — unmuting mid-session has to be audible
 * on the next tick, not after a reload.
 */
export function useSessionSounds() {
  const tickPlayer = useAudioPlayer(tickSource);
  const exerciseChangePlayer = useAudioPlayer(exerciseChangeSource);
  const milestonePlayer = useAudioPlayer(milestoneSource);
  const enabled = usePreferencesStore((state) => state.preferences.sessionSounds);

  useEffect(() => {
    // Workout timing cues should be audible even with the phone's silent switch on.
    setAudioModeAsync({ playsInSilentMode: true, interruptionMode: 'duckOthers' }).catch(() => {});
  }, []);

  const playTick = useCallback(() => {
    if (!enabled) return;
    tickPlayer.seekTo(0);
    tickPlayer.play();
  }, [enabled, tickPlayer]);

  const playExerciseChange = useCallback(() => {
    if (!enabled) return;
    exerciseChangePlayer.seekTo(0);
    exerciseChangePlayer.play();
  }, [enabled, exerciseChangePlayer]);

  const playMilestone = useCallback(() => {
    if (!enabled) return;
    milestonePlayer.seekTo(0);
    milestonePlayer.play();
  }, [enabled, milestonePlayer]);

  return { playTick, playExerciseChange, playMilestone };
}
