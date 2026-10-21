/**
 * The RN app's half of the handshake with the native shell that hosts it (docs/native-migration-plan.md,
 * M2). The shell loses anything it sends before JS listens — the native module only starts forwarding
 * once a listener exists — so it holds its messages until "ready", and "ready" is only true once the
 * listener is in place.
 *
 * expo-brownfield throws on import wherever its native module isn't built in (Expo Go, a dev client
 * from before M2), so it's required lazily and a failure degrades to doing nothing: the app then runs
 * exactly as it did before there was a shell.
 */
type BrownfieldModule = typeof import('expo-brownfield');

let cached: BrownfieldModule | null | undefined;
let subscribed = false;

function getBrownfield(): BrownfieldModule | null {
  if (cached !== undefined) return cached;
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    cached = require('expo-brownfield') as BrownfieldModule;
  } catch {
    cached = null;
  }
  return cached;
}

/**
 * Called once the app has rendered past its splash. Back goes to JS while it has somewhere to go, and
 * reaches the shell only once it hasn't.
 */
export function announceHostReady(): void {
  const brownfield = getBrownfield();
  if (!brownfield) return;
  brownfield.setNativeBackEnabled(false);
  if (!subscribed) {
    // Nothing to handle yet (M3's navigation is the first message), but the subscription is what
    // turns native's sends on.
    brownfield.addMessageListener(() => {});
    subscribed = true;
  }
  // JsChannel's READY on the native side. The two share no code, so change both.
  brownfield.sendMessage({ type: 'ready' });
}
