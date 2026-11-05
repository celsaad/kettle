import { createContext, ReactNode, useContext, useMemo } from 'react';

import { Colors } from '@/constants/theme';
import type { ThemePreference } from '@/domain/preferences';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { usePreferencesStore } from '@/state/preferences-store';

type Scheme = 'light' | 'dark';

type ThemeContextValue = {
  scheme: Scheme;
  colors: (typeof Colors)[Scheme];
  preference: ThemePreference;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Follows the OS color scheme until Settings → Appearance pins one. Native Settings writes the choice
 * (#238); this only reads it.
 *
 * `preference` replaced a `Scheme | null` override plus a `toggle()` that flipped light↔dark: once
 * flipped, nothing could put it back to following the OS, because "follow" and "currently light"
 * were the same state as far as the toggle could tell. Storing the intent instead of the outcome is
 * what makes the third option expressible.
 *
 * The preference lives in `preferences-store`, not in local state here, so there is no second copy to
 * keep in sync. That store is inside `_layout.tsx`'s startup gate and this provider mounts below it,
 * so the stored value is already hydrated on first render — the app never paints one scheme and then
 * swaps to another.
 */
export function ThemeOverrideProvider({ children }: { children: ReactNode }) {
  const systemScheme = useColorScheme();
  const preference = usePreferencesStore((state) => state.preferences.themePreference);
  const scheme: Scheme = preference === 'system' ? (systemScheme === 'dark' ? 'dark' : 'light') : preference;

  const value = useMemo<ThemeContextValue>(
    () => ({
      scheme,
      colors: Colors[scheme],
      preference,
    }),
    [preference, scheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useAppTheme must be used within ThemeOverrideProvider');
  return context;
}
