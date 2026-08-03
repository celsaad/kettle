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
