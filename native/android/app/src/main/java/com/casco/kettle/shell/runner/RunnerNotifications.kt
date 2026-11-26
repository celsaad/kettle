package com.casco.kettle.shell.runner

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.casco.kettle.shell.R

// The runner's two notifications (#296): the ongoing one the foreground service has to show, and the
// alert posted when a step ends by itself while the runner isn't on screen. Local only: nothing here
// transmits (AGENTS.md → "Platform constraints").

fun alertTitle(
    resources: Resources,
    title: StepEndTitle,
): String =
    resources.getString(
        when (title) {
            StepEndTitle.REST -> R.string.session_notification_restTitle
            StepEndTitle.HOLD -> R.string.session_notification_holdTitle
            StepEndTitle.INTERVAL -> R.string.session_notification_intervalTitle
            StepEndTitle.TIME_UP -> R.string.session_notification_timeUpTitle
        },
    )

fun alertBody(
    resources: Resources,
    alert: StepEndAlert,
): String = alert.nextName ?: resources.getString(R.string.session_notification_workoutComplete)

/** The ongoing notification, built from [content]. Tapping it reopens the runner, attached ([open]). */
fun ongoingNotification(
    context: Context,
    content: OngoingContent,
    open: PendingIntent,
): Notification {
    val builder =
        NotificationCompat
            .Builder(context, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.subText)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // A Live Update on Android 16+, which is what bridges it to a Wear OS 7 watch: an ongoing,
            // foreground-service notification otherwise never leaves the phone. Below 16, or with Live
            // Updates turned off for Kettle, it stays an ordinary ongoing notification, actions and all.
            // Never setColorized: it blocks promotion.
            .setRequestPromotedOngoing(true)
    content.shortText?.let(builder::setShortCriticalText)
    content.aim?.let { aim ->
        content.actions.forEach { builder.addAction(0, it.label, controlIntent(context, aim, it.control)) }
    }
    val chronometer = content.chronometer
    if (chronometer == null) {
        builder.setShowWhen(false)
    } else {
        builder
            .setWhen(chronometer.baseWallMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(chronometer.countDown)
    }
    return builder.build()
}

/** A button's tap: a broadcast to [SessionControlReceiver], its aim in the intent's identity. */
private fun controlIntent(
    context: Context,
    aim: SessionAim,
    control: SessionControl,
): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SessionControlReceiver::class.java).setData(sessionControlUri(aim, control)),
        PendingIntent.FLAG_IMMUTABLE,
    )

/** The step-end alert. [silent] when the service has played the cue, so it sounds once, not twice. */
fun stepEndNotification(
    context: Context,
    alert: StepEndAlert,
    silent: Boolean,
    open: PendingIntent,
): Notification =
    NotificationCompat
        .Builder(context, CUES_CHANNEL)
        .setSmallIcon(R.drawable.notification_icon)
        .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
        .setContentTitle(alertTitle(context.resources, alert.title))
        .setContentText(alertBody(context.resources, alert))
        .setContentIntent(open)
        .setAutoCancel(true)
        .setSilent(silent)
        // No category: an alarm's would let it through Do Not Disturb, which the cues respect (#296).
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

/**
 * Both channels, made or renamed. The cues' keeps the id expo-notifications' fallback channel has, which
 * RN's background cues went out on, so a user's settings for it carry over (#296); renaming it is how
 * "Workout cues" (or a new locale's name) reaches it, and the importance only takes on a fresh install,
 * as the platform keeps the user's.
 */
fun createRunnerChannels(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
        NotificationChannelCompat
            .Builder(ONGOING_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.session_notification_ongoingChannel))
            .setShowBadge(false)
            .build(),
    )
    manager.createNotificationChannel(
        NotificationChannelCompat
            .Builder(CUES_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.session_notification_cuesChannel))
            .build(),
    )
}

const val ONGOING_CHANNEL = "workout-in-progress"

/**
 * expo-notifications' fallback channel's id (`BaseNotificationBuilder.kt`), on purpose.
 * scripts/session-check checks the step-end alert is on it, so a change here goes into both twins.
 */
const val CUES_CHANNEL = "expo_notifications_fallback_notification_channel"
