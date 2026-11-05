/**
 * Settings' stub (#238, #190): an empty route that RN still pushes and pops, so its stack, back and
 * route reports stay as they were. The native shell draws Settings over it whenever RN reports this
 * path; the Compose screen is `native/android/.../settings/SettingsScreen.kt`, and native is the only
 * writer of `preferences.json` and the backup folder from the preferences flip. Goes when routing
 * moves native, after M6 (#223).
 *
 * Outside the shell (iOS, Expo Go) this is an empty sheet: M5 keeps no RN fallback (#220).
 */
export default function SettingsStub() {
  return null;
}
