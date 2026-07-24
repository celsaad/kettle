import {
  HankenGrotesk_400Regular,
  HankenGrotesk_500Medium,
  HankenGrotesk_600SemiBold,
} from '@expo-google-fonts/hanken-grotesk';
import {
  SpaceGrotesk_500Medium,
  SpaceGrotesk_600SemiBold,
  SpaceGrotesk_700Bold,
} from '@expo-google-fonts/space-grotesk';
import { DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from 'expo-font';
import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { ThemeOverrideProvider, useAppTheme } from '@/hooks/theme-context';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

SplashScreen.preventAutoHideAsync();

function Navigation() {
  const { colors } = useAppTheme();

  const navigationTheme = {
    ...DefaultTheme,
    colors: {
      ...DefaultTheme.colors,
      background: colors.background,
      card: colors.backgroundElement,
      text: colors.text,
      border: colors.border,
      primary: colors.accent,
    },
  };

  return (
    <ThemeProvider value={navigationTheme}>
      <AnimatedSplashOverlay />
      <Stack>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="session" options={{ presentation: 'modal', headerShown: false }} />
        <Stack.Screen name="import" options={{ presentation: 'modal', headerShown: false }} />
        <Stack.Screen name="exercise-editor" options={{ presentation: 'modal', headerShown: false }} />
        <Stack.Screen name="workout-editor" options={{ presentation: 'modal', headerShown: false }} />
        <Stack.Screen name="program-detail" options={{ presentation: 'modal', headerShown: false }} />
      </Stack>
    </ThemeProvider>
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

  useEffect(() => {
    hydrateLibrary();
    hydrateSessionHistory();
  }, [hydrateLibrary, hydrateSessionHistory]);

  const dataReady = libraryStatus === 'ready' || libraryStatus === 'error';
  const historyReady = sessionHistoryStatus === 'ready' || sessionHistoryStatus === 'error';

  if (!fontsLoaded || !dataReady || !historyReady) return null;

  return (
    <GestureHandlerRootView style={styles.root}>
      <ThemeOverrideProvider>
        <Navigation />
      </ThemeOverrideProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
});
