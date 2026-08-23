import { Circle, Path } from 'react-native-svg';

import { ArtCanvas } from './art-canvas';
import { Chair, Ground, Wall } from './art-parts';
import type { ExerciseArtProps } from './types';

/**
 * The eight movements of the Steady & Strong pack, drawn side-on facing right like the rest of the
 * set.
 *
 * These are the first drawings where the *support* is half the movement. A wall push-up without its
 * wall is a figure toppling forward, a heel raise without something to hold is a figure standing
 * still, and a sit-to-stand without a chair is a squat. So the wall and the chair are drawn, and
 * drawn thin — the figure stays the darkest thing on the canvas by having the most strokes, not by
 * being the only thing on it.
 *
 * Nothing here is drawn frail. The figures are the same height, the same head, the same stroke as the
 * barbell set's, because the drawing describes a movement and not a person — and a hunched figure
 * would be the app telling somebody what it assumes about them.
 */

export function SitToStand({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Chair x={26} y={62} />
      {/* Caught mid-rise, hips just off the front edge: seated is a chair with a person in it, and
          standing is a person next to a chair. Only the half-way pose is the movement. */}
      <Circle cx={72} cy={32} r={7.5} />
      <Path d="M66,38 L52,58" />
      <Path d="M64,42 L78,52" />
      <Path d="M52,58 L72,62" />
      <Path d="M72,62 L70,82" />
      <Path d="M64,82 H78" />
    </ArtCanvas>
  );
}

export function WallPushUps({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Wall x={100} />
      {/* One unbroken line from heel to shoulder, exactly as the floor push-up draws it — the same
          straight body, stood up. That parallel is deliberate: it is the same exercise at a
          different angle, and the pair should look like it. */}
      <Path d="M36,82 L70,34" />
      <Path d="M30,82 H44" />
      <Circle cx={78} cy={28} r={7.5} />
      <Path d="M70,36 L97,44" />
    </ArtCanvas>
  );
}

export function HeelRaises({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Wall x={24} from={20} />
      <Circle cx={78} cy={22} r={7.5} />
      <Path d="M78,30 L78,54" />
      {/* Heels up, so each foot is a long diagonal meeting the floor at the toe. Drawn as flat ticks
          they were marks on the ground and the figure read as standing still — the raised heel is
          the only thing this drawing has to say. */}
      <Path d="M75,54 L72,74" />
      <Path d="M72,74 L82,84" />
      <Path d="M81,54 L80,74" />
      <Path d="M80,74 L90,84" />
      {/* The reaching arm drops as it goes, clearing the head. Run level it passed through the
          circle and the head read as a bead threaded on a rail. */}
      <Path d="M74,36 L27,52" />
    </ArtCanvas>
  );
}

export function SeatedMarch({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Chair x={34} y={62} />
      {/* The torso leans a few degrees forward of the backrest on purpose: drawn parallel, the two
          lines read as one thick one and the person disappears into the chair. */}
      <Circle cx={56} cy={26} r={7.5} />
      <Path d="M52,34 L46,58" />
      <Path d="M46,58 L66,60" />
      <Path d="M66,60 L66,82" />
      <Path d="M60,82 H74" />
      {/* The lifted knee is the movement, and the only thing separating this from sitting down. The
          shin hangs from it: drawn as one straight line up and out, the raised leg was the only limb
          above the hip and read as an arm pointing. */}
      <Path d="M46,58 L70,48" />
      <Path d="M70,48 L74,66" />
      {/* Which is also why the arm is here — with nothing else above the hip, the leg had the job
          of looking like both. */}
      <Path d="M53,40 L62,52" />
    </ArtCanvas>
  );
}

export function StandingBalance({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Circle cx={60} cy={18} r={7.5} />
      <Path d="M60,26 L60,52" />
      {/* Arms out and one foot clear of the floor. The gap under the raised foot is the whole
          drawing: a foot touching down at any point is just somebody standing. */}
      <Path d="M58,34 L38,26" />
      <Path d="M62,34 L82,26" />
      <Path d="M60,52 L58,82" />
      <Path d="M52,82 H66" />
      <Path d="M60,52 L44,64" />
      <Path d="M44,64 L42,76" />
    </ArtCanvas>
  );
}

export function BandRow({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      <Wall x={14} from={20} />
      {/* The band is a curve, and a straight line here is the bug: drawn taut it is a rod, and the
          drawing becomes a cable machine the pack does not assume anybody has. */}
      <Path d="M16,52 Q36,55 54,47" />
      <Circle cx={76} cy={24} r={7.5} />
      <Path d="M76,32 L74,56" />
      {/* Two segments, bent at the elbow. A single straight line from shoulder to band continued the
          band's own curve, and arm and band became one long rope across the canvas. */}
      <Path d="M75,37 L66,45" />
      <Path d="M66,45 L54,47" />
      <Path d="M74,56 L70,82" />
      <Path d="M74,56 L80,82" />
      <Path d="M64,82 H74" />
      <Path d="M76,82 H86" />
    </ArtCanvas>
  );
}

export function HipHinge({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* **The support here is an upright, not the whole chair.** Drawn as one, the chair's seat and
          its two legs closed a rectangle against the ground line, and standing alone beside a
          horizontal torso the pair read as a person at a desk. The two seated drawings above can
          afford the full chair because somebody is sitting in it; this one cannot, and the back of a
          chair seen edge-on is a line anyway. */}
      <Wall x={14} from={40} />
      {/* Hinged at the hip with the back long and the knees soft — the same shape as the Romanian
          deadlift, which is the point: this is that movement with a chair instead of a load. */}
      <Circle cx={32} cy={42} r={7.5} />
      <Path d="M41,45 L76,52" />
      <Path d="M76,52 L78,68" />
      <Path d="M78,68 L74,82" />
      <Path d="M68,82 H82" />
      <Path d="M44,48 L17,50" />
    </ArtCanvas>
  );
}

export function Walk({ size, color }: ExerciseArtProps) {
  return (
    <ArtCanvas size={size} color={color}>
      <Ground />
      {/* Mid-stride and upright. No heart and no speed lines — those belong to the cardio drawing,
          which is about effort; this one is about the movement being a walk. */}
      <Circle cx={58} cy={20} r={7.5} />
      <Path d="M58,28 L58,54" />
      <Path d="M57,34 L46,46" />
      <Path d="M59,34 L70,44" />
      <Path d="M58,54 L70,70" />
      <Path d="M70,70 L74,82" />
      <Path d="M68,82 H80" />
      <Path d="M58,54 L46,72" />
      <Path d="M46,72 L42,82" />
      <Path d="M36,82 H48" />
    </ArtCanvas>
  );
}
