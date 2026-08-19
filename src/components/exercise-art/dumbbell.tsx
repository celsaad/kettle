import { Circle, Path } from 'react-native-svg';

import { ArtCanvas } from './art-canvas';
import { Dumbbell, Ground } from './art-parts';
import type { ExerciseArtProps } from './types';

/**
 * The six dumbbell movements. Drawn side-on like the rest, which has a happy consequence: side-on,
 * the far dumbbell sits directly behind the near one, so a two-handed press is honestly drawn with a
 * single bell rather than with two that would have to overlap.
 *
 * No bench and no rack anywhere in here. The set is iconographic — the movement and the load, nothing
 * else — and furniture is the first thing that turns a small drawing to mud.
 */

export function GobletSquat({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={60} cy={18} r={7.5} />
      <Path d="M58,26 L50,54" />
      <Path d="M57,34 L70,42" />
      {/* Held upright at the chest, and drawn nearly full size. Shrunk to fit between the arm and the
          thigh it stopped reading as a dumbbell at all and became a bracket. Sitting it above the
          thigh line is what buys the room. */}
      <Dumbbell x={77} y={42} rotate={90} scale={0.9} />
      <Path d="M50,54 L76,58" />
      <Path d="M76,58 L74,82" />
      <Path d="M68,82 H82" />
    </ArtCanvas>
  );
}

export function FloorPress({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={24} cy={74} r={7.5} />
      <Path d="M33,78 L66,80" />
      <Path d="M66,80 L84,64" />
      <Path d="M84,64 L92,82" />
      <Path d="M86,82 H98" />
      <Path d="M34,77 L38,52" />
      <Dumbbell x={38} y={48} scale={0.85} />
    </ArtCanvas>
  );
}

export function Row({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* Head pushed forward and the arm moved back off it. Drawn closer together, the head, the
          raised elbow and the bell all landed in the same square and the figure read as a bicycle. */}
      <Circle cx={22} cy={46} r={7.5} />
      <Path d="M31,48 L72,54" />
      <Path d="M40,50 L48,40" />
      <Path d="M48,40 L46,60" />
      <Dumbbell x={46} y={66} scale={0.85} />
      <Path d="M72,54 L77,68" />
      <Path d="M77,68 L73,82" />
      <Path d="M67,82 H81" />
    </ArtCanvas>
  );
}

export function RomanianDeadlift({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={24} cy={40} r={7.5} />
      <Path d="M33,43 L72,50" />
      {/* Long leg, long arm, hinge at the hip. */}
      <Path d="M72,50 L70,82" />
      <Path d="M64,82 H78" />
      <Path d="M36,46 L38,66" />
      <Dumbbell x={38} y={70} scale={0.85} />
    </ArtCanvas>
  );
}

export function OverheadPress({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={60} cy={32} r={7.5} />
      <Path d="M60,40 L60,60" />
      <Path d="M60,60 L52,82" />
      <Path d="M60,60 L68,82" />
      <Path d="M46,82 H56" />
      <Path d="M64,82 H74" />
      <Path d="M57,43 L48,24" />
      <Path d="M63,43 L72,24" />
      <Dumbbell x={46} y={20} scale={0.7} />
      <Dumbbell x={74} y={20} scale={0.7} />
    </ArtCanvas>
  );
}

export function FarmersCarry({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={60} cy={26} r={7.5} />
      <Path d="M60,34 L60,56" />
      {/* Mid-stride rather than standing still: a carry is a walk, and two vertical legs would read
          as someone simply holding two dumbbells. */}
      <Path d="M60,56 L49,80" />
      <Path d="M60,56 L70,74" />
      <Path d="M70,74 L74,82" />
      <Path d="M43,82 H53" />
      {/* One bell, on the near arm — the far one is directly behind it in this view, the same reason
          the floor press above draws a single bell. Drawing both put a plate either side of the
          stride with a hand's width between them, and the pair read as one barbell across the hips. */}
      <Path d="M62,38 L66,58" />
      <Dumbbell x={66} y={64} scale={0.85} />
    </ArtCanvas>
  );
}
