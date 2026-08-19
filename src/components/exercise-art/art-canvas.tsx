import type { ReactNode } from 'react';
import Svg, { G } from 'react-native-svg';

/**
 * The shared canvas every exercise drawing is authored against. One viewBox for the whole set is what
 * keeps seventeen separately-written figures reading as one style: a figure drawn 1.4× larger than
 * its neighbours is the drift you notice first when they're seen together.
 *
 * Landscape rather than square because the movements are — a push-up, a row and a carry are all wider
 * than they are tall, and a square canvas would shrink every one of them to fit its unused headroom.
 */
export const ArtViewBox = '0 0 120 96';

/** Height as a fraction of width, so callers size on one axis and the aspect follows. */
export const ArtAspect = 96 / 120;

/** Ground level. Figures that stand, lie or press against the floor share this line. */
export const ArtGroundY = 84;

/**
 * Uniform, and deliberately so: a single stroke weight across the set is most of what makes it look
 * drawn by one hand. Nothing in the set varies it for emphasis.
 */
export const ArtStrokeWidth = 3;

type Props = {
  size: number;
  color: string;
  children: ReactNode;
};

/**
 * Stroke, weight and joins are set once on a wrapping `<G>` and inherited, rather than repeated on
 * every path. That's not only brevity — it's what makes the whole drawing recolourable from one
 * `color` prop, which is how a single asset serves the light theme, the dark theme and the
 * always-dark runner (see `KettleMark` for the same idea hand-applied).
 */
export function ArtCanvas({ size, color, children }: Props) {
  return (
    <Svg width={size} height={size * ArtAspect} viewBox={ArtViewBox} fill="none">
      <G stroke={color} strokeWidth={ArtStrokeWidth} strokeLinecap="round" strokeLinejoin="round" fill="none">
        {children}
      </G>
    </Svg>
  );
}
