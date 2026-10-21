/**
 * What the RN app tells the native shell that hosts it (docs/native-migration-plan.md, M2). The
 * shell holds every message it has for JS until "ready" arrives — messages sent before JS listens
 * are dropped silently, so the order of the calls here is the contract.
 */
const mockCalls: string[] = [];

function mockBrownfield() {
  return {
    addMessageListener: () => {
      mockCalls.push('subscribe');
      return { remove: jest.fn() };
    },
    sendMessage: (message: { type: string }) => mockCalls.push(`send ${message.type}`),
    setNativeBackEnabled: (enabled: boolean) => mockCalls.push(`native back ${enabled}`),
  };
}

jest.mock('expo-brownfield', () => mockBrownfield());

// A fresh copy per test: the module remembers that it has subscribed, as it should once per runtime.
let announceHostReady: () => void;

beforeEach(() => {
  mockCalls.length = 0;
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
