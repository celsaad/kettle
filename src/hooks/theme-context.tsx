import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { Platform } from 'react-native';

import { Colors } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';

type Scheme = 'light' | 'dark';

/** What Settings → Appearance offers: pin a scheme, or defer to the OS. */
export type ThemePreference = Scheme | 'system';

type ThemeContextValue = {
  scheme: Scheme;
  colors: (typeof Colors)[Scheme];
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
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
 * Still in-memory only, as the override was — it resets to `system` on relaunch. Persisting it would
 * mean a storage mechanism for one value that has no place in the YAML library the user exports.
 */
export function ThemeOverrideProvider({ children }: { children: ReactNode }) {
  const systemScheme = useColorScheme();
  const [preference, setPreference] = useState<ThemePreference>('system');
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
    [preference, scheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useAppTheme must be used within ThemeOverrideProvider');
  return context;
}
