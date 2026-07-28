import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';

type Props = {
  /** What's being entered, e.g. "Reps" or "Load" — shown above the value. */
  label: string;
  /** Shown as the starting value, and kept if the user confirms without typing anything. */
  initialValue: number;
  unit?: string;
  allowDecimal?: boolean;
  onCancel: () => void;
  onConfirm: (value: number) => void;
};

const DIGITS = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];

/**
 * A keypad for typing an exact value, offered alongside the runner's −/+ steppers rather than
 * replacing them. Steppers are right for the common small adjustment and one-handed use; they're
 * wrong for large absolute values, where a 30-rep set or a 100kg load costs one tap per increment
 * mid-workout.
 *
 * Deliberately a custom keypad rather than a `TextInput` with `keyboardType="numeric"`: the runner
 * screens are `flex: 1` with no ScrollView and already tight, so the OS keyboard would cover the
 * controls it's meant to be editing. This also keeps the digits large enough to hit while out of
 * breath, which a system keyboard doesn't guarantee.
 *
 * Typing starts a fresh value rather than appending to the current one — the reason to open this at
 * all is that the current value is far from what you want.
 */
export function SessionNumberPad({ label, initialValue, unit, allowDecimal, onCancel, onConfirm }: Props) {
  const [buffer, setBuffer] = useState('');
  const { t } = useTranslation();

  const display = buffer === '' ? String(initialValue) : buffer;
  const parsed = buffer === '' ? initialValue : Number(buffer);
  // A lone "." or a trailing "." parses to NaN; confirming that should be a no-op, not a crash.
  const canConfirm = Number.isFinite(parsed) && parsed >= 0;

  const append = (char: string) => {
    setBuffer((current) => {
      if (char === '.' && current.includes('.')) return current;
      // Cap the length so a stuck finger can't produce an absurd number that breaks the layout.
      if (current.length >= 6) return current;
      return current + char;
    });
  };

  const backspace = () => setBuffer((current) => current.slice(0, -1));

  const keys = [...DIGITS, allowDecimal ? '.' : null, '0', '⌫'];

  return (
    <View style={styles.overlay}>
      <Pressable style={styles.backdrop} onPress={onCancel} accessibilityLabel={t('session.numberPad.dismiss')} />

      <View style={styles.sheet}>
        <ThemedText type="code" style={styles.label}>
          {label.toUpperCase()}
        </ThemedText>
        <ThemedText type="numeral" style={styles.value}>
          {display}
          {unit ? <ThemedText style={styles.unit}> {unit}</ThemedText> : null}
        </ThemedText>

        <View style={styles.grid}>
          {keys.map((key, index) =>
            key === null ? (
              <View key={`gap-${index}`} style={styles.key} />
            ) : (
              <Pressable
                key={key}
                onPress={() => (key === '⌫' ? backspace() : append(key))}
                accessibilityRole="button"
                accessibilityLabel={key === '⌫' ? t('common.delete') : key}
                style={({ pressed }) => [styles.key, pressed && styles.keyPressed]}>
                <ThemedText type="heading" style={styles.keyLabel}>
                  {key}
                </ThemedText>
              </Pressable>
            ),
          )}
        </View>

        <View style={styles.actions}>
          <Pressable onPress={onCancel} accessibilityRole="button" style={styles.cancelButton}>
            <ThemedText type="code" style={styles.cancelLabel}>
              {t('common.cancel')}
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={() => canConfirm && onConfirm(parsed)}
            disabled={!canConfirm}
            accessibilityRole="button"
            style={[styles.setButton, !canConfirm && styles.setButtonDisabled]}>
            <ThemedText type="heading" style={styles.setLabel}>
              {t('session.numberPad.set')}
            </ThemedText>
          </Pressable>
        </View>
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
  value: {
    fontSize: 44,
    lineHeight: 50,
    color: RunnerColors.text,
    textAlign: 'center',
  },
  unit: {
    fontSize: 18,
    color: RunnerColors.textSecondary,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.one + 2,
    justifyContent: 'center',
  },
  key: {
    width: '31%',
    minHeight: 54,
    borderRadius: 14,
    backgroundColor: RunnerColors.background,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  keyPressed: {
    opacity: 0.6,
  },
  keyLabel: {
    color: RunnerColors.text,
    fontSize: 22,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.one,
  },
  cancelButton: {
    flex: 1,
    minHeight: 52,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
  setButton: {
    flex: 1.6,
    minHeight: 52,
    borderRadius: 15,
    backgroundColor: RunnerColors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  setButtonDisabled: {
    opacity: 0.4,
  },
  setLabel: {
    color: RunnerColors.background,
  },
});
