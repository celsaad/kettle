/**
 * The RN app's half of its conversation with the native shell that hosts it
 * (docs/native-migration-plan.md, M2 and M3). The shell holds every message it has for JS until
 * "ready" arrives — messages sent before JS listens are dropped silently, so the order of the calls
 * here is the contract. What comes back is navigation.
 */
const mockCalls: string[] = [];
let mockListener: ((message: Record<string, unknown>) => void) | undefined;

/**
 * A message from the shell. Throws rather than skipping when nothing subscribed, or every "goes
 * nowhere" test below would pass for an app that never listened.
 */
function fromShell(message: Record<string, unknown>) {
  if (!mockListener) throw new Error('Nothing is listening to the shell');
  mockListener(message);
}

function mockBrownfield() {
  return {
    addMessageListener: (listener: (message: Record<string, unknown>) => void) => {
      mockCalls.push('subscribe');
      mockListener = listener;
      return { remove: jest.fn() };
    },
    sendMessage: (message: { type: string }) => mockCalls.push(`send ${message.type}`),
    setNativeBackEnabled: (enabled: boolean) => mockCalls.push(`native back ${enabled}`),
  };
}

jest.mock('expo-brownfield', () => mockBrownfield());
/**
 * Just enough of a root stack to see where a message leaves the user: how many screens sit above the
 * tabs, and whether one of them refuses to leave (the session runner's guard). Inline rather than the
 * shared stand-in, which imports the testing library: resetModules below would re-register its hooks
 * from inside a test.
 */
const mockStack = { above: 0, guarded: false };
const mockNavigate = jest.fn();
jest.mock('expo-router', () => ({
  router: {
    canDismiss: () => mockStack.above > 0,
    // Committed after the call returns, as on a device: read straight after, canDismiss is stale.
    dismissAll: () =>
      queueMicrotask(() => {
        if (!mockStack.guarded) mockStack.above = 0;
      }),
    navigate: (href: string) => mockNavigate(href),
  },
}));

/** Lets the dismissal commit and anything waiting on it run. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

// A fresh copy per test: the module remembers that it has subscribed, as it should once per runtime.
let announceHostReady: () => void;

beforeEach(() => {
  mockCalls.length = 0;
  mockListener = undefined;
  mockStack.above = 0;
  mockStack.guarded = false;
  jest.resetModules();
  ({ announceHostReady } = require('@/hooks/safe-brownfield'));
});

// The missing-module test swaps the factory; this puts the working one back for whatever runs next.
afterEach(() => {
  jest.doMock('expo-brownfield', mockBrownfield);
});

it('subscribes before it tells the shell it is ready', () => {
  announceHostReady();

  expect(mockCalls.indexOf('subscribe')).toBeGreaterThanOrEqual(0);
  expect(mockCalls.indexOf('send ready')).toBeGreaterThan(mockCalls.indexOf('subscribe'));
});

// expo-brownfield throws on import where its native module isn't built in (Expo Go, an older dev
// client). This runs on the way to hiding the splash, so a throw here would leave the app on it.
// Web takes the same path: there is no native module there at all.
it('does nothing, without throwing, where the native module is missing', () => {
  jest.doMock('expo-brownfield', () => {
    throw new Error("Cannot find native module 'ExpoBrownfieldModule'");
  });

  expect(() => announceHostReady()).not.toThrow();
  expect(mockCalls).toEqual([]);
});

// It's called from a layout callback, which fires again on any relayout.
it('subscribes once however often it is called', () => {
  announceHostReady();
  announceHostReady();

  expect(mockCalls.filter((call) => call === 'subscribe')).toHaveLength(1);
});

/**
 * With native back on, the shell would take the first press itself. Off, JS takes back while it has
 * somewhere to go (a tab returns to Workouts first) and hands it to the shell only after.
 */
it('leaves back to JS', () => {
  announceHostReady();

  expect(mockCalls).toContain('native back false');
});

// M3's shell steers the one RN surface it hosts by message (docs/native-migration-plan.md, Q3).
it('goes where a navigate message says', () => {
  announceHostReady();

  fromShell({ type: 'navigate', url: '/history' });

  expect(mockNavigate).toHaveBeenCalledWith('/history');
});

// A malformed message from a shell built against a different contract mustn't send the app to the
// router's not-found screen, or throw out of the native module's listener.
it('ignores a navigate message without a url, and messages it does not know', () => {
  announceHostReady();

  fromShell({ type: 'navigate' });
  fromShell({ type: 'navigate', url: 42 });
  fromShell({ type: 'filesChanged', url: '/history' });

  expect(mockNavigate).not.toHaveBeenCalled();
});

/**
 * Navigating to a tab from a modal or the runner pushes a second copy of the tabs over them rather
 * than returning to the first, and back then walks through the stale screens (seen on a device, #152).
 */
it('clears the screens above the tabs before it navigates', async () => {
  mockStack.above = 2;
  announceHostReady();

  fromShell({ type: 'navigate', url: '/history' });
  await settle();

  expect(mockStack.above).toBe(0);
  expect(mockNavigate).toHaveBeenCalledWith('/history');
});

// The runner's "Finish session?" guard holds the dismissal; navigating anyway would push the tabs
// over a workout still running underneath. The user's answer decides instead.
it('goes nowhere while a screen refuses to leave', async () => {
  mockStack.above = 1;
  mockStack.guarded = true;
  announceHostReady();

  fromShell({ type: 'navigate', url: '/library' });
  await settle();

  expect(mockNavigate).not.toHaveBeenCalled();
});
