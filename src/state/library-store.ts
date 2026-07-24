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
  /** Adds or updates (by id) a single workout and persists the library. */
  saveWorkout: (workout: Workout) => Promise<void>;
  /** Removes a workout by id and persists the library. */
  deleteWorkout: (id: string) => Promise<void>;
  /** Removes an exercise by id and persists the library. */
  deleteExercise: (id: string) => Promise<void>;
  /** Adds or updates (by id) a single program and persists the library. */
  saveProgram: (program: Program) => Promise<void>;
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
}));

export function findExerciseInLibrary(library: Library | null, id: string): Exercise | undefined {
  return library?.exercises.find((exercise) => exercise.id === id);
}
