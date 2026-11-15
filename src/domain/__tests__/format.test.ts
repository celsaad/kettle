import { t } from 'i18next';

import { formatEntryResult } from '@/domain/format';

// The hand-rolled `plural(count, singular)` helper is gone; i18next resolves CLDR categories from
// `count` instead. These assert the resolution itself, since it's what every count-bearing string in
// the app now depends on — and it's the piece that has to keep working when a locale with more than
// two plural forms is added.
describe('pluralisation', () => {
  it('picks the singular form for exactly one', () => {
    expect(t('format.block', { count: 1 })).toBe('1 block');
    expect(t('format.round', { count: 1 })).toBe('1 round');
  });

  it('picks the plural form for zero and for many', () => {
    expect(t('format.block', { count: 0 })).toBe('0 blocks');
    expect(t('format.block', { count: 4 })).toBe('4 blocks');
  });
});

describe('formatEntryResult', () => {
  it('says "1 round" and "1 rep" for singles', () => {
    expect(formatEntryResult({ kind: 'rounds', rounds: 1 })).toBe('1 round');
    expect(formatEntryResult({ kind: 'rounds', rounds: 1, extraReps: 1 })).toBe('1 round + 1 rep');
    expect(formatEntryResult({ kind: 'intervals', intervals: 1, totalReps: 1 })).toBe('1 interval · 1 rep');
  });

  it('joins per-set values', () => {
    expect(formatEntryResult({ kind: 'holds', holdSecs: [20, 18, 15] })).toBe('20s · 18s · 15s');
    expect(formatEntryResult({ kind: 'reps', reps: [8, 7, 5], weightsKg: [undefined, undefined, undefined] })).toBe(
      '8 · 7 · 5 reps',
    );
  });

  it('omits extras that were not recorded', () => {
    expect(formatEntryResult({ kind: 'rounds', rounds: 7 })).toBe('7 rounds');
    expect(formatEntryResult({ kind: 'intervals', intervals: 10 })).toBe('10 intervals');
    expect(formatEntryResult({ kind: 'cardio', distanceMeters: 2000 })).toBe('2000 m');
    expect(formatEntryResult({ kind: 'cardio', durationSec: 480, distanceMeters: 2000 })).toBe('480s · 2000 m');
  });
});
