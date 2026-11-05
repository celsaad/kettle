/**
 * The native runner's stub (#302, #190): an empty route that RN pushes and pops like any other, so its
 * stack, back and route reports stay as they were, while the shell draws the runner full screen over
 * it. Only `.dev` opens it until the flip release (#308); Play opens `session.tsx`, which never learns
 * this route exists. Goes when routing moves native (#309).
 *
 * Nothing removes it but the runner's own Done. Back, a `kettle://` link to a tab and the shell's
 * navigate are all refused, silently: the native runner owns its exits and their questions (#297),
 * where RN's runner asked "Finish session?" itself (#152's rule, retired for this route). The shell's
 * runnerDone drops the guard, and the back after it is the only removal that gets through.
 *
 * Only where the shell draws the runner over it, though. The route ships in every bundle, so on Play a
 * `kettle://session-native` link reaches it with nothing on top, and outside the shell nothing ever
 * is: there it leaves at once, since a guard over a blank screen would leave nothing to press.
 */
import { router, useFocusEffect } from 'expo-router';
import { usePreventRemove } from 'expo-router/react-navigation';
import { useCallback, useEffect, useState } from 'react';

import { drawsNativeRunner } from '@/hooks/brownfield-module';
import { onRunnerDone } from '@/hooks/runner-done';

const refuse = () => {};

/** Back where there is somewhere to go back to; the tabs when a cold-start link opened this alone. */
function leave(): void {
  if (router.canGoBack()) router.back();
  else router.replace('/');
}

export default function NativeRunnerStub() {
  const [drawn] = useState(drawsNativeRunner);
  const [done, setDone] = useState(false);
  const guarded = drawn && !done;
  usePreventRemove(guarded, refuse);
  useEffect(() => onRunnerDone(() => setDone(true)), []);
  // A focus effect, as expo-router's own Redirect uses, rather than a plain one: a link that launches
  // the app mounts this before the root navigator is ready, when canGoBack says false and every
  // navigation throws into the root error boundary. A focus effect waits for it. It also runs after the
  // render that dropped the guard, which the navigator reads by then: a back sent from the listener
  // itself would still meet it.
  useFocusEffect(
    useCallback(() => {
      if (!guarded) leave();
    }, [guarded]),
  );
  return null;
}
