import { fromDisplayWeight, toDisplayWeight, weightStep } from '@/domain/units';

describe('metric', () => {
  it('is a pass-through, since stored weights are already kilograms', () => {
    expect(toDisplayWeight(62.5, 'metric')).toBe(62.5);
    expect(fromDisplayWeight(62.5, 'metric')).toBe(62.5);
  });

  // Not rounding on the way in is deliberate: the number typed *is* the stored value, so trimming it
  // here would edit input the user can still see on screen.
  it('stores what was typed without rounding it', () => {
    expect(fromDisplayWeight(62.567, 'metric')).toBe(62.567);
  });

  // 60 kg → 132.3 lb → back is 59.99847, which has to read as "60" rather than as a long decimal.
  it('rounds the display so a value converted back from pounds reads cleanly', () => {
    expect(toDisplayWeight(59.99847, 'metric')).toBe(60);
  });
});

describe('imperial', () => {
  it('converts kilograms to pounds for display', () => {
    expect(toDisplayWeight(60, 'imperial')).toBe(132.3);
    expect(toDisplayWeight(100, 'imperial')).toBe(220.5);
  });

  it('converts an entered pound value back to kilograms', () => {
    expect(fromDisplayWeight(135, 'imperial')).toBe(61.23);
    expect(fromDisplayWeight(45, 'imperial')).toBe(20.41);
  });

  /**
   * The property the two rounding precisions exist to guarantee, and the reason pounds display at one
   * decimal rather than two: 0.01 kg of storage quantisation is ±0.011 lb, comfortably inside a
   * 0.1 lb display step, so a pound value always survives the trip to kilograms and back. At two
   * decimals of pounds it would not — 135 would come back as 134.99, in front of the user who just
   * typed it.
   */
  it.each([2.5, 5, 45, 95, 135, 137.5, 185, 225, 315, 405])('round-trips %s lb through storage', (pounds) => {
    expect(toDisplayWeight(fromDisplayWeight(pounds, 'imperial'), 'imperial')).toBe(pounds);
  });
});

describe('weightStep', () => {
  // 5 lb is a pair of 2.5 lb plates, not a converted 2.5 kg — which would be 5.51 lb, a number no
  // plate or dumbbell rack has.
  it('steps in increments the equipment actually has, per system', () => {
    expect(weightStep('metric')).toBe(2.5);
    expect(weightStep('imperial')).toBe(5);
  });
});
