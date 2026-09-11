import type { Exercise, SessionEntry } from '@/domain/types';
import { exerciseProgress, type ProgressView } from '@/state/selectors/exercise-progress';
import { anExercise } from '@/test-support/library';
import { aSession } from '@/test-support/sessions';

/**
 * The Stats screen's "am I getting stronger" rows.
 *
 * The measure itself is `entryBest`'s decision and is covered by `records.test.ts`; what these pin is
 * the windowing, the two-session floor, the one-kind-per-exercise rule, the fixed-hold exclusion, the
 * mover/steady split, the ordering and the best-ever mark — everything this module adds on top.
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

function holdExercise(id: string, config: { holdSecMin?: number; holdSecMax?: number }): Exercise {
  return anExercise({ id, type: 'timed_hold', config: { sets: 1, restSec: 30, ...config } } as Partial<Exercise>);
}

/** Every row in render order, for tests about the measure rather than about the split. */
function rowsOf(view: ProgressView) {
  return [...view.movers, ...view.steady];
}

it('reports the change from the first session in the window to the last', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-04T10:00:00.000Z', entries: [loaded('rdl', 10)] }),
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(view).toEqual({
    movers: [
      {
        exerciseId: 'rdl',
        kind: 'heaviestSet',
        points: [10, 11, 12],
        latest: 12,
        delta: 2,
        lastTrainedAt: '2026-08-17T10:00:00.000Z',
        bestEver: true,
        sessions: [
          { at: '2026-08-04T10:00:00.000Z', value: 10, newBest: false },
          { at: '2026-08-11T10:00:00.000Z', value: 11, newBest: true },
          { at: '2026-08-17T10:00:00.000Z', value: 12, newBest: true },
        ],
      },
    ],
    steady: [],
    fixedHoldsLeftOut: 0,
  });
});

/**
 * Last-minus-first, not best-minus-worst. A single good day inside a plateau is not movement, and a
 * delta that took the peak would report a personal best as ongoing progress forever after.
 */
it('measures the ends of the window, not its high point', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-04T10:00:00.000Z', entries: [loaded('rdl', 10)] }),
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 20)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 10)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(view.movers).toEqual([]);
  expect(view.steady[0]).toMatchObject({ delta: 0, latest: 10 });
});

it('leaves out sessions older than the window', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-05-01T10:00:00.000Z', entries: [loaded('rdl', 5)] }),
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(rowsOf(view)[0].points).toEqual([11, 12]);
});

// One session is not a trend. A row reading "+0" beside a single bar says nothing except that the
// screen wanted another row.
it('drops an exercise trained only once in the window', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11), hold('plank', 40)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(rowsOf(view).map((row) => row.exerciseId)).toEqual(['rdl']);
});

it('skips a session that was never finished', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 11)] }),
      aSession({ startedAt: '2026-08-14T10:00:00.000Z', endedAt: null, entries: [loaded('rdl', 99)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(rowsOf(view)[0].points).toEqual([11, 12]);
});

// A workout can log the same exercise in two blocks, which writes two entries. They are one session's
// work, so they make one point — at the better of the two — rather than a two-step trend inside a day.
it('takes one point per session when an exercise is logged twice in it', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 10), loaded('rdl', 12)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 14)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(rowsOf(view)[0].points).toEqual([12, 14]);
});

/**
 * An exercise that gained a dumbbell partway through has points in two units. Subtracting one from
 * the other would be arithmetic on reps and kilograms at once, so the older kind's points are dropped
 * and the shorter honest trend is reported instead of a longer invented one.
 */
it('reports only the most recent measure when an exercise changed from bodyweight to loaded', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-01T10:00:00.000Z', entries: [bodyweight('squat', 20)] }),
      aSession({ startedAt: '2026-08-08T10:00:00.000Z', entries: [bodyweight('squat', 22)] }),
      aSession({ startedAt: '2026-08-12T10:00:00.000Z', entries: [loaded('squat', 10)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('squat', 14)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(view.movers[0]).toMatchObject({ kind: 'heaviestSet', points: [10, 14], delta: 4 });
});

it('holds compete on seconds', async () => {
  const view = exerciseProgress(
    [
      aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [hold('plank', 40)] }),
      aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [hold('plank', 55)] }),
    ],
    [],
    4,
    NOW,
  );

  expect(view.movers[0]).toMatchObject({ kind: 'longestHold', points: [40, 55], delta: 15 });
});

/**
 * `hiit` and `emom` are bounded by the exercise's own config, so a rise there reports that the
 * workout was edited rather than that more work was done — `entryBest` excludes them and this screen
 * inherits that rather than deciding it again.
 */
it('leaves out interval work, whose numbers are set by the workout', async () => {
  const view = exerciseProgress(
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
    [],
    4,
    NOW,
  );

  expect(view).toEqual({ movers: [], steady: [], fixedHoldsLeftOut: 0 });
});

it('has nothing to say about an empty log', async () => {
  expect(exerciseProgress([], [], 4, NOW)).toEqual({ movers: [], steady: [], fixedHoldsLeftOut: 0 });
});

describe('fixed-target holds', () => {
  const twoHolds = (id: string, first: number, second: number) => [
    aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [hold(id, first)] }),
    aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [hold(id, second)] }),
  ];

  /**
   * The runner stops a fixed hold at its target and logs exactly that, so the row could only ever
   * read "no change" — which on a mobility-heavy log was most of the screen. Counted rather than
   * silently dropped, so the screen can say why they aren't there.
   */
  it('are left out, and counted', async () => {
    const view = exerciseProgress(twoHolds('hurdler', 45, 45), [holdExercise('hurdler', { holdSecMin: 45 })], 4, NOW);

    expect(view).toEqual({ movers: [], steady: [], fixedHoldsLeftOut: 1 });
  });

  // A range whose top equals its bottom is a fixed hold spelled differently: the schema and
  // `validateConfig` both accept `hold_sec_max == hold_sec_min`, and the runner ends it in the same
  // place, so it can only ever read "no change" too.
  it('are left out when the range has no width', async () => {
    const view = exerciseProgress(
      twoHolds('hurdler', 45, 45),
      [holdExercise('hurdler', { holdSecMin: 45, holdSecMax: 45 })],
      4,
      NOW,
    );

    expect(view).toEqual({ movers: [], steady: [], fixedHoldsLeftOut: 1 });
  });

  // Ending a stretch early is the one thing a fixed hold's number can record, and it is not "getting
  // weaker" — so the dip goes with the rest rather than becoming the only row these ever produce.
  it('are left out when cut short, too', async () => {
    const view = exerciseProgress(twoHolds('hurdler', 45, 30), [holdExercise('hurdler', { holdSecMin: 45 })], 4, NOW);

    expect(view.movers).toEqual([]);
  });

  it('are counted only once they would have been a row', async () => {
    const view = exerciseProgress(
      [aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [hold('hurdler', 45)] })],
      [holdExercise('hurdler', { holdSecMin: 45 })],
      4,
      NOW,
    );

    expect(view.fixedHoldsLeftOut).toBe(0);
  });

  // A range still has somewhere to go: climbing toward the top of it is the progression it describes.
  it('keep a range hold in', async () => {
    const view = exerciseProgress(
      twoHolds('lsit', 20, 30),
      [holdExercise('lsit', { holdSecMin: 20, holdSecMax: 40 })],
      4,
      NOW,
    );

    expect(view.movers.map((row) => row.exerciseId)).toEqual(['lsit']);
  });

  it('keep a max-effort hold in', async () => {
    const view = exerciseProgress(twoHolds('plank', 40, 55), [holdExercise('plank', {})], 4, NOW);

    expect(view.movers.map((row) => row.exerciseId)).toEqual(['plank']);
  });

  // With nothing to judge it by, it is shown: hiding a row needs a reason, and "no config" isn't one.
  it('keep an exercise that is no longer in the library', async () => {
    const view = exerciseProgress(twoHolds('gone', 45, 45), [], 4, NOW);

    expect(view.steady.map((row) => row.exerciseId)).toEqual(['gone']);
    expect(view.fixedHoldsLeftOut).toBe(0);
  });
});

describe('movers and steady', () => {
  it('splits on whether the exercise changed', async () => {
    const view = exerciseProgress(
      [
        aSession({ startedAt: '2026-08-11T10:00:00.000Z', entries: [loaded('rdl', 10), hold('plank', 30)] }),
        aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 12), hold('plank', 30)] }),
      ],
      [],
      4,
      NOW,
    );

    expect(view.movers.map((row) => row.exerciseId)).toEqual(['rdl']);
    expect(view.steady.map((row) => row.exerciseId)).toEqual(['plank']);
  });

  /**
   * Relative change, so +3 reps on 5 outranks +2 kg on 10 — which is what "most improved" means —
   * and every gain comes before any dip, however large the dip.
   */
  it('ranks gains by relative change, then dips', async () => {
    const view = exerciseProgress(
      [
        aSession({
          startedAt: '2026-08-11T10:00:00.000Z',
          entries: [loaded('rdl', 10), bodyweight('chin-up', 5), loaded('squat', 20), hold('plank', 40)],
        }),
        aSession({
          startedAt: '2026-08-17T10:00:00.000Z',
          entries: [loaded('rdl', 12), bodyweight('chin-up', 8), loaded('squat', 15), hold('plank', 38)],
        }),
      ],
      [],
      4,
      NOW,
    );

    expect(view.movers.map((row) => row.exerciseId)).toEqual(['chin-up', 'rdl', 'squat', 'plank']);
  });

  // With no change to rank by, recency is the order: what you are working on now is what you came to
  // check. Ties break on the id so the order is stable.
  it('orders steady rows most recently trained first', async () => {
    const view = exerciseProgress(
      [
        aSession({ startedAt: '2026-08-05T10:00:00.000Z', entries: [hold('plank', 30), loaded('rdl', 10)] }),
        aSession({ startedAt: '2026-08-10T10:00:00.000Z', entries: [hold('plank', 30)] }),
        aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('rdl', 10)] }),
      ],
      [],
      4,
      NOW,
    );

    expect(view.steady.map((row) => row.exerciseId)).toEqual(['rdl', 'plank']);
  });
});

describe('best ever', () => {
  const chinUps = (...sessions: [string, number][]) =>
    sessions.map(([startedAt, reps]) => aSession({ startedAt, entries: [bodyweight('chin-up', reps)] }));

  it('marks a best set inside the window by beating an earlier session', async () => {
    const view = exerciseProgress(chinUps(['2026-08-04T10:00:00.000Z', 5], ['2026-08-17T10:00:00.000Z', 8]), [], 4, NOW);

    expect(view.movers[0].bestEver).toBe(true);
  });

  it('still marks it when a later session only matches it', async () => {
    const view = exerciseProgress(
      chinUps(['2026-08-04T10:00:00.000Z', 5], ['2026-08-11T10:00:00.000Z', 8], ['2026-08-17T10:00:00.000Z', 8]),
      [],
      4,
      NOW,
    );

    expect(view.movers[0].bestEver).toBe(true);
  });

  // "8 reps · best ever" beside a latest of 8, when the best was 9, would be a false sentence.
  it('does not mark a row that has fallen back from its best', async () => {
    const view = exerciseProgress(
      chinUps(['2026-08-04T10:00:00.000Z', 5], ['2026-08-11T10:00:00.000Z', 9], ['2026-08-17T10:00:00.000Z', 8]),
      [],
      4,
      NOW,
    );

    expect(view.movers[0].bestEver).toBe(false);
  });

  // A first-ever entry beats nothing — `sessionRecords`' rule — so two equal sessions are no record.
  it('does not mark a best nobody beat', async () => {
    const view = exerciseProgress(chinUps(['2026-08-04T10:00:00.000Z', 5], ['2026-08-17T10:00:00.000Z', 5]), [], 4, NOW);

    expect(view.steady[0].bestEver).toBe(false);
  });

  // The best was set before the window; matching it inside the window is a return, not a record.
  it('does not mark a best set before the window and only matched inside it', async () => {
    const view = exerciseProgress(
      chinUps(
        ['2026-05-01T10:00:00.000Z', 8],
        ['2026-05-08T10:00:00.000Z', 12],
        ['2026-08-11T10:00:00.000Z', 10],
        ['2026-08-17T10:00:00.000Z', 12],
      ),
      [],
      4,
      NOW,
    );

    expect(view.movers[0]).toMatchObject({ delta: 2, bestEver: false });
  });

  // Loaded and bodyweight are separate records, so twenty bodyweight reps don't stand in the way of the
  // first loaded best.
  it('judges each measure against its own history', async () => {
    const view = exerciseProgress(
      [
        aSession({ startedAt: '2026-08-01T10:00:00.000Z', entries: [bodyweight('squat', 20)] }),
        aSession({ startedAt: '2026-08-12T10:00:00.000Z', entries: [loaded('squat', 10)] }),
        aSession({ startedAt: '2026-08-17T10:00:00.000Z', entries: [loaded('squat', 14)] }),
      ],
      [],
      4,
      NOW,
    );

    expect(view.movers[0]).toMatchObject({ kind: 'heaviestSet', bestEver: true });
  });
});

/**
 * The per-session detail the exercise progress screen charts. A session sets a new best by the same
 * rule a row's `bestEver` uses — strictly beating everything logged before it, the first-ever entry
 * beating nothing — so the dots on the chart and the mark on the row can't disagree.
 */
describe('sessions', () => {
  const chinUps = (...sessions: [string, number][]) =>
    sessions.map(([startedAt, reps]) => aSession({ startedAt, entries: [bodyweight('chin-up', reps)] }));

  it('marks each session that beat everything before it, and no other', async () => {
    const view = exerciseProgress(
      chinUps(
        ['2026-07-28T10:00:00.000Z', 5],
        ['2026-08-01T10:00:00.000Z', 5],
        ['2026-08-05T10:00:00.000Z', 6],
        ['2026-08-10T10:00:00.000Z', 6],
        ['2026-08-15T10:00:00.000Z', 7],
      ),
      [],
      4,
      NOW,
    );

    expect(view.movers[0].sessions.map((session) => [session.value, session.newBest])).toEqual([
      [5, false],
      [5, false],
      [6, true],
      [6, false],
      [7, true],
    ]);
  });

  // The window trims what is listed, not what counts as beaten: a best from before the window still
  // stands in the way of the sessions inside it.
  it('judges a session against the whole log, not just the window', async () => {
    const view = exerciseProgress(
      chinUps(['2026-05-01T10:00:00.000Z', 12], ['2026-08-11T10:00:00.000Z', 10], ['2026-08-17T10:00:00.000Z', 13]),
      [],
      4,
      NOW,
    );

    expect(view.movers[0].sessions).toEqual([
      { at: '2026-08-11T10:00:00.000Z', value: 10, newBest: false },
      { at: '2026-08-17T10:00:00.000Z', value: 13, newBest: true },
    ]);
  });
});
