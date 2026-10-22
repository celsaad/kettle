import { requireOptionalNativeModule } from 'expo';

/**
 * The end point of the startup Macrobenchmark (native/android/benchmark, and #150 in
 * docs/native-migration-plan.md): Android's `reportFullyDrawn`, called when Home's content has laid
 * out. adb's first frame can't answer when the home screen is ready, because RN's first frame arrives
 * before its JS has rendered anything. The same call in today's app and in the shell times both to the
 * same point.
 *
 * Only Android has the module (`modules/startup-marker`), and Expo Go has none, so a missing one
 * degrades to doing nothing. It's also what Play's own time-to-full-display reads, which costs nothing.
 */
const marker = requireOptionalNativeModule<{ reportFullyDrawn(): Promise<void> }>('StartupMarker');

let reported = false;

/** Once per runtime: a later visit to Home, or a relayout, isn't a startup. */
export function reportHomeDrawn(): void {
  if (reported || !marker) return;
  reported = true;
  // Nothing to wait for or recover: a marker that fails only leaves the benchmark without an end point.
  marker.reportFullyDrawn().catch(() => {});
}
