import { load } from 'js-yaml';

import {
  applyBlockOverride,
  applyExerciseOverride,
  diffBlockOverride,
  diffExerciseOverride,
  parseLibraryYaml,
  parseSessionYaml,
  serializeLibraryYaml,
  serializeSessionArchiveYaml,
  serializeSessionYaml,
} from '@/domain/yaml-mapping';
import type { Exercise, Library, Session, WorkoutBlock } from '@/domain/types';

/**
 * The highest-risk mapping in the app: two hand-written bidirectional conversions across 7 exercise
 * types and 7 session-entry types, over a file the user is invited to hand-edit. A single mistyped key
 * silently corrupts their library.
 *
 * Round-trip and golden-file tests are complementary and both are needed. A round-trip only proves the
 * two directions agree with *each other* — a symmetric typo (both sides writing `work_secs`) survives
 * it untouched. The golden fixture is what actually pins the on-disk contract.
 */

/** Every exercise type, each with and without its optional fields, so no branch goes unexercised. */
const exercises: Exercise[] = [
  { id: 'hiit-full', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 4 }, notes: 'Fast' },
  { id: 'hiit-bare', name: 'Jumps', type: 'hiit', config: { workSec: 30, restSec: 0, rounds: 3 } },
  { id: 'emom-full', name: 'Clean', type: 'emom', config: { intervalSec: 60, totalMinutes: 10, targetReps: 3 } },
  { id: 'emom-bare', name: 'Snatch', type: 'emom', config: { intervalSec: 90, totalMinutes: 9 } },
  { id: 'amrap', name: 'Grinder', type: 'amrap', config: { timeCapSec: 600 } },
  {
    id: 'reps-full',
    name: 'Bench',
    type: 'reps',
    config: { sets: 5, targetRepsMin: 3, targetRepsMax: 5, targetWeightKg: 80, restSec: 180 },
    notes: 'Brace',
  },
  { id: 'reps-bare', name: 'Push-ups', type: 'reps', config: { sets: 3, targetRepsMin: 12, restSec: 45 } },
  { id: 'hold-full', name: 'L-Sit', type: 'timed_hold', config: { sets: 4, holdSecMin: 15, holdSecMax: 25, restSec: 60 } },
  { id: 'hold-bare', name: 'Plank', type: 'timed_hold', config: { sets: 2, holdSecMin: 45, restSec: 30 } },
  { id: 'cardio-both', name: 'Row', type: 'cardio', config: { durationSec: 480, distanceMeters: 2000 } },
  { id: 'cardio-bare', name: 'Walk', type: 'cardio', config: {} },
  { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 90 } },
];

const library: Library = {
  version: 1,
  exercises,
  workouts: [
    {
      id: 'full',
      name: 'Full',
      blocks: [
        { kind: 'exercise', exerciseId: 'reps-full' },
        { kind: 'exercise', exerciseId: 'rest', configOverride: { durationSec: 120 } },
        {
          kind: 'circuit',
          id: 'finisher',
          rounds: 3,
          restBetweenExercisesSec: 15,
          restBetweenRoundsSec: 60,
          members: [{ exerciseId: 'reps-bare' }, { exerciseId: 'hold-bare', configOverride: { durationSec: 20 } }],
        },
      ],
    },
  ],
  programs: [
    {
      id: 'prog',
      name: 'Prog',
      weeks: [
        { week: 1, workoutId: 'full', notes: 'Baseline' },
        { week: 2, workoutId: 'full', day: 'Monday' },
        { week: 2, day: 'Tuesday', restDay: true, notes: 'Walk, nothing heavy.' },
        {
          week: 3,
          workoutId: 'full',
          overrides: [
            { kind: 'exercise', exerciseId: 'reps-full', config: { sets: 6 } },
            { kind: 'block', blockId: 'finisher', config: { rounds: 2 } },
          ],
        },
      ],
    },
  ],
};

const session: Session = {
  version: 1,
  id: 'sess-1',
  workout: 'full',
  program: 'prog',
  programWeek: 3,
  programDay: 'Monday',
  startedAt: '2026-07-27T09:00:00.000Z',
  endedAt: '2026-07-27T09:45:00.000Z',
  entries: [
    {
      exercise: 'hold-full',
      type: 'timed_hold',
      sets: [
        { holdSec: 20, restTakenSec: 60 },
        { holdSec: 18, restTakenSec: 0 },
      ],
    },
    {
      exercise: 'reps-full',
      type: 'reps',
      sets: [
        { reps: 5, weightKg: 80, rpe: 8, restTakenSec: 180 },
        { reps: 4, restTakenSec: 0 },
      ],
    },
    { exercise: 'hiit-full', type: 'hiit', roundsCompleted: 4 },
    { exercise: 'emom-full', type: 'emom', minutes: [{ reps: 3 }, { reps: 2 }, {}] },
    { exercise: 'amrap', type: 'amrap', roundsCompleted: 7, extraReps: 4 },
    { exercise: 'cardio-both', type: 'cardio', durationSec: 480, distanceMeters: 2000 },
    { exercise: 'rest', type: 'rest', restTakenSec: 90 },
  ],
};

describe('library round-trip', () => {
  it('survives serialize → parse unchanged', () => {
    const result = parseLibraryYaml(serializeLibraryYaml(library));
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.data).toEqual(library);
  });

  it('leaves omitted optional fields undefined', () => {
    const result = parseLibraryYaml(serializeLibraryYaml(library));
    if (!result.ok) throw new Error('expected parse to succeed');
    const bare = result.data.exercises.find((exercise) => exercise.id === 'reps-bare');
    // Asserts the *value*, not key absence: parsing materialises optional keys holding `undefined`
    // rather than omitting them. Harmless — the serializer drops undefined (see idempotency below)
    // and configToStrings skips it — but worth pinning so it stays a representation detail.
    expect(bare?.type === 'reps' && bare.config.targetRepsMax).toBeUndefined();
    expect(result.data.exercises.find((exercise) => exercise.id === 'hiit-bare')?.notes).toBeUndefined();
  });

  // The property that actually matters for a file the user hand-edits and re-saves: a save/load cycle
  // must not churn the file. Undefined-valued optional keys would break this if they serialised as null.
  it('is idempotent — a second serialize produces byte-identical output', () => {
    const first = serializeLibraryYaml(library);
    const reparsed = parseLibraryYaml(first);
    if (!reparsed.ok) throw new Error('expected parse to succeed');
    expect(serializeLibraryYaml(reparsed.data)).toBe(first);
  });
});

describe('session round-trip', () => {
  it('survives serialize → parse unchanged, across every entry type', () => {
    const result = parseSessionYaml(serializeSessionYaml(session));
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.data).toEqual(session);
  });

  it('defaults program tracking to null for a session written before those fields existed', () => {
    const legacy = [
      'version: 1',
      'id: old-session',
      'workout: full',
      "started_at: '2026-01-01T09:00:00.000Z'",
      'ended_at: null',
      'entries: []',
    ].join('\n');
    const result = parseSessionYaml(legacy);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.data.program).toBeNull();
      expect(result.data.programWeek).toBeNull();
      expect(result.data.programDay).toBeNull();
    }
  });
});

describe('session archive', () => {
  const other: Session = { ...session, id: 'session-2', entries: [] };

  // The promise the archive makes: it's a container, not a second format. If these two ever diverge,
  // an exported log stops being the same data the app wrote per session — which is the whole reason
  // the sessions carry their own `version` inside a document that also has one.
  it('holds each session exactly as its own file would', () => {
    const archive = load(serializeSessionArchiveYaml([session, other], '2026-08-02T12:00:00.000Z')) as {
      sessions: unknown[];
    };
    expect(archive.sessions[0]).toEqual(load(serializeSessionYaml(session)));
    expect(archive.sessions[1]).toEqual(load(serializeSessionYaml(other)));
  });

  it('reads back as one plain document, not a stream', () => {
    const archive = load(serializeSessionArchiveYaml([session], '2026-08-02T12:00:00.000Z')) as {
      version: number;
      exported_at: string;
      sessions: unknown[];
    };
    expect(archive.version).toBe(1);
    expect(archive.exported_at).toBe('2026-08-02T12:00:00.000Z');
    expect(archive.sessions).toHaveLength(1);
  });

  it('writes snake_case at the container level too', () => {
    const yaml = serializeSessionArchiveYaml([session], '2026-08-02T12:00:00.000Z');
    expect(yaml).toContain('exported_at:');
    expect(yaml).not.toMatch(/exportedAt/);
  });
});

describe('on-disk key contract', () => {
  // Pins snake_case exactly. A round-trip can't catch a symmetric rename; this can.
  it('writes snake_case keys for every multi-word field', () => {
    const yaml = serializeLibraryYaml(library);
    for (const key of [
      'work_sec',
      'rest_sec',
      'interval_sec',
      'total_minutes',
      'target_reps',
      'time_cap_sec',
      'target_reps_min',
      'target_reps_max',
      'target_weight',
      'hold_sec_min',
      'hold_sec_max',
      'duration_sec',
      'distance_meters',
      'rest_between_exercises_sec',
      'rest_between_rounds_sec',
    ]) {
      expect(yaml).toContain(`${key}:`);
    }
    expect(yaml).not.toMatch(/workSec|targetRepsMin|distanceMeters|restBetweenRoundsSec/);
  });

  it('writes snake_case keys in a session file', () => {
    const yaml = serializeSessionYaml(session);
    for (const key of ['started_at', 'ended_at', 'program_week', 'program_day', 'rest_taken_sec', 'rounds_completed']) {
      expect(yaml).toContain(`${key}:`);
    }
    expect(yaml).not.toMatch(/startedAt|programWeek|restTakenSec|roundsCompleted/);
  });
});

describe('override apply/diff are inverses', () => {
  it('round-trips an exercise config patch', () => {
    const base = exercises.find((exercise) => exercise.id === 'reps-full')!;
    const patch = { sets: 6, target_reps_min: 4 };
    const edited = applyExerciseOverride(base, patch);
    expect(edited.type === 'reps' && edited.config.sets).toBe(6);
    expect(diffExerciseOverride(base, edited)).toEqual(patch);
  });

  it('reports no diff when nothing changed', () => {
    const base = exercises.find((exercise) => exercise.id === 'hold-full')!;
    expect(diffExerciseOverride(base, base)).toEqual({});
  });

  it('round-trips a circuit block patch', () => {
    const base = library.workouts[0].blocks[2];
    const patch = { rounds: 2, rest_between_rounds_sec: 90 };
    const edited = applyBlockOverride(base, patch);
    expect(edited.kind === 'circuit' && edited.rounds).toBe(2);
    expect(diffBlockOverride(base, edited)).toEqual(patch);
  });

  it('yields nothing for a non-circuit block, which has no overridable circuit config', () => {
    const plain: WorkoutBlock = { kind: 'exercise', exerciseId: 'reps-bare' };
    expect(diffBlockOverride(plain, plain)).toEqual({});
  });
});

describe('parse failures', () => {
  it('reports an error rather than throwing on malformed yaml', () => {
    expect(parseLibraryYaml('exercises: [unclosed').ok).toBe(false);
  });

  it('rejects a library whose exercise config violates the schema', () => {
    const bad = serializeLibraryYaml(library).replace('sets: 5', 'sets: 0');
    expect(parseLibraryYaml(bad).ok).toBe(false);
  });

  /**
   * Pins the `maxAliases` load option, which js-yaml leaves off by default. Without it this document
   * parses fine and is refused a step later by zod as a `schemaMismatch`, so asserting `ok === false`
   * would pass either way — the error *kind* is the whole assertion.
   */
  it('refuses a document with more aliases than any hand-written library has', () => {
    const aliases = Array(1001).fill('*a').join(',');
    const result = parseLibraryYaml(`version: 1\na: &a 1\nexercises: [${aliases}]\nworkouts: []\nprograms: []\n`);

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error.kind).toBe('invalidYaml');
  });

  it('accepts a library that uses aliases sparingly, as a hand-written one might', () => {
    const yaml = `version: 1
exercises:
  - id: a
    name: A
    type: rest
    config: &standard-rest { duration_sec: 90 }
  - id: b
    name: B
    type: rest
    config: *standard-rest
workouts: []
programs: []
`;
    expect(parseLibraryYaml(yaml).ok).toBe(true);
  });
});

/**
 * `hold_sec_min` is optional so that "hold as long as you can" is expressible at all — a hold with no
 * target counts up and only Done ends it, the same shape `cardio` has without `duration_sec`.
 */
describe('the three timed_hold target shapes', () => {
  const holdYaml = (config: string) => `
version: 1
exercises:
  - id: hold
    name: Hold
    type: timed_hold
    config:
      sets: 3
${config}
      rest_sec: 60
workouts: []
programs: []
`;

  const configOf = (yaml: string) => {
    const result = parseLibraryYaml(yaml);
    const exercise = result.ok ? result.data.exercises[0] : null;
    return exercise?.type === 'timed_hold' ? exercise.config : null;
  };

  it('accepts no target at all', () => {
    expect(configOf(holdYaml('      # no target'))).toEqual({ sets: 3, restSec: 60 });
  });

  it('accepts a bare minimum', () => {
    expect(configOf(holdYaml('      hold_sec_min: 30'))?.holdSecMin).toBe(30);
  });

  it('accepts a full range', () => {
    expect(configOf(holdYaml('      hold_sec_min: 15\n      hold_sec_max: 25'))?.holdSecMax).toBe(25);
  });

  // A range needs both ends. Allowing a bare maximum would give a fixed target two spellings, one of
  // which reads as a range and isn't one.
  it('rejects a maximum with no minimum', () => {
    expect(parseLibraryYaml(holdYaml('      hold_sec_max: 25')).ok).toBe(false);
  });

  it('still rejects a maximum below the minimum', () => {
    expect(parseLibraryYaml(holdYaml('      hold_sec_min: 30\n      hold_sec_max: 20')).ok).toBe(false);
  });

  // Round-trip: the key has to *vanish* on export, not come back as `hold_sec_min: null`, which the
  // schema would then refuse on the next import.
  it('omits an absent target from the exported yaml', () => {
    const parsed = parseLibraryYaml(holdYaml('      # no target'));
    const yaml = parsed.ok ? serializeLibraryYaml(parsed.data) : '';
    expect(yaml).not.toContain('hold_sec_min');
    expect(parseLibraryYaml(yaml).ok).toBe(true);
  });
});

/**
 * The four rules that make `rest_day` unambiguous. Each is a schema refinement rather than a
 * convention, because each one is a file somebody would otherwise write and not find out about until
 * the app scheduled it wrongly.
 */
describe('rest days in a program', () => {
  const programYaml = (weeks: string) => `
version: 1
exercises:
  - id: pushups
    name: Push-ups
    type: reps
    config: { sets: 3, target_reps_min: 10, rest_sec: 45 }
workouts:
  - id: full
    name: Full
    blocks:
      - type: exercise
        exercise: pushups
programs:
  - id: prog
    name: Prog
    weeks:
${weeks}
`;

  const trainingWeek = '      - week: 1\n        workout: full\n';

  it('accepts a rest week that names no workout', () => {
    const result = parseLibraryYaml(programYaml(`${trainingWeek}      - week: 2\n        rest_day: true\n`));
    expect(result.ok).toBe(true);
    const week = result.ok ? result.data.programs[0].weeks[1] : null;
    expect(week?.restDay).toBe(true);
  });

  it('keeps `day` and `notes` on a rest week', () => {
    const result = parseLibraryYaml(
      programYaml(`${trainingWeek}      - week: 1\n        day: Day 2\n        rest_day: true\n        notes: Walk.\n`),
    );
    const week = result.ok ? result.data.programs[0].weeks[1] : null;
    expect(week?.day).toBe('Day 2');
    expect(week?.notes).toBe('Walk.');
  });

  // The reason rest is spelled with its own key instead of being inferred from a missing `workout`:
  // a dropped or misspelled line has to stay an error.
  it('still refuses a week with neither a workout nor rest_day', () => {
    expect(parseLibraryYaml(programYaml(`${trainingWeek}      - week: 2\n`)).ok).toBe(false);
  });

  it('refuses a rest week that also names a workout', () => {
    expect(
      parseLibraryYaml(programYaml(`${trainingWeek}      - week: 2\n        rest_day: true\n        workout: full\n`)).ok,
    ).toBe(false);
  });

  it('refuses a rest week carrying overrides, which it has nothing to apply to', () => {
    const weeks = `${trainingWeek}      - week: 2\n        rest_day: true\n        overrides:\n          - exercise: pushups\n            config: { sets: 5 }\n`;
    expect(parseLibraryYaml(programYaml(weeks)).ok).toBe(false);
  });

  it('refuses a program with nothing but rest weeks, which could never queue anything', () => {
    expect(parseLibraryYaml(programYaml('      - week: 1\n        rest_day: true\n')).ok).toBe(false);
  });

  it('accepts a written-out `rest_day: false`, which is a training week spelled in full', () => {
    const result = parseLibraryYaml(programYaml('      - week: 1\n        workout: full\n        rest_day: false\n'));
    expect(result.ok).toBe(true);
    expect(result.ok && result.data.programs[0].weeks[0].restDay).toBeFalsy();
  });

  /**
   * Round-trip: a rest week must export without a `workout` key at all. `workout: null` would be
   * refused by the schema on the next import, which is how an export could silently become
   * un-importable.
   */
  it('round-trips a rest week without inventing a workout key', () => {
    const parsed = parseLibraryYaml(
      programYaml(`${trainingWeek}      - week: 2\n        rest_day: true\n        notes: Off.\n`),
    );
    const yaml = parsed.ok ? serializeLibraryYaml(parsed.data) : '';
    expect(yaml).toContain('rest_day: true');
    expect(yaml).not.toContain('workout: null');

    const reparsed = parseLibraryYaml(yaml);
    expect(reparsed.ok).toBe(true);
    expect(reparsed.ok && reparsed.data.programs[0].weeks).toEqual(parsed.ok ? parsed.data.programs[0].weeks : null);
  });

  // A training week must not pick up `rest_day: false` on export: it would rewrite every program in
  // a user's hand-authored file the first time the app saved one, for no meaning.
  it('writes no rest_day key on a training week', () => {
    const parsed = parseLibraryYaml(programYaml(trainingWeek));
    const yaml = parsed.ok ? serializeLibraryYaml(parsed.data) : '';
    expect(yaml).not.toContain('rest_day');
  });
});

/**
 * The ceilings on `sets`, `rounds` and `total_minutes` — see MaxSets in schema.ts. `sets: 200000`
 * passed `int().positive()` and then threw `RangeError` out of the session screen before its first
 * render, on a library that persists, so the workout stayed unstartable until the file was hand-edited.
 */
describe('bounded repeat counts', () => {
  const libraryWithExercise = (type: string, config: Record<string, unknown>): string =>
    [
      'version: 1',
      'exercises:',
      '  - id: a',
      '    name: A',
      `    type: ${type}`,
      '    config:',
      ...Object.entries(config).map(([key, value]) => `      ${key}: ${value}`),
      'workouts: []',
      'programs: []',
      '',
    ].join('\n');

  const repsWith = (sets: number) => libraryWithExercise('reps', { sets, target_reps_min: 5, rest_sec: 60 });
  const holdWith = (sets: number) => libraryWithExercise('timed_hold', { sets, hold_sec_min: 30, rest_sec: 60 });
  const hiitWith = (rounds: number) => libraryWithExercise('hiit', { work_sec: 40, rest_sec: 20, rounds });

  it('refuses a set count past the ceiling, in both types that have one', () => {
    expect(parseLibraryYaml(repsWith(501)).ok).toBe(false);
    expect(parseLibraryYaml(holdWith(501)).ok).toBe(false);
  });

  it('accepts the ceiling itself, so the bound is inclusive', () => {
    expect(parseLibraryYaml(repsWith(500)).ok).toBe(true);
    expect(parseLibraryYaml(holdWith(500)).ok).toBe(true);
    expect(parseLibraryYaml(hiitWith(500)).ok).toBe(true);
  });

  it('refuses a round count past the ceiling', () => {
    expect(parseLibraryYaml(hiitWith(501)).ok).toBe(false);
  });

  it('refuses a circuit block whose rounds are past the ceiling', () => {
    const yaml = `version: 1
exercises:
  - id: a
    name: A
    type: reps
    config: { sets: 3, target_reps_min: 5, rest_sec: 60 }
  - id: b
    name: B
    type: reps
    config: { sets: 3, target_reps_min: 5, rest_sec: 60 }
workouts:
  - id: w
    name: W
    blocks:
      - type: circuit
        rounds: 501
        exercises:
          - exercise: a
          - exercise: b
programs: []
`;
    expect(parseLibraryYaml(yaml).ok).toBe(false);
    expect(parseLibraryYaml(yaml.replace('rounds: 501', 'rounds: 500')).ok).toBe(true);
  });

  /**
   * EMOM's step count is *derived*, so bounding the two fields separately doesn't bound what they
   * produce: both refused pairs below are individually in range and only the product gives them away.
   */
  it('refuses an emom whose interval count is derived past the ceiling', () => {
    expect(parseLibraryYaml(libraryWithExercise('emom', { interval_sec: 1, total_minutes: 60 })).ok).toBe(false);
    expect(parseLibraryYaml(libraryWithExercise('emom', { interval_sec: 0.001, total_minutes: 1 })).ok).toBe(false);
    expect(parseLibraryYaml(libraryWithExercise('emom', { interval_sec: 60, total_minutes: 500 })).ok).toBe(true);
  });

  it('refuses an emom longer than the day-length ceiling on its own', () => {
    expect(parseLibraryYaml(libraryWithExercise('emom', { interval_sec: 300, total_minutes: 1441 })).ok).toBe(false);
  });
});

/**
 * EMOM's interval count is the only *derived* bound in the format, and every bug it has produced came
 * from an interval that doesn't divide its block evenly. Both cases below use one that doesn't, on
 * purpose: the earlier tests all used 30 and 60, which divide exactly and cannot fail either way.
 */
describe('emom intervals that do not divide evenly', () => {
  const emom = (interval_sec: number, total_minutes: number) =>
    `version: 1
exercises:
  - id: e
    name: E
    type: emom
    config: { interval_sec: ${interval_sec}, total_minutes: ${total_minutes} }
workouts: []
programs: []
`;

  /**
   * 500 one-second intervals is `total_minutes: 8.333333333333334`, whose raw quotient round-trips to
   * 500.00000000000006. The refinement compared that quotient, so a legal block was refused for a
   * rounding error thirteen decimal places down — and, worse, it was the value `repairLibraryBounds`
   * produces, so the repair landed back outside the schema and the library was reseeded anyway.
   */
  it('accepts exactly the interval count the runner will build', () => {
    expect(parseLibraryYaml(emom(1, (1 * 500) / 60)).ok).toBe(true);
    expect(parseLibraryYaml(emom(2, (2 * 500) / 60)).ok).toBe(true);
  });

  it('still refuses a block that really is over the count', () => {
    // 60 minutes of one-second intervals is 3600 of them, and no rounding gets that under 500.
    expect(parseLibraryYaml(emom(1, 60)).ok).toBe(false);
  });

  it('accepts a partial trailing interval, which is a normal thing to write', () => {
    // 13⅓ intervals: the runner runs 13 and the last third was never going to happen.
    expect(parseLibraryYaml(emom(45, 10)).ok).toBe(true);
  });
});
