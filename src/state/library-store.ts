import { create } from 'zustand';

import type { Exercise, Library, Program, Workout } from '@/domain/types';
import { loadLibrary, saveLibrary } from '@/storage/library-file';

type LibraryStoreState = {
  status: 'idle' | 'loading' | 'ready' | 'error';
  library: Library | null;
  error: string | null;
  hydrate: () => Promise<void>;
  /** Persists a whole new library (e.g. the result of an import merge) and updates state. */
  replaceLibrary: (library: Library) => Promise<void>;
  /** Adds or updates (by id) a single exercise and persists the library. */
  saveExercise: (exercise: Exercise) => Promise<void>;
  /**
   * Sets a `reps` exercise's target load, in kilograms, and persists the library. The runner's
   * one-tap adopt writes through here.
   *
   * **Kilograms in, kilograms out, with no display round trip.** The caller has a value straight off
   * a logged set, which is already stored units — sending it through the exercise form's path instead
   * would reintroduce the lossy conversion `previousWeightKg` exists to prevent
   * (`domain/exercise-form.ts`), turning an adopted 100 kg into 100.02 for a pound user.
   *
   * A no-op for an unknown id or a non-`reps` exercise: only `RepsConfig` has a target weight, and the
   * caller is a set row that can't reach the others.
   */
  setTargetWeightKg: (exerciseId: string, weightKg: number) => Promise<void>;
  /** Adds or updates (by id) a single workout and persists the library. */
  saveWorkout: (workout: Workout) => Promise<void>;
  /** Removes a workout by id and persists the library. */
  deleteWorkout: (id: string) => Promise<void>;
  /** Removes an exercise by id and persists the library. */
  deleteExercise: (id: string) => Promise<void>;
  /** Adds or updates (by id) a single program and persists the library. */
  saveProgram: (program: Program) => Promise<void>;
  /** Removes a program by id and persists the library. */
  deleteProgram: (id: string) => Promise<void>;
};

export const useLibraryStore = create<LibraryStoreState>((set, get) => ({
  status: 'idle',
  library: null,
  error: null,
  hydrate: async () => {
    set({ status: 'loading', error: null });
    const result = await loadLibrary();
    if (result.ok) set({ status: 'ready', library: result.library });
    else set({ status: 'error', error: result.error });
  },
  replaceLibrary: async (library) => {
    await saveLibrary(library);
    set({ status: 'ready', library, error: null });
  },
  saveExercise: async (exercise) => {
    const current = get().library;
    if (!current) return;
    const exists = current.exercises.some((candidate) => candidate.id === exercise.id);
    const exercises = exists
      ? current.exercises.map((candidate) => (candidate.id === exercise.id ? exercise : candidate))
      : [...current.exercises, exercise];
    const next = { ...current, exercises };
    await saveLibrary(next);
    set({ library: next });
  },
  setTargetWeightKg: async (exerciseId, weightKg) => {
    const current = get().library;
    const exercise = current?.exercises.find((candidate) => candidate.id === exerciseId);
    if (!current || exercise?.type !== 'reps') return;
    await get().saveExercise({ ...exercise, config: { ...exercise.config, targetWeightKg: weightKg } });
  },
  saveWorkout: async (workout) => {
    const current = get().library;
    if (!current) return;
    const exists = current.workouts.some((candidate) => candidate.id === workout.id);
    const workouts = exists
      ? current.workouts.map((candidate) => (candidate.id === workout.id ? workout : candidate))
      : [...current.workouts, workout];
    const next = { ...current, workouts };
    await saveLibrary(next);
    set({ library: next });
  },
  deleteWorkout: async (id) => {
    const current = get().library;
    if (!current) return;
    const next = { ...current, workouts: current.workouts.filter((workout) => workout.id !== id) };
    await saveLibrary(next);
    set({ library: next });
  },
  deleteExercise: async (id) => {
    const current = get().library;
    if (!current) return;
    const next = { ...current, exercises: current.exercises.filter((exercise) => exercise.id !== id) };
    await saveLibrary(next);
    set({ library: next });
  },
  saveProgram: async (program) => {
    const current = get().library;
    if (!current) return;
    const exists = current.programs.some((candidate) => candidate.id === program.id);
    const programs = exists
      ? current.programs.map((candidate) => (candidate.id === program.id ? program : candidate))
      : [...current.programs, program];
    const next = { ...current, programs };
    await saveLibrary(next);
    set({ library: next });
  },
  deleteProgram: async (id) => {
    const current = get().library;
    if (!current) return;
    const next = { ...current, programs: current.programs.filter((program) => program.id !== id) };
    await saveLibrary(next);
    set({ library: next });
  },
}));

export function findExerciseInLibrary(library: Library | null, id: string): Exercise | undefined {
  return library?.exercises.find((exercise) => exercise.id === id);
}
