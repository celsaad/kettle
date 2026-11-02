/**
 * Programs' stub (#232, #190): an empty tab route, so RN's stack, back and route reports stay as they
 * were, and Program detail and the program editor it opens are pushed over this tab. The native shell
 * draws the screen over it whenever RN reports this path; the Compose screen is
 * `native/android/.../programs/ProgramsScreen.kt`. Goes when routing moves native, after M6 (#223).
 */
export default function ProgramsStub() {
  return null;
}
