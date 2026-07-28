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

import { usePreferencesStore } from '@/state/preferences-store';

beforeEach(() => {
  usePreferencesStore.setState({ status: 'idle', preferences: { unitSystem: 'metric' } });
  mockLoad.mockReset().mockResolvedValue(null);
  mockSave.mockReset().mockResolvedValue(true);
  mockDeviceUnitSystem.mockReset().mockReturnValue('metric');
});

describe('hydrate', () => {
  it('loads the persisted preference and marks itself ready', async () => {
    mockLoad.mockResolvedValue({ unitSystem: 'imperial' });

    await usePreferencesStore.getState().hydrate();

    expect(usePreferencesStore.getState().preferences.unitSystem).toBe('imperial');
    expect(usePreferencesStore.getState().status).toBe('ready');
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
    mockLoad.mockResolvedValue({ unitSystem: 'metric' });
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
    expect(mockSave).toHaveBeenCalledWith({ unitSystem: 'imperial' });
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
