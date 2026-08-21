import { dump, load } from 'js-yaml';
import type { z } from 'zod';

import {
  emomIntervalCount,
  MaxRounds,
  rawExerciseSchema,
  rawLibrarySchema,
  rawProgramSchema,
  rawSessionEntrySchema,
  rawSessionSchema,
  rawWorkoutBlockSchema,
  rawWorkoutSchema,
} from '@/domain/schema';
import type {
  CircuitMember,
  Exercise,
  Library,
  Program,
  ProgramOverride,
  ProgramWeek,
  Session,
  SessionEntry,
  Workout,
  WorkoutBlock,
} from '@/domain/types';

type RawExercise = z.infer<typeof rawExerciseSchema>;
type RawWorkout = z.infer<typeof rawWorkoutSchema>;
type RawWorkoutBlock = z.infer<typeof rawWorkoutBlockSchema>;
type RawCircuitBlock = Extract<RawWorkoutBlock, { type: 'circuit' }>;
type RawCircuitMember = RawCircuitBlock['exercises'][number];
type RawProgram = z.infer<typeof rawProgramSchema>;
type RawLibrary = z.infer<typeof rawLibrarySchema>;
type RawSessionEntry = z.infer<typeof rawSessionEntrySchema>;
type RawSession = z.infer<typeof rawSessionSchema>;

/**
 * Why a parse failed, as data rather than a sentence — the import screen renders it, and the two
 * storage callers only log it. `detail` is the one part that stays English in every locale: it's
 * js-yaml's syntax message or zod's issue list, library output rather than prose of ours, so the
 * translated frame goes around it instead of replacing it.
 */
export type ParseError = { kind: 'invalidYaml'; detail: string } | { kind: 'schemaMismatch'; detail: string };

export type ParseResult<T> = { ok: true; data: T } | { ok: false; error: ParseError };

/**
 * Load options shared by both parsers. `maxAliases` is the one that isn't a default: js-yaml ships
 * `maxDepth: 100` and `maxTotalMergeKeys: 10000`, but leaves aliases unbounded at `-1`.
 *
 * An alias bomb (nine anchors each referencing the previous nine times — 437 bytes describing 387
 * million leaves) doesn't detonate on `load`: js-yaml resolves an alias to a *shared reference*, so
 * the result is a cheap DAG. It detonates on whatever walks that DAG without tracking identity. What
 * saves us today is that nothing does — zod refuses an element without recursing into it, and this
 * schema has no recursive types, so exponential fan-out is always refused at depth 1. That's a
 * property of the current schema rather than a guarantee, and the whole point of a bomb is that it
 * costs the author nothing to try. 1000 is far above any hand-written library and far below a bomb.
 */
const LOAD_OPTIONS = { maxAliases: 1000 } as const;

function zodIssueDetail(error: z.ZodError): string {
  return error.issues.map((issue) => `${issue.path.join('.') || '(root)'}: ${issue.message}`).join('; ');
}

// --- Exercise / Workout / Program / Library ---

function exerciseToDomain(raw: RawExercise): Exercise {
  switch (raw.type) {
    case 'hiit':
      return {
        id: raw.id,
        name: raw.name,
        type: 'hiit',
        config: { workSec: raw.config.work_sec, restSec: raw.config.rest_sec, rounds: raw.config.rounds },
        notes: raw.notes,
      };
    case 'emom':
      return {
        id: raw.id,
        name: raw.name,
        type: 'emom',
        config: {
          intervalSec: raw.config.interval_sec,
          totalMinutes: raw.config.total_minutes,
          targetReps: raw.config.target_reps,
        },
        notes: raw.notes,
      };
    case 'amrap':
      return {
        id: raw.id,
        name: raw.name,
        type: 'amrap',
        config: { timeCapSec: raw.config.time_cap_sec },
        notes: raw.notes,
      };
    case 'reps':
      return {
        id: raw.id,
        name: raw.name,
        type: 'reps',
        config: {
          sets: raw.config.sets,
          targetRepsMin: raw.config.target_reps_min,
          targetRepsMax: raw.config.target_reps_max,
          targetWeightKg: raw.config.target_weight,
          restSec: raw.config.rest_sec,
        },
        notes: raw.notes,
      };
    case 'timed_hold':
      return {
        id: raw.id,
        name: raw.name,
        type: 'timed_hold',
        config: {
          sets: raw.config.sets,
          holdSecMin: raw.config.hold_sec_min,
          holdSecMax: raw.config.hold_sec_max,
          restSec: raw.config.rest_sec,
        },
        notes: raw.notes,
      };
    case 'cardio':
      return {
        id: raw.id,
        name: raw.name,
        type: 'cardio',
        config: { durationSec: raw.config.duration_sec, distanceMeters: raw.config.distance_meters },
        notes: raw.notes,
      };
    case 'rest':
      return {
        id: raw.id,
        name: raw.name,
        type: 'rest',
        config: { durationSec: raw.config.duration_sec },
        notes: raw.notes,
      };
  }
}

function exerciseToRaw(exercise: Exercise): RawExercise {
  switch (exercise.type) {
    case 'hiit':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'hiit',
        config: { work_sec: exercise.config.workSec, rest_sec: exercise.config.restSec, rounds: exercise.config.rounds },
        notes: exercise.notes,
      };
    case 'emom':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'emom',
        config: {
          interval_sec: exercise.config.intervalSec,
          total_minutes: exercise.config.totalMinutes,
          target_reps: exercise.config.targetReps,
        },
        notes: exercise.notes,
      };
    case 'amrap':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'amrap',
        config: { time_cap_sec: exercise.config.timeCapSec },
        notes: exercise.notes,
      };
    case 'reps':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'reps',
        config: {
          sets: exercise.config.sets,
          target_reps_min: exercise.config.targetRepsMin,
          target_reps_max: exercise.config.targetRepsMax,
          target_weight: exercise.config.targetWeightKg,
          rest_sec: exercise.config.restSec,
        },
        notes: exercise.notes,
      };
    case 'timed_hold':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'timed_hold',
        config: {
          sets: exercise.config.sets,
          hold_sec_min: exercise.config.holdSecMin,
          hold_sec_max: exercise.config.holdSecMax,
          rest_sec: exercise.config.restSec,
        },
        notes: exercise.notes,
      };
    case 'cardio':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'cardio',
        config: { duration_sec: exercise.config.durationSec, distance_meters: exercise.config.distanceMeters },
        notes: exercise.notes,
      };
    case 'rest':
      return {
        id: exercise.id,
        name: exercise.name,
        type: 'rest',
        config: { duration_sec: exercise.config.durationSec },
        notes: exercise.notes,
      };
  }
}

function circuitMemberToDomain(raw: RawCircuitMember): CircuitMember {
  return { exerciseId: raw.exercise, configOverride: raw.config ? { durationSec: raw.config.duration_sec } : undefined };
}

function circuitMemberToRaw(member: CircuitMember): { exercise: string; config?: { duration_sec?: number } } {
  return {
    exercise: member.exerciseId,
    config: member.configOverride ? { duration_sec: member.configOverride.durationSec } : undefined,
  };
}

function workoutBlockToDomain(raw: RawWorkoutBlock): WorkoutBlock {
  if (raw.type === 'circuit') {
    return {
      kind: 'circuit',
      id: raw.id,
      rounds: raw.rounds,
      restBetweenExercisesSec: raw.rest_between_exercises_sec,
      restBetweenRoundsSec: raw.rest_between_rounds_sec,
      members: raw.exercises.map(circuitMemberToDomain),
    };
  }
  return {
    kind: 'exercise',
    exerciseId: raw.exercise,
    configOverride: raw.config ? { durationSec: raw.config.duration_sec } : undefined,
  };
}

function workoutBlockToRaw(block: WorkoutBlock): RawWorkoutBlock {
  if (block.kind === 'circuit') {
    return {
      type: 'circuit',
      id: block.id,
      rounds: block.rounds,
      rest_between_exercises_sec: block.restBetweenExercisesSec,
      rest_between_rounds_sec: block.restBetweenRoundsSec,
      exercises: block.members.map(circuitMemberToRaw),
    };
  }
  return {
    type: 'exercise',
    exercise: block.exerciseId,
    config: block.configOverride ? { duration_sec: block.configOverride.durationSec } : undefined,
  };
}

function workoutToDomain(raw: RawWorkout): Workout {
  return { id: raw.id, name: raw.name, blocks: raw.blocks.map(workoutBlockToDomain) };
}

function workoutToRaw(workout: Workout): RawWorkout {
  return { id: workout.id, name: workout.name, blocks: workout.blocks.map(workoutBlockToRaw) };
}

type RawProgramOverride = NonNullable<RawProgram['weeks'][number]['overrides']>[number];

function programOverrideToDomain(raw: RawProgramOverride): ProgramOverride {
  if (raw.block) return { kind: 'block', blockId: raw.block, config: raw.config };
  return { kind: 'exercise', exerciseId: raw.exercise as string, config: raw.config };
}

function programOverrideToRaw(override: ProgramOverride): RawProgramOverride {
  if (override.kind === 'block') return { block: override.blockId, config: override.config };
  return { exercise: override.exerciseId, config: override.config };
}

/**
 * Both directions branch on rest, and neither carries the other shape's keys across: a rest week has
 * no `workout` and no `overrides` (the schema refuses both), and a training week never writes
 * `rest_day`. Emitting `rest_day: false` on every training week would rewrite every program in the
 * user's file the first time the app saved one, for no meaning.
 */
function programWeekToDomain(raw: RawProgram['weeks'][number]): ProgramWeek {
  if (raw.rest_day === true) return { week: raw.week, day: raw.day, restDay: true, notes: raw.notes };
  return {
    week: raw.week,
    day: raw.day,
    // The schema requires `workout` on every week that isn't a rest day, which is the branch above —
    // but zod can't express that back into the inferred type, so it stays optional here. `?? ''`
    // rather than a cast: the guarantee lives in a refinement two files away, and if that ever
    // loosens, an empty id degrades to the same "every week needs a workout" the editor already
    // reports, where an `undefined` would reach the domain claiming to be a string.
    workoutId: raw.workout ?? '',
    notes: raw.notes,
    overrides: raw.overrides?.map(programOverrideToDomain),
  };
}

function programWeekToRaw(week: ProgramWeek): RawProgram['weeks'][number] {
  if (week.restDay) return { week: week.week, day: week.day, rest_day: true, notes: week.notes };
  return {
    week: week.week,
    day: week.day,
    workout: week.workoutId,
    notes: week.notes,
    overrides: week.overrides?.map(programOverrideToRaw),
  };
}

function programToDomain(raw: RawProgram): Program {
  return { id: raw.id, name: raw.name, weeks: raw.weeks.map(programWeekToDomain) };
}

function programToRaw(program: Program): RawProgram {
  return { id: program.id, name: program.name, weeks: program.weeks.map(programWeekToRaw) };
}

function libraryToDomain(raw: RawLibrary): Library {
  return {
    version: raw.version,
    exercises: raw.exercises.map(exerciseToDomain),
    workouts: raw.workouts.map(workoutToDomain),
    programs: raw.programs.map(programToDomain),
  };
}

function libraryToRaw(library: Library): RawLibrary {
  return {
    version: library.version,
    exercises: library.exercises.map(exerciseToRaw),
    workouts: library.workouts.map(workoutToRaw),
    programs: library.programs.map(programToRaw),
  };
}

export function parseLibraryYaml(text: string): ParseResult<Library> {
  let parsed: unknown;
  try {
    parsed = load(text, LOAD_OPTIONS);
  } catch (error) {
    return { ok: false, error: { kind: 'invalidYaml', detail: (error as Error).message } };
  }
  const result = rawLibrarySchema.safeParse(parsed);
  if (!result.success) return { ok: false, error: { kind: 'schemaMismatch', detail: zodIssueDetail(result.error) } };
  return { ok: true, data: libraryToDomain(result.data) };
}

export function serializeLibraryYaml(library: Library): string {
  return dump(libraryToRaw(library), { noRefs: true, sortKeys: false });
}

/**
 * Clamps a library that predates the repeat-count ceilings back into range, returning the rewritten
 * YAML — or null if there was nothing to repair, or nothing parseable to repair it in.
 *
 * **This exists because adding the ceilings is otherwise a destructive upgrade.** A file holding
 * `sets: 200000` parsed cleanly before them and does not after, and `loadLibrary` responds to a parse
 * failure by reseeding — so the release that fixes the unstartable workout would have replaced the
 * user's entire library with the starter one on next launch. That is a worse outcome than the bug.
 *
 * **Driven by the schema's own complaints rather than by a list of fields.** The first version walked
 * the document clamping `sets`, `rounds` and `total_minutes` by name, which made it a fourth
 * hand-maintained copy of `schema.ts` — the exact thing that has cost this codebase the most. Zod
 * reports an out-of-range value as a `too_big` issue carrying both the `path` and the `maximum`, which
 * is everything a repair needs, so the ceiling is read off the schema at the moment it is violated and
 * a ceiling added later is handled without touching this function.
 *
 * That also makes the narrow promise structural instead of careful: **only `too_big` is repaired.** A
 * value that was already invalid before the ceilings — a zero, a fraction where the schema wants an
 * integer, a missing field — produces some other issue code, is left alone, and still goes to
 * quarantine. Repairing those too would quietly turn every future schema tightening into a silent
 * rewrite of the user's data, which is a far larger promise than "the ceilings we just added won't
 * cost you your library".
 */
export function repairLibraryBounds(text: string): string | null {
  let doc: unknown;
  try {
    doc = load(text, LOAD_OPTIONS);
  } catch {
    // A syntax error, not an out-of-range value — there is no document to walk.
    return null;
  }

  const result = rawLibrarySchema.safeParse(doc);
  if (result.success) return null;

  let changed = false;
  for (const issue of result.error.issues) {
    if (issue.code !== 'too_big') continue;
    if (setAtPath(doc, issue.path, Number(issue.maximum))) changed = true;
  }
  if (clampEmomIntervals(doc)) changed = true;

  return changed ? dump(doc, { noRefs: true, sortKeys: false }) : null;
}

/** Writes `value` at a zod issue path. Reports false if the path doesn't lead anywhere writable. */
function setAtPath(doc: unknown, path: readonly PropertyKey[], value: number): boolean {
  if (path.length === 0 || !Number.isFinite(value)) return false;
  let cursor: unknown = doc;
  for (const key of path.slice(0, -1)) {
    if (typeof cursor !== 'object' || cursor === null) return false;
    cursor = (cursor as Record<PropertyKey, unknown>)[key];
  }
  if (typeof cursor !== 'object' || cursor === null) return false;
  const leaf = path[path.length - 1];
  if ((cursor as Record<PropertyKey, unknown>)[leaf] === value) return false;
  (cursor as Record<PropertyKey, unknown>)[leaf] = value;
  return true;
}

/**
 * The one ceiling the loop above cannot see: EMOM's interval count is *derived*, so it is a refinement
 * rather than a `.max()` and carries no `maximum` to read. Handled explicitly, and deliberately the
 * only such case — if a second derived rule ever appears, it belongs here beside this one rather than
 * as a general-purpose document walk.
 *
 * `interval_sec` is the more meaningful of the two fields — a 30-second EMOM is the thing the user
 * wrote down — so the block is shortened rather than the interval stretched.
 */
function clampEmomIntervals(doc: unknown): boolean {
  const root = doc as { exercises?: unknown };
  if (!Array.isArray(root?.exercises)) return false;
  let changed = false;
  for (const raw of root.exercises) {
    const exercise = raw as { type?: unknown; config?: Record<string, unknown> };
    if (exercise?.type !== 'emom' || typeof exercise.config !== 'object' || exercise.config === null) continue;
    const intervalSec = exercise.config.interval_sec;
    const totalMinutes = exercise.config.total_minutes;
    if (typeof intervalSec !== 'number' || typeof totalMinutes !== 'number' || intervalSec <= 0) continue;
    if (emomIntervalCount(intervalSec, totalMinutes) <= MaxRounds) continue;
    // Checked against the same helper rather than trusted: this is the inverse of a floored division,
    // and the value it produces is the one the whole repair has to survive being re-parsed with.
    let minutes = (intervalSec * MaxRounds) / 60;
    if (emomIntervalCount(intervalSec, minutes) > MaxRounds) minutes = (intervalSec * (MaxRounds - 1)) / 60;
    exercise.config.total_minutes = minutes;
    changed = true;
  }
  return changed;
}

/**
 * Applies a program week's per-exercise config override (authored with the same snake_case config
 * keys as the base exercise) on top of the exercise's base library definition, producing the
 * effective exercise for that week. Round-trips through the raw shape so the override keys line up
 * with what's hand-written in the yaml.
 */
export function applyExerciseOverride(exercise: Exercise, config: Record<string, number | string>): Exercise {
  const raw = exerciseToRaw(exercise);
  const mergedConfig = { ...raw.config, ...config } as typeof raw.config;
  return exerciseToDomain({ ...raw, config: mergedConfig } as RawExercise);
}

/**
 * Applies a program week's block-targeted override (e.g. a deload week's `rounds: 2`) on top of a
 * circuit block's own params. No-ops for a plain `exercise` block — those are addressed via
 * applyExerciseOverride instead, since their only "config" is the underlying exercise's.
 */
export function applyBlockOverride(block: WorkoutBlock, config: Record<string, number | string>): WorkoutBlock {
  if (block.kind !== 'circuit') return block;
  const raw = workoutBlockToRaw(block);
  const merged = { ...raw, ...config } as typeof raw;
  return workoutBlockToDomain(merged);
}

/**
 * The inverse of applyExerciseOverride: given an exercise's base (library) definition and an edited
 * version of it (same id/type, different config values), returns just the raw/snake_case config keys
 * that actually changed — suitable to store as a ProgramOverride's `config`. Round-trips both through
 * exerciseToRaw so the returned keys line up with what applyExerciseOverride (and hand-written YAML)
 * expect.
 */
export function diffExerciseOverride(base: Exercise, edited: Exercise): Record<string, number | string> {
  const baseConfig = exerciseToRaw(base).config as Record<string, number | string | undefined>;
  const editedConfig = exerciseToRaw(edited).config as Record<string, number | string | undefined>;
  const diff: Record<string, number | string> = {};
  for (const key of Object.keys(editedConfig)) {
    const value = editedConfig[key];
    if (value !== undefined && value !== baseConfig[key]) diff[key] = value;
  }
  return diff;
}

/**
 * The inverse of applyBlockOverride: given a circuit block's base params and an edited version, returns
 * just the raw/snake_case keys that changed. No-ops (returns {}) for a non-circuit block, same guard as
 * applyBlockOverride.
 */
export function diffBlockOverride(base: WorkoutBlock, edited: WorkoutBlock): Record<string, number | string> {
  if (base.kind !== 'circuit' || edited.kind !== 'circuit') return {};
  const baseRaw = workoutBlockToRaw(base);
  const editedRaw = workoutBlockToRaw(edited);
  if (baseRaw.type !== 'circuit' || editedRaw.type !== 'circuit') return {};
  const diff: Record<string, number | string> = {};
  if (editedRaw.rounds !== baseRaw.rounds) diff.rounds = editedRaw.rounds;
  if (
    editedRaw.rest_between_exercises_sec !== undefined &&
    editedRaw.rest_between_exercises_sec !== baseRaw.rest_between_exercises_sec
  ) {
    diff.rest_between_exercises_sec = editedRaw.rest_between_exercises_sec;
  }
  if (
    editedRaw.rest_between_rounds_sec !== undefined &&
    editedRaw.rest_between_rounds_sec !== baseRaw.rest_between_rounds_sec
  ) {
    diff.rest_between_rounds_sec = editedRaw.rest_between_rounds_sec;
  }
  return diff;
}

// --- Sessions ---

function sessionEntryToDomain(raw: RawSessionEntry): SessionEntry {
  switch (raw.type) {
    case 'timed_hold':
      return {
        exercise: raw.exercise,
        type: 'timed_hold',
        sets: raw.sets.map((set) => ({ holdSec: set.hold_sec, restTakenSec: set.rest_taken_sec })),
      };
    case 'reps':
      return {
        exercise: raw.exercise,
        type: 'reps',
        sets: raw.sets.map((set) => ({
          reps: set.reps,
          weightKg: set.weight,
          rpe: set.rpe,
          restTakenSec: set.rest_taken_sec,
        })),
      };
    case 'rest':
      return { exercise: raw.exercise, type: 'rest', restTakenSec: raw.rest_taken_sec };
    case 'hiit':
      return { exercise: raw.exercise, type: 'hiit', roundsCompleted: raw.rounds_completed };
    case 'emom':
      return { exercise: raw.exercise, type: 'emom', minutes: raw.minutes.map((minute) => ({ reps: minute.reps })) };
    case 'amrap':
      return { exercise: raw.exercise, type: 'amrap', roundsCompleted: raw.rounds_completed, extraReps: raw.extra_reps };
    case 'cardio':
      return { exercise: raw.exercise, type: 'cardio', durationSec: raw.duration_sec, distanceMeters: raw.distance_meters };
  }
}

function sessionEntryToRaw(entry: SessionEntry): RawSessionEntry {
  switch (entry.type) {
    case 'timed_hold':
      return {
        exercise: entry.exercise,
        type: 'timed_hold',
        sets: entry.sets.map((set) => ({ hold_sec: set.holdSec, rest_taken_sec: set.restTakenSec })),
      };
    case 'reps':
      return {
        exercise: entry.exercise,
        type: 'reps',
        sets: entry.sets.map((set) => ({
          reps: set.reps,
          weight: set.weightKg,
          rpe: set.rpe,
          rest_taken_sec: set.restTakenSec,
        })),
      };
    case 'rest':
      return { exercise: entry.exercise, type: 'rest', rest_taken_sec: entry.restTakenSec };
    case 'hiit':
      return { exercise: entry.exercise, type: 'hiit', rounds_completed: entry.roundsCompleted };
    case 'emom':
      return { exercise: entry.exercise, type: 'emom', minutes: entry.minutes.map((minute) => ({ reps: minute.reps })) };
    case 'amrap':
      return {
        exercise: entry.exercise,
        type: 'amrap',
        rounds_completed: entry.roundsCompleted,
        extra_reps: entry.extraReps,
      };
    case 'cardio':
      return {
        exercise: entry.exercise,
        type: 'cardio',
        duration_sec: entry.durationSec,
        distance_meters: entry.distanceMeters,
      };
  }
}

function sessionToDomain(raw: RawSession): Session {
  return {
    version: raw.version,
    id: raw.id,
    workout: raw.workout,
    program: raw.program,
    programWeek: raw.program_week,
    programDay: raw.program_day,
    startedAt: raw.started_at,
    endedAt: raw.ended_at,
    entries: raw.entries.map(sessionEntryToDomain),
  };
}

function sessionToRaw(session: Session): RawSession {
  return {
    version: session.version,
    id: session.id,
    workout: session.workout,
    program: session.program,
    program_week: session.programWeek,
    program_day: session.programDay,
    started_at: session.startedAt,
    ended_at: session.endedAt,
    entries: session.entries.map(sessionEntryToRaw),
  };
}

export function parseSessionYaml(text: string): ParseResult<Session> {
  let parsed: unknown;
  try {
    // Session files are app-written and `dump`ed with `noRefs`, so they carry no aliases at all —
    // the limit rides along so neither parser is the one somebody has to notice is missing it.
    parsed = load(text, LOAD_OPTIONS);
  } catch (error) {
    return { ok: false, error: { kind: 'invalidYaml', detail: (error as Error).message } };
  }
  const result = rawSessionSchema.safeParse(parsed);
  if (!result.success) return { ok: false, error: { kind: 'schemaMismatch', detail: zodIssueDetail(result.error) } };
  return { ok: true, data: sessionToDomain(result.data) };
}

export function serializeSessionYaml(session: Session): string {
  return dump(sessionToRaw(session), { noRefs: true, sortKeys: false });
}

/**
 * Every session in one document, for the "export history" path — `expo-sharing` hands over a single
 * URI, so a whole log has to arrive as one file rather than as the directory it's stored in.
 *
 * A wrapper object with a `sessions:` list rather than a stream of `---`-separated documents: it
 * reads back with a plain `load()`, the same as every other file this module produces, and it has
 * somewhere to put `exported_at`. Each element is exactly what `serializeSessionYaml` would write for
 * that session, its own `version` included — the archive's `version` is the container's, not the
 * sessions', and they're deliberately separate numbers.
 *
 * `exportedAt` is a parameter rather than a `new Date()` here so this stays pure like the rest of the
 * module; the caller owns the clock.
 */
export function serializeSessionArchiveYaml(sessions: Session[], exportedAt: string): string {
  return dump(
    { version: 1, exported_at: exportedAt, sessions: sessions.map(sessionToRaw) },
    {
      noRefs: true,
      sortKeys: false,
    },
  );
}
