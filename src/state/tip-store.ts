import { Platform } from 'react-native';
import { create } from 'zustand';

import { EMPTY_SUPPORTER_STATE, withTipRecorded, type SupporterState, type TipTier } from '@/domain/tip';
import { loadSupporterState, saveSupporterState } from '@/storage/tip-file';

/**
 * Play Billing is native-only. Lives here rather than in `domain/tip.ts` because that layer is pure
 * logic with no platform dependencies, and mirrors `isFileStorageSupported` in `storage/paths.ts`.
 */
export const isTipJarSupported = Platform.OS !== 'web';

type TipStoreState = {
  status: 'idle' | 'loading' | 'ready';
  supporter: SupporterState;
  /** Safe to call repeatedly — re-entrant calls while loading are ignored. */
  hydrate: () => Promise<void>;
  /** Records a completed tip. Resolves false when the write failed, so the UI can say so. */
  recordTip: (tier: TipTier) => Promise<boolean>;
};

/**
 * Deliberately *not* hydrated in `_layout.tsx` alongside the library and session stores. The root
 * layout renders null until every store it awaits is ready, so gating cold start on a file almost
 * no one has would make the app slower to open for a feature nobody asked to see. Screens that need
 * it hydrate on mount instead.
 */
export const useTipStore = create<TipStoreState>((set, get) => ({
  status: 'idle',
  supporter: EMPTY_SUPPORTER_STATE,
  hydrate: async () => {
    if (get().status === 'loading') return;
    set({ status: 'loading' });
    set({ status: 'ready', supporter: await loadSupporterState() });
  },
  recordTip: async (tier) => {
    const next = withTipRecorded(get().supporter, tier, new Date());
    const saved = await saveSupporterState(next);
    // Reflect the tip either way: the user really did pay, and showing "no tip recorded" because a
    // JSON write failed would read as the payment being lost. It just won't survive a restart.
    set({ supporter: next, status: 'ready' });
    return saved;
  },
}));
