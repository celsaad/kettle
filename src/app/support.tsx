/**
 * Support's stub (#198, #190): an empty route that RN still pushes and pops, so its stack, back and
 * route reports stay as they were. The native shell draws the tip jar over it whenever RN reports
 * this path; the Compose screen is `native/android/.../support/SupportScreen.kt`. Goes at the M5 flip.
 *
 * Nothing else reaches it: Settings offers Support on Android alone, where the shell is the app.
 */
export default function SupportStub() {
  return null;
}
