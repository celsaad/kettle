import { estimatedOneRepMaxKg } from '@/domain/one-rm';

describe('estimatedOneRepMaxKg', () => {
  it('applies Epley to a multi-rep set', () => {
    // 100 × (1 + 8/30)
    expect(estimatedOneRepMaxKg(100, 8)).toBeCloseTo(126.667, 3);
  });

  // A single already *is* a one-rep max. Running it through the formula would report a 3% lift the
  // user never made, on the one input where the honest answer is known exactly.
  it('returns a single as itself rather than inflating it', () => {
    expect(estimatedOneRepMaxKg(140, 1)).toBe(140);
  });

  /**
   * Bodyweight sets have `weightKg` *absent* rather than 0 (see `commitCurrentStep`), so both shapes
   * arrive here and neither has a load to project from. Answering 0 instead of null would put "Est.
   * 1RM 0 kg" under a bodyweight PR.
   */
  it.each([
    ['absent', undefined],
    ['zero', 0],
  ])('has no answer for a %s load', (_label, weightKg) => {
    expect(estimatedOneRepMaxKg(weightKg, 10)).toBeNull();
  });

  it('has no answer for a set with no reps', () => {
    expect(estimatedOneRepMaxKg(100, 0)).toBeNull();
  });

  // Epley diverges past ~12 reps — a 20-rep set projects to nearly 1.7× the load, which is not a
  // number anyone should be shown next to the word "estimated".
  it('declines to estimate from a high-rep set', () => {
    expect(estimatedOneRepMaxKg(100, 12)).not.toBeNull();
    expect(estimatedOneRepMaxKg(100, 13)).toBeNull();
  });
});
