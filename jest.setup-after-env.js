// Global per-test teardown, so no suite can leak state into the next one.
//
// Mock lifecycle is handled declaratively by `clearMocks` + `restoreMocks` in the jest config. Timers
// need this hook: a suite that calls jest.useFakeTimers() and doesn't hand them back leaves every
// later suite on a frozen clock, and the resulting failures surface far from their cause — the
// runner tests hit exactly that, as opaque AggregateErrors in tests that passed in isolation.
afterEach(() => {
  jest.useRealTimers();
});
