/**
 * The RN app's half of its conversation with the native shell that hosts it
 * (docs/native-migration-plan.md): the "ready" handshake from M2, and from M3 the navigate messages
 * native steers the surface with. The shell loses anything it sends before JS listens — the native
 * module only starts forwarding once a listener exists — so it holds its messages until "ready", and
 * "ready" is only true once the listener is in place.
 *
 * expo-brownfield throws on import wherever its native module isn't built in (Expo Go, a dev client
 * from before M2), so it's required lazily and a failure degrades to doing nothing: the app then runs
 * exactly as it did before there was a shell.
 */
import { router, type Href } from 'expo-router';

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
 * The shell steers the one RN surface it hosts by message, since a second surface can't be routed
 * by deep link (docs/native-migration-plan.md, Q3). The url is a route path, as `router.navigate`
 * takes it.
 *
 * Everything above the tabs is dismissed first. `navigate` alone, from a modal or the runner, pushes a
 * second copy of the tabs over them, and back then walks through the stale screens; `dismissTo` gets
 * that right but does nothing between tabs. So a message always starts from the tabs, and a modal it
 * opens backs out to one. If a screen refuses to leave (the runner's "Finish session?"), the message
 * is dropped and the user's answer decides. All three were seen on a device (#152).
 */
function onShellMessage(message: Record<string, unknown>): void {
  // The shell's JsBridge.navigate. The two share no code, so change both.
  if (message.type !== 'navigate' || typeof message.url !== 'string') return;
  const href = message.url as Href;
  if (!router.canDismiss()) {
    router.navigate(href);
    return;
  }
  router.dismissAll();
  // The dismissal commits after dismissAll returns, so canDismiss read straight away still sees the
  // screens it removed. On a device it had settled by the next microtask; a timeout is one tick later
  // still, and a refused dismissal leaves it true either way.
  setTimeout(() => {
    if (!router.canDismiss()) router.navigate(href);
  }, 0);
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
    // The subscription is also what turns native's sends on, so it's needed even for messages JS
    // ignores.
    brownfield.addMessageListener(onShellMessage);
    subscribed = true;
  }
  // JsChannel's READY on the native side. The two share no code, so change both.
  brownfield.sendMessage({ type: 'ready' });
}
