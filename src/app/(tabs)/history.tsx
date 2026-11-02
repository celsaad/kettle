/**
 * History's stub (#202, #190): an empty tab route, so RN's stack, back and route reports stay as they
 * were, and the session editor and Stats it opens are pushed over this tab. The native shell draws the
 * screen over it whenever RN reports this path; the Compose screen is
 * `native/android/.../history/HistoryScreen.kt`. Goes when routing moves native, after M6 (#223).
 */
export default function HistoryStub() {
  return null;
}
