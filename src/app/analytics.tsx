/**
 * Stats' stub (#201, #190): an empty route that native History pushes from its header, so its stack,
 * back and route reports stay as they were. The native shell draws the screen over it whenever RN
 * reports this path; the Compose screen is `native/android/.../stats/StatsScreen.kt`. Goes at the M5
 * flip.
 */
export default function AnalyticsStub() {
  return null;
}
