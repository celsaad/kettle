import { setAudioModeAsync, useAudioPlayer } from 'expo-audio';
import { useCallback, useEffect } from 'react';

const tickSource = require('../../assets/sounds/tick.wav');
const exerciseChangeSource = require('../../assets/sounds/exercise-change.wav');

/**
 * Session audio cues: a short "tick" ding for the 3-2-1 approach on any countdown (the pre-session
 * get-ready count-in, and every rest/interval countdown's final 3 seconds), and a distinct
 * "exercise change" ding for when the runner actually moves to a different exercise. Two dedicated
 * players (rather than one reused player) so a tick and a change cue can never cut each other off.
 */
export function useSessionSounds() {
  const tickPlayer = useAudioPlayer(tickSource);
  const exerciseChangePlayer = useAudioPlayer(exerciseChangeSource);

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

  return { playTick, playExerciseChange };
}
