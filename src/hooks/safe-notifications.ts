/**
 * expo-notifications throws on native-module init in Expo Go on SDK 53+ (Android push support was
 * removed there; see https://docs.expo.dev/develop/development-builds/introduction/). A static
 * `import` of the module fails at module-evaluation time and crashes the whole screen, so it's
 * loaded lazily via `require` inside a try/catch instead — this is a best-effort background
 * fallback (§7.1), never something the session runner depends on to function.
 */
type NotificationsModule = typeof import('expo-notifications');

let cached: NotificationsModule | null | undefined;

function getNotifications(): NotificationsModule | null {
  if (cached !== undefined) return cached;
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

export async function scheduleRestCompleteNotification(title: string, body: string, seconds: number): Promise<string | null> {
  const notifications = getNotifications();
  if (!notifications) return null;
  try {
    return await notifications.scheduleNotificationAsync({
      content: { title, body },
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
