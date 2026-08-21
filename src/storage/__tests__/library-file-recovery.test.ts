const mockLibraryWrite = jest.fn();
const mockQuarantineWrite = jest.fn();
// `mock`-prefixed because `jest.mock`'s factory is hoisted above every declaration in the file and
// may only close over names matching that prefix — see the house rule in AGENTS.md.
let mockLibraryText = '';

jest.mock('@/storage/paths', () => ({
  isFileStorageSupported: true,
  ensureStorageReady: jest.fn(),
  storagePaths: {
    libraryFile: {
      get exists() {
        return true;
      },
      text: () => Promise.resolve(mockLibraryText),
      create: jest.fn(),
      // Called through rather than passed by reference: the factory is hoisted above the `jest.fn()`
      // declarations, so a direct reference captures `undefined` and the write silently vanishes.
      write: (text: string) => mockLibraryWrite(text),
    },
  },
  quarantineFile: () => ({ exists: false, create: jest.fn(), write: (text: string) => mockQuarantineWrite(text) }),
}));

import { loadLibrary } from '@/storage/library-file';
import { parseLibraryYaml } from '@/domain/yaml-mapping';
import { validateConfig } from '@/domain/exercise-form';

/**
 * **Recovering from a library the app can no longer read must not destroy it.**
 *
 * `loadLibrary` reseeds on any parse failure, and it used to do that by writing the seed straight over
 * the file — so the release that added the repeat-count ceilings would have replaced the whole library
 * of anyone holding a `sets: 200000` on their next launch. That file parsed fine before the ceilings
 * and is exactly the file the ceilings were added for.
 *
 * Two behaviours, in order: repair what the new ceilings broke, and quarantine whatever is left.
 */
const libraryWith = (sets: number, extra = '') => `version: 1
exercises:
  - id: bench
    name: Bench
    type: reps
    config: { sets: ${sets}, target_reps_min: 5, rest_sec: 60 }
  - id: plank
    name: Plank
    type: timed_hold
    config: { sets: 3, hold_sec_min: 30, rest_sec: 30 }
workouts: []
programs: []
${extra}`;

/** What the reseed/repair actually persisted, which is the only durable evidence of either. */
function written(): string {
  expect(mockLibraryWrite).toHaveBeenCalled();
  return mockLibraryWrite.mock.calls.at(-1)![0] as string;
}

beforeEach(() => {
  mockLibraryWrite.mockReset();
  mockQuarantineWrite.mockReset();
});

describe('a library the new ceilings reject', () => {
  it('is clamped into range rather than replaced, keeping everything else', async () => {
    mockLibraryText = libraryWith(200_000);
    const result = await loadLibrary();

    if (!result.ok) throw new Error('expected a library');
    const bench = result.library.exercises.find((exercise) => exercise.id === 'bench');
    expect(bench?.type === 'reps' && bench.config.sets).toBe(500);
    // The user's own name and the exercise the ceiling never touched both survive — this is a repair,
    // not a reseed wearing one's clothes.
    expect(bench?.name).toBe('Bench');
    expect(result.library.exercises.map((exercise) => exercise.id)).toEqual(['bench', 'plank']);
  });

  it('persists the repair, so the next launch has nothing left to fix', async () => {
    mockLibraryText = libraryWith(200_000);
    await loadLibrary();

    expect(parseLibraryYaml(written()).ok).toBe(true);
    expect(mockQuarantineWrite).not.toHaveBeenCalled();
  });

  it('shortens an emom whose derived interval count is over, keeping the interval the user wrote', async () => {
    mockLibraryText = `version: 1
exercises:
  - id: emom
    name: EMOM
    type: emom
    config: { interval_sec: 30, total_minutes: 600 }
workouts: []
programs: []
`;
    const result = await loadLibrary();

    if (!result.ok) throw new Error('expected a library');
    const emom = result.library.exercises[0];
    expect(emom.type === 'emom' && emom.config.intervalSec).toBe(30);
    expect(emom.type === 'emom' && emom.config.totalMinutes).toBe(250);
  });

  it('clamps an over-cap circuit too', async () => {
    mockLibraryText = `version: 1
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
        rounds: 9000
        exercises:
          - exercise: a
          - exercise: b
programs: []
`;
    const result = await loadLibrary();

    if (!result.ok) throw new Error('expected a library');
    const block = result.library.workouts[0].blocks[0];
    expect(block.kind === 'circuit' && block.rounds).toBe(500);
  });
});

describe('a library that is broken some other way', () => {
  /**
   * The repair is deliberately narrow — only the constraints the ceilings introduced. Anything else
   * still reseeds, exactly as before, because repairing every possible violation would quietly turn
   * each future schema tightening into a silent rewrite of the user's file.
   */
  it('is not repaired, and is moved aside before the seed replaces it', async () => {
    mockLibraryText = libraryWith(3, '  - id: broken\n');
    const result = await loadLibrary();

    expect(result.ok).toBe(true);
    expect(mockQuarantineWrite).toHaveBeenCalledWith(mockLibraryText);
  });

  it('quarantines a file that is not YAML at all', async () => {
    mockLibraryText = 'exercises: [unclosed';
    await loadLibrary();

    expect(mockQuarantineWrite).toHaveBeenCalledWith('exercises: [unclosed');
  });

  /**
   * The rescue runs on the recovery path, so a failure to save the old file must not also block the
   * reseed that gets the app running again — the user would be left staring at a broken app *and*
   * still have lost nothing they could reach.
   */
  it('still reseeds when the rescue itself fails', async () => {
    mockLibraryText = 'exercises: [unclosed';
    mockQuarantineWrite.mockImplementation(() => {
      throw new Error('no space left on device');
    });

    const result = await loadLibrary();

    expect(result.ok).toBe(true);
    expect(result.ok && result.library.exercises.length).toBeGreaterThan(0);
  });
});

it('leaves a library that parses completely alone', async () => {
  mockLibraryText = libraryWith(5);
  const result = await loadLibrary();

  if (!result.ok) throw new Error('expected a library');
  const bench = result.library.exercises.find((exercise) => exercise.id === 'bench');
  expect(bench?.type === 'reps' && bench.config.sets).toBe(5);
  // No rewrite and no rescue: the file was fine, so nothing touches it.
  expect(mockLibraryWrite).not.toHaveBeenCalled();
  expect(mockQuarantineWrite).not.toHaveBeenCalled();
});

/**
 * The repair's own arithmetic, on an interval that does not divide its block evenly. The first version
 * of the emom repair produced `total_minutes` whose quotient round-tripped to 500.00000000000006, so
 * the repaired library was refused again and the user was reseeded anyway — the exact destructive
 * upgrade this function exists to prevent. The original test used `interval_sec: 30`, which divides
 * exactly and passes either way.
 */
it('repairs an emom at an interval that does not divide evenly, and the result parses', async () => {
  mockLibraryText = `version: 1
exercises:
  - id: emom
    name: EMOM
    type: emom
    config: { interval_sec: 1, total_minutes: 60 }
workouts: []
programs: []
`;
  const result = await loadLibrary();

  if (!result.ok) throw new Error('expected a library');
  const emom = result.library.exercises[0];
  expect(emom.type === 'emom' && emom.config.intervalSec).toBe(1);
  expect(parseLibraryYaml(written()).ok).toBe(true);
  // Repaired, not rescued: reaching quarantine here means the repair produced something still invalid.
  expect(mockQuarantineWrite).not.toHaveBeenCalled();
});

/**
 * The narrow promise, made structural: the repair acts on zod's `too_big` issues, which carry the
 * ceiling that was breached. Anything invalid for another reason produces another issue code and is
 * left exactly as the user wrote it.
 */
it('leaves an in-range fractional total_minutes alone while repairing something else', async () => {
  mockLibraryText = `version: 1
exercises:
  - id: emom
    name: EMOM
    type: emom
    config: { interval_sec: 60, total_minutes: 7.5 }
  - id: bench
    name: Bench
    type: reps
    config: { sets: 200000, target_reps_min: 5, rest_sec: 60 }
workouts: []
programs: []
`;
  const result = await loadLibrary();

  if (!result.ok) throw new Error('expected a library');
  const emom = result.library.exercises.find((exercise) => exercise.id === 'emom');
  // 7.5 is legal — the schema never asked total_minutes to be whole — so the repair must not round it.
  expect(emom?.type === 'emom' && emom.config.totalMinutes).toBe(7.5);
  const bench = result.library.exercises.find((exercise) => exercise.id === 'bench');
  expect(bench?.type === 'reps' && bench.config.sets).toBe(500);
});

it('does not repair a violation the ceilings did not cause', async () => {
  // `sets: 0` is `too_small`, and was refused long before this change — so it quarantines, as it did.
  mockLibraryText = `version: 1
exercises:
  - id: bench
    name: Bench
    type: reps
    config: { sets: 0, target_reps_min: 5, rest_sec: 60 }
workouts: []
programs: []
`;
  await loadLibrary();

  expect(mockQuarantineWrite).toHaveBeenCalled();
});

/**
 * The end-to-end shape of the repair, across every layer that has ever disagreed about an interval
 * count: the repair writes a value, the library loads, and the in-app editor accepts that same value
 * without the user changing anything. Before `emomIntervalCount`, the form refused it.
 */
it('writes an emom the editor will also accept', async () => {
  mockLibraryText = `version: 1
exercises:
  - id: emom
    name: EMOM
    type: emom
    config: { interval_sec: 1, total_minutes: 60 }
workouts: []
programs: []
`;
  const result = await loadLibrary();

  if (!result.ok) throw new Error('expected a library');
  const emom = result.library.exercises[0];
  if (emom.type !== 'emom') throw new Error('expected an emom');

  expect(
    validateConfig('emom', {
      intervalSec: String(emom.config.intervalSec),
      totalMinutes: String(emom.config.totalMinutes),
    }),
  ).toBeNull();
});

/**
 * A load-bearing and non-obvious ordering invariant: the `too_big` loop clamps `total_minutes` to the
 * 1440 ceiling *before* `clampEmomIntervals` reads it, and the emom clamp only fires when the count is
 * still over — which cannot happen above `interval_sec: 172.8`, since 1440 minutes of intervals that
 * long is under 500 of them. So the emom repair can never write a `total_minutes` back over the day
 * ceiling, and the repair never needs a second pass.
 */
it('never repairs an emom into a value over the day ceiling', async () => {
  for (const intervalSec of [1, 60, 172, 173, 500, 3600]) {
    mockLibraryWrite.mockReset();
    mockQuarantineWrite.mockReset();
    mockLibraryText = `version: 1
exercises:
  - id: emom
    name: EMOM
    type: emom
    config: { interval_sec: ${intervalSec}, total_minutes: 99999 }
workouts: []
programs: []
`;
    const result = await loadLibrary();

    if (!result.ok) throw new Error('expected a library');
    const emom = result.library.exercises[0];
    expect(emom.type === 'emom' && emom.config.totalMinutes).toBeLessThanOrEqual(1440);
    // Repaired rather than rescued, at every interval — reaching quarantine means a repair that
    // produced something the schema still refuses.
    expect(mockQuarantineWrite).not.toHaveBeenCalled();
    expect(parseLibraryYaml(written()).ok).toBe(true);
  }
});
