import { Platform } from 'react-native';
import { create } from 'zustand';

import { EMPTY_SUPPORTER_STATE, type SupporterState } from '@/domain/tip';
import { loadSupporterState } from '@/storage/tip-file';

/**
 * The tip jar is the native shell's Support screen on Play Billing (#198), so it exists on Android
 * alone. iOS has no store behind it until the App Store port, and web never had one. Lives here rather
 * than in `domain/tip.ts` because that layer is pure logic with no platform dependencies.
 */
export const isTipJarSupported = Platform.OS === 'android';

type TipStoreState = {
  status: 'idle' | 'loading' | 'ready';
  supporter: SupporterState;
  /**
   * Safe to call repeatedly — re-entrant calls while loading are ignored. Also how RN hears a tip:
   * the shell's Support writes the file and sends `filesChanged('supporter')`, which calls this.
   */
  hydrate: () => Promise<void>;
};

/**
 * Read-only: native is the supporter file's only writer from M4 (docs/native-migration-plan.md), and
 * Settings reads it until it moves too, in M5.
 *
 * Deliberately *not* hydrated in `_layout.tsx` alongside the library and session stores. The root
 * layout renders null until every store it awaits is ready, so gating cold start on a file almost
 * no one has would make the app slower to open for a feature nobody asked to see. Settings hydrates
 * it on mount instead.
 */
export const useTipStore = create<TipStoreState>((set, get) => ({
  status: 'idle',
  supporter: EMPTY_SUPPORTER_STATE,
  hydrate: async () => {
    if (get().status === 'loading') return;
    set({ status: 'loading' });
    set({ status: 'ready', supporter: await loadSupporterState() });
  },
}));
