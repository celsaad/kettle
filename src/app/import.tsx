/**
 * Import's stub (#244, #190): an empty route that RN still pushes and pops, so its stack, back and route
 * reports stay as they were. The native shell draws the screen over it whenever RN reports this path;
 * the Compose screen is `native/android/.../importer/ImportScreen.kt`, and native is the library's only writer
 * from the library flip (#245). Goes when routing moves native, after M6 (#223).
 *
 * Outside the shell (iOS, web, Expo Go) this is an empty sheet: M5 keeps no RN fallback (#220).
 */
export default function ImportStub() {
  return null;
}
