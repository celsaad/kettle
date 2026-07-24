import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { Platform } from 'react-native';

import { Colors } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';

type Scheme = 'light' | 'dark';

type ThemeContextValue = {
  scheme: Scheme;
  colors: (typeof Colors)[Scheme];
  toggle: () => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

/** Follows the OS color scheme by default; `toggle` (the moon/sun control on Today) overrides it. */
export function ThemeOverrideProvider({ children }: { children: ReactNode }) {
  const systemScheme = useColorScheme();
  const [override, setOverride] = useState<Scheme | null>(null);
  const scheme: Scheme = override ?? (systemScheme === 'dark' ? 'dark' : 'light');

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
      toggle: () => setOverride(scheme === 'dark' ? 'light' : 'dark'),
    }),
    [scheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useAppTheme must be used within ThemeOverrideProvider');
  return context;
}
