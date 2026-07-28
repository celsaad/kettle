import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { RestPreview } from '@/hooks/use-session-runner';

/**
 * Above this text scale the runner screens stop fitting. They're `flex: 1` with `space-between` and
 * no ScrollView, so overflow *clips* rather than scrolls — and what gets clipped is the bottom, which
 * is where the primary action lives. Dropping this card is the cheapest way to buy that room back:
 * it's the only genuinely supplementary element on those screens, and the information is a preview of
 * a step the user is about to reach anyway.
 */
const MAX_FONT_SCALE_FOR_PREVIEW = 1.5;

/**
 * The "what's coming up next" card — originally only shown on the rest screen, now shared across
 * every session step screen (hold/reps/interval too) so you can see what's next without waiting for
 * rest. Renders nothing when there's nothing upcoming (end of workout).
 */
export function SessionNextCard({ next }: { next: RestPreview }) {
  const { t } = useTranslation();
  const { fontScale } = useWindowDimensions();
  if (!next || fontScale > MAX_FONT_SCALE_FOR_PREVIEW) return null;
  return (
    <View style={styles.nextCard}>
      <ThemedText type="code" style={styles.nextLabel}>
        {t('session.next')}
      </ThemedText>
      <View style={styles.nextText}>
        <ThemedText type="heading" style={styles.nextName}>
          {next.label}
        </ThemedText>
        <ThemedText type="small" style={styles.nextDetail}>
          {next.detail}
        </ThemedText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  nextCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three - 2,
    backgroundColor: RunnerColors.backgroundElement,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    borderRadius: 18,
    padding: Spacing.three - 2,
  },
  nextLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1.4,
  },
  nextText: {
    flex: 1,
    gap: 2,
  },
  nextName: {
    color: RunnerColors.text,
  },
  nextDetail: {
    color: RunnerColors.textSecondary,
  },
});
