/**
 * Estimated one-rep max.
 *
 * **A display-layer estimate, never stored.** Nothing here reaches a session file or the library: the
 * number is derived from what was logged and is recomputed every time it's shown, so a change of
 * formula can never disagree with history the way a persisted estimate would.
 *
 * Epley is the conventional default and is named on screen wherever the number appears — an
 * unattributed 1RM is a number people argue with, and Brzycki/Lombardi give visibly different answers
 * from the same set.
 */

/** Past this, Epley diverges far enough that the estimate is worse than no estimate. */
const MAX_ESTIMABLE_REPS = 12;

/**
 * Epley: `weight × (1 + reps / 30)`. Kilograms in, kilograms out — conversion to the user's display
 * unit happens at the render boundary, like every other weight (`domain/units.ts`).
 *
 * Null rather than a number for the three inputs the formula can't honestly answer: a bodyweight set
 * (no load to project from), a set with no reps, and a high-rep set. A single is returned as itself,
 * since `1 + 1/30` would inflate a genuine 1RM by 3%.
 */
export function estimatedOneRepMaxKg(weightKg: number | undefined, reps: number): number | null {
  if (!weightKg || weightKg <= 0) return null;
  if (reps < 1 || reps > MAX_ESTIMABLE_REPS) return null;
  if (reps === 1) return weightKg;
  return weightKg * (1 + reps / 30);
}
