import { Platform, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The strip every modal screen opens with: the drag grabber, plus a close button.
 *
 * Rendered *outside* the screen's ScrollView so it stays pinned — the grabber used to be the first
 * thing in the scroll content, and the only way out of a modal was the Cancel button at the very
 * bottom of the page, so dismissing a long form meant scrolling all the way down to reach it.
 *
 * Carries the modal's top spacing (previously each screen's `scrollContent.paddingTop`) so the strip
 * sits where the grabber used to, and matches `scrollContent`'s centered max-width column so the
 * button lines up with the content below it rather than the raw screen edge.
 */
export function ModalHeader({ onClose }: { onClose: () => void }) {
  const theme = useTheme();

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <View style={[styles.grabber, { backgroundColor: theme.border }]} />
        <Pressable
          onPress={onClose}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel="Close"
          style={({ pressed }) => [
            styles.closeButton,
            { borderColor: theme.border, backgroundColor: theme.backgroundElement },
            pressed && styles.pressed,
          ]}>
          <ThemedText type="heading" themeColor="textSecondary" style={styles.closeGlyph}>
            ×
          </ThemedText>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingTop: Platform.select({ web: Spacing.six, default: Spacing.three }),
    paddingBottom: Spacing.three - 2,
  },
  // Height comes from the button, and the grabber centers against it, so the two don't stack.
  row: {
    height: 32,
    justifyContent: 'center',
  },
  grabber: {
    alignSelf: 'center',
    width: 40,
    height: 5,
    borderRadius: 3,
  },
  closeButton: {
    position: 'absolute',
    right: 0,
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeGlyph: {
    // The glyph's own line box sits low; tightening the line height centers it in the circle.
    lineHeight: 21,
  },
  pressed: {
    opacity: 0.7,
  },
});
