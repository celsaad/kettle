/**
 * expo-notifications throws on native-module init in Expo Go on SDK 53+ (Android push support was
 * removed there; see https://docs.expo.dev/develop/development-builds/introduction/). Requiring the
 * module there doesn't just throw — it logs its own console.error first, which LogBox turns into a
 * full redbox. So Expo Go is detected up front via expo-constants and the require is skipped
 * entirely; this is a best-effort background fallback (§7.1), never something the session runner
 * depends on to function.
 */
import Constants, { ExecutionEnvironment } from 'expo-constants';

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
 * Title and body are the caller's, translated: this schedules any step's completion cue, not rest's.
 *
 * `sound` and `interruptionLevel` are what make this a *cue* rather than a banner nobody sees. When
 * the app is suspended the in-app audio in `use-session-sounds.ts` is gone and this is all that's
 * left, so a rest ending with the phone in a pocket is silent without them. Both are iOS-only in
 * effect — Android 8+ takes a notification's sound from its channel and ignores the content field,
 * and `interruptionLevel` is an iOS concept — so this changes nothing on the platform that ships
 * today and everything on the one that doesn't yet.
 *
 * `timeSensitive` breaks through Focus modes, which a rest timer the user started seconds ago has a
 * real claim to. Not `critical`: that bypasses the mute switch and needs a special entitlement, and
 * this is a workout, not an alarm.
 *
 * The `shouldPlaySound: false` in `setNotificationHandler` above is not a contradiction. On iOS that
 * handler runs only while the app is *foregrounded*, where `use-session-sounds.ts` is already playing
 * the real cue — flipping it would double up.
 */
export async function scheduleStepCompleteNotification(
  title: string,
  body: string,
  seconds: number,
): Promise<string | null> {
  const notifications = getNotifications();
  if (!notifications) return null;
  try {
    return await notifications.scheduleNotificationAsync({
      content: { title, body, sound: 'default', interruptionLevel: 'timeSensitive' },
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
