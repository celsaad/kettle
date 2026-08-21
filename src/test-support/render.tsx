import { act, render } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import type { Alert, AlertButton } from 'react-native';

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
 *
 * Passed as `wrapper` rather than wrapped inline, so the returned `rerender` keeps the provider.
 * Wrapping inline meant `rerender(<Screen …/>)` replaced the *whole* tree, provider included, and the
 * next `ThemedText` threw "useAppTheme must be used within ThemeOverrideProvider" — which reads like
 * a bug in the component rather than in how the test re-rendered it.
 */
export function renderScreen(ui: ReactElement) {
  return render(ui, { wrapper: ThemeOverrideProvider });
}

type AlertSpy = jest.SpyInstance<ReturnType<typeof Alert.alert>, Parameters<typeof Alert.alert>>;

/**
 * Presses a button on the alert a spied `Alert.alert` was handed.
 *
 * `Alert` is a no-op on web and unrendered here, so its buttons can't be found in the tree — driving
 * a confirm flow means reaching into the call's third argument. That handler writes to the store and
 * re-renders the screen, which is why it needs its own `act` scope; without one React reports an
 * unwrapped update from inside the *store*, naming a file nowhere near the test that caused it.
 */
export async function pressAlertButton(alert: AlertSpy, style: AlertButton['style'], call = 0) {
  const buttons = alert.mock.calls[call]?.[2];
  const button = buttons?.find((candidate) => candidate.style === style);
  if (!button?.onPress) throw new Error(`No "${style}" button on alert call ${call}`);
  await act(async () => {
    await button.onPress!();
  });
}
