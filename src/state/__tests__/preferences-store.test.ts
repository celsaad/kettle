const mockLoad = jest.fn();
const mockSave = jest.fn();
jest.mock('@/storage/preferences-file', () => ({
  loadPreferences: () => mockLoad(),
  savePreferences: (preferences: unknown) => mockSave(preferences),
}));

const mockDeviceUnitSystem = jest.fn();
jest.mock('@/i18n', () => ({
  ...jest.requireActual('@/i18n'),
  deviceUnitSystem: () => mockDeviceUnitSystem(),
}));

import { DEFAULT_LIST_SORTS } from '@/domain/preferences';
import { usePreferencesStore } from '@/state/preferences-store';

beforeEach(() => {
  usePreferencesStore.setState({
    status: 'idle',
    preferences: { unitSystem: 'metric', themePreference: 'system', listSort: DEFAULT_LIST_SORTS, restDayReminder: false },
  });
  mockLoad.mockReset().mockResolvedValue(null);
  mockSave.mockReset().mockResolvedValue(true);
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

describe('setUnitSystem', () => {
  it('persists the choice', async () => {
    expect(await usePreferencesStore.getState().setUnitSystem('imperial')).toBe(true);
    expect(mockSave).toHaveBeenCalledWith({
      unitSystem: 'imperial',
      themePreference: 'system',
      listSort: DEFAULT_LIST_SORTS,
      restDayReminder: false,
    });
  });

  /**
   * A failed write must not roll back the change. Every weight on screen has already been redrawn in
   * the new unit; snapping them back would look like the control is broken, when all that's actually
   * lost is the choice surviving a relaunch — which is what the return value is for.
   */
  it('still applies the change when persistence fails, and says so', async () => {
    mockSave.mockResolvedValue(false);

    expect(await usePreferencesStore.getState().setUnitSystem('imperial')).toBe(false);
    expect(usePreferencesStore.getState().preferences.unitSystem).toBe('imperial');
  });
});

describe('setThemePreference', () => {
  it('persists the choice', async () => {
    expect(await usePreferencesStore.getState().setThemePreference('dark')).toBe(true);
    expect(mockSave).toHaveBeenCalledWith({
      unitSystem: 'metric',
      themePreference: 'dark',
      listSort: DEFAULT_LIST_SORTS,
      restDayReminder: false,
    });
  });

  // Every preference shares one file, so each setter writes the *merged* object. Writing only its own
  // field would drop the others on every change.
  it('keeps the other preferences when writing', async () => {
    await usePreferencesStore.getState().setUnitSystem('imperial');
    await usePreferencesStore.getState().setThemePreference('light');

    expect(mockSave).toHaveBeenLastCalledWith({
      unitSystem: 'imperial',
      themePreference: 'light',
      listSort: DEFAULT_LIST_SORTS,
      restDayReminder: false,
    });
  });

  // Same reasoning as units: the whole app has already repainted in the new scheme, and snapping it
  // back would look broken when all that's lost is the choice surviving a relaunch.
  it('still applies the change when persistence fails, and says so', async () => {
    mockSave.mockResolvedValue(false);

    expect(await usePreferencesStore.getState().setThemePreference('dark')).toBe(false);
    expect(usePreferencesStore.getState().preferences.themePreference).toBe('dark');
  });
});

describe('setListSort', () => {
  it('persists the choice', async () => {
    expect(await usePreferencesStore.getState().setListSort('workouts', 'name')).toBe(true);
    expect(usePreferencesStore.getState().preferences.listSort.workouts).toBe('name');
  });

  // The merge above goes one level deeper here: `listSort` is a map of three lists in one field, so a
  // setter that replaced the whole object would reset the two lists the user isn't looking at.
  it('leaves the other lists alone', async () => {
    await usePreferencesStore.getState().setListSort('workouts', 'name');
    await usePreferencesStore.getState().setListSort('exercises', 'recent');

    expect(usePreferencesStore.getState().preferences.listSort).toEqual({
      workouts: 'name',
      programs: 'custom',
      exercises: 'recent',
    });
  });
});

describe('setRestDayReminder', () => {
  // Off unless asked for. A local notification is the one thing this app does that reaches outside
  // itself, and an opt-in that shipped as on would start notifying people who never chose it.
  it('starts off', () => {
    expect(usePreferencesStore.getState().preferences.restDayReminder).toBe(false);
  });

  it('persists the choice alongside the other preferences', async () => {
    expect(await usePreferencesStore.getState().setRestDayReminder(true)).toBe(true);
    expect(mockSave).toHaveBeenCalledWith({
      unitSystem: 'metric',
      themePreference: 'system',
      listSort: DEFAULT_LIST_SORTS,
      restDayReminder: true,
    });
  });

  it('still applies the change when persistence fails, and says so', async () => {
    mockSave.mockResolvedValue(false);

    expect(await usePreferencesStore.getState().setRestDayReminder(true)).toBe(false);
    expect(usePreferencesStore.getState().preferences.restDayReminder).toBe(true);
  });
});
