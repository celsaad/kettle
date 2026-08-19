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
 * plate each side. That's the whole vocabulary the set has for equipment.
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
