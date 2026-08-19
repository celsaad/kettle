import { Circle, Path } from 'react-native-svg';

import { ArtCanvas } from './art-canvas';
import { Ground } from './art-parts';
import type { ExerciseArtProps } from './types';

/**
 * The four conditioning movements. These are the only drawings in the set that have to say something
 * about *time* rather than about a shape, which is where the source sheet went wrong: its stopwatch
 * read "60s" and its dial read "12 min". Both are `config` values the user can edit, so the drawing
 * would go quietly wrong in the one place the app claims to be showing you your own data. Nothing in
 * here carries a numeral — a stopwatch says "timed" and a loop says "repeated" without asserting how
 * long or how many.
 */

export function Burpees({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* Two figures, because a burpee is a transition and no single pose is one. Both are drawn at
          the same scale — a smaller floor figure beside a larger airborne one read as two different
          people rather than as one person twice. */}
      <Path d="M12,74 L42,62" />
      <Path d="M12,74 L7,79" />
      <Circle cx={48} cy={59} r={6.5} />
      <Path d="M42,63 L44,80" />
      <Circle cx={86} cy={22} r={6.5} />
      <Path d="M86,29 L86,48" />
      <Path d="M84,32 L75,18" />
      <Path d="M88,32 L97,18" />
      <Path d="M86,48 L79,70" />
      <Path d="M86,48 L93,70" />
      {/* Diagonal, not horizontal. Flat ticks under the feet read as marks lying on the floor; these
          read as the feet having just left it. */}
      <Path d="M77,74 L75,79" />
      <Path d="M95,74 L97,79" />
    </ArtCanvas>
  );
}

export function EmomPushUps({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={28} cy={30} r={15} />
      <Path d="M28,15 V10" />
      <Path d="M23,10 H33" />
      <Path d="M28,30 V21" />
      <Path d="M28,30 L35,33" />
      <Path d="M36,76 L88,58" />
      <Path d="M36,76 L30,81" />
      <Path d="M88,58 L93,56" />
      <Circle cx={99} cy={54} r={7} />
      <Path d="M88,59 L90,82" />
      <Path d="M82,61 L84,82" />
    </ArtCanvas>
  );
}

export function AmrapBodyweight({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      {/* A broken ring with two arrowheads: rounds, repeated, count unstated. That last part is the
          point — an AMRAP's cap is user-editable, so the drawing must not name one.
          The ring is wide enough to hold a figure that can still be read. Drawn tighter, the figure
          inside had to shrink until it was a scribble in a circle. */}
      <Path d="M24.3,33 A38,38 0 0 1 95.7,33" />
      <Path d="M97.3,24.1 L95.7,33 L88.8,27.2" />
      <Path d="M95.7,59 A38,38 0 0 1 24.3,59" />
      <Path d="M22.7,67.9 L24.3,59 L31.2,64.8" />
      <Circle cx={62} cy={30} r={5.5} />
      <Path d="M60,35 L54,50" />
      <Path d="M58,39 L74,42" />
      <Path d="M54,50 L72,53" />
      <Path d="M72,53 L71,66" />
      <Path d="M65,66 H77" />
    </ArtCanvas>
  );
}

export function EasyCardio({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={54} cy={24} r={7.5} />
      <Path d="M55,32 L60,54" />
      <Path d="M58,37 L70,32" />
      <Path d="M56,37 L44,44" />
      <Path d="M60,54 L74,66" />
      <Path d="M74,66 L80,80" />
      <Path d="M60,54 L50,64" />
      <Path d="M50,64 L42,58" />
      {/* Heart and speed lines: effort and pace, neither of them a number. */}
      <Path d="M96,32 C88,26 88,18 93,18 C95,18 96,20 96,21 C96,20 97,18 99,18 C104,18 104,26 96,32" />
      <Path d="M22,44 H34" />
      <Path d="M18,54 H30" />
    </ArtCanvas>
  );
}
