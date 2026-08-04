import { Pressable, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { RunnerColors, Spacing } from '@/constants/theme';

type Props = {
  /** "Set 2 of 4", already assembled by the caller — reps and holds phrase it differently. */
  label: string;
  /** False inside a circuit, where a member's set count is the block's rounds. Hides the controls. */
  canAdd?: boolean;
  /** False at the floor: what has already been logged, plus the set in progress. Disables, not hides. */
  canDrop?: boolean;
  /** False when the library holds no other exercise of this type — nothing to swap to. */
  canSwap?: boolean;
  onAdd?: () => void;
  onDrop?: () => void;
  onSwap?: () => void;
};

/**
 * The "Set 2 of 4" line, and the controls that change the 4.
 *
 * Shared by the reps and hold screens because the controls are identical and the a11y work on them
 * shouldn't be written twice — only the label differs, so that comes in as a string.
 *
 * Drop is disabled rather than hidden at the floor: a control that disappears reflows the header
 * mid-set, right where the user is reading their set number.
 */
export function SessionSetCount({ label, canAdd, canDrop, canSwap, onAdd, onDrop, onSwap }: Props) {
  const { t } = useTranslation();
  const setsLabel = t('session.sets.label');

  return (
    <View style={styles.row}>
      <ThemedText type="small" style={styles.label}>
        {label}
      </ThemedText>
      {canAdd && (
        <View style={styles.controls}>
          {/* Glyphs, so they can't take a name from their own children — both need an explicit label. */}
          <Pressable
            onPress={onDrop}
            disabled={!canDrop}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel={t('common.decrease', { label: setsLabel })}
            accessibilityState={{ disabled: !canDrop }}
            style={[styles.button, !canDrop && styles.buttonDisabled]}>
            <ThemedText type="smallMedium" style={styles.glyph}>
              −
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={onAdd}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel={t('common.increase', { label: setsLabel })}
            style={styles.button}>
            <ThemedText type="smallMedium" style={styles.glyph}>
              +
            </ThemedText>
          </Pressable>
        </View>
      )}
      {canSwap && (
        <Pressable onPress={onSwap} hitSlop={12} accessibilityRole="button" style={styles.swapButton}>
          {/* Names itself from this text — no accessibilityLabel to drift from what's on screen. */}
          <ThemedText type="code" style={styles.swapLabel}>
            {t('session.swap.action')}
          </ThemedText>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  // Wraps: the label already carries a target range, and a third control after it runs out of width
  // on a narrow screen at a raised text size.
  row: {
    marginTop: 4,
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  label: {
    color: RunnerColors.textSecondary,
  },
  controls: {
    flexDirection: 'row',
    gap: Spacing.one,
  },
  // Small on purpose — this is an occasional correction, not a primary control, and it sits beside a
  // caption. hitSlop rather than size carries the 44px target so it doesn't crowd the exercise name.
  button: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 1,
    borderColor: RunnerColors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonDisabled: {
    opacity: 0.4,
  },
  swapButton: {
    minHeight: 26,
    justifyContent: 'center',
    paddingHorizontal: 10,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: RunnerColors.border,
  },
  swapLabel: {
    color: RunnerColors.textSecondary,
    letterSpacing: 1,
  },
  /**
   * No fixed `lineHeight`, and `includeFontPadding: false`.
   *
   * A line height shorter than the 26px box leaves the glyph to be positioned by the font's own
   * ascent/descent rather than by the flex centring, and on Android `includeFontPadding` then adds
   * asymmetric space above and below on top of that — so both glyphs sat visibly high in the circle.
   * Letting flexbox do the centring, with the font padding off and `textAlignVertical` centred for
   * Android's text layout, puts them where the box says.
   */
  glyph: {
    color: RunnerColors.textSecondary,
    textAlign: 'center',
    textAlignVertical: 'center',
    includeFontPadding: false,
  },
});
