import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';

type Props = {
  workoutName: string;
  onDone: () => void;
};

/**
 * The bookend to SessionCountdown at the other end of a session — same dark full-screen, centered
 * layout family, deliberately a different scale (a checkmark + a large title, not a giant numeral)
 * since this is a different kind of moment, not another countdown.
 */
export function SessionComplete({ workoutName, onDone }: Props) {
  const { t } = useTranslation();
  return (
    <View style={styles.container}>
      <View style={styles.badge}>
        <ThemedText style={styles.checkmark}>✓</ThemedText>
      </View>
      <ThemedText type="title" style={styles.title}>
        {t('session.complete.title')}
      </ThemedText>
      <ThemedText type="small" style={styles.workoutName}>
        {workoutName}
      </ThemedText>
      <Pressable onPress={onDone} accessibilityRole="button" style={styles.doneButton}>
        <ThemedText type="heading" style={styles.doneButtonLabel}>
          {t('session.complete.done')}
        </ThemedText>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  badge: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: RunnerColors.accentSoft,
    borderWidth: 1,
    borderColor: 'rgba(207,106,55,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.one,
  },
  checkmark: {
    fontSize: 44,
    lineHeight: 44,
    color: RunnerColors.accent,
  },
  title: {
    color: RunnerColors.text,
    textAlign: 'center',
  },
  workoutName: {
    color: RunnerColors.textSecondary,
  },
  doneButton: {
    marginTop: Spacing.four,
    minHeight: 52,
    paddingVertical: Spacing.one,
    minWidth: 160,
    borderRadius: 15,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  doneButtonLabel: {
    color: RunnerColors.background,
  },
});
