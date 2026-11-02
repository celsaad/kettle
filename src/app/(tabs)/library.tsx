/**
 * Library's stub (#234, #190): an empty tab route, so RN's stack, back and route reports stay as they
 * were, and the import sheet and the exercise editor it opens are pushed over this tab. The native shell
 * draws the screen over it whenever RN reports this path; the Compose screen is
 * `native/android/.../library/LibraryScreen.kt`. Goes when routing moves native, after M6 (#223).
 */
export default function LibraryStub() {
  return null;
}
