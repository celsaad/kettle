import { create } from 'zustand';

import type { Exercise, Library } from '@/domain/types';
import { loadLibrary } from '@/storage/library-file';

type LibraryStoreState = {
  status: 'idle' | 'loading' | 'ready' | 'error';
  library: Library | null;
  error: string | null;
  hydrate: () => Promise<void>;
};

export const useLibraryStore = create<LibraryStoreState>((set) => ({
  status: 'idle',
  library: null,
  error: null,
  hydrate: async () => {
    set({ status: 'loading', error: null });
    const result = await loadLibrary();
    if (result.ok) set({ status: 'ready', library: result.library });
    else set({ status: 'error', error: result.error });
  },
}));

export function findExerciseInLibrary(library: Library | null, id: string): Exercise | undefined {
  return library?.exercises.find((exercise) => exercise.id === id);
}
