# How the shell learns RN has drawn a route (#178)

Throwaway branch, never merged. These are research findings from reading the sources in
`node_modules`; nothing was run on a device. Versions: RN 0.86.3, React 19.2.3, expo-brownfield
57.0.24, expo-router 57.0.21 (which bundles React Navigation), react-native-screens 4.26.2.

Path shorthands: `RA` = `node_modules/react-native/ReactAndroid/src/main/java/com/facebook/react/`,
`RC` = `node_modules/react-native/ReactCommon/react/`. The parent session re-checked the
load-bearing citations (marked ✓) against the sources before publishing.

## Recommendation

No existing signal is both early and guaranteed. Two small changes give a reliable one:

1. **JS sends the route report one task later:** `setTimeout(() => reportRoute(pathname), 0)` inside
   the existing `useEffect`. When the message arrives, that commit's mount items are already queued
   for the UI thread.
2. **Native waits three steps after receiving the report:**
   1. the next Fabric `didDispatchMountItems` (a `UIManagerListener`),
   2. then one `Handler.post` on the main looper,
   3. then the surface's next `OnPreDrawListener`.

   That pre-draw is the first frame showing the new route. Start the sheet animation there, in both
   directions.

**Snapshot:** possible without input hooks. Capture in `UIManagerListener.willMountItems`, armed by
a JS message sent synchronously before the navigation commit. React Navigation's
`__unsafe_action__` container event fires before the router's `setState`, and it fires for every
action, including a TalkBack double-tap.

**How sure each part is:**

- **Guaranteed by RN's code:** the RN half. The batch is queued before the report arrives, and it
  mounts and is drawn in the same frame.
- **True by reading react-native-screens 4.26.2, not by any contract:** the extra main-looper hop
  (§4). Re-check it on every upgrade.
- **Worst case:** the signal is one frame late, never early.
- **Still to do:** confirm on the device with frame tiles, as #177 did.

## 1. Why the prototype ran early

The route report comes two React commits after the screen changes, yet it reaches native before
either commit has mounted.

- **Commit A renders the new screen.** The router dispatch is in
  `expo-router/build/react-navigation/core/useOnAction.js:73-79`.
- **`onStateChange` runs in a `useEffect` after A** (`…/core/BaseNavigationContainer.js:247-308`).
  expo-router's store (`global-state/store.js:102-126`) then feeds `usePathname` through
  `useSyncExternalStore` (`global-state/useRouteInfo.js:10`).
- **Commit B re-renders, and its `useEffect` calls `reportRoute`** (`src/app/_layout.tsx:79-80`).
- **`sendMessage` is synchronous** (`expo-brownfield/.../ExpoBrownfieldModule.kt:31`,
  `Messaging.kt:30-32`). Native gets the message on the JS thread straight away, and `JsBridge`
  posts it to main.
- **Bridgeless React commits don't mount when they commit.** `completeSurface` passes
  `mountSynchronously = false` (`RC/renderer/uimanager/UIManagerBinding.cpp:480-485` ✓). The mount
  is queued as a rendering update (`RC/renderer/scheduler/Scheduler.cpp:302-336`) and handed to Java
  only at the end of the JS event-loop task (`RuntimeScheduler_Modern.cpp:294-323`, `332-359`).
  `RC/renderer/mounting/ShadowTree.h:63-68` gives the reason: it lets layout effects run before
  paint.
- **A press is a sync-lane commit, so A, B and the report all happen in one task.** Passive effects
  flush synchronously at the end of the commit (`ReactFabric-prod.js:9817`), and the store update
  re-renders in a microtask. So the report leaves JS before `scheduleMountItem` has even run.
- **Java only queues the batch** (`RA/fabric/FabricUIManager.java:909-958`). The next `DISPATCH_UI`
  Choreographer callback applies it (`:1536-1631`).
- **react-native-screens then adds one more hop (§4).**

## 2. Candidate (c): a JS message after mount

| JS hook | When it reaches native | Guaranteed? |
|---|---|---|
| `useEffect` (today) | Before the batch is queued, for sync-lane commits | No |
| `useLayoutEffect` | Earlier still, by design (`ShadowTree.h:63-68`) | No: always early |
| `requestAnimationFrame` | Bridgeless implements it as `setTimeout(0)` (`RC/runtime/TimerManager.cpp:359-366` ✓) | Only "queued", not "mounted" |
| `setTimeout(0)` | In a later task, after `addMountItem` | Only "queued" |

- **Why "queued" holds:** each task ends by handing its renders to Java
  (`RuntimeScheduler_Modern.cpp:254-268`). The queue is a `ConcurrentLinkedQueue`
  (`RA/fabric/mounting/MountItemDispatcher.kt:34`, `54-56`).
- **Why "mounted" doesn't:** timers fire in `TIMERS_EVENTS`, which runs after `DISPATCH_UI` within
  the same frame (`RA/modules/core/JavaTimerManager.kt:130-137`, `182-189`;
  `RA/modules/core/ReactChoreographer.kt:26-66`).
- **There's no public after-mount hook:** `reportMount` (`FabricUIManager.java:1463-1506`) only
  feeds RN's internal mount hooks.

## 3. Candidate (b): `UIManagerListener`

**It can be registered.** `addUIManagerEventListener` is public (`RA/bridge/UIManager.kt:112`,
`119`; `FabricUIManager.java:750-757`), and the shell can reach it through
`ReactNativeHostManager.shared.getReactHost()` → `currentReactContext?.fabricUIManager`
(`RA/runtime/BridgelessReactContext.kt:73`).

**Costs:**

- It's `@UnstableReactNativeAPI`, an opt-in at error level, so it needs `@OptIn` and may change
  between RN releases.
- It must be registered again after every reload: drop it in the before-destroy hook `JsBridge`
  already uses, and re-add it via `addReactInstanceEventListener` (`RA/ReactHost.kt:189`).
- One callback runs every frame, so its guard has to be a single boolean check.

**Callbacks** (`RA/bridge/UIManagerListener.kt:21-50`; dispatch sites ✓):

| Callback | When it runs | Use |
|---|---|---|
| `didScheduleMountItems` | JS thread, when a batch is queued | Not usable: an empty commit queues nothing, and it can't tell which batch is the route's |
| `willMountItems` | UI thread, before a non-empty batch (`MountItemDispatcher.kt:153`) | The snapshot hook |
| `didMountItems` | UI thread, after a non-empty batch (`:304`) | Correct for Fabric views, but fires only when there were items |
| `didDispatchMountItems` | UI thread, after every dispatch, even an empty one (`:100-103`) | The "drawn" trigger |

`didDispatchMountItems` fires every frame because `DISPATCH_UI` reschedules itself
(`FabricUIManager.java:1552-1556`, `1629-1630`).

**Draws in the same frame:** mounts run in a Choreographer frame callback, which comes before that
frame's layout and draw.

**Resizing for the sheet:** a size change commits from the UI thread (`RA/runtime/ReactSurfaceView.kt:81-130`)
with `mountSynchronously = true`, so it mounts inline. The modal is laid out at the sheet's size in
the same frame as the sheet's geometry changes.

**Wiring:**

1. `JsBridge`'s main-thread post sets a pending route.
2. `didDispatchMountItems` sees it, clears it, and posts to main.
3. That runnable adds a one-shot `OnPreDrawListener` on the surface and calls `invalidate()`.
4. `onPreDraw` starts the slide.

## 4. react-native-screens adds a hop after Fabric's mount

- Fabric's add or remove only edits a list (`ScreenStackViewManager.kt:26-42`, `ScreenContainer.kt:101-123`).
- `onScreenChanged` then posts `performUpdates()` to the main Handler, with the comment "run after
  the current Fabric commit" (`ScreenContainer.kt:343-360` ✓).
- That runnable commits the fragment transaction with `commitNowAllowingStateLoss`
  (`ScreenStack.kt:153`, `317`), and the relayout comes the next frame, before draw
  (`ScreenContainer.kt:76-90`).

So a frame that has run `didMountItems` still draws the old screen. The shell's post from
`didDispatchMountItems` lands after react-native-screens' post: it's the same looper, first in
first out, and theirs is posted during the mount. The next pre-draw therefore shows the new screen.

This step depends on library internals. A react-native-screens version that delays the fragment
transaction further would break it silently.

## 5. Snapshotting before RN draws over the sheet

**How it works:**

- **Arm it from JS before the commit.** `__unsafe_action__` is emitted before `setState`
  (`…/core/BaseNavigationContainer.js:194-199` ✓, called from `useOnAction.js:73`). It fires for
  presses, accessibility clicks, back routed into RN, and the shell's `navigate`.
- **Send the arming message.** Reach the event through `useNavigationContainerRef()`, skip `noop`
  actions, and send `{type: 'routeWillChange'}`.
- **The flag is set before the batch is queued.** The shell's listener runs synchronously on the JS
  thread, so it sets an `AtomicBoolean` before `addMountItem`, and the `willMountItems` that applies
  the navigation's batch is guaranteed to see it.
- **Capture.** While the flag is set, call `surface.drawToBitmap()` on each non-empty
  `willMountItems`, and clear the flag when the deferred route report arrives. Nothing in the batch
  has been applied yet, and react-native-screens delays the visible change by one more hop, so the
  picture is the last frame the user saw. That holds in both directions.

**Costs:**

- One synchronous software draw on the UI thread, once or twice per navigation. It may drop a frame
  on a heavy screen, so time it on the device. The #177 prototype measured 8–28ms per capture on the
  S25+.
- `__unsafe_action__` is unstable API.
- Tab switches also trigger it, so filter them out.

**Rejected:** snapshotting on every batch while a modal is open, because the editors commit on every
keystroke.

## 6. Candidate (a): the surface's next draw after the report

Not reliable:

- The report can arrive before the batch has even been queued (§1).
- `ReactSurfaceView` doesn't hook drawing (`ReactSurfaceView.kt:81-256`).
- Even after the mount has happened, the react-native-screens hop still makes this signal early.

It's only useful as the last step after a trigger you already trust.

## Not verified

- Any of it on a device.
- Whether `Screen.onLayout` → `updateScreenSizeFabric` causes a second commit. It shouldn't matter
  with `headerShown: false` and a fixed sheet size.
- NativeTabs on close, which goes through the same `ScreenContainer.onScreenChanged` path.
- A Suspense fallback committing first.
