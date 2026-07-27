/**
 * Regression tests for two selector fixes.
 *
 * The streak cases need a DST-observing timezone, pinned in jest.setup.js and in the CI workflow.
 * **They only bite where that pin works** — Node ignores TZ on Windows and uses the OS zone, so on a
 * DST-free machine (São Paulo, UTC) these pass whether the day-stepping is correct or not, because
 * there's no transition to cross. Treat a local green as "not broken", and CI as the real check.
 */
import { blockChips, currentStreak } from '@/state/selectors';
import type { Exercise, Session, Workout } from '@/domain/types';

function sessionOn(startedAt: string): Session {
  return {
    version: 1,
    id: `s-${startedAt}`,
    workout: 'w',
    program: null,
    programWeek: null,
    programDay: null,
    startedAt,
    endedAt: startedAt,
    entries: [],
  };
}

describe('currentStreak across a DST transition', () => {
  afterEach(() => jest.useRealTimers());

  // US spring-forward 2026 is Sunday 8 March: that day is only 23 hours long. Walking back by a fixed
  // 86_400_000ms from Monday 9th lands on Saturday 7th, skipping Sunday entirely and truncating the
  // streak to 1. Stepping by calendar day keeps all three.
  it('counts every day through a 23-hour spring-forward day', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-03-09T18:00:00-04:00'));

    const sessions = [
      sessionOn('2026-03-09T12:00:00-04:00'),
      sessionOn('2026-03-08T12:00:00-05:00'),
      sessionOn('2026-03-07T12:00:00-05:00'),
    ];

    expect(currentStreak(sessions)).toBe(3);
  });

  // Autumn 2026 falls back on Sunday 1 November, a 25-hour day. A fixed-24h step stalls on the same
  // calendar day, which would double-count it.
  it('counts every day through a 25-hour fall-back day', () => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-11-02T18:00:00-05:00'));

    const sessions = [
      sessionOn('2026-11-02T12:00:00-05:00'),
      sessionOn('2026-11-01T12:00:00-04:00'),
      sessionOn('2026-10-31T12:00:00-04:00'),
    ];

    expect(currentStreak(sessions)).toBe(3);
  });
});

describe('blockChips', () => {
  const exercises: Exercise[] = [
    { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, restSec: 90 } },
    // Deliberately not named "Rest": the styling used to key off this literal name.
    { id: 'breather', name: 'Pausa', type: 'rest', config: { durationSec: 90 } },
  ];

  const workout: Workout = {
    id: 'w',
    name: 'W',
    blocks: [
      { kind: 'exercise', exerciseId: 'pullups' },
      { kind: 'exercise', exerciseId: 'breather' },
      { kind: 'circuit', rounds: 2, members: [{ exerciseId: 'pullups' }, { exerciseId: 'breather' }] },
    ],
  };

  it('flags rest chips by exercise type, not by their display name', () => {
    expect(blockChips(workout, exercises)).toEqual([
      { name: 'Pull-ups', isRest: false },
      { name: 'Pausa', isRest: true },
      { name: 'Pull-ups', isRest: false },
      { name: 'Pausa', isRest: true },
    ]);
  });

  it('falls back to the id and treats an unknown exercise as non-rest', () => {
    const orphan: Workout = { id: 'o', name: 'O', blocks: [{ kind: 'exercise', exerciseId: 'ghost' }] };
    expect(blockChips(orphan, exercises)).toEqual([{ name: 'ghost', isRest: false }]);
  });
});
