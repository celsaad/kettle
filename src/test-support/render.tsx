import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';

import { ThemeOverrideProvider } from '@/hooks/theme-context';

/**
 * Renders a screen the way the app mounts it: inside the theme provider.
 *
 * Anything containing a `ThemedText` — which is every screen — reads its colours from that context
 * and throws without it, so every screen test needs the wrapper and none of them differ in how.
 *
 * `render` is awaited by callers because RNTL 14 returns a Promise (React 19 made rendering
 * async-aware); forgetting that surfaces as "render function has not been called", which points at
 * the query rather than the missing await.
 */
export function renderScreen(ui: ReactElement) {
  return render(<ThemeOverrideProvider>{ui}</ThemeOverrideProvider>);
}
