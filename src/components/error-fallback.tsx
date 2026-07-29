import { router } from 'expo-router';
import type { ErrorBoundaryProps } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The screen a route falls back to when its render throws.
 *
 * Every route wires one of the boundaries below up via an `ErrorBoundary` export, which expo-router
 * turns into a `<Try>` around that route's component (see its `useScreens.fromImport`) — so a throw
 * takes out one screen instead of the whole app. Without them a single bad render unmounted
 * everything, which mid-workout meant losing the session in progress; `session.tsx` exports its own
 * boundary for exactly that case rather than reusing these.
 *
 * The error's own message is shown, and selectable: nothing in this app phones home (a Play Data
 * Safety claim, not an oversight — see the decision log), so what's on screen is the only diagnostic
 * that exists for a user reporting a bug.
 */
export function ErrorFallback({
  title,
  body,
  error,
  primary,
  secondary,
}: {
  title: string;
  body: string;
  error: Error;
  primary: { label: string; onPress: () => void };
  secondary?: { label: string; onPress: () => void };
}) {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <View style={styles.content}>
        <ThemedText type="subtitle" style={styles.centered}>
          {title}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" style={styles.centered}>
          {body}
        </ThemedText>

        {/* The message can be long and is the only thing worth copying, hence its own bordered block. */}
        <View style={[styles.detail, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
          <ThemedText type="code" themeColor="textSecondary" selectable accessibilityLabel={t('errorBoundary.detail')}>
            {error.message}
          </ThemedText>
        </View>

        <Pressable
          onPress={primary.onPress}
          accessibilityRole="button"
          accessibilityLabel={primary.label}
          style={({ pressed }) => [styles.button, { backgroundColor: theme.accent }, pressed && styles.pressed]}>
          <ThemedText type="heading" themeColor="onAccent">
            {primary.label}
          </ThemedText>
        </Pressable>

        {secondary && (
          <Pressable
            onPress={secondary.onPress}
            accessibilityRole="button"
            accessibilityLabel={secondary.label}
            style={({ pressed }) => [styles.secondaryButton, pressed && styles.pressed]}>
            <ThemedText type="smallMedium" themeColor="textSecondary">
              {secondary.label}
            </ThemedText>
          </Pressable>
        )}
      </View>
    </SafeAreaView>
  );
}

/**
 * For the tab screens. Retry is the only move available — a tab can't be dismissed, and the tab bar
 * itself stays alive because the boundary sits inside the tab's own route, so the other four tabs
 * keep working while this one is broken. If the throw is deterministic the retry just lands here
 * again, which is honest: it re-renders from the current store state, so it recovers exactly when
 * the cause was transient and not otherwise.
 */
export function RouteErrorBoundary({ error, retry }: ErrorBoundaryProps) {
  const { t } = useTranslation();

  return (
    <ErrorFallback
      title={t('errorBoundary.title')}
      body={t('errorBoundary.body')}
      error={error}
      primary={{ label: t('errorBoundary.retry'), onPress: () => void retry() }}
    />
  );
}

/**
 * For the modal routes. Closing is the useful action here in a way it isn't on a tab: the stores are
 * untouched by a render throw, so dismissing lands the user back in a working app. Retry stays as the
 * secondary — it remounts the editor from the same route params, which recovers a transient failure,
 * though any unsaved draft in that screen's local state is gone either way.
 */
export function ModalErrorBoundary({ error, retry }: ErrorBoundaryProps) {
  const { t } = useTranslation();

  return (
    <ErrorFallback
      title={t('errorBoundary.title')}
      body={t('errorBoundary.body')}
      error={error}
      primary={{ label: t('common.close'), onPress: () => router.back() }}
      secondary={{ label: t('errorBoundary.retry'), onPress: () => void retry() }}
    />
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  content: {
    flex: 1,
    alignSelf: 'center',
    justifyContent: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    gap: Spacing.two,
  },
  centered: {
    textAlign: 'center',
  },
  detail: {
    borderWidth: 1,
    borderRadius: 12,
    padding: Spacing.two,
  },
  button: {
    marginTop: Spacing.two,
    // minHeight, never height: a fixed one clips the label at large accessibility text sizes.
    minHeight: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
  },
  secondaryButton: {
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
});
