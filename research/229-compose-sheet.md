# Research: a Compose sheet now that routing is native

> **Throwaway research note** for #229. It lives only on the `research/229-compose-sheet` branch and
> is never merged. It records facts, each with its source, and ends in a recommendation; the follow-up
> ticket makes the decision. Gathered 2026-11-10 against `master` at `51f4168` (Kotlin unpinned to
> 2.4.21, #356). Nothing was built or run on a device: every "device check" below is still open.

**How the library facts were read.** From the published `-sources.jar` of each artifact on Google
Maven (`https://dl.google.com/android/maven2/androidx/...`), at three versions: material3
**1.5.0-alpha17** (today's `material3Shipped`), **1.4.0** (what #362's BOM 2026.09.00 names), and
**1.5.0-rc01** (the newest 1.5.0 on 2026-11-10, the fallback #362 names). foundation at
**1.11.0-beta02** (today's `composeShipped`) and **1.12.1** (#362), and activity-compose **1.10.1**
(pinned). File and line references into a jar are written `material3@<version>:<path>:<line>`.
Repo paths are under `native/android/app/src/main/java/com/casco/kettle/shell/` unless they say
otherwise.

---

## 1. The answer

**Switch, to a sheet drawn in the shell's own Compose tree (candidate C below), built on foundation's
`AnchoredDraggableState`/`Animatable` and activity-compose's `PredictiveBackHandler`, with no Material
sheet. Do it after #362, not before, and treat it as optional rather than forced.**

- **Material3's `ModalBottomSheet` (A) is still out**, at every version checked. One of #191's two
  reasons is gone (the scrim tap can now be turned off), but the other half still holds, and a third
  reason replaces RN's. **Drag is all-or-nothing**: `sheetGesturesEnabled` switches the whole
  surface's drag, and a custom grabber can't drive the sheet because `SheetState`'s draggable state is
  `internal`. **The handle's tap dismisses.** And **it is still its own dialog window**, so the runner,
  a layer of the activity's window, can't be drawn over it (Program detail → session).
- **Material3's new non-dialog `BottomSheet` (B) fixes the window, and nothing else.** Drag is still
  whole-surface and the handle still dismisses on tap. It also **doesn't exist in 1.4.0**, which #362
  moves to, so taking it would mean shipping on alpha17 or waiting for 1.5.0 stable.
- **A shell-drawn Compose sheet (C) meets every requirement** with APIs that are stable at today's
  pins and at #362's. It deletes the bitmap pictures, the `NativeFrames` hold and roughly 950 lines of
  View code, and adds roughly 400–500 lines of Compose. The runner, a tab and a sheet become siblings
  in one composition, which is what makes the pictures and the hold unnecessary (§3).
- **Keeping the View host with Compose content (D, today's shape)** stays viable and costs nothing.
  A **partial** version (live tab under the scrim, `AnimatedContent` for sheet → sheet, View animators
  for the rest) is possible, but it keeps two animation clocks and `NativeFrames`, and is worse than
  either end (§4.3).

**Why switch at all, if D works?** The View sheet's machinery (pictures, the hold and the window-wide
frame cancel, repaint plumbing, raw-coordinate drag) exists to stack two UI systems that no longer
both exist. Each picture costs 8–28ms on the editors (#177, quoted in `ModalSheet.kt`'s `pictureOf`),
and a hold that misses holds the whole window for up to 500ms (`NativeFrames.HOLD_MS`). **Why after
#362:** #362's acceptance already re-checks "the sheets and `SheetChrome`" on the API 37 AVD in light
and dark. Landing C first would mean verifying the new sheet against Compose 1.11/material3 alpha17,
then again against 1.12.1/1.4.0. Nothing C uses changes between them (§5), so the order is about
verifying on a device once, not about API availability.

**CMP doesn't force this.** #344's findings (`git show origin/research/m8-ios-ui:research/m8-ios-ui.md`,
§2 Tier A and §5) say CMP on iOS "needs *some* non-View sheet: a Compose one (#229's work, done first),
or SwiftUI's `.sheet` hosting Compose content". #345 chose the second (`docs/decisions.md`, the M8 entry:
"the iOS sheet (SwiftUI's `.sheet` hosting Compose, which is why a Compose sheet, #229, stays
optional)"). On Android, moving the screens into a KMP UI module changes nothing about the sheet:
`ShellLayout.nativeSheet` is a `ComposeView` that calls `RouteContent(route)`, and it can call a
composable from a `commonMain` module as easily as one from `:app`. The View sheet is Android glue in
`:app`, beside `MainActivity` and `RunnerService`, all of which stay Android-only under CMP. So the
M8 note's line "the View sheet replaced (#229)" holds only for the rejected Compose-on-iOS sheet
option. Under #345's choice, the switch is optional. Candidate C would also live in `:app` (it uses
`androidx.activity.compose.PredictiveBackHandler`), so it isn't a step towards an iOS sheet either.

---

## 2. Candidates against the requirements

**What the sheet has to keep**, from #155, #179 and #191, as `SheetStage`, `SheetChrome` and
`SheetMotion` implement it today: grabber and × in a strip (`SheetStripHeight`), the top at the
status bar + 12dp, a 640dp cap, 16dp top corners, a drag from the grabber only, no scrim-tap or
content-drag dismissal, Material's predictive shrink (to 90%, `ModalSheet.PREDICTIVE_SHRINK`), every
dismissal a `navigator.back()`, #155's transitions table (`SheetTransition.kt`), the runner over a
sheet with grow/shrink, and TalkBack reading × → grabber → content
(`SheetChrome.init`, `readBefore`).

| | A. M3 `ModalBottomSheet` | B. M3 `BottomSheet` (non-dialog) | C. Shell-drawn Compose sheet | D. Keep the View host |
|---|---|---|---|---|
| **Exists at today's pins** (m3 1.5.0-alpha17, foundation 1.11.0-beta02) | Yes | Yes (added 1.5.0-alpha15) | Yes | Yes (it ships) |
| **Exists at #362's pins** (m3 1.4.0, foundation 1.12.1) | Yes | **No** (back in 1.5.0-rc01) | Yes | Yes |
| **Grabber-only drag** | **No.** Whole surface or nothing | **No.** Same | **Works** | **Works** |
| **No scrim-tap dismiss** | **Works** (`shouldDismissOnClickOutside = false`), but the handle's tap still dismisses | **Works** (it draws no scrim), but the handle's tap still dismisses | **Works** | **Works** |
| **Predictive back** | **Partial.** Built in, but its commit hides the whole sheet, wrong for sheet → sheet | **Partial.** Same, or off via `backHandlerEnabled` | **Works** | **Works** |
| **Runner over a sheet** (Program detail → session) | **No.** Separate dialog window | **Works** | **Works** | **Works** |
| **TalkBack: × → grabber → content** | Works with `traversalIndex` (unverified) | Works with `traversalIndex` (unverified) | Works with `traversalIndex` (unverified on a device) | **Works** (shipped) |
| **#155's transitions** (slide up/down, shared axis X, fade through, grow/shrink) | No: Material's own spring, no fade-through or grow | Partly: same open/close, rest by hand | Works: written to the same constants | Works |

The cells, with sources:

### A. Material3 `ModalBottomSheet`

- **Still a dialog window.** `ModalBottomSheet` wraps its content in `ModalBottomSheetDialog`
  (`material3@1.4.0:commonMain/.../ModalBottomSheet.kt:170`, `@1.5.0-alpha17:...ModalBottomSheet.kt`
  `ModalBottomSheetDialog(` call), whose Android actual is a `ComponentDialog` with its own window
  (`material3@1.4.0:androidMain/.../ModalBottomSheet.android.kt:458`,
  `@1.5.0-alpha17:androidMain/.../ModalBottomSheet.android.kt:311`, unchanged in 1.5.0-rc01 at
  `:315`). The runner is `ShellLayout.nativeRunner`, a `ComposeView` in the activity's window
  (`nav/ShellLayout.kt`), so it is drawn under any dialog. Presenting the runner over a sheet would
  mean moving the runner into a window of its own too, which changes how it's attached on launch
  (`MainActivity.attachRunner`) and how its system bars are set (`PreferredKettleTheme(runnerShown)`).
  That's out of all proportion to a sheet change. **#191's reason (a) is gone with RN, but the dialog
  window still matters, for the runner instead.**
- **Scrim tap: can be turned off.** `ModalBottomSheetProperties(shouldDismissOnClickOutside = false)`
  exists at both versions. In 1.4.0 it gates the scrim's `detectTapGestures`
  (`material3@1.4.0:commonMain/.../ModalBottomSheet.kt:191`, `Scrim(dismissEnabled = ...)` at `:514–526`).
  In alpha17 it gates the scrim's `onClick` (`...ModalBottomSheet.kt:151`). **This half of #191's
  reason (b) no longer holds.**
- **Drag from the content: can't be turned off without turning off all drag.** `sheetGesturesEnabled`
  gates both the surface's `nestedScroll` connection and its `anchoredDraggable`
  (`material3@1.4.0:.../ModalBottomSheet.kt:283, 330`, `@1.5.0-alpha17:.../SheetDefaults.kt:309, 355`).
  The surface is the whole sheet, content included. With it off, nothing drags, the grabber included.
  A custom grabber can't move the sheet itself either: `SheetState`'s public surface is
  `show`/`hide`/`expand`/`partialExpand`, `currentValue`/`targetValue`/`isVisible` and `requireOffset`,
  and `anchoredDraggableState` is `internal` (`material3@1.4.0:.../SheetDefaults.kt:278`,
  `@1.5.0-alpha17:.../SheetDefaults.kt:646`, `@1.5.0-rc01:.../SheetDefaults.kt:336`).
  **This half of #191's reason (b) still holds.**
- **The handle's tap dismisses.** The drag handle is wrapped in `Modifier.clickable { Expanded ->
  animateToDismiss() }` whatever `sheetGesturesEnabled` says
  (`material3@1.4.0:.../ModalBottomSheet.kt:386`, `@1.5.0-alpha17:.../SheetDefaults.kt:389`). That's
  exactly the stray tap `SheetChrome.drag` refuses. The only ways out are `dragHandle = null`, which
  draws our own grabber that can't drag anything (above), or a `confirmValueChange` that vetoes
  `Hidden`, which also vetoes the drag dismissal we want.
- **Predictive back: built in, but it dismisses the whole sheet.** 1.4.0 registers its own
  `OnBackPressedCallback` on the dialog's dispatcher, which scales the sheet and then calls
  `onDismissRequest` (`material3@1.4.0:androidMain/.../ModalBottomSheet.android.kt:526–537, 611–640`).
  alpha17 uses `PredictiveBackHandler` and `hide()`s before `onDismissRequest`
  (`@1.5.0-alpha17:.../SheetDefaults.kt:185`). Kettle's back from a stacked sheet goes to the sheet
  beneath along the shared axis (`Transition.Backward`), not off screen. Getting that would mean
  `shouldDismissOnBackPress = false` and a back handler of our own, which loses Material's shrink: its
  progress `Animatable` is private to the component. Back also lands on the **dialog's** dispatcher,
  not the activity's that `MainActivity` registers the shell's and sheet's callbacks on.
- **IME:** 1.4.0 applies `imePadding()` to the sheet's root unconditionally
  (`material3@1.4.0:.../ModalBottomSheet.kt:186`). The editors pad for the IME themselves
  (`exerciseeditor/ExerciseEditorScreen.kt` and three others match `imePadding|WindowInsets.ime`). The
  release notes record the fix in 1.5.0-alpha19 ("`imePadding` was applied unconditionally",
  [compose-material3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)),
  which is past #362.

### B. Material3 `BottomSheet`, the non-dialog one

- **In-window**: "renders **directly in the composition hierarchy** (the main UI tree), unlike
  [ModalBottomSheet] which launches a separate Dialog window", with no scrim of its own
  (`material3@1.5.0-alpha17:commonMain/.../SheetDefaults.kt:105–113`, the function at `:145`; moved to
  `BottomSheet.kt:117` in 1.5.0-rc01, matching the alpha18 release note "`BottomSheet` composable has
  been moved to `BottomSheet.kt`"). The release notes introduce it in 1.5.0-alpha15 as a "standalone
  static sheet component" ([releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)).
  That the alpha15 line means this composable is my reading of the note. The source confirms it's in
  alpha17 and rc01 and **absent from 1.4.0** (no `fun BottomSheet(` anywhere in the 1.4.0 sources jar).
  `@ExperimentalMaterial3Api` throughout.
- So the runner goes over it (it's a sibling in one tree), and no scrim tap exists unless we draw one.
- **The same drag and handle problems as A**: the same `gesturesEnabled` gates the whole surface's
  `anchoredDraggable` (`@1.5.0-alpha17:.../SheetDefaults.kt:355`, `@1.5.0-rc01:.../BottomSheet.kt:329–332`),
  the same `internal` state, and the same `clickable` handle (`@1.5.0-rc01:.../BottomSheet.kt:366`).
- **Predictive back** via `PredictiveBackHandler(enabled = backHandlerEnabled && state.isVisible)`,
  which hides the sheet on commit (`@1.5.0-alpha17:.../SheetDefaults.kt:185`): the same sheet → sheet
  problem as A.
- **Not viable on #362's pins**, and it fails grabber-only drag anyway. Dropped from the cost section.

### C. A sheet drawn in the shell's own Compose tree

One composition for the shell: the tab's `NavHost`, a scrim, the sheet and the runner as siblings in
a `Box`, with the bar under them, in place of `ShellLayout`'s four `ComposeView`s and the View stage.

- **Grabber-only drag: works.** `Modifier.anchoredDraggable(state, orientation, ...)` and
  `AnchoredDraggableState<T>` are public with no opt-in annotation at foundation 1.11.0-beta02 and
  1.12.1 (`foundation@<both>:commonMain/androidx/compose/foundation/gestures/AnchoredDraggable.kt:107`,
  class at `:779`). Putting the modifier on the grabber's node alone and reading `state.offset` into
  the sheet's `Modifier.offset` makes only the grabber start a drag. A draggable on a node that moves
  with its own drag is what Material does (`anchoredDraggable` on the very `Surface` it offsets,
  `material3@1.4.0:.../ModalBottomSheet.kt:330`), so `SheetChrome`'s reason for being a View ("only a
  raw screen coordinate stays still under a moving host") applies to a `ComposeView` moved by View
  translation, not to a node moved inside its own composition. Two anchors, shown and hidden, and
  `confirmValueChange`/a settle callback that calls `navigator.back()` in place of a hide keep "every
  dismissal is a back". The 25%/1000dp-per-second thresholds (`ModalSheet.letGo`) map onto
  `AnchoredDraggableDefaults.flingBehavior(positionalThreshold, ...)` (same file, `:1518`), or onto a
  plain `draggable` + `VelocityTracker` if the thresholds have to match exactly.
- **No scrim-tap dismiss: works.** The scrim is ours. It takes pointer input to block the tab and does
  nothing with it.
- **Predictive back: works.** `PredictiveBackHandler(enabled, onBack: suspend (Flow<BackEventCompat>) -> Unit)`
  is in the pinned activity-compose 1.10.1 (`activity-compose@1.10.1:androidx/activity/compose/PredictiveBackHandler.kt:72`).
  It registers on the activity's dispatcher (the shell has no dialog), so `MainActivity`'s ordering,
  with the sheet's callback added after the shell's, is kept. The progress drives a `graphicsLayer`
  scale to `1 - 0.1·progress` about the bottom centre (`SheetStage.scale`), and commit calls
  `navigator.back()`, which then plays the table's transition, `Backward` included. The manifest
  already opts in (`app/src/main/AndroidManifest.xml:51`, `enableOnBackInvokedCallback="true"`).
- **Runner over a sheet: works.** The runner is the top sibling. Grow and shrink become one
  `Animatable` read by the sheet's top offset and corner radius, which is what `SheetMotion.grow`
  does with translation and `round()` today.
- **TalkBack order: expected to work, unverified on a device.** Draw the chrome after the content so
  it's on top, and give it a lower `traversalIndex` inside the sheet's `isTraversalGroup`. Material
  uses the same pair (`material3@1.4.0:.../ModalBottomSheet.kt:186` `isTraversalGroup`,
  `@1.5.0-alpha17:.../SheetDefaults.kt:360` `traversalIndex`). The × then the grabber is a second
  `traversalIndex` step. The grabber's three actions (`SheetChrome.grabTalkBackActions`: click,
  dismiss, collapse, each a back) become `semantics { onClick; dismiss; collapse }`, the same
  semantics Material's handle sets (`material3@1.4.0:.../ModalBottomSheet.kt:386–420`), with `role =
  Role.Button`. The covered tab is hidden from TalkBack with semantics rather than `INVISIBLE`, since
  it now stays drawn (§3).
- **The transitions table: works.** `transitionBetween` (`nav/SheetTransition.kt`) is pure and stays
  as it is. Each `Transition` becomes a Compose animation with the same durations and the same cubic
  curves (`SheetMotion`'s companion constants map one to one onto `tween(ms, CubicBezierEasing(...))`).
  The look (§1 of #179) is the same shapes in a `Modifier`: `widthIn(max = 640.dp)`, a top inset of
  status bar + 12dp, a `RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)` clip.

### D. Keep the View host with Compose content inside

Today's code. Every row works, because it ships and passed #155's device acceptance. Its one gap
against the issue is cost, not behaviour: the pictures and the hold (§3).

---

## 3. The pictures, and whether `NativeFrames`' wait survives

**Why the pictures exist today.** Three of them, all `drawToBitmap` (`ModalSheet.willMove`, `pictureOf`):

1. **The tab under an opening sheet** (`tabPicture`). `ShellLayout.show` makes the tabs' `ComposeView`
   `INVISIBLE` the moment a sheet shows, "since a view left showing under another still takes touches
   and TalkBack's focus" (`nav/ShellLayout.kt`), so the scrim needs a picture of it to sit on.
2. **The sheet leaving** (`sheetPicture`), on close and on sheet → sheet. There is one `nativeSheet`
   `ComposeView`, and `NativeSheet` recomposes straight to the new top of the stack (`key(route)` in
   `nav/Shell.kt`). The old route's content is gone before its exit animation can play.
3. **The held picture** (`heldPicture`), while a hold is still waiting.

The original reason (an RN surface and native views stacking, #155/#191) is gone. What's left is the
shell's own split into separate `ComposeView`s with a View animator over them.

**Under C, none survives.**

- (1) The tab stays composed and drawn under the scrim, and nothing pictures it. It's already
  composed today (`INVISIBLE` keeps it laid out, per `ShellLayout.show`'s comment). Touches stop at the
  scrim, and TalkBack is kept off it with `Modifier.clearAndSetSemantics {}` (or
  `semantics { hideFromAccessibility() }`) while a sheet shows.
- (2) `AnimatedContent(targetState = navigator.sheet)` keeps the outgoing route composed until its
  exit finishes, and `AnimatedVisibility` does the same for the last sheet closing. So the old screen
  slides out live, not as a frozen frame. The one behaviour change: an outgoing screen runs for the
  ~200–300ms of its exit. Only `support/SupportScreen.kt` has an `onDispose` (`jar.close()`), so the
  billing client closes 300ms later. Harmless, but a device check, and the reason `key(route)` stays
  inside the content lambda.
- (3) Goes with the hold.

If some screen ever proves too heavy to keep live through its exit, Compose's own picture is the
fallback: `rememberGraphicsLayer()` + `drawWithContent { layer.record { ... } }` replays a recorded
display list rather than copying pixels. That's a fallback, not a plan, and it isn't needed for
anything known today.

**`NativeFrames`' wait-for-first-composition does not survive C.** It exists because the transition
runs in the View system (`ValueAnimator`, started on a pre-draw), while the content it moves is
composed by a *different* composition, `nativeSheet`'s. The View side can't know when that composition
has applied, so `NativeSheet` reports it (`onComposed`), and `NativeFrames` cancels every frame
(`onPreDraw` returns `false`) until it does, with a 500ms backstop (`NativeFrames.kt`). The test
`a sheet arrives within frames, not at the backstop` (`ShellTest.kt:291`) pins that.

In C, the navigator's state change, the arriving route's composition and the animation's first frame
happen in one composition on one frame clock. `AnimatedContent` composes the target content in the
same recomposition that starts the transition, and its first animated frame is the first frame drawn
with that content. There's no second composition to wait on, so no hold and no window-wide frame
cancel. `SheetMotion.present`'s extra `doOnPreDraw` frame, the workaround for "the drawn signal ran a
frame early" on an open, goes for the same reason. **What doesn't change:** a screen that reads its
files asynchronously still draws its loading state in the first frame. That's true today too, since
`NativeFrames` waits for composition, not for data.

Under D, and under the partial version in §4.3, `NativeFrames` stays: the sheet's transition is still
a View animation over a separate composition.

---

## 4. Cost per viable candidate

A and B fail grabber-only drag, and A fails the runner, so neither is costed. Line counts are
`wc -l` at `51f4168`. Added lines are estimates.

### 4.1 C, the shell-drawn Compose sheet

**Deleted** (≈ 950 lines of main code):

| File | Lines | What goes |
|---|---|---|
| `nav/ModalSheet.kt` | 167 | All of it: `willMove`/`moved`/`pictureOf`, the three bitmaps, `letGo`, `repaint`, the `OnBackPressedCallback` (replaced by `PredictiveBackHandler`) |
| `nav/SheetStage.kt` | 205 | All of it: the backdrop and leaving `ImageView`s, scrim and `sheetBack` views, `ViewOutlineProvider`, layout params |
| `nav/SheetMotion.kt` | 252 | All of it, except the timing and easing constants, which move |
| `nav/NativeFrames.kt` | 83 | All of it |
| `nav/SheetChrome.kt` | 229 | The View half, about 200 lines: `FrameLayout`/`ImageView`, `VelocityTracker`, `AccessibilityDelegateCompat`, `GradientDrawable` painting. `SheetStripHeight` and the dp constants stay |
| `nav/ShellLayout.kt` | 105 | The `LinearLayout` of four `ComposeView`s and `show(place)`, rewritten as one composable (≈ 50 lines) |
| `MainActivity.kt` | ≈ 25 of 287 | The `ModalSheet` factory, `navigator.moves = sheet`, `sheet.backCallback`, `sheet?.repaint(colors)` and its `LaunchedEffect`, the `colors = { preferredColors(...) }` lambda |
| `nav/Navigator.kt` | ≈ 15 of 198 | `MoveListener` and `moves`, unless kept to feed the transition (C can read `Move` from state instead) |

**Kept as is:** `nav/SheetTransition.kt` (87, the pure table), `Navigator`'s stack and `Move`/`MoveKind`,
`nav/Shell.kt`'s `Destinations`, `NativeRunner` and `TabBar` (`NativeSheet` folds into the new host),
`nav/Route.kt`.

**Added** (≈ 400–500 lines):

- **`SheetHost`** (≈ 250–300 lines): the scrim, the sheet's shape and insets, `AnimatedContent` keyed on
  the sheet route with the transitions mapped from `transitionBetween`, the grow/shrink `Animatable`
  for the runner, the grabber's `AnchoredDraggableState` with release → `navigator.back()`, and
  `PredictiveBackHandler`.
- **Compose `SheetChrome`** (≈ 120 lines): × and grabber as composables, their semantics, their
  traversal order, 48dp targets, and the existing `common_close`/`common_dragHandle` strings (no new
  locale keys).
- **The shell as one `setContent`** (≈ 50 lines, in place of `ShellLayout`).

**Net:** about −450 to −550 lines, plus no `Bitmap`s and no frame hold.

**Tests** (`app/src/test/java/com/casco/kettle/shell/nav/`):

| File | Fate |
|---|---|
| `NativeFramesTest.kt` (111, 6 cases) | **Deleted** with `NativeFrames` |
| `SheetChromeTest.kt` (123, 7 cases) | **Rewritten** as compose-test semantics assertions, the same 7 cases: × labelled 48dp button that goes back, icon one em of 20sp, grabber 48dp target with TalkBack's close, TalkBack double-tap closes, labels from the bundles, × read before grabber, chrome read before content. The order cases become `traversalIndex` assertions, which still need a TalkBack check on a device |
| `SheetTransitionTest.kt` (125, 9 cases) | **Split.** The six `transitionBetween` cases stay unchanged. The three `SheetStage`/`SheetMotion` cases (`the live view takes the sheet's shape...`, `a hand-over brings the live view in under the old sheet's picture`, `a sheet closes from where it was dragged to`) are rewritten against the Compose host with `mainClock` control. The hand-over one becomes "the old sheet's screen is still composed while the new one enters" |
| `ShellTest.kt` (383, 16 cases) | **Harness rewritten**: `show()` builds `ShellLayout` + `ModalSheet` today, and `go()` dispatches pre-draws by hand and idles the main looper for the `ValueAnimator`. Under C it's `setContent` and `mainClock.advanceTimeBy`. `the sheet takes back only while a sheet shows` (reads `backCallback.isEnabled`) is rewritten to observe the back handler's enabled state. `a sheet arrives within frames, not at the backstop` is **deleted** (there is no backstop). `a pushed sheet shows its screen in the sheet's view` loses its `nativeSheet.visibility` assertion. The other 13 cases (bar, tabs, splash, `pt`/`ja` labels, strip offset, sheet over sheet, close, runner over sheet) keep their assertions. The `@GraphicsMode(NATIVE)` comment ("the sheet pictures the screen before every move") stops being the reason; `a tab paints the theme's background` still draws by hand |
| `NavigatorTest.kt` | Unchanged, unless `MoveListener` goes |

**Device checks** that come back, since #155's acceptance was against the View sheet: the frame tiles
for open, close, push, replace and grow (no wrong-route frame, no blank flash), TalkBack order by ear
and with a uiautomator dump, the keyboard in the editors (#180), predictive back on API 37, and
`scripts/route-check.sh` plus `scripts/session-check.sh` (the runner's layer moves).

### 4.2 D, keep

Deletes and adds nothing. One clean-up is due whatever is decided: comments still describe RN
(`SheetChrome`'s "RN's `ModalHeader`" geometry notes, `SheetStage.place`'s "before RN's content draws
under them"). That's a comment pass, not this ticket.

### 4.3 Partial (View host kept, bitmaps replaced)

Possible, and listed because the issue names it. Keep `ShellLayout`'s tabs `VISIBLE` under the scrim
(deletes `tabPicture`), and put `AnimatedContent` inside `NativeSheet` so sheet → sheet and close
animate live (deletes `sheetPicture`/`heldPicture`/`leaving`). That deletes ≈ 120 lines and keeps
`ModalSheet`, `SheetStage`'s shaping, `SheetMotion`'s open/grow/drag/predictive and `NativeFrames`.
The cost is that one transition is now driven by two clocks, a View `ValueAnimator` moving the
`ComposeView` while Compose animates inside it. And the tab, left `VISIBLE`, needs
`importantForAccessibility` and a touch-blocking scrim to replace what `INVISIBLE` gave for free.
**Not recommended**: it keeps most of C's risk (the transitions change on a device) for a fifth of
the deletion.

---

## 5. Pins: today vs #362

| What C uses | Today (`gradle/libs.versions.toml` at `51f4168`) | #362 (BOM 2026.09.00) | Source |
|---|---|---|---|
| `Modifier.anchoredDraggable`, `AnchoredDraggableState`, `AnchoredDraggableDefaults` | foundation `composeShipped` 1.11.0-beta02, public, no opt-in | foundation 1.12.1, the same | `foundation@1.11.0-beta02` and `@1.12.1:commonMain/.../gestures/AnchoredDraggable.kt:107, 779, 1518` |
| `AnimatedContent`, `AnimatedVisibility`, `Animatable`, `tween`/`CubicBezierEasing` | animation 1.11.0-beta02 | 1.12.1 | Already used by the app (`compose-animation` is a dependency, `app/build.gradle.kts:293`) |
| `traversalIndex`, `isTraversalGroup`, semantics actions | ui 1.11.0-beta02 | ui 1.12.1 | Used by Material at both 1.4.0 and alpha17 (cited in §2), which depend on ui ≥ 1.8.2 (`material3-android-1.4.0.pom`) |
| `PredictiveBackHandler` | activity-compose 1.10.1 | unchanged by #362 | `activity-compose@1.10.1:.../PredictiveBackHandler.kt:72` |
| Material3 | not used by the sheet | not used by the sheet | |

BOM 2026.09.00 → foundation/ui 1.12.1 and material3 1.4.0: read from
`https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom`.

**So C runs on today's pins and on #362's.** It's sequenced after #362 for the device pass (§1), not
for any API. Kotlin 2.4.21 (`kotlin`) is irrelevant to every candidate. detekt's 2.0.21 PSI
(`detektKotlin`) only constrains language features, and none of this needs a new one.

**A runs on both. B runs only on today's material3 1.5.0-alpha17**, or on 1.5.0 once stable (1.5.0-rc01
was published 2026-11-04 per the
[release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3), and is
listed in `material3/maven-metadata.xml`). Its rc01 POM wants foundation/ui 1.12.0, so it would sit on
#362's 1.12.1. That's moot, since B fails grabber-only drag.

---

## 6. Network and the zero-data claim

**No candidate adds an artifact.** C uses only `androidx.compose.foundation`, `.animation`, `.ui` and
`androidx.activity:activity-compose`, all already `implementation` dependencies of `:app`
(`app/build.gradle.kts:289–300`). A would use `androidx.compose.material3`, also already one (`:295`).
None of these is a networking library, and material3 1.4.0's own POM lists only Compose, AndroidX
annotation/collection/lifecycle and the Kotlin stdlib (`material3-android-1.4.0.pom`). Nothing is
fetched at runtime, so the Play listing's zero-data declaration is untouched by any of them.

---

## 7. What this note couldn't verify

- **TalkBack order under C or A.** That `traversalIndex` inside an `isTraversalGroup` reads the × →
  grabber → content as `SheetChrome`'s `accessibilityTraversalBefore` does is inferred from the API
  and from Material's use of it, not heard on a device.
- **That C's first frame matches the View sheet's frame tiles** (no wrong-route frame, no blank). It's
  argued from how `AnimatedContent` composes its target (§3), not measured.
- **The 1.5.0-alpha15 release-note line** ("Introduces standalone static sheet component") being the
  `BottomSheet` composable is a reading of the note's wording. Its presence in alpha17 and rc01 and its
  absence from 1.4.0 are read from source.
- **The IME behaviour** of the editors inside A at 1.4.0 (`imePadding` on the sheet root) was read
  from source only.

---

## Sources

- Material3 sources jars, Google Maven:
  [`material3-android-1.5.0-alpha17-sources.jar`](https://dl.google.com/android/maven2/androidx/compose/material3/material3-android/1.5.0-alpha17/material3-android-1.5.0-alpha17-sources.jar),
  [`material3-android-1.4.0-sources.jar`](https://dl.google.com/android/maven2/androidx/compose/material3/material3-android/1.4.0/material3-android-1.4.0-sources.jar),
  [`material3-android-1.5.0-rc01-sources.jar`](https://dl.google.com/android/maven2/androidx/compose/material3/material3-android/1.5.0-rc01/material3-android-1.5.0-rc01-sources.jar).
  Files: `commonMain/androidx/compose/material3/ModalBottomSheet.kt`, `SheetDefaults.kt`,
  `BottomSheet.kt` (rc01), `androidMain/androidx/compose/material3/ModalBottomSheet.android.kt`.
- Foundation sources jars:
  [`foundation-android-1.11.0-beta02-sources.jar`](https://dl.google.com/android/maven2/androidx/compose/foundation/foundation-android/1.11.0-beta02/foundation-android-1.11.0-beta02-sources.jar),
  [`foundation-android-1.12.1-sources.jar`](https://dl.google.com/android/maven2/androidx/compose/foundation/foundation-android/1.12.1/foundation-android-1.12.1-sources.jar),
  `commonMain/androidx/compose/foundation/gestures/AnchoredDraggable.kt`.
- [`activity-compose-1.10.1-sources.jar`](https://dl.google.com/android/maven2/androidx/activity/activity-compose/1.10.1/activity-compose-1.10.1-sources.jar),
  `androidx/activity/compose/PredictiveBackHandler.kt`.
- [Compose BOM 2026.09.00 POM](https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom);
  [material3 `maven-metadata.xml`](https://dl.google.com/android/maven2/androidx/compose/material3/material3/maven-metadata.xml);
  [material3-android 1.4.0 POM](https://dl.google.com/android/maven2/androidx/compose/material3/material3-android/1.4.0/material3-android-1.4.0.pom).
- [Compose Material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
  (1.5.0-alpha15, alpha18, alpha19, rc01 entries).
- This repo at `51f4168`: `native/android/app/src/main/java/com/casco/kettle/shell/nav/{ModalSheet,SheetStage,SheetMotion,SheetChrome,NativeFrames,SheetTransition,ShellLayout,Shell,Navigator,Route}.kt`,
  `shell/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`,
  `gradle/libs.versions.toml`, the tests in `app/src/test/java/com/casco/kettle/shell/nav/`,
  `docs/decisions.md` (the M8 entry).
- Issues: #229 (and its Agent Brief), #155, #179, #191 (decision comment), #197, #309, #345, #362;
  #344's findings at `origin/research/m8-ios-ui:research/m8-ios-ui.md`.
