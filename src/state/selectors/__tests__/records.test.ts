import type { Library, Session } from '@/domain/types';
import { personalBestFor, previousSetFor, sessionRecords } from '@/state/selectors/records';
import { aSession } from '@/test-support/sessions';

describe('sessionRecords', () => {
  const exercises: Library['exercises'] = [
    { id: 'bench', name: 'Bench Press', type: 'reps', config: { sets: 3, targetRepsMin: 5, restSec: 120 } },
    { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 8, restSec: 90 } },
    { id: 'plank', name: 'Plank', type: 'timed_hold', config: { sets: 3, holdSecMin: 45, restSec: 60 } },
    { id: 'burpees', name: 'Burpees', type: 'amrap', config: { timeCapSec: 300 } },
  ];

  const benchAt = (startedAt: string, weightKg: number, reps = 5): Session =>
    aSession({
      startedAt,
      id: `sess-${startedAt}-${weightKg}`,
      entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps, weightKg, restTakenSec: 120 }] }],
    });

  it('reports nothing when there is no log to beat', () => {
    expect(sessionRecords(benchAt('2026-07-24T09:00:00.000Z', 100), [], exercises)).toEqual([]);
  });

  /**
   * A record has to beat something. Without this every exercise in a new user's first week reads as a
   * PR, and the badge means nothing by session three.
   */
  it('does not call a first-ever entry a record', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 100);
    const otherExercise = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 12, restTakenSec: 90 }] }],
    });

    expect(sessionRecords(today, [otherExercise], exercises)).toEqual([]);
  });

  // Regression: a `>=` comparison here celebrates repeating last week's top set as progress.
  it('does not call a tie a record', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 100);
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [before], exercises)).toEqual([]);
  });

  it('reports the heaviest set once it beats the log', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 105);
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    const records = sessionRecords(today, [before], exercises);
    expect(records).toHaveLength(1);
    expect(records[0]).toMatchObject({
      kind: 'heaviestSet',
      exerciseId: 'bench',
      exerciseName: 'Bench Press',
      weightKg: 105,
      reps: 5,
    });
  });

  /**
   * Bodyweight sets carry no `weightKg` at all (`commitCurrentStep` writes `|| undefined`), so they
   * compete on reps. Reporting a `heaviestSet` of 0 kg here would be the wrong record and a wrong
   * number.
   */
  it('judges a bodyweight exercise on reps, not on a zero load', () => {
    const today = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'pullups',
          type: 'reps',
          sets: [
            { reps: 14, restTakenSec: 90 },
            { reps: 9, restTakenSec: 90 },
          ],
        },
      ],
    });
    const before = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-before',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 12, restTakenSec: 90 }] }],
    });

    expect(sessionRecords(today, [before], exercises)).toEqual([
      { kind: 'mostReps', exerciseId: 'pullups', exerciseName: 'Pull-ups', reps: 14 },
    ]);
  });

  it('reports the longest hold and the most amrap rounds', () => {
    const today = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        { exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 75, restTakenSec: 60 }] },
        { exercise: 'burpees', type: 'amrap', roundsCompleted: 9 },
      ],
    });
    const before = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-before',
      entries: [
        { exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 60, restTakenSec: 60 }] },
        { exercise: 'burpees', type: 'amrap', roundsCompleted: 8 },
      ],
    });

    expect(sessionRecords(today, [before], exercises).map((record) => record.kind)).toEqual(['longestHold', 'mostRounds']);
  });

  /**
   * `hiit` rounds and `emom` minutes are bounded by the exercise's own config, so "more than last
   * time" reports a workout edit rather than an achievement. Cardio is out until distance and
   * duration have comparability rules of their own.
   */
  it('has no record for hiit, emom or cardio', () => {
    const today = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        { exercise: 'sprints', type: 'hiit', roundsCompleted: 12 },
        { exercise: 'kb', type: 'emom', minutes: [{ reps: 10 }, { reps: 10 }] },
        { exercise: 'row', type: 'cardio', durationSec: 900, distanceMeters: 4000 },
      ],
    });
    const before = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-before',
      entries: [
        { exercise: 'sprints', type: 'hiit', roundsCompleted: 8 },
        { exercise: 'kb', type: 'emom', minutes: [{ reps: 5 }] },
        { exercise: 'row', type: 'cardio', durationSec: 600, distanceMeters: 2000 },
      ],
    });

    expect(sessionRecords(today, [before], exercises)).toEqual([]);
  });

  /**
   * An abandoned session is not part of the log the rest of the app reports on (`exerciseHistory`
   * skips it too), so a heavy set inside one must not be able to suppress a real record.
   */
  it('ignores unfinished sessions when deciding what was beaten', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 105);
    const abandoned = aSession({
      startedAt: '2026-07-20T09:00:00.000Z',
      id: 'sess-abandoned',
      endedAt: null,
      entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 200, restTakenSec: 120 }] }],
    });
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [abandoned, before], exercises)).toHaveLength(1);
  });

  // The caller reads both the finished session and the log from the same store, so the session is in
  // its own `priorSessions`. Left in, every record would be compared against itself and never beat it.
  it('excludes the session being judged from its own comparison', () => {
    const today = benchAt('2026-07-24T09:00:00.000Z', 105);
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [today, before], exercises)).toHaveLength(1);
  });

  // Two blocks of the same exercise are two entries under two member keys. One PR, not two.
  it('reports one record per exercise even when it was logged in two blocks', () => {
    const today = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        { exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 105, restTakenSec: 120 }] },
        { exercise: 'bench', type: 'reps', sets: [{ reps: 3, weightKg: 110, restTakenSec: 120 }] },
      ],
    });
    const before = benchAt('2026-07-17T09:00:00.000Z', 100);

    expect(sessionRecords(today, [before], exercises)).toEqual([
      expect.objectContaining({ kind: 'heaviestSet', weightKg: 110, reps: 3 }),
    ]);
  });

  // Epley across the whole entry: 90 x 8 projects higher than the heavier 100 x 1 single beside it.
  it('estimates the 1RM from the best set, not necessarily the heaviest', () => {
    const today = aSession({
      startedAt: '2026-07-24T09:00:00.000Z',
      entries: [
        {
          exercise: 'bench',
          type: 'reps',
          sets: [
            { reps: 1, weightKg: 100, restTakenSec: 120 },
            { reps: 8, weightKg: 90, restTakenSec: 120 },
          ],
        },
      ],
    });
    const before = benchAt('2026-07-17T09:00:00.000Z', 95);

    const [record] = sessionRecords(today, [before], exercises);
    expect(record).toMatchObject({ kind: 'heaviestSet', weightKg: 100, reps: 1 });
    if (record.kind !== 'heaviestSet') throw new Error('expected a heaviestSet record');
    expect(record.oneRepMaxKg).toBeCloseTo(114, 5);
  });
});

describe('previousSetFor', () => {
  const benchSession = (startedAt: string, sets: { reps: number; weightKg?: number }[]): Session =>
    aSession({
      startedAt,
      id: `sess-${startedAt}`,
      entries: [{ exercise: 'bench', type: 'reps', sets: sets.map((set) => ({ ...set, restTakenSec: 120 })) }],
    });

  it('has nothing to show on an empty log', () => {
    expect(previousSetFor([], 'bench', 1)).toBeNull();
  });

  it('has nothing to show for an exercise never logged', () => {
    expect(previousSetFor([benchSession('2026-07-17T09:00:00.000Z', [{ reps: 5, weightKg: 100 }])], 'squat', 1)).toBeNull();
  });

  // Set 3 shows set 3 of last time, not a summary of the whole entry — the number you are about to
  // try to beat is the one from the same position in the session.
  it('matches on set index', () => {
    const session = benchSession('2026-07-17T09:00:00.000Z', [
      { reps: 8, weightKg: 100 },
      { reps: 7, weightKg: 100 },
      { reps: 5, weightKg: 95 },
    ]);

    expect(previousSetFor([session], 'bench', 3)).toEqual({ kind: 'reps', reps: 5, weightKg: 95 });
  });

  // You did four sets today but only three last time: the honest answer for set 4 is what you were
  // lifting by the end of that session, not nothing at all.
  it('falls back to the last set when the previous entry was shorter', () => {
    const session = benchSession('2026-07-17T09:00:00.000Z', [
      { reps: 8, weightKg: 100 },
      { reps: 6, weightKg: 100 },
    ]);

    expect(previousSetFor([session], 'bench', 4)).toEqual({ kind: 'reps', reps: 6, weightKg: 100 });
  });

  // Absent, not 0 — `commitCurrentStep` writes `weightKg: … || undefined`, and the set row reads the
  // difference to choose between "60 kg × 8" and a bodyweight line.
  it('carries a bodyweight set with no weight rather than a zero one', () => {
    const session = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 12, restTakenSec: 90 }] }],
    });

    expect(previousSetFor([session], 'pullups', 1)).toEqual({ kind: 'reps', reps: 12, weightKg: undefined });
  });

  it('reads a hold set', () => {
    const session = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [
        {
          exercise: 'plank',
          type: 'timed_hold',
          sets: [
            { holdSec: 60, restTakenSec: 60 },
            { holdSec: 45, restTakenSec: 60 },
          ],
        },
      ],
    });

    expect(previousSetFor([session], 'plank', 2)).toEqual({ kind: 'hold', holdSec: 45 });
  });

  // The newest *finished* one. An abandoned session is not part of the log the app reports on, and
  // letting one answer "last time" would quote a set from a workout the user walked out of.
  it('skips unfinished sessions and takes the most recent finished one', () => {
    const abandoned = aSession({
      startedAt: '2026-07-23T09:00:00.000Z',
      id: 'sess-abandoned',
      endedAt: null,
      entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 1, weightKg: 200, restTakenSec: 120 }] }],
    });
    const older = benchSession('2026-07-17T09:00:00.000Z', [{ reps: 5, weightKg: 100 }]);

    expect(previousSetFor([abandoned, older], 'bench', 1)).toEqual({ kind: 'reps', reps: 5, weightKg: 100 });
  });

  it('has nothing to show for interval work, which has no per-set number to carry', () => {
    const session = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'burpees', type: 'amrap', roundsCompleted: 9 }],
    });

    expect(previousSetFor([session], 'burpees', 1)).toBeNull();
  });
});

describe('personalBestFor', () => {
  it('has no best for an exercise never logged', () => {
    expect(personalBestFor([], 'bench')).toEqual({
      heaviestSetKg: undefined,
      mostReps: undefined,
      longestHoldSec: undefined,
    });
  });

  it('reports the highest value across every finished session', () => {
    const sessions = [
      aSession({
        startedAt: '2026-07-24T09:00:00.000Z',
        id: 'a',
        entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 95, restTakenSec: 120 }] }],
      }),
      aSession({
        startedAt: '2026-07-17T09:00:00.000Z',
        id: 'b',
        entries: [{ exercise: 'bench', type: 'reps', sets: [{ reps: 5, weightKg: 105, restTakenSec: 120 }] }],
      }),
    ];

    expect(personalBestFor(sessions, 'bench').heaviestSetKg).toBe(105);
  });

  // The same split entryBest makes: a bodyweight exercise competes on reps, a loaded one on load. The
  // marker and the completion screen have to agree about this, which is why they share the traversal.
  it('keeps the loaded and bodyweight bests apart', () => {
    const session = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      entries: [{ exercise: 'pullups', type: 'reps', sets: [{ reps: 14, restTakenSec: 90 }] }],
    });

    expect(personalBestFor([session], 'pullups')).toEqual({
      heaviestSetKg: undefined,
      mostReps: 14,
      longestHoldSec: undefined,
    });
  });

  it('ignores unfinished sessions', () => {
    const abandoned = aSession({
      startedAt: '2026-07-23T09:00:00.000Z',
      id: 'sess-abandoned',
      endedAt: null,
      entries: [{ exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 300, restTakenSec: 60 }] }],
    });
    const finished = aSession({
      startedAt: '2026-07-17T09:00:00.000Z',
      id: 'sess-finished',
      entries: [{ exercise: 'plank', type: 'timed_hold', sets: [{ holdSec: 60, restTakenSec: 60 }] }],
    });

    expect(personalBestFor([abandoned, finished], 'plank').longestHoldSec).toBe(60);
  });
});
