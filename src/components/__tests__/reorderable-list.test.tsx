import { render, screen } from '@testing-library/react-native';
import { Text, View } from 'react-native';

import { ReorderableList, targetIndexFor, type Slot } from '@/components/reorderable-list';

/**
 * Covers the reorder path a browser check cannot reach: the drag is a gesture, and the alternative to
 * it is a screen-reader action, so neither shows up in a Playwright run. Reordering by drag was the
 * *only* path until now, which made this list impossible to use without sight.
 *
 * The assertions go through the handle's `onAccessibilityAction` because that is exactly what a
 * screen reader invokes — testing a private helper instead would prove the arithmetic and not the
 * wiring, and the wiring is the part that was missing.
 */
const ITEMS = ['a', 'b', 'c'];

function renderList(onReorder: (next: string[]) => void) {
  return render(
    <ReorderableList
      data={ITEMS}
      keyExtractor={(item) => item}
      onReorder={onReorder}
      labelsFor={(item, index, total) => ({
        handle: `Reorder ${item}`,
        position: `Position ${index + 1} of ${total}`,
        moveUp: 'Move up',
        moveDown: 'Move down',
      })}
      renderItem={(item, _index, dragHandle) => (
        <View {...dragHandle.a11yProps}>
          <Text>{item}</Text>
        </View>
      )}
    />,
  );
}

function fireAction(label: string, actionName: 'increment' | 'decrement') {
  const handle = screen.getByLabelText(label);
  handle.props.onAccessibilityAction({ nativeEvent: { actionName } });
}

it('moves an item up, reporting the whole new order', async () => {
  const onReorder = jest.fn();
  await renderList(onReorder);

  fireAction('Reorder c', 'increment');

  expect(onReorder).toHaveBeenCalledWith(['a', 'c', 'b']);
});

it('moves an item down', async () => {
  const onReorder = jest.fn();
  await renderList(onReorder);

  fireAction('Reorder a', 'decrement');

  expect(onReorder).toHaveBeenCalledWith(['b', 'a', 'c']);
});

/**
 * Clamped, not wrapped. Wrapping would teleport the first row to the bottom on a "move up" — the kind
 * of jump that is merely surprising when you can see the list and disorienting when you can't.
 */
it('does nothing moving the first item up, or the last item down', async () => {
  const onReorder = jest.fn();
  await renderList(onReorder);

  fireAction('Reorder a', 'increment');
  fireAction('Reorder c', 'decrement');

  expect(onReorder).not.toHaveBeenCalled();
});

it('announces each item position, so the effect of a move is perceivable', async () => {
  await renderList(jest.fn());

  // Without a value there is no feedback that anything moved: the row's own text is unchanged by a
  // reorder, so position is the only thing that says the action worked.
  expect(screen.getByLabelText('Reorder b').props.accessibilityValue).toEqual({ text: 'Position 2 of 3' });
});

it('offers both directions as named actions on an adjustable control', async () => {
  await renderList(jest.fn());

  const handle = screen.getByLabelText('Reorder b');
  expect(handle.props.accessibilityRole).toBe('adjustable');
  expect(handle.props.accessibilityActions).toEqual([
    { name: 'increment', label: 'Move up' },
    { name: 'decrement', label: 'Move down' },
  ]);
});

/**
 * The drop-target arithmetic, which the a11y path above cannot reach: it is driven by a pan gesture,
 * and RNTL can't simulate one. These are the numbers `workout-editor` actually produces — 70px rows
 * with the list container's `gap: 10` between them, so consecutive tops are 80 apart.
 *
 * The bug these pin: the old model summed *heights* to locate every slot, so it measured travel in
 * units of 70 where a position is really 80 apart, and the target ran one gap ahead of the finger per
 * position crossed. The values below are chosen to sit inside that drift — 260px is three positions
 * of real travel and four of the old model's — because a value near the middle of a position rounds
 * to the same answer either way and would pin nothing.
 */
const ROWS: Slot[] = [0, 1, 2, 3, 4].map((i) => ({ y: i * 80, height: 70 }));

it('lands where the finger is, counting the spacing between rows', () => {
  // Centre now at 295; slot 3's is at 275 and slot 4's at 355, so the row is plainly over position 4.
  expect(targetIndexFor(0, 260, ROWS)).toBe(3);
  // And the same distance travelled upwards.
  expect(targetIndexFor(4, -260, ROWS)).toBe(1);
});

it('changes place once the row is past halfway, in either direction', () => {
  expect(targetIndexFor(1, 39, ROWS)).toBe(1);
  expect(targetIndexFor(1, 41, ROWS)).toBe(2);
  expect(targetIndexFor(1, -39, ROWS)).toBe(1);
  expect(targetIndexFor(1, -41, ROWS)).toBe(0);
});

it('clamps at both ends rather than wrapping', () => {
  expect(targetIndexFor(2, -10000, ROWS)).toBe(0);
  expect(targetIndexFor(2, 10000, ROWS)).toBe(4);
});

/**
 * Rows and circuit blocks are very different heights, which is why slots are measured at all. A tall
 * row's centre starts far from every other centre, so the nearest-centre test has to keep working
 * when the dragged item is several times its neighbours' size.
 */
it('handles a tall block among short rows', () => {
  const mixed: Slot[] = [
    { y: 0, height: 70 },
    { y: 80, height: 240 },
    { y: 330, height: 70 },
  ];

  // The tall block's centre rests at 200 and slot 2's at 365, so it changes place at the midpoint
  // between them — 82.5px of travel, half the distance between the two centres and not half its own
  // considerable height.
  expect(targetIndexFor(1, 80, mixed)).toBe(1);
  expect(targetIndexFor(1, 85, mixed)).toBe(2);
  // Symmetric going up: slot 0's centre is 165 above too, so the same 82.5px threshold applies.
  expect(targetIndexFor(1, -80, mixed)).toBe(1);
  expect(targetIndexFor(1, -85, mixed)).toBe(0);
});

/**
 * Before the first `onLayout` every slot is zero-sized. Treating those as real candidates would make
 * the nearest one index 0 and commit a move to the top of the list on any drag.
 */
it('refuses to pick a target before anything has been measured', () => {
  const unmeasured: Slot[] = [0, 1, 2].map(() => ({ y: 0, height: 0 }));

  expect(targetIndexFor(2, 500, unmeasured)).toBe(2);
});
