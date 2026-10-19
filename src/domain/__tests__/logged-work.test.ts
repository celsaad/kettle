import { hasLoggedWork, entryWithoutZeroWork, withoutZeroWork } from '@/domain/logged-work';
import type { SessionEntry } from '@/domain/types';
import { aSession } from '@/test-support/sessions';

/**
 * What "not done" means in the log. Logging 0 is how an exercise is skipped in the runner, so a zero
 * set shown in History or counted as a set reads as work that never happened.
 */
describe('entryWithoutZeroWork', () => {
  it('drops zero sets from a reps or hold entry and keeps the rest', () => {
    expect(
      entryWithoutZeroWork({
        exercise: 'pullups',
        type: 'reps',
        sets: [
          { reps: 6, restTakenSec: 90 },
          { reps: 0, restTakenSec: 90 },
        ],
      }),
    ).toEqual({ exercise: 'pullups', type: 'reps', sets: [{ reps: 6, restTakenSec: 90 }] });
    expect(
      entryWithoutZeroWork({
        exercise: 'plank',
        type: 'timed_hold',
        sets: [
          { holdSec: 0, restTakenSec: 0 },
          { holdSec: 2, restTakenSec: 0 },
        ],
      }),
    ).toEqual({ exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 2, restTakenSec: 0 }] });
  });

  it('returns null when nothing of the entry is left', () => {
    expect(entryWithoutZeroWork({ exercise: 'pullups', type: 'reps', sets: [{ reps: 0, restTakenSec: 0 }] })).toBeNull();
    expect(entryWithoutZeroWork({ exercise: 'pullups', type: 'reps', sets: [] })).toBeNull();
    expect(
      entryWithoutZeroWork({ exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 0, restTakenSec: 0 }] }),
    ).toBeNull();
    expect(entryWithoutZeroWork({ exercise: 'grinder', type: 'amrap', roundsCompleted: 0 })).toBeNull();
    expect(entryWithoutZeroWork({ exercise: 'row', type: 'cardio', durationSec: 0 })).toBeNull();
  });

  it('keeps an AMRAP or cardio entry with any work in it', () => {
    const extraOnly: SessionEntry = { exercise: 'grinder', type: 'amrap', roundsCompleted: 0, extraReps: 5 };
    const distanceOnly: SessionEntry = { exercise: 'row', type: 'cardio', durationSec: 0, distanceMeters: 500 };
    expect(entryWithoutZeroWork(extraOnly)).toBe(extraOnly);
    expect(entryWithoutZeroWork(distanceOnly)).toBe(distanceOnly);
  });

  // An EMOM minute with no reps entered was still worked, and a HIIT round only counts once finished.
  it('leaves EMOM, HIIT and rest entries alone', () => {
    const entries: SessionEntry[] = [
      { exercise: 'swings', type: 'emom', minutes: [{}, {}] },
      { exercise: 'burpees', type: 'hiit', roundsCompleted: 0 },
      { exercise: 'rest', type: 'rest', restTakenSec: 0 },
    ];
    for (const entry of entries) expect(entryWithoutZeroWork(entry)).toBe(entry);
  });
});

describe('withoutZeroWork', () => {
  // Reference-stable on purpose: `loggedSessions` maps every session through this on each store update.
  it('returns the same session when there is nothing to remove', () => {
    const session = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 6, restTakenSec: 0 }] }],
    });
    expect(withoutZeroWork(session)).toBe(session);
  });

  it('removes zero sets, then entries left empty by that', () => {
    const session = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        { exercise: 'pullups', type: 'reps', sets: [{ reps: 0, restTakenSec: 0 }] },
        { exercise: 'dips', type: 'reps', sets: [{ reps: 8, restTakenSec: 0 }] },
      ],
    });
    expect(withoutZeroWork(session).entries).toEqual([
      { exercise: 'dips', type: 'reps', sets: [{ reps: 8, restTakenSec: 0 }] },
    ]);
  });
});

describe('hasLoggedWork', () => {
  // A workout whose every exercise was skipped still logs its rest blocks, and that isn't a training day.
  it('does not count standalone rests as work', () => {
    const restOnly = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [{ exercise: 'rest', type: 'rest', restTakenSec: 60 }],
    });
    expect(hasLoggedWork(restOnly)).toBe(false);
    expect(hasLoggedWork(aSession({ startedAt: '2026-07-24T09:00:00.000Z' }))).toBe(false);
  });
});
