import * as Haptics from 'expo-haptics';
import { useCallback, useRef, type ReactNode } from 'react';
import { View, type LayoutChangeEvent, type StyleProp, type ViewStyle } from 'react-native';
import { Gesture, type PanGesture } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/**
 * Which original-index "slot" the actively-dragged item's current center falls into, using each
 * item's *resting* (undisplaced) cumulative height — not the currently-animated positions of the
 * other items. Hit-testing against the static layout avoids a feedback loop between "where things
 * are drawn" and "what counts as the drop target".
 */
function targetIndexFor(activeIndex: number, dragY: number, heights: number[]): number {
  'worklet';
  let restingTop = 0;
  for (let i = 0; i < activeIndex; i++) restingTop += heights[i] ?? 0;
  const activeHeight = heights[activeIndex] ?? 0;
  const currentCenter = restingTop + activeHeight / 2 + dragY;

  let cumulative = 0;
  for (let i = 0; i < heights.length; i++) {
    const h = heights[i] ?? 0;
    if (currentCenter < cumulative + h) return i;
    cumulative += h;
  }
  return heights.length - 1;
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

type ReorderableItemProps = {
  index: number;
  total: number;
  labels: ReorderLabels;
  heights: SharedValue<number[]>;
  activeIndex: SharedValue<number>;
  dragTranslateY: SharedValue<number>;
  onCommit: (from: number, to: number) => void;
  children: (dragHandle: DragHandle) => ReactNode;
};

function ReorderableItem({
  index,
  total,
  labels,
  heights,
  activeIndex,
  dragTranslateY,
  onCommit,
  children,
}: ReorderableItemProps) {
  // Long-press-then-drag (not an immediate pan) so ordinary vertical scrolling of the surrounding
  // ScrollView keeps working untouched, and gives a clear "picked up" moment to pair with a haptic.
  const dragHandle = Gesture.Pan()
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
    .onEnd(() => {
      'worklet';
      const from = activeIndex.value;
      const target = targetIndexFor(from, dragTranslateY.value, heights.value);
      activeIndex.value = -1;
      dragTranslateY.value = withTiming(0, { duration: 150 });
      if (from !== target) scheduleOnRN(onCommit, from, target);
    });

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
    const target = targetIndexFor(a, dragTranslateY.value, heights.value);
    let shift = 0;
    if (a < target && index > a && index <= target) shift = -(heights.value[a] ?? 0);
    else if (a > target && index >= target && index < a) shift = heights.value[a] ?? 0;
    return { transform: [{ translateY: withTiming(shift, { duration: 150 }) }], zIndex: 0, elevation: 0, shadowOpacity: 0 };
  });

  const onLayout = useCallback(
    (event: LayoutChangeEvent) => {
      const measured = event.nativeEvent.layout.height;
      if (heights.value[index] === measured) return;
      heights.value = heights.value.map((h, i) => (i === index ? measured : h));
    },
    [heights, index],
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
  style,
}: ReorderableListProps<T>) {
  const heights = useSharedValue<number[]>(data.map(() => 0));
  const activeIndex = useSharedValue(-1);
  const dragTranslateY = useSharedValue(0);

  // Resize the shared heights array to match `data` across add/remove, preserving already-measured
  // heights by index. Cheap, and only ever runs between drags (add/remove close any open picker UI).
  if (heights.value.length !== data.length) {
    heights.value = data.map((_, i) => heights.value[i] ?? 0);
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
          heights={heights}
          activeIndex={activeIndex}
          dragTranslateY={dragTranslateY}
          onCommit={commitReorder}>
          {(dragHandle) => renderItem(item, index, dragHandle)}
        </ReorderableItem>
      ))}
    </View>
  );
}
