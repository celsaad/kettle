import { create } from 'zustand';

import type { Preferences } from '@/domain/preferences';
import type { UnitSystem } from '@/domain/units';
import { deviceUnitSystem } from '@/i18n';
import { loadPreferences } from '@/storage/preferences-file';

type PreferencesStoreState = {
  status: 'idle' | 'loading' | 'ready';
  preferences: Preferences;
  /** Safe to call repeatedly — re-entrant calls while loading are ignored. */
  hydrate: () => Promise<void>;
  /**
   * Reads the file again after another writer changed it (the native shell's `filesChanged`). Never
   * passes through `loading`, which the root layout answers by unmounting the app.
   */
  reload: () => Promise<void>;
};

const DEFAULT_PREFERENCES: Omit<Preferences, 'unitSystem'> = {
  themePreference: 'system',
  restDayReminder: false,
  sessionSounds: true,
  backupFolderUri: null,
};

/**
 * Read-only: native Settings is the only writer of `preferences.json` from the preferences flip (#238),
 * and says so with `filesChanged('preferences')`, which calls `reload`. What RN still reads is what the
 * runner and the remaining RN screens render: the unit system, the session sounds and the scheme.
 *
 * In `_layout.tsx`'s startup gate. It decides how every weight in
 * the app is rendered — and, since the appearance preference moved here, what color everything is —
 * so hydrating it after first paint would show a pound user kilograms, or a dark-mode user a light
 * app, and then swap it under them. It's one small JSON read next to the library and session files
 * the layout already awaits.
 *
 * The pre-hydration value below is never rendered for that reason; it exists so the store has a shape
 * before `hydrate()` resolves, and so tests that don't hydrate get metric.
 */
export const usePreferencesStore = create<PreferencesStoreState>((set, get) => ({
  status: 'idle',
  preferences: { unitSystem: 'metric', ...DEFAULT_PREFERENCES },
  hydrate: async () => {
    if (get().status === 'loading') return;
    set({ status: 'loading' });
    await get().reload();
  },
  reload: async () => {
    // `null` means the user has never chosen, so follow the device — this is the only layer that can
    // make that call, which is why the file and domain layers deliberately carry no default.
    const stored = await loadPreferences();
    set({
      status: 'ready',
      preferences: stored ?? { unitSystem: deviceUnitSystem(), ...DEFAULT_PREFERENCES },
    });
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

/** One list's chosen order. Narrow selector so a change on Build doesn't re-render Programs. */
