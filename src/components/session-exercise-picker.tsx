import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';
import type { Exercise } from '@/domain/types';

type Props = {
  /** The exercise being replaced — named in the title so the sheet says what it is about to do. */
  replacing: string;
  /** Same-type candidates, already filtered by the runner. */
  candidates: Exercise[];
  onCancel: () => void;
  onSelect: (exerciseId: string) => void;
};

/**
 * Picks a substitute for the rest of the current exercise.
 *
 * A sheet inside the runner rather than a route, for the same reasons `SessionNumberPad` is one: the
 * runner screens are `flex: 1` with no room to push anything aside, and a route would need registering
 * in `_layout.tsx` and the router types regenerating for a control that only exists mid-session.
 *
 * The list is deliberately plain — no search. Filtering to one exercise type already cuts a library to
 * a handful, and a search field here would summon the OS keyboard over the sheet mid-workout.
 */
export function SessionExercisePicker({ replacing, candidates, onCancel, onSelect }: Props) {
  const { t } = useTranslation();

  return (
    <View style={styles.overlay}>
      <Pressable
        style={styles.backdrop}
        onPress={onCancel}
        accessibilityRole="button"
        accessibilityLabel={t('session.swap.dismiss')}
      />

      <View style={styles.sheet}>
        <ThemedText type="code" style={styles.label}>
          {t('session.swap.title')}
        </ThemedText>
        {/* The user's own exercise name, interpolated rather than translated. */}
        <ThemedText type="small" style={styles.replacing}>
          {t('session.swap.replacing', { name: replacing })}
        </ThemedText>

        <ScrollView style={styles.list} contentContainerStyle={styles.listContent}>
          {candidates.map((exercise) => (
            <Pressable
              key={exercise.id}
              onPress={() => onSelect(exercise.id)}
              accessibilityRole="button"
              style={({ pressed }) => [styles.option, pressed && styles.optionPressed]}>
              {/* Takes its name from this text, so it needs no accessibilityLabel of its own. */}
              <ThemedText type="heading" style={styles.optionLabel}>
                {exercise.name}
              </ThemedText>
            </Pressable>
          ))}
        </ScrollView>

        <Pressable onPress={onCancel} accessibilityRole="button" style={styles.cancelButton}>
          <ThemedText type="code" style={styles.cancelLabel}>
            {t('common.cancel')}
          </ThemedText>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'flex-end',
    zIndex: 10,
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.55)',
  },
  sheet: {
    backgroundColor: RunnerColors.backgroundElement,
    borderTopWidth: 1,
    borderColor: RunnerColors.border,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  label: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1.4,
    textAlign: 'center',
  },
  replacing: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
  },
  // Capped so a long library scrolls inside the sheet rather than pushing Cancel off the screen.
  list: {
    maxHeight: 320,
  },
  listContent: {
    gap: Spacing.two - 2,
    paddingVertical: Spacing.one,
  },
  option: {
    minHeight: 56,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: 14,
    backgroundColor: RunnerColors.background,
    borderWidth: 1,
    borderColor: RunnerColors.border,
  },
  optionPressed: {
    opacity: 0.6,
  },
  optionLabel: {
    color: RunnerColors.text,
  },
  cancelButton: {
    minHeight: 52,
    paddingVertical: Spacing.one,
    paddingHorizontal: Spacing.two,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelLabel: {
    textAlign: 'center',
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
});
