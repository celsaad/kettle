import { setAudioModeAsync, useAudioPlayer } from 'expo-audio';
import { useCallback, useEffect } from 'react';

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
 */
export function useSessionSounds() {
  const tickPlayer = useAudioPlayer(tickSource);
  const exerciseChangePlayer = useAudioPlayer(exerciseChangeSource);
  const milestonePlayer = useAudioPlayer(milestoneSource);

  useEffect(() => {
    // Workout timing cues should be audible even with the phone's silent switch on.
    setAudioModeAsync({ playsInSilentMode: true, interruptionMode: 'duckOthers' }).catch(() => {});
  }, []);

  const playTick = useCallback(() => {
    tickPlayer.seekTo(0);
    tickPlayer.play();
  }, [tickPlayer]);

  const playExerciseChange = useCallback(() => {
    exerciseChangePlayer.seekTo(0);
    exerciseChangePlayer.play();
  }, [exerciseChangePlayer]);

  const playMilestone = useCallback(() => {
    milestonePlayer.seekTo(0);
    milestonePlayer.play();
  }, [milestonePlayer]);

  return { playTick, playExerciseChange, playMilestone };
}
