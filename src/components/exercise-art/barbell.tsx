import { Circle, Path } from 'react-native-svg';

import { ArtCanvas } from './art-canvas';
import { Barbell, Ground } from './art-parts';
import type { ExerciseArtProps } from './types';

/**
 * The six barbell movements, side-on like the rest of the set.
 *
 * **The bench is the one piece of furniture in the whole set, and it took an argument.** The dumbbell
 * drawings ban furniture outright — the movement and the load, nothing else — and that ban is why
 * the floor press is drawn on the floor rather than on a bench nobody needs. A bench press has no
 * such escape: without the bench it is a floor press, which is a different exercise that this app
 * already ships a drawing of. So the bench is here, held to three strokes, and the ban stands
 * everywhere it can be kept: no rack, no plates on the floor, no gym.
 *
 * The pull-up is the only drawing in the set with **no ground line**, for the same reason the AMRAP
 * ring has none: the feet are off the floor and drawing one under them would put them back on it.
 */

export function BackSquat({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={64} cy={20} r={7.5} />
      <Path d="M62,28 L52,54" />
      {/* The bar sits behind the neck, just under the head — high enough to be on the shoulders and
          not in the hands, which is the only thing separating this from a front-loaded squat. */}
      <Barbell x={62} y={32} />
      <Path d="M60,34 L76,32" />
      <Path d="M52,54 L77,58" />
      <Path d="M77,58 L75,82" />
      <Path d="M69,82 H83" />
    </ArtCanvas>
  );
}

export function BenchPress({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* Three strokes of bench: a top and two legs. Anything more — a frame, an upright, a pad —
          and the drawing is about the furniture. */}
      <Path d="M20,66 H92" />
      <Path d="M30,66 V84" />
      <Path d="M84,66 V84" />
      <Circle cx={26} cy={57} r={7.5} />
      <Path d="M35,61 L70,63" />
      <Path d="M70,63 L80,74" />
      <Path d="M80,74 L82,84" />
      <Path d="M44,60 L42,44" />
      <Barbell x={42} y={40} scale={0.8} />
    </ArtCanvas>
  );
}

export function Deadlift({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={24} cy={40} r={7.5} />
      <Path d="M33,43 L72,50" />
      <Path d="M72,50 L74,66" />
      <Path d="M74,66 L70,82" />
      <Path d="M64,82 H78" />
      {/* Arm straight down and the bar at the shins, plates nearly on the floor. Held any higher it
          is the Romanian deadlift the dumbbell set already draws. */}
      <Path d="M36,46 L38,68" />
      <Barbell x={38} y={73} scale={0.85} />
    </ArtCanvas>
  );
}

export function OverheadPressBarbell({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={60} cy={34} r={7.5} />
      <Path d="M60,42 L60,60" />
      <Path d="M60,60 L52,82" />
      <Path d="M60,60 L68,82" />
      <Path d="M46,82 H56" />
      <Path d="M64,82 H74" />
      <Path d="M57,45 L52,24" />
      <Path d="M63,45 L68,24" />
      <Barbell x={60} y={19} scale={0.9} />
    </ArtCanvas>
  );
}

export function BarbellRow({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={24} cy={38} r={7.5} />
      <Path d="M33,42 L72,50" />
      <Path d="M72,50 L75,66" />
      <Path d="M75,66 L71,82" />
      <Path d="M65,82 H79" />
      {/* Mid-pull: the bar is up under the torso rather than hanging, which is the entire difference
          between this and the deadlift above. */}
      <Path d="M38,46 L42,60" />
      <Barbell x={42} y={65} scale={0.85} />
    </ArtCanvas>
  );
}

export function PullUps({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      {/* The bar alone, no uprights — the same call the inverted row makes, and for the same reason:
          posts running down to a ground line close a box around the figure. */}
      <Path d="M24,16 H104" />
      <Path d="M56,12 V20" />
      <Path d="M70,12 V20" />
      <Path d="M56,19 L60,46" />
      <Path d="M70,19 L66,46" />
      {/* Head forward of the arms rather than between them: drawn between, the two arms cross the
          circle and the top of the figure becomes a knot. */}
      <Circle cx={46} cy={44} r={7.5} />
      <Path d="M54,46 L61,47" />
      <Path d="M62,47 L66,68" />
      <Path d="M66,68 L52,76" />
      <Path d="M52,76 L56,86" />
    </ArtCanvas>
  );
}
