/**
 * The session editor's stub (#240, #190): an empty route that RN still pushes and pops, so its stack,
 * back and route reports stay as they were. The native shell draws the editor over it whenever RN
 * reports this path; the Compose screen is `native/android/.../sessioneditor/SessionEditorScreen.kt`,
 * and native is the only writer of an edited session file from the sessions flip. RN's runner still
 * writes the new ones. Goes when routing moves native, after M6 (#223).
 *
 * Outside the shell (iOS, web, Expo Go) this is an empty sheet: M5 keeps no RN fallback (#220).
 */
export default function SessionEditorStub() {
  return null;
}
