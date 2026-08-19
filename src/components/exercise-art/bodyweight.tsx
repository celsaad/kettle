import { Circle, Path } from 'react-native-svg';

import { ArtCanvas } from './art-canvas';
import { Ground } from './art-parts';
import type { ExerciseArtProps } from './types';

/**
 * The seven bodyweight movements, all drawn side-on facing right. One viewpoint for the whole set is
 * a constraint worth keeping: a figure drawn front-on among side-on neighbours reads as a different
 * illustrator, and side-on is the view that shows a hinge, a depth and a straight line — which is
 * what these drawings exist to show.
 */

export function PushUps({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* One unbroken line from heel to shoulder — the straight body *is* the push-up. */}
      <Path d="M20,77 L86,53" />
      <Path d="M20,77 L13,82" />
      <Path d="M86,53 L91,50" />
      <Circle cx={97} cy={48} r={7.5} />
      <Path d="M86,54 L88,82" />
      <Path d="M80,56 L82,82" />
    </ArtCanvas>
  );
}

export function BodyweightSquats({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={64} cy={20} r={7.5} />
      <Path d="M62,28 L52,54" />
      {/* The reaching arm starts well below the collar. Drawn any higher it meets the head at the
          same point the torso does, and the head then reads as being stuck on the arm. */}
      <Path d="M59,37 L82,41" />
      <Path d="M52,54 L77,58" />
      <Path d="M77,58 L75,82" />
      <Path d="M69,82 H83" />
    </ArtCanvas>
  );
}

export function InvertedRows({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* The bar alone, with no uprights: running posts down to the floor closed a rectangle against
          the ground line and the drawing read as a box with a brace in it.
          The figure hangs a long way below the bar so the arms are unmistakably arms. Drawn closer
          up, they were two short ticks that merged into the bar and the head, and the whole thing
          collapsed into a circle on a diagonal. */}
      <Path d="M26,20 H104" />
      <Path d="M56,16 V24" />
      <Path d="M64,16 V24" />
      <Path d="M56,23 L59,50" />
      <Path d="M64,23 L62,50" />
      <Circle cx={48} cy={46} r={7.5} />
      <Path d="M60,50 L55,47" />
      <Path d="M61,50 L96,80" />
      <Path d="M96,80 L104,82" />
    </ArtCanvas>
  );
}

export function SplitSquats({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={58} cy={20} r={7.5} />
      <Path d="M58,28 L58,52" />
      <Path d="M58,52 L80,58" />
      <Path d="M80,58 L79,82" />
      <Path d="M73,82 H87" />
      {/* Rear knee dropped and the heel lifted — the half that tells it apart from a squat. */}
      <Path d="M58,52 L44,70" />
      <Path d="M44,70 L30,81" />
      <Path d="M30,81 L24,82" />
    </ArtCanvas>
  );
}

export function GluteBridge({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* No arm along the floor: it ran parallel to the torso a few pixels below it and closed a
          long thin triangle, which at this size is a smudge rather than an arm. */}
      <Circle cx={20} cy={74} r={7.5} />
      <Path d="M28,78 L62,60" />
      <Path d="M62,60 L88,58" />
      <Path d="M88,58 L90,82" />
      <Path d="M84,82 H96" />
    </ArtCanvas>
  );
}

export function Plank({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Path d="M20,76 L86,54" />
      <Path d="M20,76 L13,81" />
      <Path d="M86,54 L91,51" />
      <Circle cx={96} cy={49} r={7.5} />
      {/* Forearm flat on the floor. This is the *only* thing separating a plank from a push-up at a
          glance, so it's drawn horizontal rather than merely bent. */}
      <Path d="M86,55 L88,81" />
      <Path d="M88,81 H105" />
    </ArtCanvas>
  );
}

export function MountainClimbers({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Path d="M52,66 L86,54" />
      <Path d="M86,54 L91,51" />
      <Circle cx={96} cy={49} r={7.5} />
      <Path d="M86,55 L88,82" />
      <Path d="M80,57 L82,82" />
      <Path d="M52,66 L18,79" />
      <Path d="M18,79 L12,82" />
      {/* One knee tucked under the chest, the other leg long: the asymmetry is the movement. */}
      <Path d="M52,66 L68,72" />
      <Path d="M68,72 L62,82" />
    </ArtCanvas>
  );
}
