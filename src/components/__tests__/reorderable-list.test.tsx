import { render, screen } from '@testing-library/react-native';
import { Text, View } from 'react-native';

import { ReorderableList } from '@/components/reorderable-list';

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
