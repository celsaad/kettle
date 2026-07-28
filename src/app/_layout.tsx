import {
  HankenGrotesk_400Regular,
  HankenGrotesk_500Medium,
  HankenGrotesk_600SemiBold,
} from '@expo-google-fonts/hanken-grotesk';
import { SpaceGrotesk_500Medium, SpaceGrotesk_600SemiBold, SpaceGrotesk_700Bold } from '@expo-google-fonts/space-grotesk';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from 'expo-font';
import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
// Imported for its side effect: initialises i18next (and the Intl.PluralRules polyfill Hermes lacks)
// before any screen renders a translated string.
import '@/i18n';
import { RunnerColors } from '@/constants/theme';
import { ThemeOverrideProvider, useAppTheme } from '@/hooks/theme-context';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

SplashScreen.preventAutoHideAsync();

function Navigation() {
  const { colors, scheme } = useAppTheme();

  // Base off the matching React Navigation theme rather than always DefaultTheme (its light one), so
  // the handful of colors not overridden below — and anything keying off the `dark` flag — don't pull
  // light-mode surfaces into a dark shell.
  const base = scheme === 'dark' ? DarkTheme : DefaultTheme;
  const navigationTheme = {
    ...base,
    colors: {
      ...base.colors,
      background: colors.background,
      card: colors.backgroundElement,
      text: colors.text,
      border: colors.border,
      primary: colors.accent,
    },
  };

  return (
    // The root view has to carry a real background: every container React renders into is transparent
    // by default (verified in the browser — #root and each wrapper below it compute to rgba(0,0,0,0)).
    // On web the <body> color set in global.css/theme-context.tsx shows through, which is why the
    // earlier flash fix worked there; on native there's no body, so a modal sliding away exposed the
    // native window background — white — for the length of the transition.
    <GestureHandlerRootView style={[styles.root, { backgroundColor: colors.background }]}>
      <ThemeProvider value={navigationTheme}>
        <AnimatedSplashOverlay />
        {/*
          Native-stack defaults each screen's own contentStyle background to white regardless of theme —
          a separate issue from (but same symptom as) the web body-background flash fixed in
          theme-context.tsx/global.css: without this, closing a modal briefly flashes white through the
          transition before the destination screen's own background paints over it. session overrides to
          RunnerColors.background since the live session runner is always dark regardless of the shell's
          scheme, per the design (constants/theme.ts) — every other screen uses the current theme.
        */}
        <Stack screenOptions={{ contentStyle: { backgroundColor: colors.background } }}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen
            name="session"
            options={{
              presentation: 'modal',
              headerShown: false,
              contentStyle: { backgroundColor: RunnerColors.background },
            }}
          />
          <Stack.Screen name="import" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="exercise-editor" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="workout-editor" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="program-detail" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="program-editor" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="program-guide" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="settings" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="support" options={{ presentation: 'modal', headerShown: false }} />
        </Stack>
      </ThemeProvider>
    </GestureHandlerRootView>
  );
}

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    SpaceGrotesk_500Medium,
    SpaceGrotesk_600SemiBold,
    SpaceGrotesk_700Bold,
    HankenGrotesk_400Regular,
    HankenGrotesk_500Medium,
    HankenGrotesk_600SemiBold,
  });

  const libraryStatus = useLibraryStore((state) => state.status);
  const hydrateLibrary = useLibraryStore((state) => state.hydrate);
  const sessionHistoryStatus = useSessionHistoryStore((state) => state.status);
  const hydrateSessionHistory = useSessionHistoryStore((state) => state.hydrate);
  // Gated alongside the data stores rather than hydrated on mount like the tip store: this one picks
  // the unit every weight renders in, so arriving late would swap the numbers under the user.
  const preferencesStatus = usePreferencesStore((state) => state.status);
  const hydratePreferences = usePreferencesStore((state) => state.hydrate);

  useEffect(() => {
    hydrateLibrary();
    hydrateSessionHistory();
    hydratePreferences();
  }, [hydrateLibrary, hydrateSessionHistory, hydratePreferences]);

  const dataReady = libraryStatus === 'ready' || libraryStatus === 'error';
  const historyReady = sessionHistoryStatus === 'ready' || sessionHistoryStatus === 'error';

  if (!fontsLoaded || !dataReady || !historyReady || preferencesStatus !== 'ready') return null;

  // GestureHandlerRootView now lives inside Navigation, since it needs the active theme to paint its
  // background and that's only readable below ThemeOverrideProvider.
  return (
    <ThemeOverrideProvider>
      <Navigation />
    </ThemeOverrideProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
});
