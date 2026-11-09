import { create } from 'zustand';

import type { Library } from '@/domain/types';
import { loadLibrary } from '@/storage/library-file';

type LibraryStoreState = {
  status: 'idle' | 'loading' | 'ready' | 'error';
  library: Library | null;
  error: string | null;
  hydrate: () => Promise<void>;
  /**
   * Reads the file again after another writer changed it (the native shell's `filesChanged`). Never
   * passes through `loading`, which the root layout answers by unmounting the app.
   *
   * A read that finds nothing usable keeps the library it had: native writes the file atomically, so
   * that is a stray hand on the file mid-run, and the library already loaded is still the user's.
   * Only a first read, with nothing to keep, goes to `error`.
   */
  reload: () => Promise<void>;
};

/**
 * Read-only: native is the library's only writer from the library flip (#245), and says so with
 * `filesChanged('library')`, which calls `reload`. RN's runner was its last reader, and went in the
 * flip release (#308); the store goes with routing (#309).
 */
export const useLibraryStore = create<LibraryStoreState>((set, get) => ({
  status: 'idle',
  library: null,
  error: null,
  hydrate: async () => {
    set({ status: 'loading', error: null });
    await get().reload();
  },
  reload: async () => {
    const result = await loadLibrary();
    if (result.ok) set({ status: 'ready', library: result.library, error: null });
    else if (get().library) console.warn(`Re-reading the library failed; keeping what was loaded: ${result.error}`);
    else set({ status: 'error', error: result.error });
  },
}));
