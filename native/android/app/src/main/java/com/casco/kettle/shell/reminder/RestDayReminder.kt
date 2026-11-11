package com.casco.kettle.shell.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.REST_DAY_REMINDER_DAYS
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.restDayReminderAt
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.DiskFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.i18n.pluralText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The opt-in rest-day nudge (#221, #236): one pending alarm, sitting a couple of days past the newest
 * session, so logging a session pushes it back out and someone training regularly never sees it.
 *
 * Kept in step by running this on every read of the files, from the boot receiver, and from Settings'
 * toggle. Never asks for the notification permission: that's the toggle's job.
 *
 * An inexact alarm, since Play restricts `SCHEDULE_EXACT_ALARM` and a nudge minutes late costs
 * nothing. Local only: nothing here transmits (`AGENTS.md` → "Platform constraints").
 */
fun Context.reconcileReminder(
    log: SessionLog,
    preferences: Preferences?,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val alarms = getSystemService(AlarmManager::class.java)
    // logged, as RN's useLoggedSessions: newest first, an in-flight session included, whose start is
    // already the right anchor.
    val at = restDayReminderAt(preferences?.restDayReminder == true, log.logged.firstOrNull(), now, zone)
    if (at == null) {
        alarms.cancel(alarmIntent(this))
        return
    }
    // Made here as well as when posting, so the channel is in system settings to mute from the moment
    // the reminder is on.
    createChannel(this)
    // The same PendingIntent every time, so this replaces the pending alarm rather than adding one.
    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), alarmIntent(this))
}

/** The alarm going off: posts the nudge, or nothing where notifications are denied. */
class RestDayReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val allowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        createChannel(context)
        val resources = context.resources
        val open =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
                .setContentTitle(resources.getString(R.string.session_notification_reminderTitle))
                .setContentText(
                    resources.pluralText(
                        R.plurals.session_notification_reminderBody,
                        R.string.session_notification_reminderBody_zero,
                        REST_DAY_REMINDER_DAYS,
                    ),
                ).setContentIntent(open)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(CHANNEL, NOTIFICATION_ID, notification)
    }
}

/**
 * Alarms don't survive a reboot, so this schedules the one the files call for again. It reads them
 * itself, since no activity has: off the main thread, and holding the broadcast open until it's done.
 */
class RestDayReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            // An unreadable file already reads as none; this is for the platform refusing the alarm,
            // which costs the nudge until the next launch reconciles, not a crash at boot.
            runCatching {
                val files = DiskFiles(StoragePaths(context.filesDir))
                context.reconcileReminder(files.sessionLog(), files.preferences())
            }.onFailure { Log.w(TAG, "Couldn't reschedule the reminder after a reboot", it) }
            pending.finish()
        }
    }
}

/**
 * Its own channel, apart from the step cues' fallback one, so muting the nudge in system settings
 * leaves a pocketed phone's rest cues alone. Recreating it is how a new locale's name reaches it.
 */
private fun createChannel(context: Context) =
    NotificationManagerCompat.from(context).createNotificationChannel(
        NotificationChannelCompat
            .Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.session_notification_reminderChannel))
            .build(),
    )

private fun alarmIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RestDayReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private const val CHANNEL = "rest-day-reminder"

// Tagged with the channel id as well, so it can't replace a notification another part of the app posts
// under the same number.
private const val NOTIFICATION_ID = 1
private const val TAG = "RestDayReminder"
