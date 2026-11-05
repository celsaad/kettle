import { act } from '@testing-library/react-native';
import { useEffect } from 'react';

/**
 * A stand-in for `expo-router`, shared by the screen tests.
 *
 * It lives in its own module rather than inline in each suite because a `jest.mock` factory is
 * hoisted above every `const` in the file and may not close over one — the fix is `require()` inside
 * the factory, which needs something to require. Every screen test therefore starts with:
 *
 *     jest.mock('expo-router', () => require('@/test-support/expo-router'));
 *
 * and then imports `router` / `setSearchParams` from here directly. Same resolved path, same module
 * instance, so assertions see the calls the screen made.
 *
 * `back`, `push`, `dismissTo` and `replace` are the only navigation calls anywhere in `src/`
 * (`dismissTo` for the two places that have to leave a modal *and* land on a tab: `session.tsx`'s
 * error boundary, and deleting a program; `replace` for the program guide handing off to import,
 * which are both modals and would otherwise stack), and `clearMocks` in the jest config resets them
 * between tests.
 */
export const router = {
  // As the real one: false until navigation is ready, which every test but a cold start's already is.
  canGoBack: jest.fn(() => navigationReady),
  back: jest.fn(() => assertReady()),
  push: jest.fn(() => assertReady()),
  dismissTo: jest.fn(() => assertReady()),
  replace: jest.fn(() => assertReady()),
};

/**
 * Whether expo-router can navigate yet. A link that launches the app mounts its screen before the root
 * navigator is ready, and every navigation call until then throws, as `store.assertIsReady` does; a
 * focus effect waits for it. Ready unless a test says otherwise, and a test that says so resets it.
 */
let navigationReady = true;
const waitingFocusEffects = new Set<() => void>();

function assertReady() {
  if (!navigationReady) throw new Error('Attempted to navigate before mounting the Root Layout component.');
}

/** Makes navigation ready, or not; ready runs the focus effects that were waiting, in its own `act`. */
export async function setNavigationReady(ready: boolean) {
  navigationReady = ready;
  if (!ready) return;
  const waiting = [...waitingFocusEffects];
  waitingFocusEffects.clear();
  await act(async () => {
    for (const effect of waiting) effect();
  });
}

/** Stands in for `useFocusEffect`: runs once navigation is ready, as the real one waits for it. */
export function useFocusEffect(effect: () => void) {
  useEffect(() => {
    if (navigationReady) return effect();
    waitingFocusEffects.add(effect);
    return () => {
      waitingFocusEffects.delete(effect);
    };
  }, [effect]);
}

let searchParams: Record<string, string | undefined> = {};

/** Stands in for the route's URL params — set it before rendering, as navigation would have. */
export function setSearchParams(params: Record<string, string | undefined>) {
  searchParams = params;
}

export const useLocalSearchParams = () => searchParams;

/**
 * Stands in for `usePreventRemove`, which needs a real navigator. It comes from a different module
 * from everything above, so a suite that renders a guarded screen mocks that module as well:
 *
 *     jest.mock('expo-router/react-navigation', () => require('@/test-support/expo-router'));
 *
 * Registers from an effect and clears on unmount, as the real one does, so a guard can't outlive the
 * screen that set it — which is what lets a test assert that finishing lifts it.
 */
let leaveGuard: (() => void) | null = null;

export function usePreventRemove(preventRemove: boolean, callback: () => void) {
  useEffect(() => {
    if (!preventRemove) return;
    leaveGuard = callback;
    return () => {
      if (leaveGuard === callback) leaveGuard = null;
    };
  }, [preventRemove, callback]);
}

/**
 * What back or swipe-down does to the current screen: runs its guard, if one is up, in its own `act`
 * scope. Resolves to whether the screen stopped the removal — `false` means it would have left.
 */
export async function attemptLeave(): Promise<boolean> {
  const guard = leaveGuard;
  if (!guard) return false;
  await act(async () => {
    guard();
  });
  return true;
}
