/**
 * expo-notifications throws on native-module init in Expo Go on SDK 53+ (Android push support was
 * removed there; see https://docs.expo.dev/develop/development-builds/introduction/). Requiring the
 * module there doesn't just throw — it logs its own console.error first, which LogBox turns into a
 * full redbox. So Expo Go is detected up front via expo-constants and the require is skipped
 * entirely; this is a best-effort background fallback (§7.1), never something the session runner
 * depends on to function.
 */
import Constants, { ExecutionEnvironment } from 'expo-constants';
import { Platform } from 'react-native';

type NotificationsModule = typeof import('expo-notifications');

let cached: NotificationsModule | null | undefined;

function getNotifications(): NotificationsModule | null {
  if (cached !== undefined) return cached;
  if (Constants.executionEnvironment === ExecutionEnvironment.StoreClient) {
    cached = null;
    return cached;
  }
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const notifications = require('expo-notifications') as NotificationsModule;
    notifications.setNotificationHandler({
      handleNotification: async () => ({
        shouldShowBanner: false,
        shouldShowList: false,
        shouldPlaySound: false,
        shouldSetBadge: false,
      }),
    });
    cached = notifications;
  } catch {
    cached = null;
  }
  return cached;
}

export async function requestNotificationPermissions(): Promise<void> {
  const notifications = getNotifications();
  if (!notifications) return;
  try {
    await notifications.requestPermissionsAsync();
  } catch {
    // Best effort — notifications are a background fallback, not required functionality.
  }
}

/**
 * The cue's content, as a pure function so both platforms' payloads are visible without a device or a
 * `Platform` mock — jest runs every hook here as iOS, so the Android arm has no other way to be seen.
 *
 * The asymmetry in `sound` is load-bearing, not tidiness. On iOS the app owns the sound: no `sound`
 * key means a silent banner, so the preference maps straight onto it and `false` is the documented
 * way to ask for silence.
 *
 * On Android the *channel* owns it, and `false` is not the neutral value it looks like. A boolean
 * `sound` comes back as-is from `ArgumentsNotificationContentBuilder.shouldPlayDefaultSound`, and
 * with no `vibrate` key `shouldUseDefaultVibrationPattern` is false too — that one is inverted
 * upstream, so `vibrate: true` doesn't rescue it — and `ExpoNotificationBuilder` turns "neither sound
 * nor vibration" into `setSilent(true)`, whose own comment says *regardless of channel*. On O+ that
 * drops the heads-up alert, not just the noise, and the cue whose entire job is to reach a pocketed
 * phone would land quietly in the shade.
 *
 * So Android gets no `sound` key at all and the channel keeps governing, exactly as before any of
 * this. Its users already have a per-channel control in system settings; taking their heads-up away
 * to honour an in-app toggle is a product decision worth making deliberately, and not here.
 *
 * The key has to be *absent* rather than present-and-undefined, which is why this builds the object
 * rather than the caller inlining it: an undefined that survives serialization is a `false` with
 * extra steps.
 *
 * `interruptionLevel: 'timeSensitive'` lets the cue through a Focus mode, which a timer the user
 * started seconds ago has a real claim to. **It is not free**: iOS downgrades it to `active` unless
 * the app carries `com.apple.developer.usernotifications.time-sensitive`, which `expo-notifications`'
 * own plugin does not write (it writes `aps-environment` and nothing else). That entitlement is
 * declared in `app.json`'s `ios.entitlements` and asserted by `app-config.test.ts`, because the
 * failure is silent in exactly the way this whole file is about — the flag stays in the code, the cue
 * keeps being swallowed, and nothing says so. Not `critical`, which bypasses the mute switch and
 * needs Apple's approval; this is a workout, not an alarm. Android has no interruption levels and
 * ignores it.
 */
export function stepCueContent(
  title: string,
  body: string,
  soundsOn: boolean,
  os: string,
): { title: string; body: string; interruptionLevel: 'timeSensitive'; sound?: 'default' | false } {
  const base = { title, body, interruptionLevel: 'timeSensitive' as const };
  if (soundsOn) return { ...base, sound: 'default' };
  return os === 'ios' ? { ...base, sound: false } : base;
}

/**
 * Title and body are the caller's, translated: this schedules any step's completion cue, not rest's.
 *
 * The sound is what makes this a *cue* rather than a banner nobody sees — once the app is suspended
 * the in-app audio in `use-session-sounds.ts` is gone and this is all that's left, so a rest ending
 * with the phone in a pocket cues nothing without it. It is also the same ding to the user, so it
 * answers to the same switch; `stepCueContent` above has what that means on each platform.
 *
 * The preference is passed in rather than read here: this module has no other store dependency and is
 * imported by code that runs before anything is hydrated.
 *
 * The `shouldPlaySound: false` in `setNotificationHandler` above is not a contradiction: on iOS that
 * handler runs only while the app is *foregrounded*, where the real cue is already playing.
 */
export async function scheduleStepCompleteNotification(
  title: string,
  body: string,
  seconds: number,
  sound: boolean,
): Promise<string | null> {
  const notifications = getNotifications();
  if (!notifications) return null;
  try {
    return await notifications.scheduleNotificationAsync({
      content: stepCueContent(title, body, sound, Platform.OS),
      trigger: { type: notifications.SchedulableTriggerInputTypes.TIME_INTERVAL, seconds, repeats: false },
    });
  } catch {
    return null;
  }
}

export function cancelNotification(id: string): void {
  const notifications = getNotifications();
  if (!notifications) return;
  notifications.cancelScheduledNotificationAsync(id).catch(() => {});
}

/**
 * A fixed identifier rather than a generated one, so the rest-day reminder can be replaced and
 * cancelled across app restarts without persisting an id anywhere — scheduling under the same
 * identifier overwrites the pending request. Cancelling everything would have been the alternative,
 * and it would have taken the runner's in-flight step notification with it.
 */
const REST_DAY_REMINDER_ID = 'kettle-rest-day-reminder';

/**
 * Schedules the opt-in rest-day nudge for an absolute local instant, replacing any pending one.
 *
 * A DATE trigger, not TIME_INTERVAL: the target is a wall-clock moment days away, and an interval
 * computed now would drift across a DST boundary or a device clock change. Title and body are the
 * caller's, translated.
 */
export async function scheduleRestDayReminder(title: string, body: string, date: Date): Promise<void> {
  const notifications = getNotifications();
  if (!notifications) return;
  try {
    await notifications.scheduleNotificationAsync({
      identifier: REST_DAY_REMINDER_ID,
      content: { title, body },
      trigger: { type: notifications.SchedulableTriggerInputTypes.DATE, date },
    });
  } catch {
    // Best effort — a reminder that couldn't be scheduled costs a nudge, not any workout data.
  }
}

export function cancelRestDayReminder(): void {
  const notifications = getNotifications();
  if (!notifications) return;
  notifications.cancelScheduledNotificationAsync(REST_DAY_REMINDER_ID).catch(() => {});
}
