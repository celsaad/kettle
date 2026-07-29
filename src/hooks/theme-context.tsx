import { createContext, ReactNode, useContext, useEffect, useMemo } from 'react';
import { Platform } from 'react-native';

import { Colors } from '@/constants/theme';
import type { ThemePreference } from '@/domain/preferences';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { usePreferencesStore } from '@/state/preferences-store';

type Scheme = 'light' | 'dark';

type ThemeContextValue = {
  scheme: Scheme;
  colors: (typeof Colors)[Scheme];
  preference: ThemePreference;
  /** Resolves false when the write failed; the change still applies for this run. */
  setPreference: (preference: ThemePreference) => Promise<boolean>;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Follows the OS color scheme until Settings → Appearance pins one.
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
  const setPreference = usePreferencesStore((state) => state.setThemePreference);
  const scheme: Scheme = preference === 'system' ? (systemScheme === 'dark' ? 'dark' : 'light') : preference;

  // Keeps the web page's own background in sync with the active scheme — including a manual
  // override, which global.css's prefers-color-scheme media query can't see (it only tracks system
  // preference). Without this, closing a modal briefly flashes the browser's default white body
  // through the gap before React repaints. global.css's media query still covers the moment before
  // this effect has run at all (first paint, pre-hydration).
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    document.body.style.backgroundColor = Colors[scheme].background;
  }, [scheme]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      scheme,
      colors: Colors[scheme],
      preference,
      setPreference,
    }),
    // `setPreference` is in here where the old `useState` setter didn't need to be: it's now a zustand
    // action, and while those are just as stable, that's a fact about the store rather than one the
    // lint rule can see. Listing it costs nothing and keeps the accepted-warning baseline honest.
    [preference, scheme, setPreference],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useAppTheme must be used within ThemeOverrideProvider');
  return context;
}
