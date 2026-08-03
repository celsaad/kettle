import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useRef, type ReactNode } from 'react';
import { View, type LayoutChangeEvent, type StyleProp, type ViewStyle } from 'react-native';
import { Gesture, type PanGesture } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/**
 * Each item's resting (undisplaced) position and size in the list's own coordinate space, taken
 * straight from `onLayout`.
 *
 * `y` is measured rather than derived from a running sum of heights, because the *consumer* owns the
 * spacing between rows — `workout-editor` sets `gap: 10` on the list container — and a heights-only
 * model has no way to see it. Summing heights puts every slot boundary at a multiple of the row
 * *height* where the row actually sits at a multiple of its *pitch*, so the drop target runs ahead of
 * the finger by one gap per position crossed. Two or three rows down that is a whole position, which
 * is what made dropping into a chosen middle position impossible.
 */
export type Slot = { y: number; height: number };

/**
 * Which resting slot the actively-dragged item's current centre is nearest to. Hit-testing against
 * the static layout — not the currently-animated positions of the other items — avoids a feedback
 * loop between "where things are drawn" and "what counts as the drop target".
 *
 * Nearest-centre rather than "first slot whose band contains the centre": with real gaps a centre can
 * land *between* two bands, and it makes the threshold symmetric — a row changes place once it has
 * travelled past the midpoint between its own centre and its neighbour's, in either direction.
 *
 * Exported for tests only. The gesture that drives it cannot be simulated in RNTL, so this arithmetic
 * has no other reachable surface; the a11y move path is covered through the handle instead.
 */
export function targetIndexFor(activeIndex: number, dragY: number, slots: Slot[]): number {
  'worklet';
  const active = slots[activeIndex];
  // Nothing measured yet (first frame, or the array is mid-resize after an add/remove): stay put
  // rather than treating every zero-height slot as a candidate and committing a move to index 0.
  if (!active || active.height === 0) return activeIndex;

  const currentCenter = active.y + active.height / 2 + dragY;
  let target = activeIndex;
  let bestDistance = Infinity;
  for (let i = 0; i < slots.length; i++) {
    const slot = slots[i];
    if (!slot || slot.height === 0) continue;
    const distance = Math.abs(currentCenter - (slot.y + slot.height / 2));
    if (distance < bestDistance) {
      bestDistance = distance;
      target = i;
    }
  }
  return target;
}

/**
 * How far the rows between `from` and `to` slide to open up the gap. That distance is the *pitch* the
 * dragged row vacates — its height plus the container's spacing — which is exactly the difference
 * between two consecutive resting tops, so it never has to be told what the spacing is.
 */
function displacementFor(from: number, to: number, slots: Slot[]): number {
  'worklet';
  // `from < to` guarantees `from + 1` exists; `from > to` guarantees `from - 1` does.
  const [a, b] = from < to ? [slots[from], slots[from + 1]] : [slots[from - 1], slots[from]];
  if (!a || !b) return 0;
  return from < to ? -(b.y - a.y) : b.y - a.y;
}

function triggerPickupHaptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

/**
 * What the consumer spreads onto whatever it uses as the drag handle. The gesture alone left this
 * list impossible to reorder without sight — a drag has no non-visual equivalent — so the handle also
 * carries an `adjustable` role, which both VoiceOver and TalkBack expose as swipe-up/swipe-down.
 * It lives here rather than on the row wrapper deliberately: making the wrapper `accessible` would
 * collapse the row into a single element and hide the remove button and text fields inside it.
 */
export type DragHandle = {
  gesture: PanGesture;
  a11yProps: {
    accessible: true;
    accessibilityRole: 'adjustable';
    accessibilityLabel: string;
    accessibilityValue: { text: string };
    accessibilityActions: { name: 'increment' | 'decrement'; label: string }[];
    onAccessibilityAction: (event: { nativeEvent: { actionName: string } }) => void;
  };
};

export type ReorderLabels = {
  /** Names the handle, e.g. "Reorder Push-ups". */
  handle: string;
  /** States where the item currently sits, e.g. "Position 2 of 5". */
  position: string;
  moveUp: string;
  moveDown: string;
};

/**
 * A ref to the scrollable ancestor the list sits in, so the drag can outrank it.
 *
 * Android's `ScrollView` claims any touch that drifts past its ~8dp slop, and it decides that before
 * the 150ms long-press has elapsed — so a finger that isn't perfectly still scrolls the list instead
 * of picking a block up, with no haptic and no lift to say why. RNGH can only arbitrate against a
 * scroller it knows about, which is what `blocksExternalGesture` plus the gesture-handler `ScrollView`
 * gives it: the scroller now waits for this pan to fail before it may claim anything. Move fast and
 * the long-press fails immediately and scrolling still works, so this costs nothing but the case it
 * fixes.
 */
export type ScrollableRef = React.RefObject<React.ComponentType | null>;

type ReorderableItemProps = {
  index: number;
  total: number;
  labels: ReorderLabels;
  scrollRef?: ScrollableRef;
  slots: SharedValue<Slot[]>;
  activeIndex: SharedValue<number>;
  dragTranslateY: SharedValue<number>;
  onCommit: (from: number, to: number) => void;
  children: (dragHandle: DragHandle) => ReactNode;
};

function ReorderableItem({
  index,
  total,
  labels,
  scrollRef,
  slots,
  activeIndex,
  dragTranslateY,
  onCommit,
  children,
}: ReorderableItemProps) {
  // Long-press-then-drag (not an immediate pan) so ordinary vertical scrolling of the surrounding
  // ScrollView keeps working untouched, and gives a clear "picked up" moment to pair with a haptic.
  //
  // Memoized because `GestureDetector` re-attaches on a new gesture object, and this list re-renders
  // on every keystroke in the sibling name field — rebuilding the handler mid-drag drops the drag.
  const dragHandle = useMemo(() => {
    const pan = Gesture.Pan();
    if (scrollRef) pan.blocksExternalGesture(scrollRef);
    return pan
      .activateAfterLongPress(150)
      .onStart(() => {
        'worklet';
        activeIndex.value = index;
        dragTranslateY.value = 0;
        scheduleOnRN(triggerPickupHaptic);
      })
      .onUpdate((event) => {
        'worklet';
        dragTranslateY.value = event.translationY;
      })
      .onEnd((_event, success) => {
        'worklet';
        const from = activeIndex.value;
        activeIndex.value = -1;
        const target = success && from >= 0 ? targetIndexFor(from, dragTranslateY.value, slots.value) : from;
        if (from < 0 || target === from) {
          dragTranslateY.value = withTiming(0, { duration: 150 });
          return;
        }
        // Snap rather than ease back to zero: the row's new position comes from the `data` change a
        // frame later, so animating the transform towards its *old* slot at the same time renders as
        // the drop being rejected and then teleporting.
        dragTranslateY.value = 0;
        scheduleOnRN(onCommit, from, target);
      })
      .onFinalize(() => {
        'worklet';
        // A gesture can be cancelled without `onEnd` ever running — it only fires out of ACTIVE.
        // Without this the list stays frozen mid-drag: every other row displaced, the handle dead,
        // and no way back short of leaving the screen.
        if (activeIndex.value !== index) return;
        activeIndex.value = -1;
        dragTranslateY.value = withTiming(0, { duration: 150 });
      });
  }, [activeIndex, dragTranslateY, index, onCommit, scrollRef, slots]);

  const animatedStyle = useAnimatedStyle(() => {
    if (activeIndex.value === index) {
      return {
        transform: [{ translateY: dragTranslateY.value }, { scale: 1.02 }],
        zIndex: 10,
        elevation: 8,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.18,
        shadowRadius: 10,
      };
    }
    if (activeIndex.value === -1) {
      return { transform: [{ translateY: withTiming(0, { duration: 150 }) }], zIndex: 0, elevation: 0, shadowOpacity: 0 };
    }
    const a = activeIndex.value;
    const target = targetIndexFor(a, dragTranslateY.value, slots.value);
    const displaced = a < target ? index > a && index <= target : index >= target && index < a;
    const shift = displaced ? displacementFor(a, target, slots.value) : 0;
    return { transform: [{ translateY: withTiming(shift, { duration: 150 }) }], zIndex: 0, elevation: 0, shadowOpacity: 0 };
  });

  const onLayout = useCallback(
    (event: LayoutChangeEvent) => {
      const { y, height } = event.nativeEvent.layout;
      const current = slots.value[index];
      if (current && current.y === y && current.height === height) return;
      slots.value = slots.value.map((slot, i) => (i === index ? { y, height } : slot));
    },
    [slots, index],
  );

  // Clamped rather than wrapped: "move up" on the first row should do nothing, not teleport it to the
  // bottom, which is disorienting when you can't see the list.
  const move = useCallback(
    (delta: number) => {
      const target = index + delta;
      if (target < 0 || target >= total) return;
      onCommit(index, target);
    },
    [index, total, onCommit],
  );

  const handle: DragHandle = {
    gesture: dragHandle,
    a11yProps: {
      accessible: true,
      accessibilityRole: 'adjustable',
      accessibilityLabel: labels.handle,
      accessibilityValue: { text: labels.position },
      accessibilityActions: [
        { name: 'increment', label: labels.moveUp },
        { name: 'decrement', label: labels.moveDown },
      ],
      // `increment` is up because the actions also back the adjustable role's swipe gestures, where
      // swipe-up is increment — so the gesture and the named action can't disagree.
      onAccessibilityAction: (event) => move(event.nativeEvent.actionName === 'increment' ? -1 : 1),
    },
  };

  return (
    <Animated.View style={animatedStyle} onLayout={onLayout}>
      {children(handle)}
    </Animated.View>
  );
}

export type ReorderableListProps<T> = {
  data: T[];
  keyExtractor: (item: T, index: number) => string;
  /**
   * `dragHandle.gesture` goes on a `<GestureDetector>`; `dragHandle.a11yProps` spreads onto the View
   * inside it. Both belong on the same element — that's what makes the drag and the screen-reader
   * path the same control rather than two competing ones.
   */
  renderItem: (item: T, index: number, dragHandle: DragHandle) => ReactNode;
  onReorder: (data: T[]) => void;
  /**
   * Labels for the non-visual reorder path. Required rather than defaulted: this component is in a
   * fully localized app and has no locale bundle of its own, so an English fallback here would be a
   * hardcoded string that no pt test could catch.
   */
  labelsFor: (item: T, index: number, total: number) => ReorderLabels;
  /**
   * The scrollable ancestor, if there is one — and there should be, since a list worth reordering is
   * usually longer than a screen. It must be the `ScrollView` from `react-native-gesture-handler`, not
   * the one from `react-native`: RNGH can only make a scroller defer to the drag if it is a scroller
   * RNGH knows about. See `ScrollableRef`.
   */
  scrollRef?: ScrollableRef;
  style?: StyleProp<ViewStyle>;
};

/**
 * A generic press-and-hold-then-drag reorderable list. Items stay in normal document flow and in
 * their original React children order/keys at all times — reordering is purely a `translateY`
 * transform during the gesture, and `data` only changes once, on drop. That's what keeps an open
 * TextInput inside a non-dragged item from losing focus or remounting mid-drag.
 *
 * Deliberately generic (no workout/exercise concepts) so it can be reused for other reorderable
 * lists later (e.g. a future program-week editor).
 */
export function ReorderableList<T>({
  data,
  keyExtractor,
  renderItem,
  onReorder,
  labelsFor,
  scrollRef,
  style,
}: ReorderableListProps<T>) {
  const slots = useSharedValue<Slot[]>(data.map(() => ({ y: 0, height: 0 })));
  const activeIndex = useSharedValue(-1);
  const dragTranslateY = useSharedValue(0);

  // Resize the shared slot array to match `data` across add/remove, preserving already-measured slots
  // by index. Cheap, and only ever runs between drags (add/remove close any open picker UI).
  if (slots.value.length !== data.length) {
    slots.value = data.map((_, i) => slots.value[i] ?? { y: 0, height: 0 });
  }

  // Keep `onReorder`/`data` reachable from the commit callback without changing its identity on every
  // unrelated re-render (e.g. typing in a sibling text field), so the per-item Gesture.Pan objects
  // stay stable and aren't rebuilt while a drag could be in flight.
  const dataRef = useRef(data);
  dataRef.current = data;
  const onReorderRef = useRef(onReorder);
  onReorderRef.current = onReorder;
  const commitReorder = useCallback((from: number, to: number) => {
    const next = [...dataRef.current];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    onReorderRef.current(next);
  }, []);

  return (
    <View style={style}>
      {data.map((item, index) => (
        <ReorderableItem
          key={keyExtractor(item, index)}
          index={index}
          total={data.length}
          labels={labelsFor(item, index, data.length)}
          scrollRef={scrollRef}
          slots={slots}
          activeIndex={activeIndex}
          dragTranslateY={dragTranslateY}
          onCommit={commitReorder}>
          {(dragHandle) => renderItem(item, index, dragHandle)}
        </ReorderableItem>
      ))}
    </View>
  );
}
