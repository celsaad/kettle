import { render } from '@testing-library/react-native';
import { act } from 'react';

import NativeRunnerStub from '@/app/session-native';
import { emitRunnerDone } from '@/hooks/runner-done';
import { attemptLeave, router } from '@/test-support/expo-router';

jest.mock('expo-router', () => require('@/test-support/expo-router'));
jest.mock('expo-router/react-navigation', () => require('@/test-support/expo-router'));

let mockDrawn = true;
jest.mock('@/hooks/brownfield-module', () => ({ drawsNativeRunner: () => mockDrawn }));

beforeEach(() => {
  mockDrawn = true;
  router.canGoBack.mockReturnValue(true);
});

/**
 * The native runner's stub (#302): where the shell draws the runner over it, nothing removes it but
 * the runner's Done; anywhere else nothing would, so it leaves straight away.
 */
describe('under the native runner', () => {
  it('refuses to leave, without asking or going anywhere', async () => {
    await render(<NativeRunnerStub />);

    expect(await attemptLeave()).toBe(true);
    expect(router.back).not.toHaveBeenCalled();
  });

  it("drops its guard and goes back on the runner's Done", async () => {
    await render(<NativeRunnerStub />);

    await act(async () => emitRunnerDone());

    expect(router.back).toHaveBeenCalledTimes(1);
    expect(await attemptLeave()).toBe(false);
  });
});

// Play, or outside the shell: a `kettle://session-native` link reaches the route with nothing to draw
// over it, and a guard here would leave a blank screen that back can't leave.
describe('with no native runner drawn over it', () => {
  it('backs out at once, unguarded', async () => {
    mockDrawn = false;

    await render(<NativeRunnerStub />);

    expect(router.back).toHaveBeenCalledTimes(1);
    expect(await attemptLeave()).toBe(false);
  });

  it('goes to the tabs when a cold-start link left nothing to go back to', async () => {
    mockDrawn = false;
    router.canGoBack.mockReturnValue(false);

    await render(<NativeRunnerStub />);

    expect(router.replace).toHaveBeenCalledWith('/');
    expect(router.back).not.toHaveBeenCalled();
  });
});
