import {
  applyBlockOverride,
  applyExerciseOverride,
  diffBlockOverride,
  diffExerciseOverride,
  parseLibraryYaml,
  parseSessionYaml,
  serializeLibraryYaml,
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
