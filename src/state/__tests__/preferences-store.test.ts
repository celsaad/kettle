const mockLoad = jest.fn();
jest.mock('@/storage/preferences-file', () => ({ loadPreferences: () => mockLoad() }));

const mockDeviceUnitSystem = jest.fn();
jest.mock('@/i18n', () => ({
  ...jest.requireActual('@/i18n'),
  deviceUnitSystem: () => mockDeviceUnitSystem(),
}));

import { usePreferencesStore } from '@/state/preferences-store';

beforeEach(() => {
  usePreferencesStore.setState({
    status: 'idle',
    preferences: {
      unitSystem: 'metric',
      themePreference: 'system',
      restDayReminder: false,
      sessionSounds: true,
      backupFolderUri: null,
    },
  });
  mockLoad.mockReset().mockResolvedValue(null);
  mockDeviceUnitSystem.mockReset().mockReturnValue('metric');
});

describe('hydrate', () => {
  // Both fields together, since `loadPreferences` never returns a partial one — the schema defaults
  // any key the stored file predates.
  it('loads the persisted preferences and marks itself ready', async () => {
    mockLoad.mockResolvedValue({ unitSystem: 'imperial', themePreference: 'dark' });

    await usePreferencesStore.getState().hydrate();

    expect(usePreferencesStore.getState().preferences).toEqual({ unitSystem: 'imperial', themePreference: 'dark' });
    expect(usePreferencesStore.getState().status).toBe('ready');
  });

  /**
   * The bug this closes: the appearance choice used to be `useState` in `theme-context.tsx`, so it
   * reset to `system` on every relaunch. Hydration is the whole fix — the provider reads it from here.
   */
  it('restores a pinned scheme rather than falling back to system', async () => {
    mockLoad.mockResolvedValue({ unitSystem: 'metric', themePreference: 'light' });

    await usePreferencesStore.getState().hydrate();

    expect(usePreferencesStore.getState().preferences.themePreference).toBe('light');
  });

  // Unlike units, there is no device-derived seed to fall back to: `system` already *is* "follow the
  // device", so nothing has to be resolved from the locale here.
  it('defaults the theme to system when nothing is stored', async () => {
    await usePreferencesStore.getState().hydrate();

    expect(usePreferencesStore.getState().preferences.themePreference).toBe('system');
  });

  /**
   * The reason the file layer returns `null` instead of a default: "never chosen" has to reach here,
   * where the device's own measurement system can answer it. Collapsing it earlier would hand a US
   * user kilograms on first launch.
   */
  it('falls back to the device measurement system when nothing is stored', async () => {
    mockDeviceUnitSystem.mockReturnValue('imperial');

    await usePreferencesStore.getState().hydrate();

    expect(usePreferencesStore.getState().preferences.unitSystem).toBe('imperial');
  });

  // A stored choice has to beat the device, or switching to metric on a US phone would undo itself
  // at every launch.
  it('prefers a stored choice over the device default', async () => {
    mockLoad.mockResolvedValue({ unitSystem: 'metric', themePreference: 'system' });
    mockDeviceUnitSystem.mockReturnValue('imperial');

    await usePreferencesStore.getState().hydrate();

    expect(usePreferencesStore.getState().preferences.unitSystem).toBe('metric');
  });

  it('ignores a second call while the first is still in flight', async () => {
    let release: (value: null) => void = () => {};
    mockLoad.mockReturnValue(new Promise((resolve) => (release = resolve)));

    const first = usePreferencesStore.getState().hydrate();
    const second = usePreferencesStore.getState().hydrate();
    release(null);
    await Promise.all([first, second]);

    expect(mockLoad).toHaveBeenCalledTimes(1);
  });
});

/**
 * On for a fresh install, asserted through `hydrate` rather than off the initial state: the store's
 * pre-hydration value is never rendered, so only this path proves what someone who has never opened
 * Settings actually gets from the runner.
 */
it('leaves the session sounds on for an install with nothing stored', async () => {
  await usePreferencesStore.getState().hydrate();

  expect(usePreferencesStore.getState().preferences.sessionSounds).toBe(true);
});
