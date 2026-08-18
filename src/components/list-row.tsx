import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The row's own metrics, exported because two screens need a row that is *not* this component.
 *
 * History's rows expand in place, so they are vertical containers with a pressable header inside
 * rather than the horizontal flex box below; Settings' rows are grouped in sections rather than fed
 * to a `FlatList`. Both should still be exactly as tall and as generously tappable as these, and the
 * way to guarantee that is to share the numbers rather than to copy them — copying is what left four
 * byte-identical card styles across the app in the first place.
 */
export const ListRowMinHeight = 56;
export const ListRowVerticalPadding = Spacing.two + 2;

/**
 * One row of one of the three library lists — a workout, a program, an exercise.
 *
 * **These used to be cards**: a filled surface with a 1px border and a 16px radius, one per item.
 * A card is for a single discrete privileged object; a list of peers is rows, and all three of these
 * screens are lists of peers. Twenty-six bordered boxes down a screen is twenty-six frames competing
 * with the twenty-six names inside them, and the fill bought nothing — nothing sits behind a row that
 * it needs to be lifted off.
 *
 * The three screens had byte-identical copies of that card style, so this exists as much to stop them
 * drifting as to change them. Two sibling lists that look subtly unlike each other read as a bug, not
 * as a distinction — the runner's two progress indicators had just taught that lesson the hard way.
 *
 * **No horizontal padding.** The card's own padding sat on top of the screen's, insetting every name
 * 30px from the edge while the title and search box above sat at 16px. Rows line up with the rest of
 * the screen instead.
 *
 * `minHeight`, never `height`: the row grows with the reader's text size, and both lines inside it are
 * free to wrap.
 */
export function ListRow({ children }: { children: ReactNode }) {
  return <View style={styles.row}>{children}</View>;
}

/**
 * The hairline between two rows, in the same `border` token the cards drew their outline with.
 *
 * A separator rather than the 5px gap the cards were spaced by: without a fill to define them, rows
 * need a drawn boundary or a long list becomes one undifferentiated column of text. One line between
 * neighbours, and none above the first or below the last — which is what `ItemSeparatorComponent`
 * gives for free, and the reason it is used instead of a border on the row itself.
 */
export function ListRowSeparator() {
  const theme = useTheme();

  // Decorative geometry, so a fixed `height` is right here — the same reason the progress bars and the
  // modal grabber keep one. The rule about `minHeight` is about controls, and this is a line.
  return <View style={[styles.separator, { backgroundColor: theme.border }]} />;
}

/**
 * The hairline that ends a list's header and starts the list.
 *
 * Without a fill on the rows, a list has no visible top edge: the search box just stops and names
 * begin, so the first row reads as one more piece of header. This is the same line as the separators
 * between rows, which is the point — the list begins the way it continues.
 *
 * It carries the gap above it so the header's own `marginBottom` can go to zero; the space belongs
 * above the line, not below, or the first row sits further from the line than every other row does.
 */
export function ListHeaderRule() {
  return (
    <View style={styles.headerRule}>
      <ListRowSeparator />
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    minHeight: ListRowMinHeight,
    paddingVertical: ListRowVerticalPadding,
  },
  separator: {
    height: 1,
  },
  headerRule: {
    marginTop: Spacing.three,
  },
});
