package com.casco.kettle.shell.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The native reminder as the platform sees it: Robolectric's own AlarmManager and NotificationManager,
 * rather than a fake of either, since "exactly one alarm" is a fact about the platform's queue. When it
 * fires is `:shared`'s `RestDayReminderTest`; this is what gets scheduled and posted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class RestDayReminderTest {
    private val context = RuntimeEnvironment.getApplication()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifications = shadowOf(notificationManager)

    private val zone = TimeZone.of("America/New_York")
    private val now = local(24, 9)

    private fun local(
        day: Int,
        hour: Int,
    ): Instant = LocalDateTime(2026, 7, day, hour, 0).toInstant(zone)

    private fun preferences(reminder: Boolean) =
        Preferences(
            UnitSystem.METRIC,
            ThemePreference.SYSTEM,
            restDayReminder = reminder,
            sessionSounds = true,
            backupFolderUri = null,
        )

    // In flight, so the log's `logged` keeps it with no entries: the newest session anchors the nudge
    // from the moment it starts, as RN's did.
    private fun logOf(vararg startedDays: Int) =
        SessionLog(
            startedDays.map {
                Session(1, "s$it", "w", null, null, null, local(it, 7).toString(), null, emptyList())
            },
            emptyList(),
        )

    private fun reconcile(
        reminder: Boolean,
        log: SessionLog,
    ) = context.reconcileReminder(log, preferences(reminder), now, zone)

    @Test
    fun `schedules one inexact alarm two days after the newest session`() {
        reconcile(reminder = true, logOf(24))

        val alarm = alarms.scheduledAlarms.single()
        assertEquals(local(26, 18).toEpochMilliseconds(), alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test
    fun `moves the one alarm rather than stacking a second when a session is logged`() {
        reconcile(reminder = true, logOf(24))
        reconcile(reminder = true, logOf(25, 24))

        assertEquals(local(27, 18).toEpochMilliseconds(), alarms.scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun `cancels the alarm when the preference is switched off`() {
        reconcile(reminder = true, logOf(24))
        reconcile(reminder = false, logOf(24))

        assertEquals(emptyList<Any>(), alarms.scheduledAlarms)
    }

    @Test
    fun `posts the nudge on its own Reminders channel when the alarm fires`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        RestDayReminderReceiver().onReceive(context, Intent())

        val posted = notifications.allNotifications.single()
        assertEquals("Lembretes", notificationManager.getNotificationChannel(posted.channelId).name)
        assertEquals("Quando você quiser", posted.extras.getString("android.title"))
    }

    // As RN's best effort: a denial costs the nudge and nothing else, and nothing here asks again.
    @Test
    fun `posts nothing when notifications are denied`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        RestDayReminderReceiver().onReceive(context, Intent())

        assertEquals(emptyList<Any>(), notifications.allNotifications)
    }
}
