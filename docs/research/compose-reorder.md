# Research: drag-to-reorder in Compose (#226)

> Research notes for the M5 map (#219), on an unmerged branch. A snapshot taken on 2026-10-28 against
> `master` at 94edf09; not maintained.

**Question.** How does the native workout editor do drag-to-reorder in Compose, without a networked
transitive dependency, on the Kotlin and Compose the shell is pinned to, and without regressing what
screen-reader users can do today?

**Answer.** Use Calvin-LL's `sh.calvin.reorderable:reorderable`, **pinned at 3.1.0**, on a `LazyColumn`
that holds the whole editor, with the drag on the grip via `draggableHandle()`. Give each block row
two `CustomAccessibilityAction`s, "Move up" and "Move down", and a `stateDescription` of "Position n
of m". The library adds no a11y of its own, so that half is ours either way. Hand-rolling is a viable
fallback of about 200 lines, but it means owning auto-scroll and hit-testing, and those two are where
the RN version needed four bug-fix commits.

## 1. What today's RN version does

Sources: `src/components/reorderable-list.tsx`, `src/components/drag-handle-grip.tsx`,
`src/app/workout-editor.tsx`, `src/components/__tests__/reorderable-list.test.tsx`, and the history
`git log -- src/components/reorderable-list.tsx` (6f54f56, 265ed05, c2df328, da946b4, 736d6fc).

- **One list: the workout's blocks.** `ReorderableList` has exactly one caller, `workout-editor.tsx`,
  and its items are the blocks: single exercises and whole circuits. **Circuit members are not
  reorderable today.** `workout-circuit-block.tsx` only lets you remove a member, and only while more
  than two are left. The ticket's "blocks and circuit members" overstates the scope. The port needs
  one reorderable list, not a nested one.
- **Gesture.** An RNGH `Pan` with `activateAfterLongPress(150)`, on the ⣿ grip only
  (`DragHandleGrip`: 44×44 minimum target). The pickup fires a medium haptic. The drag
  `blocksExternalGesture(scrollRef)` the gesture-handler `ScrollView`, so a drag that starts on the grip
  outranks the scroll. Elsewhere on the screen, scrolling is untouched.
- **Model.** Rows stay in document order and are only `translateY`-ed during the drag. `data` changes
  once, on drop (`onReorder(next)`). The drop target is the slot whose centre is nearest to the dragged
  row's centre, measured on the UI thread at drag start, so the gap between rows counts
  (`targetIndexFor`). The dragged row lifts (scale 1.02, shadow), and its neighbours slide by one pitch.
- **No auto-scroll.** Dragging to the edge of the viewport doesn't scroll the `ScrollView`. On a long
  workout, you can only drop within what's on screen. (Nothing in the file scrolls, and the
  gesture-handler `ScrollView` is only given as an arbitration target.)
- **Keys are positional.** `keyExtractor` returns `exercise-${index}` / `circuit-${index}`, because a
  `WorkoutBlock` has no id and the same exercise can appear twice.

### What screen-reader users get today

The grip is the screen-reader path, so dragging and the a11y move are one control
(`DragHandle.a11yProps`):

- `accessibilityRole: 'adjustable'`, label "Reorder {{name}}" (`workoutEditor.reorderAccessibility`),
  value text "Position {{index}} of {{total}}" (`workoutEditor.positionOf`), and two actions,
  `increment` = "Move up" and `decrement` = "Move down". Moves are clamped rather than wrapped. The jest
  suite covers all of it: tests 1–5 of `reorderable-list.test.tsx`.
- **How that reaches TalkBack** (React Native 0.86.3,
  `ReactAndroid/.../uimanager/ReactAccessibilityDelegate.kt`): `ADJUSTABLE` is reported as class
  `android.widget.SeekBar` (line 466), and `increment` / `decrement` become the *standard*
  `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD` (lines 567–568), carrying our labels. So TalkBack
  sees a slider. It adjusts with its slider gestures, not through the custom-actions menu.
- Two things in today's path, found by reading the code. **Neither is verified on a device**:
  - **No announcement after a move.** RN schedules the "value changed" event for an adjustable only
    when `accessibilityValue` has no `text` key (`ReactAccessibilityDelegate.kt` lines 251–257). Ours
    is `{ text }`, so nothing is sent proactively. TalkBack may stay silent after a move until focus
    re-reads the node.
  - **Focus stays on the slot, not the block.** Keys are positional, so after "move up" the focused
    grip at index *i* now belongs to the block that used to be at *i−1*. A second "move up" moves
    *that* block, which undoes the first move. When the two blocks are of different kinds, the key
    changes from `exercise-i` to `circuit-i`, the node remounts, and focus is lost.

The port shouldn't copy either one. Section 4 says how to avoid them.

## 2. Candidates

### A. `sh.calvin.reorderable:reorderable` (Calvin-LL/Reorderable)

All of the following was read from Maven Central and GitHub, not from the README:

| | |
|---|---|
| Licence | Apache-2.0 (GitHub API `license.spdx_id`) |
| Latest | **3.1.0**, released 2026-04-20; before it, 3.0.0 (2025-08-12) and 2.5.1 (2025-06-08) (`maven-metadata.xml`, GitHub releases) |
| Maintenance | One maintainer, not archived, about 1.4k stars, 29 open issues. 3.1.0 only adds macOS; the last API change was 3.0.0. A slow cadence, but alive. |
| Built with | Kotlin **2.0.21**, Compose Multiplatform plugin 1.7.0, AGP 8.8.1 (repo `gradle/libs.versions.toml`). Published class metadata is **`mv=[1,9,0]`** (`javap -v` on `ReorderableLazyListState.class` in the 3.1.0 AAR). |
| Declares (`.module`, Android variant) | `kotlin-stdlib-jdk8` 1.9.0, `kotlin-stdlib-common` 1.9.0, `org.jetbrains.compose.{runtime,animation,foundation}` 1.7.0 |
| AAR manifest | `minSdkVersion 21`, **no `<uses-permission>`** |
| Classes | 171, all in `sh.calvin.reorderable`. A grep of the class files for `java/net`, `android/net`, `okhttp`, `HttpURLConnection` and `Socket` finds nothing. |

**Against the shell's pins** (`native/android/gradle/libs.versions.toml`: Kotlin 2.1.20, AGP 8.12.0,
Compose BOM 2025.05.01, which is androidx Compose **1.8.2** per the BOM's POM; `minSdk` 24):

- **Kotlin.** Metadata 1.9 is well within what a 2.1.20 compiler reads. This is the constraint that
  forced `kotlinxSerialization` 1.8.1, `kotlinxDatetime` 0.6.2, and plain `billing` over `billing-ktx`.
  3.1.0 clears it, but **a future release built with Kotlin 2.3 or later would not**. Pin the exact
  version in the catalog, with the same comment those entries carry, and add the library to M7's
  "moves with Kotlin" list.
- **Compose.** It needs 1.7, and the BOM gives 1.8.2. Built on Kotlin 2.0's Compose compiler, it runs
  on a newer runtime, which Compose guarantees backwards. No androidx artifact moves (see the diff
  below).
- **minSdk** 21 ≤ 24.

**Full transitive diff.** Resolved, not read from the POM. A scratch Android library on AGP 8.12.0,
Kotlin 2.1.20, the 2025.05.01 BOM, `material3` and `foundation`, with `releaseRuntimeClasspath`
resolved with and without `reorderable:3.1.0`. Adding it brings in:

- `sh.calvin.reorderable:reorderable` and `reorderable-android`;
- `org.jetbrains.compose.{runtime, runtime-saveable, animation, animation-core, foundation,
  foundation-layout, ui, ui-geometry, ui-graphics, ui-text, ui-unit, ui-util}`,
  `org.jetbrains.compose.annotation-internal:annotation` and
  `org.jetbrains.compose.collection-internal:collection`, all 1.7.0;
- `org.jetbrains.androidx.lifecycle:{lifecycle-common, lifecycle-runtime, lifecycle-runtime-compose,
  lifecycle-viewmodel}` 2.8.3;
- `org.jetbrains.kotlinx:atomicfu` and `atomicfu-jvm` 0.23.2.

**Every `org.jetbrains.compose.*` / `org.jetbrains.androidx.*` entry is a redirect with no artifact on
Android.** Its `release*` variant in the Gradle module metadata has no `files`, only a dependency on the
androidx module, e.g. `org.jetbrains.compose.foundation:foundation:1.7.0` → `androidx.compose.foundation:foundation:1.7.1`.
All of those androidx modules are already on the classpath at higher versions, so the version changes
in the resolved diff are only `1.7.1 -> 1.8.2` and `2.8.5 -> 2.8.7` arrows, with the old resolved
versions unchanged. `atomicfu-jvm` is a 70 KB jar of 34 entries with no networking classes. **No
dependency the library adds touches the network.** The only code it ships is `atomicfu-jvm` and its
own 171 classes.

**Fit for the editor.**
- `rememberReorderableLazyListState(lazyListState) { from, to -> … }` and `ReorderableItem(state, key)`
  inside `LazyColumn` items. **Only items wrapped in `ReorderableItem` are drop targets**
  (`reorderableKeys` in `ReorderableLazyCollection.kt`, about lines 336 and 545). So the name field,
  the block count, the add buttons and the pickers can be ordinary items in the same `LazyColumn`, and
  the whole editor scrolls as one, as it does in RN.
- **Auto-scroll at the edges** is built in (`scrollThreshold`, `Scroller.kt`), which RN lacks.
- **The non-lazy `ReorderableColumn` has no auto-scroll** (it doesn't use `Scroller`) and takes an
  `onSettle(from, to)` at drop. That is closer to RN's commit-on-drop, but inside a `verticalScroll` it
  would keep RN's "drop only within what's on screen". **Prefer the lazy one.**
- The handle: `Modifier.draggableHandle()` (drag starts on press, after touch slop) or
  `longPressDraggableHandle()`. Both take a pluggable `DragGestureDetector`. In Compose, the handle's
  pointer input sees events before the enclosing `LazyColumn`'s scroll does, so a drag that starts on
  the grip wins without RN's `blocksExternalGesture` workaround. RN's 150 ms hold was there only to
  leave scrolling to the rest of the row, and the grip isn't the rest of the row. Plain
  `draggableHandle()` is the natural port. Whether that counts as "no redesign" is the editor spec's
  call, and it should be checked on a device.
- **Contract difference:** the lazy state calls `onMove` *during* the drag, and the list must be
  updated before `onMove` returns (README FAQ, "flickers/jumps"). The draft therefore reorders live,
  not once on drop. For an in-memory draft that's harmless.
- **Accessibility:** the library sets no semantics. Its README's "Accessibility" section points to the
  demo app, whose `SimpleReorderableLazyColumnScreen.kt` adds "Move Up" / "Move Down"
  `CustomAccessibilityAction`s in app code, and recommends `clearAndSetSemantics` on the handle.

### B. Hand-rolled on `LazyColumn`

androidx keeps a reference implementation:
`compose/foundation/foundation/integration-tests/foundation-demos/.../LazyColumnDragAndDropDemo.kt`
(about 235 lines, of which about 150 are state). It uses `detectDragGesturesAfterLongPress` on the whole
list, offsets the dragged item by hand, swaps when the item crosses a neighbour, auto-scrolls with
`scrollBy`, and animates with `Modifier.animateItem()`. It has no handle, no keys for mixed item kinds
and no a11y.

- **For:** zero new dependencies, and no exposure to a future library release outgrowing the Kotlin pin.
- **Against:** we would own the hit-testing and the cancel/settle state machine. On RN those took 6f54f56
  through 736d6fc: off-by-a-gap targets, a dropped cancel, unmeasured rows. Calvin-LL's version has
  had three major versions of fixes for the Compose equivalents (3.0.0's breaking change exists to fix
its #94, for example).
  The a11y half costs the same either way.

### Rejected

- **`org.burnoutcrew.composereorderable` (aclassen/ComposeReorderable).** Apache-2.0, and the repo had
  a dependency bump in 2026, but its last Maven Central release is **0.9.6, from November 2022**
  (`maven-metadata.xml` `lastUpdated 20221113005710`). It predates `animateItem` and Compose 1.7.
  Taking a fork from JitPack would add a second repository to the build for one dependency.
- **A built-in androidx API.** None exists in foundation 1.8. `Modifier.dragAndDropSource/Target` is
  cross-app drag and drop through the platform `DragEvent`, not list reordering.

## 3. Recommendation

**Option A, `reorderable:3.1.0`, pinned.** The dependency is small (171 classes, plus redirects and
`atomicfu`), licensed and network-free, and readable by the pinned compiler. It solves the two parts
the RN version got wrong most often, hit-testing and settle. It also adds auto-scroll, which RN never
had. If the maintainer stalls, the code is Apache-2.0 and about 840 lines for the lazy state, which
can be vendored.

Cost to record on the map: one more entry for M7's unpin list (`docs/native-migration-plan.md`, M7,
"Unpin the Kotlin toolchain") and for M7's Data Safety re-check.

## 4. Accessibility for the port (either option)

TalkBack can't drag, so the move actions are the feature, not an extra:

- **Model it as custom actions, not a fake slider.** Compose's slider semantics
  (`progressBarRangeInfo` + `setProgress`) are for values. Google's documented pattern for reordering
  is `Modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { … }) }`
  (developer.android.com, "Key steps to improve Compose accessibility", custom actions), and it's what
  the library's demo does. TalkBack lists them in its Actions menu (and, as far as TalkBack's help
  goes, under its "Actions" reading control; not checked on the S25+). **That is a different gesture from today's
  slider adjust**, which nothing in the jest suite pins. Call it out in the spec rather than discover
  it in the end-of-migration TalkBack pass.
- **Where:** on the grip, which stays a 48dp target with a role and the label "Reorder {name}". The
  row itself can't merge, for the same reason RN keeps the props off the wrapper: the row holds a
  remove button, and a circuit also holds its fields and member remove buttons. The grip, carrying
  the actions, is the one focusable reorder control per block.
- **Clamp.** Leave "Move up" off the first block and "Move down" off the last, rather than offering an
  action that returns `false`. That is the clamping rule from test 3, made visible.
- **Announce the result.** Set `stateDescription` = "Position n of m" (`workoutEditor.positionOf`) on
  the grip, so it reads with focus and changes with the move.
- **Keep focus on the block that moved.** Give each draft block a stable synthetic key (e.g. a counter
  assigned when the draft is built or a block is added; `WorkoutBlock` itself has no id and duplicates
  are legal) and use it as both the `LazyColumn` item key and the `ReorderableItem` key. The grip's
  node then moves with the block, so repeated "Move up" keeps moving the same block, which is the
  second RN problem above. The library needs these keys anyway.
- **Tests (Compose UI, driven in `pt` per #193):** port the five a11y cases of
  `reorderable-list.test.tsx` as `performCustomAccessibilityActionWithLabel` plus assertions on the
  saved order. Add the clamp, the `stateDescription` text and "two moves up move the same block". The
  four `targetIndexFor` geometry tests have nothing to port to if the library owns hit-testing. A drag
  itself is a device check.
- The strings already exist in all three bundles (`workoutEditor.reorderAccessibility`, `positionOf`,
  `moveUp`, `moveDown`), and `:strings` generates them for `:app`. No new keys are needed.

## 5. Open risks

1. **Unverified on a device:** that a grip `draggableHandle()` beats the `LazyColumn`'s scroll on a
   real finger (the RN failure mode in c2df328); that TalkBack exposes the custom actions on the grip
   and reads the new `stateDescription` after a move; that focus follows a keyed item through the
   move.
2. **Library pinned below its future.** Any release built with Kotlin 2.3 or later can't be taken
   until M7. It's pinned like the four others already in that position.
3. **The live `onMove`** reorders the draft during the drag, where RN commits on drop. If the editor
   spec ever derives something expensive from the block order, debounce it to drop
   (`onDragStopped`).
4. **Gesture change.** RN holds 150 ms before a drag; `draggableHandle()` doesn't. The editor ticket
   should decide whether that's "no redesign".

## Sources

- Repo: `src/components/reorderable-list.tsx`, `drag-handle-grip.tsx`, `workout-block-row.tsx`,
  `workout-circuit-block.tsx`, `src/app/workout-editor.tsx`,
  `src/components/__tests__/reorderable-list.test.tsx`, `src/i18n/locales/en.json`
  (`workoutEditor.*`), `native/android/gradle/libs.versions.toml`, `native/android/app/build.gradle.kts`,
  `docs/native-migration-plan.md` (M5, M7).
- React Native 0.86.3, `node_modules/react-native/ReactAndroid/src/main/java/com/facebook/react/uimanager/ReactAccessibilityDelegate.kt`.
- Maven Central: `sh/calvin/reorderable/reorderable{,-android}/maven-metadata.xml`;
  `reorderable-android-3.1.0.{pom,module,aar}`; `org/jetbrains/compose/foundation/foundation/1.7.0/foundation-1.7.0.module`;
  `org/jetbrains/kotlinx/atomicfu-jvm/0.23.2`; `org/burnoutcrew/composereorderable/reorderable/maven-metadata.xml`.
- Google Maven: `androidx/compose/compose-bom/2025.05.01/compose-bom-2025.05.01.pom`.
- GitHub: `Calvin-LL/Reorderable` (README, releases, `gradle/libs.versions.toml`,
  `reorderable/src/commonMain/.../{ReorderableList,ReorderableLazyCollection,draggable}.kt`,
  `demoApp/.../SimpleReorderableLazyColumnScreen.kt`); `aclassen/ComposeReorderable`;
  `androidx/androidx` `compose/foundation/foundation/integration-tests/foundation-demos/.../LazyColumnDragAndDropDemo.kt`.
- Resolved dependency diff: a scratch Gradle project (AGP 8.12.0, Kotlin 2.1.20, BOM 2025.05.01),
  `:lib:dependencies --configuration releaseRuntimeClasspath` with and without the library.
- developer.android.com, "Key steps to improve Compose accessibility" → custom actions (linked from
  the library README's Accessibility section).
