import { create } from 'zustand';

import type { Preferences, ThemePreference } from '@/domain/preferences';
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
  /** Resolves false when the write failed; the change still applies for this run. */
  setThemePreference: (themePreference: ThemePreference) => Promise<boolean>;
};

/**
 * Unlike `useTipStore`, this one *is* in `_layout.tsx`'s startup gate. It decides how every weight in
 * the app is rendered — and, since the appearance preference moved here, what color everything is —
 * so hydrating it after first paint would show a pound user kilograms, or a dark-mode user a light
 * app, and then swap it under them. It's one small JSON read next to the library and session files
 * the layout already awaits.
 *
 * The pre-hydration value below is never rendered for that reason; it exists so the store has a shape
 * before `hydrate()` resolves, and so tests that don't hydrate get metric.
 */
export const usePreferencesStore = create<PreferencesStoreState>((set, get) => {
  /**
   * Applied before the write resolves: these redraw every weight (or the whole app's colors) on
   * screen, and a control that waited on disk would feel broken. A failed write costs the choice at
   * next launch, nothing more — which is what the returned boolean is for.
   */
  const applyAndPersist = async (patch: Partial<Preferences>): Promise<boolean> => {
    const next = { ...get().preferences, ...patch };
    set({ preferences: next, status: 'ready' });
    return savePreferences(next);
  };

  return {
    status: 'idle',
    preferences: { unitSystem: 'metric', themePreference: 'system' },
    hydrate: async () => {
      if (get().status === 'loading') return;
      set({ status: 'loading' });
      // `null` means the user has never chosen, so follow the device — this is the only layer that can
      // make that call, which is why the file and domain layers deliberately carry no default.
      const stored = await loadPreferences();
      set({
        status: 'ready',
        preferences: stored ?? { unitSystem: deviceUnitSystem(), themePreference: 'system' },
      });
    },
    setUnitSystem: (unitSystem) => applyAndPersist({ unitSystem }),
    setThemePreference: (themePreference) => applyAndPersist({ themePreference }),
  };
});

/**
 * The display unit is ambient, like the theme — every component that renders a weight needs it, and
 * none of them need anything else from this store. Mirrors `useTheme()` rather than threading a prop
 * down through the session runner and both exercise forms.
 */
export function useUnitSystem(): UnitSystem {
  return usePreferencesStore((state) => state.preferences.unitSystem);
}
