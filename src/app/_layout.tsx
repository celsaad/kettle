import {
  HankenGrotesk_400Regular,
  HankenGrotesk_500Medium,
  HankenGrotesk_600SemiBold,
} from '@expo-google-fonts/hanken-grotesk';
import { SpaceGrotesk_500Medium, SpaceGrotesk_600SemiBold, SpaceGrotesk_700Bold } from '@expo-google-fonts/space-grotesk';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import type { ErrorBoundaryProps } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from 'expo-font';
import { useEffect } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
// Imported for its side effect: initialises i18next (and the Intl.PluralRules polyfill Hermes lacks)
// before any screen renders a translated string.
import '@/i18n';
import { Colors, RunnerColors, MaxContentWidth, Spacing } from '@/constants/theme';
import { ThemeOverrideProvider, useAppTheme } from '@/hooks/theme-context';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useRestDayReminder } from '@/hooks/use-rest-day-reminder';
import { useLibraryStore } from '@/state/library-store';
import { usePreferencesStore } from '@/state/preferences-store';
import { useSessionHistoryStore } from '@/state/session-history-store';

SplashScreen.preventAutoHideAsync();

/**
 * The last resort: a throw in this layout, or in any route without a boundary of its own, lands here
 * with nothing above it left to catch a second failure. So it deliberately uses none of the app's own
 * UI — `ThemedText`/`ThemedView` read `useAppTheme`, which *throws* outside `ThemeOverrideProvider`,
 * and this renders in place of the component that provides it. The scheme comes straight from the OS
 * for the same reason (the stored preference lives in a store this may have failed to hydrate), and
 * the text uses the system font rather than `Fonts.*`, since the app's own faces may be exactly what
 * didn't load. `SafeAreaView` is safe: expo-router's `ExpoRoot` mounts the provider above every route.
 *
 * Retry re-renders the whole tree from scratch, which is the closest thing to a restart the app can
 * offer without the user killing it — worth having, since the hydration gate below runs again with it.
 */
export function ErrorBoundary({ error, retry }: ErrorBoundaryProps) {
  const { t } = useTranslation();
  const colors = Colors[useColorScheme() === 'dark' ? 'dark' : 'light'];

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]} edges={['top', 'bottom', 'left', 'right']}>
      <View style={styles.rootErrorContent}>
        <Text style={[styles.rootErrorTitle, { color: colors.text }]}>{t('errorBoundary.title')}</Text>
        <Text style={[styles.rootErrorBody, { color: colors.textSecondary }]}>{t('errorBoundary.rootBody')}</Text>
        <Text selectable style={[styles.rootErrorDetail, { color: colors.textSecondary }]}>
          {error.message}
        </Text>
        <Pressable
          onPress={() => void retry()}
          accessibilityRole="button"
          accessibilityLabel={t('errorBoundary.retry')}
          style={({ pressed }) => [
            styles.rootErrorButton,
            { backgroundColor: colors.accent },
            pressed && styles.rootErrorPressed,
          ]}>
          <Text style={[styles.rootErrorButtonLabel, { color: colors.onAccent }]}>{t('errorBoundary.retry')}</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

function Navigation() {
  const { colors, scheme } = useAppTheme();
  // Here rather than in the runner: the reminder has to be rescheduled when the *preference* changes
  // too, and this is the one component that is mounted for both of its inputs and only after they've
  // hydrated. It also means the schedule self-heals on every launch.
  useRestDayReminder();

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
          <Stack.Screen name="analytics" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="program-detail" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="program-editor" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="program-guide" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="session-editor" options={{ presentation: 'modal', headerShown: false }} />
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
  rootErrorContent: {
    flex: 1,
    alignSelf: 'center',
    justifyContent: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    gap: Spacing.two,
  },
  rootErrorTitle: {
    fontSize: 22,
    fontWeight: '600',
    textAlign: 'center',
  },
  rootErrorBody: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  rootErrorDetail: {
    fontSize: 12,
    lineHeight: 18,
  },
  rootErrorButton: {
    marginTop: Spacing.two,
    // minHeight, never height — a fixed one clips the label at large accessibility text sizes.
    minHeight: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
  },
  rootErrorButtonLabel: {
    fontSize: 16,
    fontWeight: '600',
  },
  rootErrorPressed: {
    opacity: 0.7,
  },
});
