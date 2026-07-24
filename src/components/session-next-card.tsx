import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { RestPreview } from '@/hooks/use-session-runner';

/**
 * The "what's coming up next" card — originally only shown on the rest screen, now shared across
 * every session step screen (hold/reps/interval too) so you can see what's next without waiting for
 * rest. Renders nothing when there's nothing upcoming (end of workout).
 */
export function SessionNextCard({ next }: { next: RestPreview }) {
  if (!next) return null;
  return (
    <View style={styles.nextCard}>
      <ThemedText type="code" style={styles.nextLabel}>
        NEXT
      </ThemedText>
      <View style={styles.nextText}>
        <ThemedText type="heading" style={styles.nextName}>
          {next.label}
        </ThemedText>
        <ThemedText type="small" style={styles.nextDetail}>
          {next.detail}
        </ThemedText>
      </View>
      <ThemedText style={styles.nextArrow}>→</ThemedText>
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
  nextArrow: {
    fontSize: 22,
    color: RunnerColors.textSecondary,
  },
});
