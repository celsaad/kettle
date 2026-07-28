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
 * `router.back` and `router.push` are the only two navigation calls anywhere in `src/`, and
 * `clearMocks` in the jest config resets them between tests.
 */
export const router = {
  back: jest.fn(),
  push: jest.fn(),
};

let searchParams: Record<string, string | undefined> = {};

/** Stands in for the route's URL params — set it before rendering, as navigation would have. */
export function setSearchParams(params: Record<string, string | undefined>) {
  searchParams = params;
}

export const useLocalSearchParams = () => searchParams;
