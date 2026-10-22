/**
 * The marker the startup Macrobenchmark measures to (docs/native-migration-plan.md, M2): Home tells
 * Android its content is drawn, in today's app and in the shell alike, so the two are timed to the
 * same point.
 */
const mockReportFullyDrawn = jest.fn().mockResolvedValue(undefined);
let mockModule: { reportFullyDrawn: () => Promise<void> } | null = { reportFullyDrawn: mockReportFullyDrawn };

jest.mock('expo', () => ({
  requireOptionalNativeModule: (name: string) => (name === 'StartupMarker' ? mockModule : null),
}));

// A fresh copy per test: the module remembers that it has reported, as it should once per runtime.
let reportHomeDrawn: () => void;

beforeEach(() => {
  mockReportFullyDrawn.mockClear();
  mockModule = { reportFullyDrawn: mockReportFullyDrawn };
  jest.resetModules();
  ({ reportHomeDrawn } = require('@/hooks/startup-marker'));
});

it('tells Android the home screen is drawn', () => {
  reportHomeDrawn();

  expect(mockReportFullyDrawn).toHaveBeenCalledTimes(1);
});

// It's called from a layout callback, which fires again on any relayout, and returning to Home later
// is not a startup.
it('reports once however often it is called', () => {
  reportHomeDrawn();
  reportHomeDrawn();

  expect(mockReportFullyDrawn).toHaveBeenCalledTimes(1);
});

// Web, iOS and Expo Go have no StartupMarker. This runs on Home's first layout, so a throw here would
// cost the home screen.
it('does nothing, without throwing, where the native module is missing', () => {
  mockModule = null;
  jest.resetModules();
  ({ reportHomeDrawn } = require('@/hooks/startup-marker'));

  expect(() => reportHomeDrawn()).not.toThrow();
  expect(mockReportFullyDrawn).not.toHaveBeenCalled();
});
