import { G, Path } from 'react-native-svg';

import { ArtGroundY } from './art-canvas';

/**
 * The floor. Drawn rather than implied because without it a side-view figure has no orientation —
 * a push-up and a plank both read as a person floating at an angle.
 */
export function Ground({ from = 8, to = 112, y = ArtGroundY }: { from?: number; to?: number; y?: number }) {
  return <Path d={`M${from},${y} H${to}`} />;
}

/**
 * A dumbbell, centred on `x`/`y` so callers position it at the hand rather than at a corner, and
 * rotatable for the presses where the bar isn't horizontal. Five strokes: bar, and a near and far
 * plate each side.
 */
export function Dumbbell({ x, y, rotate = 0, scale = 1 }: { x: number; y: number; rotate?: number; scale?: number }) {
  return (
    <G transform={`translate(${x} ${y}) rotate(${rotate}) scale(${scale})`}>
      <Path d="M-7,0 H7" />
      <Path d="M-9,-6 V6" />
      <Path d="M-12.5,-3.5 V3.5" />
      <Path d="M9,-6 V6" />
      <Path d="M12.5,-3.5 V3.5" />
    </G>
  );
}

/**
 * A barbell — the same five strokes as the dumbbell, four times as wide.
 *
 * Width is the only thing that distinguishes the two at this size, so it is exaggerated rather than
 * measured: a bar drawn to scale beside a figure would be most of the canvas. Anything narrower than
 * this read as a dumbbell held in one hand, which is the one reading the barbell pack cannot afford.
 */
export function Barbell({ x, y, rotate = 0, scale = 1 }: { x: number; y: number; rotate?: number; scale?: number }) {
  return (
    <G transform={`translate(${x} ${y}) rotate(${rotate}) scale(${scale})`}>
      <Path d="M-30,0 H30" />
      <Path d="M-24,-8 V8" />
      <Path d="M-29,-4.5 V4.5" />
      <Path d="M24,-8 V8" />
      <Path d="M29,-4.5 V4.5" />
    </G>
  );
}

/**
 * A kettlebell, centred on the **body** — so a caller places the mass and the handle follows, which
 * is what the hand then reaches for. It occupies roughly `y - 13` (top of the handle) to `y + 9`
 * (the flat bottom).
 *
 * **The bottom is flat, and that is not decoration.** The first version drew the body as a circle
 * with a small arc on top, and every drawing that used it then held two circles of nearly the same
 * size — one of them a head. On a contact sheet the swing read as a two-headed figure. A domed body
 * on a flat base shares no silhouette with a head at any size, which is the entire job here.
 *
 * `rotate` is how the bell is carried: 180° for the upside-down goblet and rack holds, -90° for a
 * swing, where the handle points back at the hands rather than up.
 */
export function Kettlebell({ x, y, rotate = 0, scale = 1 }: { x: number; y: number; rotate?: number; scale?: number }) {
  return (
    <G transform={`translate(${x} ${y}) rotate(${rotate}) scale(${scale})`}>
      <Path d="M-5,-7 A6.5,7 0 0 1 5,-7" />
      <Path d="M-5,-6 C-9,-2 -9,7 -7,9 H7 C9,7 9,-2 5,-6" />
    </G>
  );
}

/**
 * A wall, or any vertical thing a hand can rest against — a door frame, a counter's edge, a post.
 *
 * It exists because three of the low-impact movements are *defined* by their support: a wall push-up
 * without the wall is a figure falling over, and heel raises without something to hold are a figure
 * standing still. Deliberately one bare stroke, with no bracket or hatching to say which kind of
 * surface it is: the drawing only has to say "something solid is here".
 */
export function Wall({ x, from = 10, to = ArtGroundY }: { x: number; from?: number; to?: number }) {
  return <Path d={`M${x},${from} V${to}`} />;
}

/**
 * A chair, side-on, drawn from the **top of the seat** at `x`/`y` — where a person actually meets it.
 *
 * Furniture is the one thing the dumbbell set banned outright, on the grounds that it turns a small
 * drawing to mud, and that still holds for a bench nobody needs to see. It doesn't hold here: a chair
 * is not scenery in this pack, it is the equipment, and "sit to stand" and "seated march" have no
 * subject without one. Four strokes is the concession — seat, back, two legs, no arms, no frame.
 *
 * `facing` puts the backrest on the left or the right, so the chair can be behind a figure rising off
 * it or in front of one holding it.
 */
export function Chair({ x, y, facing = 'left' }: { x: number; y: number; facing?: 'left' | 'right' }) {
  const direction = facing === 'left' ? 1 : -1;

  return (
    <G transform={`translate(${x} ${y}) scale(${direction} 1)`}>
      <Path d="M0,0 H28" />
      <Path d="M0,0 V-28" />
      <Path d="M2,0 V22" />
      <Path d="M26,0 V22" />
    </G>
  );
}
