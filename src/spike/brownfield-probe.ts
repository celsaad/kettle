// M0 spike only (docs/native-migration-plan.md) — never merges. Reports what the spike's questions
// need from the JS side over BrownfieldMessaging, so the native screen can show it.
import { addMessageListener, sendMessage, setNativeBackEnabled } from 'expo-brownfield';
import { Paths } from 'expo-file-system';
import { usePathname } from 'expo-router';
import { useEffect } from 'react';
import { Linking } from 'react-native';

import { useLibraryStore } from '@/state/library-store';

// Module scope runs once per JS runtime, so two surfaces reporting the same id share one (Q2).
const runtimeId = Math.random().toString(36).slice(2, 8);
const loadedAt = Date.now();
let mountedSurfaces = 0;

addMessageListener((event) => {
  if (event.type !== 'ping') return;
  sendMessage({
    type: 'pong',
    runtimeId,
    mountedSurfaces,
    documentDir: Paths.document.uri,
    exerciseCount: useLibraryStore.getState().library?.exercises.length ?? -1,
    roundTripMs: Date.now() - Number(event.sentAt),
  });
});

export function useBrownfieldProbe() {
  const pathname = usePathname();

  useEffect(() => {
    // JS owns back while it has somewhere to go; with nothing left, RN calls
    // invokeDefaultOnBackPressed and BrownfieldActivity finishes (Q6).
    setNativeBackEnabled(false);
    mountedSurfaces += 1;
    void Linking.getInitialURL().then((initialUrl) =>
      sendMessage({ type: 'mounted', runtimeId, mountedSurfaces, initialUrl, msSinceJsLoad: Date.now() - loadedAt }),
    );
    return () => {
      mountedSurfaces -= 1;
      sendMessage({ type: 'unmounted', runtimeId, mountedSurfaces });
    };
  }, []);

  useEffect(() => {
    sendMessage({ type: 'route', runtimeId, pathname });
  }, [pathname]);
}
