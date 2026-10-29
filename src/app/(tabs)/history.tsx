/**
 * History's stub (#202, #190): an empty tab route, so RN's stack, back and route reports stay as they
 * were, and the session editor and Stats it opens are pushed over this tab. The native shell draws the
 * screen over it whenever RN reports this path; the Compose screen is
 * `native/android/.../history/HistoryScreen.kt`. Goes at the M5 flip.
 */
export default function HistoryStub() {
  return null;
}
