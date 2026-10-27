/**
 * The RN app's half of its conversation with the native shell that hosts it
 * (docs/native-migration-plan.md, M2 and M3). The shell holds every message it has for JS until
 * "ready" arrives — messages sent before JS listens are dropped silently, so the order of the calls
 * here is the contract. What comes back is navigation.
 */
const mockCalls: string[] = [];
let mockListener: ((message: Record<string, unknown>) => void) | undefined;
/** Every message JS sent the shell, whole. */
const mockSent: Record<string, unknown>[] = [];
/** The shell's side of expo-brownfield's shared state. */
const mockShared = new Map<string, unknown>();

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
    sendMessage: (message: { type: string }) => {
      mockCalls.push(`send ${message.type}`);
      mockSent.push(message);
    },
    getSharedStateValue: (key: string) => mockShared.get(key),
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
const mockStack = { screensAboveTabs: 0, guarded: false };
const mockNavigate = jest.fn();
jest.mock('expo-router', () => ({
  router: {
    canDismiss: () => mockStack.screensAboveTabs > 0,
    // Committed after the call returns, as on a device: read straight after, canDismiss is stale.
    dismissAll: () =>
      queueMicrotask(() => {
        if (!mockStack.guarded) mockStack.screensAboveTabs = 0;
      }),
    navigate: (href: string) => mockNavigate(href),
  },
}));

// The stores behind it have their own suite (state/__tests__/files-changed.test.ts); this one only
// checks the message gets there.
const mockOnFilesChanged = jest.fn();
jest.mock('@/state/files-changed', () => ({ onFilesChanged: (kind: unknown) => mockOnFilesChanged(kind) }));

/** Lets the dismissal commit and anything waiting on it run. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

// A fresh copy per test: the module remembers that it has subscribed, as it should once per runtime.
let announceHostReady: () => void;

beforeEach(() => {
  mockCalls.length = 0;
  mockSent.length = 0;
  mockShared.clear();
  mockListener = undefined;
  mockStack.screensAboveTabs = 0;
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

// Once a native screen writes a file, the RN store that reads it has to read it again (M3's
// re-read rule). What a kind means, and an unknown one, is the handler's business.
it('hands a filesChanged message to the store that reads that file', () => {
  announceHostReady();

  fromShell({ type: 'filesChanged', kind: 'library' });

  expect(mockOnFilesChanged).toHaveBeenCalledWith('library');
  expect(mockNavigate).not.toHaveBeenCalled();
});

/**
 * Navigating to a tab from a modal or the runner pushes a second copy of the tabs over them rather
 * than returning to the first, and back then walks through the stale screens (seen on a device, #152).
 */
it('clears the screens above the tabs before it navigates', async () => {
  mockStack.screensAboveTabs = 2;
  announceHostReady();

  fromShell({ type: 'navigate', url: '/history' });
  await settle();

  expect(mockStack.screensAboveTabs).toBe(0);
  expect(mockNavigate).toHaveBeenCalledWith('/history');
});

// The runner's "Finish session?" guard holds the dismissal; navigating anyway would push the tabs
// over a workout still running underneath. The user's answer decides instead.
it('goes nowhere while a screen refuses to leave', async () => {
  mockStack.screensAboveTabs = 1;
  mockStack.guarded = true;
  announceHostReady();

  fromShell({ type: 'navigate', url: '/library' });
  await settle();

  expect(mockNavigate).not.toHaveBeenCalled();
});

// M3's tab bar is native, and has to follow RN wherever RN goes by itself: a delete's dismissTo, the
// runner's exit, a modal covering the tabs.
it('reports each route to the shell', () => {
  const { reportRoute } = require('@/hooks/safe-brownfield');

  reportRoute('/settings');

  expect(mockSent).toEqual([{ type: 'route', pathname: '/settings' }]);
});

it('reports nothing, without throwing, where the native module is missing', () => {
  jest.doMock('expo-brownfield', () => {
    throw new Error("Cannot find native module 'ExpoBrownfieldModule'");
  });
  const { reportRoute } = require('@/hooks/safe-brownfield');

  expect(() => reportRoute('/')).not.toThrow();
  expect(mockSent).toEqual([]);
});

/**
 * Hosted by the shell, RN's own bar would be a second one, and RN's tab back would move between tabs
 * inside RN, where the shell's bar can't own the transition. The shell says so before JS starts, so
 * the first frame is already right.
 */
it('hides its tab bar and leaves tab back to the shell when the shell draws the bar', () => {
  mockShared.set('nativeTabBar', true);
  const { hostedTabOptions } = require('@/hooks/safe-brownfield');

  expect(hostedTabOptions()).toEqual({ hidden: true, backBehavior: 'none' });
});

// Every build without the shell: iOS, web, Expo Go, and a prebuilt Android dev build, which links the
// same native module but whose host never sets the key.
it('keeps its own tab bar anywhere else', () => {
  const { hostedTabOptions } = require('@/hooks/safe-brownfield');
  expect(hostedTabOptions()).toEqual({});

  jest.doMock('expo-brownfield', () => {
    throw new Error("Cannot find native module 'ExpoBrownfieldModule'");
  });
  jest.resetModules();
  expect(require('@/hooks/safe-brownfield').hostedTabOptions()).toEqual({});
});
