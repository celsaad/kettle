import { router } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Web has no Play Billing, so this variant renders the pitch without ever calling `useIAP` — matching
 * the platform split already used by `_layout.web.tsx` and `animated-icon.web.tsx`.
 *
 * It does *not* keep expo-iap out of the web bundle, which was the original hope: Expo Router's
 * `require.context` enumerates every route file, so `ExpoIapModule` is still present in the dev
 * bundle (verified by grepping the served bundle). That turned out not to matter — expo-iap imports
 * harmlessly on web, and web isn't a shipping target. What this file buys is runtime behaviour: the
 * hook never runs where there's no store to talk to.
 *
 * Unreachable in normal use, since Settings hides the entry point on web. It exists so a deep link
 * degrades to an explanation instead of a broken screen.
 */
export default function SupportScreen() {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={() => router.back()} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('support.title')}</ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.paragraph}>
          {t('support.pitch')}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" style={styles.paragraph}>
          {t('support.unavailable')}
        </ThemedText>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingBottom: Spacing.four,
  },
  paragraph: {
    marginTop: Spacing.three,
  },
});
