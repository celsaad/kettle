import type { ReactElement } from 'react';

/**
 * Every drawing takes the same two props, which is what lets the map hold them as one type and the
 * renderer stay ignorant of which one it got. `color` is required rather than defaulted: a drawing
 * that silently falls back to a fixed colour is invisible on one of the three surfaces it can land
 * on (light, dark, and the always-dark runner).
 */
export type ExerciseArtProps = {
  size: number;
  color: string;
};

export type ExerciseArtComponent = (props: ExerciseArtProps) => ReactElement;
