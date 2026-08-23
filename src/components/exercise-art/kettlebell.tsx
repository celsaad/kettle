import { Circle, Path } from 'react-native-svg';

import { ArtCanvas } from './art-canvas';
import { Ground, Kettlebell } from './art-parts';
import type { ExerciseArtProps } from './types';

/**
 * The seven kettlebell movements, side-on like the rest of the set.
 *
 * Three of these are the dumbbell set's poses with a different implement — the goblet squat, the row,
 * the carry — and that repetition is the reason the bell has to be unmistakable at this size. It is,
 * because of the handle: swap the bell for a circle and every one of them becomes a person holding a
 * ball. Which is why the handle's *direction* is drawn honestly rather than always upward — pointing
 * back at the hands on the swing, and downward for the two upside-down holds.
 */

export function KettlebellSwing({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* Hinged, with the bell out in front at chest height and the arms long. The bell at the top of
          an arc rather than between the legs: the bottom of a swing is a deadlift with a bell. */}
      <Circle cx={84} cy={30} r={7.5} />
      <Path d="M78,36 L46,48" />
      <Path d="M46,48 L44,66" />
      <Path d="M44,66 L48,82" />
      <Path d="M42,82 H56" />
      <Path d="M76,40 L100,45" />
      <Kettlebell x={106} y={46} rotate={-90} scale={0.9} />
    </ArtCanvas>
  );
}

export function KettlebellGobletSquat({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={60} cy={18} r={7.5} />
      <Path d="M58,26 L50,54" />
      <Path d="M57,34 L74,47" />
      {/* Upside down, held by the horns — which is how a goblet squat is actually carried and, at
          this size, the quickest way to say "kettlebell, not dumbbell" without a second glance. */}
      <Kettlebell x={76} y={42} rotate={180} scale={0.9} />
      <Path d="M50,54 L76,58" />
      <Path d="M76,58 L74,82" />
      <Path d="M68,82 H82" />
    </ArtCanvas>
  );
}

export function CleanAndPress({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={56} cy={32} r={7.5} />
      <Path d="M56,40 L56,60" />
      <Path d="M56,60 L48,82" />
      <Path d="M56,60 L64,82" />
      <Path d="M42,82 H54" />
      <Path d="M60,82 H72" />
      {/* One arm locked out overhead and one hanging: the asymmetry is what says this is a
          single-side lift, which is what its `notes` promise the reps are counted as. */}
      <Path d="M58,43 L74,27" />
      <Kettlebell x={80} y={17} rotate={180} scale={0.85} />
      <Path d="M54,43 L44,58" />
    </ArtCanvas>
  );
}

export function SingleLegDeadlift({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* Torso and rear leg on one long line through the hip. That line is the movement — bent
          anywhere along it, the drawing is a bent-over row with a leg in the air. */}
      <Circle cx={24} cy={42} r={7.5} />
      <Path d="M33,45 L66,52" />
      <Path d="M66,52 L96,40" />
      <Path d="M66,52 L70,82" />
      <Path d="M64,82 H78" />
      <Path d="M40,49 L40,62" />
      <Kettlebell x={40} y={70} scale={0.85} />
    </ArtCanvas>
  );
}

export function KettlebellRow({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={22} cy={44} r={7.5} />
      <Path d="M31,47 L72,54" />
      {/* Elbow up and back, forearm hanging straight down from it — the pull, rather than the hang
          the single-leg deadlift beside it draws. */}
      <Path d="M40,49 L48,39" />
      <Path d="M48,39 L46,56" />
      <Kettlebell x={46} y={68} scale={0.8} />
      <Path d="M72,54 L77,68" />
      <Path d="M77,68 L73,82" />
      <Path d="M67,82 H81" />
    </ArtCanvas>
  );
}

export function Halo({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* A broken arc over the head with an arrowhead on it, and the bell parked at the open end.
          The arc is the movement: a bell held beside a head is a rack hold, and only the path says
          it is travelling around one. Same vocabulary as the AMRAP ring, deliberately. */}
      <Path d="M44,30 A16,16 0 0 1 76,30" />
      <Path d="M46,21 L44,30 L52,29" />
      <Kettlebell x={84} y={30} rotate={180} scale={0.62} />
      <Circle cx={58} cy={32} r={7.5} />
      <Path d="M58,40 L58,60" />
      <Path d="M58,44 L72,38" />
      <Path d="M58,60 L50,82" />
      <Path d="M58,60 L66,82" />
      <Path d="M44,82 H56" />
      <Path d="M62,82 H74" />
    </ArtCanvas>
  );
}

export function RackCarry({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={62} cy={24} r={7.5} />
      <Path d="M62,32 L62,54" />
      {/* Mid-stride, like the farmer's carry it sits beside: a carry is a walk, and two vertical legs
          would read as somebody simply standing there holding a bell. */}
      <Path d="M62,54 L51,78" />
      <Path d="M62,54 L72,72" />
      <Path d="M72,72 L76,82" />
      <Path d="M45,82 H55" />
      {/* Racked at the chest and slightly in front of the torso line. Drawn centred on it, the bell
          and the body shared strokes and the pair went to mud. */}
      <Path d="M60,36 L50,49" />
      <Kettlebell x={47} y={43} rotate={180} scale={0.8} />
    </ArtCanvas>
  );
}
