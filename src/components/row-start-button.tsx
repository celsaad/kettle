import { Pressable, StyleSheet, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

/**
 * The "start this one" control that sits at the end of a row.
 *
 * **Accent, after a detour.** It was neutralised for a while on the argument that one of these per row
 * meant twenty accent marks down a screen, competing with the single primary action. On device that
 * traded too much: a grey triangle beside a grey chevron stops announcing itself as the way to start
 * anything, and starting a specific item is the second most common thing anyone does on these screens.
 * The accent budget is spent instead on the *fill* — the next-up card's Start button is the only solid
 * accent block on the Workouts tab, and these are a tint behind a glyph.
 *
 * The glyph takes `accentText`, not `accent`. That is the token for accent-coloured *marks on a
 * surface*, and it is the difference between passing and failing: `accent` on `accentSoft` measures
 * 2.90:1 in light mode, under the 3:1 WCAG asks of a meaningful graphic, where `accentText` measures
 * 4.53:1 light and 6.58:1 dark.
 *
 * Shared rather than copied, because the Workouts list and the program detail screen both need it and
 * a second copy is how the four identical card styles this codebase just finished deleting happened.
 *
 * The triangle is a CSS-style border trick rather than a glyph, so it has no text to name itself
 * with — hence the required `accessibilityLabel`. `hitSlop` carries the 44px target rather than the
 * drawn size, which stays 36 so it doesn't crowd the name beside it.
 */
export function RowStartButton({ onPress, accessibilityLabel }: { onPress: () => void; accessibilityLabel: string }) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={onPress}
      hitSlop={8}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      style={({ pressed }) => [styles.button, { backgroundColor: theme.accentSoft }, pressed && styles.pressed]}>
      <View style={[styles.triangle, { borderLeftColor: theme.accentText }]} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  // Decorative geometry with no text in it, so a fixed size is right — the 44px target comes from
  // hitSlop above. See the note on the component for the contrast measurements.
  button: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  triangle: {
    width: 0,
    height: 0,
    marginLeft: 2,
    borderTopWidth: 6,
    borderBottomWidth: 6,
    borderLeftWidth: 9,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
  },
  pressed: {
    opacity: 0.7,
  },
});
