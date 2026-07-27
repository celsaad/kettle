import { formatCircuitShape, formatEntryResult, formatWorkoutShape, plural } from '@/domain/format';

describe('plural', () => {
  it('uses the singular for exactly one', () => {
    expect(plural(1, 'block')).toBe('1 block');
    expect(plural(1, 'workout')).toBe('1 workout');
  });

  it('uses the plural for zero and for many', () => {
    expect(plural(0, 'block')).toBe('0 blocks');
    expect(plural(4, 'block')).toBe('4 blocks');
  });

  it('accepts an irregular plural', () => {
    expect(plural(2, 'entry', 'entries')).toBe('2 entries');
  });
});

describe('formatWorkoutShape', () => {
  // The bug this replaced: the count was interpolated straight into "N blocks", so a one-block
  // workout read "1 blocks" on both the Today card and every Build row.
  it('says "1 block" for a single-block workout', () => {
    expect(formatWorkoutShape({ blockCount: 1, types: ['reps'], estimatedMinutes: 5 })).toBe('1 block · reps · ~5 min');
  });

  it('lists a single type bare and several as "mixed"', () => {
    expect(formatWorkoutShape({ blockCount: 3, types: ['reps'], estimatedMinutes: 9 })).toBe('3 blocks · reps · ~9 min');
    expect(formatWorkoutShape({ blockCount: 4, types: ['timed_hold', 'reps'], estimatedMinutes: 14 })).toBe(
      '4 blocks · mixed hold + reps · ~14 min',
    );
  });

  it('says "rest only" when nothing but rest is configured', () => {
    expect(formatWorkoutShape({ blockCount: 2, types: [], estimatedMinutes: 3 })).toBe('2 blocks · rest only · ~3 min');
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
    expect(formatEntryResult({ kind: 'reps', reps: [8, 7, 5] })).toBe('8 · 7 · 5 reps');
  });

  it('omits extras that were not recorded', () => {
    expect(formatEntryResult({ kind: 'rounds', rounds: 7 })).toBe('7 rounds');
    expect(formatEntryResult({ kind: 'intervals', intervals: 10 })).toBe('10 intervals');
    expect(formatEntryResult({ kind: 'cardio', distanceMeters: 2000 })).toBe('2000 m');
    expect(formatEntryResult({ kind: 'cardio', durationSec: 480, distanceMeters: 2000 })).toBe('480s · 2000 m');
  });
});

describe('formatCircuitShape', () => {
  it('says "1 round" for a single-round circuit', () => {
    expect(formatCircuitShape({ rounds: 1, restBetweenExercisesSec: 15, restBetweenRoundsSec: 60 })).toBe(
      '1 round · 15s / 60s rest',
    );
  });

  it('pluralises past one', () => {
    expect(formatCircuitShape({ rounds: 3, restBetweenExercisesSec: 0, restBetweenRoundsSec: 90 })).toBe(
      '3 rounds · 0s / 90s rest',
    );
  });
});
