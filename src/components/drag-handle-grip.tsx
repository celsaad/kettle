import { StyleSheet, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';

import type { DragHandle } from '@/components/reorderable-list';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';

/**
 * The grip a `ReorderableList` row is picked up by: the gesture, the a11y props and the ⣿ glyph in one
 * place, so the two block shapes in the workout editor can't drift into two differently-sized handles.
 *
 * It lives here rather than in `reorderable-list.tsx` because that file is deliberately free of visual
 * styling — it owns the gesture, the measurement and the accessibility contract, and takes every
 * appearance decision as a prop.
 */
export function DragHandleGrip({ handle }: { handle: DragHandle }) {
  return (
    <GestureDetector gesture={handle.gesture}>
      <View {...handle.a11yProps} style={styles.touchArea}>
        <ThemedText themeColor="textSecondary" style={styles.glyph}>
          ⣿
        </ThemedText>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  // The whole target for picking a block up, so it is sized as a control rather than around the
  // glyph: the ⣿ renders about 14×30dp, roughly 2.5mm wide on a phone against a ~9mm fingertip, and
  // missing it looks exactly like a drag that refused to start. `minWidth`/`minHeight` rather than
  // `hitSlop`, which RNGH cannot expand past the view's own bounds on Android.
  touchArea: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -Spacing.one,
  },
  glyph: {
    letterSpacing: -2,
  },
});
