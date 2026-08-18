import type { SessionEntry } from '@/domain/types';
import { exerciseProgress } from '@/state/selectors/exercise-progress';
import { aSession } from '@/test-support/sessions';

/**
 * The Stats screen's "am I getting stronger" rows.
 *
 * The measure itself is `entryBest`'s decision and is covered by `records.test.ts`; what these pin is
 * the windowing, the two-session floor, the one-kind-per-exercise rule and the ordering — everything
 * this module adds on top.
 */

const NOW = new Date('2026-08-18T12:00:00.000Z');

function loaded(exercise: string, weightKg: number): SessionEntry {
  return { exercise, type: 'reps', sets: [{ reps: 8, weightKg, restTakenSec: 60 }] };
}

function bodyweight(exercise: string, reps: number): SessionEntry {
  return { exercise, type: 'reps', sets: [{ reps, restTakenSec: 60 }] };
}

function hold(exercise: string, holdSec: number): SessionEntry {
  return { exercise, type: 'timed_hold', sets: [{ holdSec, restTakenSec: 30 }] };
}

it('reports the change from the first session in the window to the last', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-04T10:00:00.000Z', entries: [loaded('rdl', 10)] }),
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    4,
    NOW,
  );

  expect(rows).toEqual([
    {
      exerciseId: 'rdl',
      kind: 'heaviestSet',
      points: [10, 11, 12],
      latest: 12,
      delta: 2,
      lastTrainedAt: '2026-08-17T10:00:00.000Z',
    },
  ]);
});

/**
 * Last-minus-first, not best-minus-worst. A single good day inside a plateau is not movement, and a
 * delta that took the peak would report a personal best as ongoing progress forever after.
 */
it('measures the ends of the window, not its high point', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-04T10:00:00.000Z', entries: [loaded('rdl', 10)] }),
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 20)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 10)] }),
    ],
    4,
    NOW,
  );

  expect(rows[0].delta).toBe(0);
  expect(rows[0].latest).toBe(10);
});

it('leaves out sessions older than the window', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-05-01T10:00:00.000Z', entries: [loaded('rdl', 5)] }),
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    4,
    NOW,
  );

  expect(rows[0].points).toEqual([11, 12]);
});

// One session is not a trend. A row reading "+0" beside a single bar says nothing except that the
// screen wanted another row.
it('drops an exercise trained only once in the window', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11), hold('plank', 40)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    4,
    NOW,
  );

  expect(rows.map((row) => row.exerciseId)).toEqual(['rdl']);
});

it('skips a session that was never finished', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11)] }),
      aSession({ startedAt: '2026-08-14T10:00:00.000Z', endedAt: null, entries: [loaded('rdl', 99)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    4,
    NOW,
  );

  expect(rows[0].points).toEqual([11, 12]);
});

/**
 * An exercise that gained a dumbbell partway through has points in two units. Subtracting one from
 * the other would be arithmetic on reps and kilograms at once, so the older kind's points are dropped
 * and the shorter honest trend is reported instead of a longer invented one.
 */
it('reports only the most recent measure when an exercise changed from bodyweight to loaded', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-01T10:00:00.000Z', entries: [bodyweight('squat', 20)] }),
      aSession({ startedAt: '2026-08-08T10:00:00.000Z', entries: [bodyweight('squat', 22)] }),
      aSession({ startedAt: '2026-08-12T10:00:00.000Z', entries: [loaded('squat', 10)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('squat', 14)] }),
    ],
    4,
    NOW,
  );

  expect(rows[0].kind).toBe('heaviestSet');
  expect(rows[0].points).toEqual([10, 14]);
  expect(rows[0].delta).toBe(4);
});

it('holds compete on seconds', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [hold('plank', 40)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [hold('plank', 55)] }),
    ],
    4,
    NOW,
  );

  expect(rows[0]).toMatchObject({ kind: 'longestHold', points: [40, 55], delta: 15 });
});

/**
 * `hiit` and `emom` are bounded by the exercise's own config, so a rise there reports that the
 * workout was edited rather than that more work was done — `entryBest` excludes them and this screen
 * inherits that rather than deciding it again.
 */
it('leaves out interval work, whose numbers are set by the workout', async () => {
  const rows = exerciseProgress(
    [
      aSession({
        startedAt: '2026-08-11T10:00:00.000Z',
        entries: [
          { exercise: 'burpees', type: 'hiit', roundsCompleted: 3 },
          { exercise: 'row', type: 'cardio', durationSec: 600 },
        ],
      }),
      aSession({
        startedAt: '2026-08-17T10:00:00.000Z',
        entries: [
          { exercise: 'burpees', type: 'hiit', roundsCompleted: 5 },
          { exercise: 'row', type: 'cardio', durationSec: 900 },
        ],
      }),
    ],
    4,
    NOW,
  );

  expect(rows).toEqual([]);
});

it('puts the most recently trained exercise first', async () => {
  const rows = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-05T10:00:00.000Z', entries: [hold('plank', 30), loaded('rdl', 10)] }),
      aSession({ startedAt: '2026-08-10T10:00:00.000Z', entries: [hold('plank', 35)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    4,
    NOW,
  );

  expect(rows.map((row) => row.exerciseId)).toEqual(['rdl', 'plank']);
});

it('has nothing to say about an empty log', async () => {
  expect(exerciseProgress([], 4, NOW)).toEqual([]);
});
