# Does Wear OS mirror the runner's ongoing notification?

> Research note for #541 (map #540), read against primary sources on 2026-11-25. Not a plan and
> never merged; the spike and the rewritten `docs/watch-remote-plan.md` are where it lands.

## Answer in one line

**No.** Wear OS does not bridge the runner's notification as it is built today, for two separate
reasons, each enough on its own: it is `setOngoing(true)`, and it is a foreground-service
notification, which the platform makes non-clearable. The one documented exception is a **Live
Update** (a promoted ongoing notification, Android 16+), and that bridges only to Wear OS 7 watches
whose OEM has turned it on. The recollection that "ongoing isn't bridged" is right. The claim that
"the Ongoing Activity API is watch-app only" is right in effect, though no doc says so in those words.

## 1. Is the ongoing FGS notification bridged at all?

**Not bridged.** The bridging page lists what the system does not bridge
([Bridging options, "When notifications aren't bridged"](https://developer.android.com/training/wearables/notifications/bridger#non-bridged)):

> - Local-only notifications set using `Notification.Builder.setLocalOnly(boolean)`.
> - Ongoing notifications set using `Notification.Builder.setOngoing(boolean)` or
>   `Notification.FLAG_ONGOING_EVENT`, except for Live Updates. Although Live Updates are ongoing
>   notifications, the system might still bridge them depending on the Wear OS version and device
>   manufacturer.
> - Non-clearable notifications set using `Notification.FLAG_NO_CLEAR`.

`RunnerNotifications.kt` sets `setOngoing(true)`, so the second rule applies. Dropping
`setOngoing` would not help, because of the third rule: AOSP's `NotificationManagerService` adds
`FLAG_NO_CLEAR` to every notification that carries `FLAG_FOREGROUND_SERVICE` when it is posted
([NotificationManagerService.java, "Ensure if this is a foreground service that the proper
additional flags are set"](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationManagerService.java)):

```java
if ((notification.flags & FLAG_FOREGROUND_SERVICE) != 0) {
    notification.flags |= FLAG_NO_CLEAR;
}
```

So **the notification `startForeground` posts can never be bridged as an ordinary notification**,
whatever the builder says. Category, visibility, channel importance and alert-once play no part in
the rules.

**What would be bridged instead:**

- **A second, ordinary notification.** Bridging is on by default for a phone app with no watch app
  ([Bridging options](https://developer.android.com/training/wearables/notifications/bridger):
  "By default, the system bridges, or shares, notifications from a phone app to any paired
  watches"). A companion card that is not ongoing, not posted by `startForeground`, and not
  `setLocalOnly` matches none of the exclusions. The catch is that it is clearable, so the user can
  swipe it away on either device. `WearableExtender.setDismissalId` syncs a dismissal between
  phone and watch
  ([same page, "Set a dismissal ID"](https://developer.android.com/training/wearables/notifications/bridger#set-dismissal-id)).
  Whether a LOW-importance channel's card shows on the wrist is **for the spike**.
- **`setLocalOnly(false)`** is the default and only a hint. `setLocalOnly(true)` blocks bridging,
  but `false` cannot override the ongoing or no-clear rules
  ([Notification.Builder.setLocalOnly](https://developer.android.com/reference/android/app/Notification.Builder#setLocalOnly(boolean)):
  "This hint can be set to recommend this notification not be bridged").
- **Bridge tags** (`WearableExtender.setBridgeTag`, `BridgingConfig`) only matter when a watch app
  is installed and has turned bridging off. They select which notifications get through that
  filter. They do not override the exclusion list
  ([Bridging options, "Use bridge tags"](https://developer.android.com/training/wearables/notifications/bridger#use-bridge-tags)).
  They are irrelevant to an app with no watch app.
- **A Live Update.** Ongoing notifications are excluded "except for Live Updates". The phone-side
  requirements
  ([Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)): Standard,
  `BigTextStyle`, `CallStyle`, `ProgressStyle` or `MetricStyle`; the
  `POST_PROMOTED_NOTIFICATIONS` manifest permission; `setRequestPromotedOngoing(true)` (in
  `NotificationCompat.Builder`,
  [source](https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/app/NotificationCompat.java),
  which writes the `android.requestPromotedOngoing` extra); ongoing; a content title; no custom
  `RemoteViews`; not a group summary; not colorized; and a channel above `IMPORTANCE_MIN`. The runner's
  notification already meets every one of these except the permission and the request. `setColor`
  is not `setColorized`. The page also warns that "OEMs can enforce additional criteria", and the
  user can turn Live Updates off per app (`NotificationManager.canPostPromotedNotifications()`).
  On the watch side, bridging is **not guaranteed**:
  - [Wear OS Live Updates](https://developer.android.com/training/wearables/notifications/live-updates):
    "Starting with Wear OS 7, Live Updates can be displayed on Wear OS devices either as locally
    generated notifications or as bridged notifications … This is not guaranteed: OEMs can set
    their own configuration for bridging Live Updates. Frequent updates may cause data to be
    hidden in order to conserve battery."
  - [What's new in Wear OS 7](https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html):
    "For supporting OEMs, Live Updates published by your phone app will also be bridged to users'
    watches." Wear OS 7 is based on Android 17. In May 2026 it was a Canary emulator "arriving later
    this year".

  So a Live Update reaches only phones on Android 16+ with promotion allowed, paired to Wear OS 7
  watches from a supporting OEM. Which OEMs support it, and whether the Pixel Watch line does, is
  documented nowhere I found. That is **for the spike**.

**Is the Ongoing Activity API watch-app only?** In effect, yes, though no page uses those words.
The guide is written for apps running on the watch: it is a Wear OS App Quality requirement (WO-V4),
and its surfaces are the watch face icon and the watch launcher's Recents
([Ongoing Activity](https://developer.android.com/training/wearables/ongoing-activity)). Mechanically,
`OngoingActivity.apply()` only writes extras into the `NotificationCompat.Builder`
([OngoingActivity.java](https://github.com/androidx/androidx/blob/androidx-main/wear/wear-ongoing/src/main/java/androidx/wear/ongoing/OngoingActivity.java):
`SerializationHelper.extend(mNotificationBuilder, mData)`). It runs on a phone without complaint.
But the notification it decorates has to be ongoing, and an ongoing notification does not bridge,
so the extras never reach a watch. As of Wear OS 7, Google is replacing it for local use too: "In
your watch app, use Live Updates instead of the Ongoing Activities API"
([What's new in Wear OS 7](https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html)).

## 2. How many actions, label truncation, and `WearableExtender`

- **Count: not documented for Wear.** On the phone, "A notification in its expanded form can display
  up to 3 actions"
  ([Notification.Builder.addAction](https://developer.android.com/reference/android/app/Notification.Builder#addAction(android.app.Notification.Action))).
  The Wear docs say only that actions are "stacked at the bottom of the expanded state"
  ([Notifications on Wear OS](https://developer.android.com/training/wearables/notifications)). The
  Wear design guide names templates "with actions" and gives no number
  ([Wear notifications design](https://developer.android.com/design/ui/wear/guides/surfaces/notifications)).
  The count that shows and how labels truncate are **for the spike**.
- **Icons.** Since Android N the phone draws action buttons without their icons, but icons "are
  still required and are available to `NotificationListenerService`, which may display them in other
  contexts, for example on a wearable device"
  ([Notification.Action.Builder](https://developer.android.com/reference/android/app/Notification.Action.Builder#Builder(int,%20java.lang.CharSequence,%20android.app.PendingIntent))).
  Give every action a real icon. Whether the watch shows the icon, the label or both is **for the
  spike**.
- **`WearableExtender` changes the set, not the limit.** Once any action is added with
  `WearableExtender.addAction`, "the set of actions that show on a wearable device splits from
  devices that only show actions added using `NotificationCompat.Builder#addAction`"
  ([WearableExtender.addAction](https://developer.android.com/reference/androidx/core/app/NotificationCompat.WearableExtender#addAction(androidx.core.app.NotificationCompat.Action))).
  The sample on the Wear notifications page says the same: "Actions added to the Notification
  builder directly will not be shown on the watch, because one or more actions are defined in the
  WearableExtender"
  ([Notifications on Wear OS](https://developer.android.com/training/wearables/notifications#wearable-specific-features)).
  This is how the wrist could get a different or longer list than the phone's three.
  `setContentAction(index)` promotes one action to the card's primary tap. Neither doc states a
  maximum. The docs also say some `WearableExtender` features need the notification posted through
  `NotificationManagerCompat`, not the framework `NotificationManager`. Caveat: all of this applies
  only to a notification that is bridged in the first place (see 1).

## 3. Does `setUsesChronometer` / `setChronometerCountDown` render on the wrist?

**Not documented for an ordinary bridged notification. For the spike.** The only Wear statement
covers Live Updates: to show time counting up or down, "use the when guidance as opposed to
regularly updating the Live Update"
([Wear OS Live Updates, "Battery considerations"](https://developer.android.com/training/wearables/notifications/live-updates#battery-considerations)).
The linked phone guidance says "A timer in the chip can be shown when using Chronometer for when
time"
([Live Updates, "When time"](https://developer.android.com/develop/ui/views/notifications/live-update#when-time)).
Status chips on Wear "may show on the watch face or in the notification tray, depending on the
device". That suggests, but does not state, that a bridged Live Update's chronometer renders. For an
ordinary bridged card, nothing says either way.

## 4. Does `notify(id)` replace in place on the watch, or re-alert?

**Partly documented. The buzz is for the spike.** The bridging page treats an update as a
modification, not a new notification. It warns about latency and says "Only modify or remove the
notifications that have actually been modified. This avoids the latency on updating the wearable
device and reduces your app's impact on battery life"
([Bridging options, "Implementation considerations"](https://developer.android.com/training/wearables/notifications/bridger#implementation-considerations)).
For Live Updates, "Frequent updates may cause data to be hidden in order to conserve battery"
([Wear OS Live Updates](https://developer.android.com/training/wearables/notifications/live-updates)).
`setOnlyAlertOnce` is documented only for the phone: sound and vibration play "only … if the
notification is not already showing"
([Notification.Builder.setOnlyAlertOnce](https://developer.android.com/reference/android/app/Notification.Builder#setOnlyAlertOnce(boolean))).
No Wear doc says whether the watch respects it on a bridged update. Whether each `notify(id)` buzzes
the wrist, and how much update latency there is, are **for the spike**. The runner updates at every
step boundary, so this decides whether the card is usable.

## 5. Does a wrist tap reach the phone's receiver without opening an activity? And on the lock screen?

- **Where it runs: on the phone.** The Wear notifications sample says a bridged action's intent "will
  be fired as a result of the user clicking … However, it executes on the phone, not on the watch"
  ([Notifications on Wear OS](https://developer.android.com/training/wearables/notifications#wearable-specific-features)).
  That sample uses `PendingIntent.getActivity`. No page says a broadcast `PendingIntent` is delivered
  the same way, though nothing forbids it. **For the spike**: confirm the broadcast arrives at
  `RunnerCommandReceiver`'s path with no activity opening. Do not misread the page's other note,
  "You can't use a `BroadcastReceiver` as the target of the notification action". It sits in the
  section on notifications *created on the watch* that launch the phone app, not on bridged ones.
- **No activity opens from it anyway (API 31+).** Apps targeting S or higher "won't be able to start
  activities while processing broadcast receivers or services in response to notification action
  clicks"
  ([Notification.Action.Builder](https://developer.android.com/reference/android/app/Notification.Action.Builder#Builder(android.graphics.drawable.Icon,%20java.lang.CharSequence,%20android.app.PendingIntent))).
  The app targets 37, so a broadcast action cannot pop the runner open, which is the behaviour we
  want.
- **Stale taps.** Because of bridging latency, "continue to receive pending intents in your app from
  notifications it has canceled: when canceling notifications, keep those notifications' pending
  intent receivers valid"
  ([Bridging options](https://developer.android.com/training/wearables/notifications/bridger#implementation-considerations)).
  The engine's `Advance(visit)` refusing a stale tap is the right guard. The receiver also has to
  outlive the session, and a tap after the finish has to be a no-op.
- **Phone lock screen.** `setAuthenticationRequired(true)` (API 31): "If this is true and the device
  is locked when the action is invoked, the OS will show the keyguard and require successful
  authentication". If it is `false` (the default), "the OS will decide whether authentication should
  be required"
  ([Notification.Action.Builder.setAuthenticationRequired](https://developer.android.com/reference/android/app/Notification.Action.Builder#setAuthenticationRequired(boolean))).
  So leave it `false`. Whether the OS then runs a broadcast action from the lock screen without
  unlocking, and whether a wrist tap while the phone is locked goes through keyguard at all (the
  companion sends it as a notification listener, not through SystemUI), are **for the spike**.

## For the spike (device only)

1. Does a **Live Update** version of the runner notification (add `POST_PROMOTED_NOTIFICATIONS` and
   `setRequestPromotedOngoing(true)`) bridge to a Wear OS 7 emulator or watch, and on which OEMs
   (Pixel Watch in particular)?
2. If not, does a **second, ordinary companion card** on the LOW channel bridge and show, and does
   `setDismissalId` keep it in step with the phone?
3. How many actions the wrist shows, and how it truncates or wraps labels, in all three locales.
   Does it show the icon, the label or both? Does `WearableExtender.addAction` raise the count?
4. Does the chronometer (count up and count down) render and tick on the wrist, for each transport?
5. Does a `notify(id)` update replace the card in place without buzzing, and what is the latency?
6. Does a wrist tap on a broadcast action reach the phone's receiver with no activity opening, with
   the phone unlocked and locked? On the phone's own lock screen with `setAuthenticationRequired(false)`,
   does it run without unlocking?
7. What does a tap do once the session has finished, or once the card has been cancelled but not
   yet removed from the watch?
