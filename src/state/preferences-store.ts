import { create } from 'zustand';

import type { Preferences } from '@/domain/preferences';
import type { UnitSystem } from '@/domain/units';
import { deviceUnitSystem } from '@/i18n';
import { loadPreferences, savePreferences } from '@/storage/preferences-file';

type PreferencesStoreState = {
  status: 'idle' | 'loading' | 'ready';
  preferences: Preferences;
  /** Safe to call repeatedly — re-entrant calls while loading are ignored. */
  hydrate: () => Promise<void>;
  /** Resolves false when the write failed; the change still applies for this run. */
  setUnitSystem: (unitSystem: UnitSystem) => Promise<boolean>;
};

/**
 * Unlike `useTipStore`, this one *is* in `_layout.tsx`'s startup gate. It decides how every weight in
 * the app is rendered, so hydrating it after first paint would show a pound user kilograms and then
 * swap the numbers under them. It's one small JSON read next to the library and session files the
 * layout already awaits.
 *
 * The pre-hydration value below is never rendered for that reason; it exists so the store has a shape
 * before `hydrate()` resolves, and so tests that don't hydrate get metric.
 */
export const usePreferencesStore = create<PreferencesStoreState>((set, get) => ({
  status: 'idle',
  preferences: { unitSystem: 'metric' },
  hydrate: async () => {
    if (get().status === 'loading') return;
    set({ status: 'loading' });
    // `null` means the user has never chosen, so follow the device — this is the only layer that can
    // make that call, which is why the file and domain layers deliberately carry no default.
    const stored = await loadPreferences();
    set({ status: 'ready', preferences: stored ?? { unitSystem: deviceUnitSystem() } });
  },
  setUnitSystem: async (unitSystem) => {
    const next = { ...get().preferences, unitSystem };
    // Applied before the write resolves: this redraws every weight on screen, and a control that
    // waited on disk would feel broken. A failed write costs the choice at next launch, nothing more.
    set({ preferences: next, status: 'ready' });
    return savePreferences(next);
  },
}));

/**
 * The display unit is ambient, like the theme — every component that renders a weight needs it, and
 * none of them need anything else from this store. Mirrors `useTheme()` rather than threading a prop
 * down through the session runner and both exercise forms.
 */
export function useUnitSystem(): UnitSystem {
  return usePreferencesStore((state) => state.preferences.unitSystem);
}
