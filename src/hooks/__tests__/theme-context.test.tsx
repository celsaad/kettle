import { act, renderHook } from '@testing-library/react-native';
import type { ReactNode } from 'react';

import { ThemeOverrideProvider, useAppTheme } from '@/hooks/theme-context';
import { usePreferencesStore } from '@/state/preferences-store';

/**
 * What this pins is the *source* of the appearance preference, not the light/dark maths.
 *
 * The preference used to be `useState` inside the provider, which meant it reset to `system` on every
 * relaunch — the app could not remember a pinned scheme. It now reads from `preferences-store`, so the
 * cases worth holding are that a stored choice is already in effect on the first render (a value
 * arriving one render late would paint the wrong scheme and then swap it), that `system` still defers
 * to the device, and that choosing a scheme writes through to disk rather than staying in memory.
 */
const mockSave = jest.fn();
jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: jest.fn().mockResolvedValue(null),
  savePreferences: (preferences: unknown) => mockSave(preferences),
}));

const mockColorScheme = jest.fn<'light' | 'dark', []>();
jest.mock('@/hooks/use-color-scheme', () => ({
  useColorScheme: () => mockColorScheme(),
}));

function wrapper({ children }: { children: ReactNode }) {
  return <ThemeOverrideProvider>{children}</ThemeOverrideProvider>;
}

beforeEach(() => {
  mockSave.mockReset().mockResolvedValue(true);
  mockColorScheme.mockReset().mockReturnValue('light');
});

/**
 * The regression. `status: 'ready'` stands in for the hydration the root layout awaits before this
 * provider ever mounts — so the very first render already has the stored value, and there is no
 * intermediate frame in the wrong scheme to assert against.
 */
it('has a stored scheme in effect on the first render', async () => {
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'dark',
      restDayReminder: false,
      sessionSounds: true,
      backupFolderUri: null,
    },
  });

  const { result } = await renderHook(() => useAppTheme(), { wrapper });

  expect(result.current.preference).toBe('dark');
  expect(result.current.scheme).toBe('dark');
});

// A pinned scheme is a pin: the device disagreeing with it must not win.
it('keeps a pinned scheme when the device is set to the other one', async () => {
  mockColorScheme.mockReturnValue('dark');
  usePreferencesStore.setState({
    status: 'ready',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'light',
      restDayReminder: false,
      sessionSounds: true,
      backupFolderUri: null,
    },
  });

  const { result } = await renderHook(() => useAppTheme(), { wrapper });

  expect(result.current.scheme).toBe('light');
});

it('follows the device while the preference is system', async () => {
  mockColorScheme.mockReturnValue('dark');

  const { result } = await renderHook(() => useAppTheme(), { wrapper });

  expect(result.current.preference).toBe('system');
  expect(result.current.scheme).toBe('dark');
});

it('persists a chosen scheme instead of holding it in memory', async () => {
  const { result } = await renderHook(() => useAppTheme(), { wrapper });

  await act(async () => {
    await result.current.setPreference('dark');
  });

  expect(mockSave).toHaveBeenCalledWith({
    unitSystem: 'metric',
    themePreference: 'dark',
    restDayReminder: false,
    sessionSounds: true,
    backupFolderUri: null,
  });
  expect(result.current.scheme).toBe('dark');
});
