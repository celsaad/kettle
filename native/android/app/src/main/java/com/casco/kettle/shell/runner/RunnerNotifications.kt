package com.casco.kettle.shell.runner

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.WorkStep
import com.casco.kettle.shell.R

// The runner's two notifications (#296): the ongoing one the foreground service has to show, and the
// alert posted when a step ends by itself while the runner isn't on screen. Local only: nothing here
// transmits (AGENTS.md → "Platform constraints").

/** A chronometer in the shade: counting down to [baseWallMs], or up from it. */
data class Chronometer(
    val baseWallMs: Long,
    val countDown: Boolean,
)

/** What the ongoing notification says. No actions in M6; #300 adds them to this. */
data class OngoingContent(
    val title: String,
    val text: String,
    val chronometer: Chronometer?,
)

/**
 * The ongoing notification for [view]. The title is the workout's name, verbatim. The text is the
 * step: with a chronometer for a timed one (down for a countdown, up for a hold or count-up cardio,
 * as the screen shows them), with the set position for reps, and saying Paused with no chronometer
 * while paused.
 */
fun ongoingContent(
    resources: Resources,
    view: RunnerView.Active,
): OngoingContent {
    val title = view.plan.workout?.name ?: resources.getString(R.string.format_adHocSession)
    return when (view) {
        is RunnerView.CountingIn -> {
            val getReady = resources.getString(R.string.session_notification_getReady)
            OngoingContent(title, getReady, Chronometer(view.countIn.endsAt, true))
        }

        is RunnerView.Running -> {
            val snapshot = view.snapshot
            val step =
                snapshot.step ?: return OngoingContent(title, resources.getString(R.string.session_adhoc_title), null)
            val label = (step as? WorkStep)?.exerciseName ?: resources.getString(R.string.session_notification_rest)
            val clock = snapshot.clock
            val started = clock.phaseStartedAt + clock.pausedMs
            when {
                clock.paused -> {
                    OngoingContent(title, resources.getString(R.string.session_notification_paused, label), null)
                }

                step is RunnerStep.Reps -> {
                    // Arguments by placeholder name, alphabetically: index, step, total. Every placeholder is
                    // a `%s`, so the numbers go as text.
                    val text =
                        resources.getString(
                            R.string.session_notification_setPosition,
                            step.setIndex.toString(),
                            label,
                            step.setTotal.toString(),
                        )
                    OngoingContent(title, text, null)
                }

                step is RunnerStep.Rest || (step is RunnerStep.Interval && !step.countUp) -> {
                    OngoingContent(
                        title,
                        label,
                        Chronometer(started + (clock.stepEndSec * MS_PER_SECOND).toLong(), true),
                    )
                }

                else -> {
                    OngoingContent(title, label, Chronometer(started, false))
                }
            }
        }
    }
}

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
    // Spike for #543, never merged: the session controls on this notification, promoted to a Live Update
    // ("live"), or on a second ordinary card beside it ("card").
    spikeView: RunnerView.Active? = null,
    spikeFrom: String = "live",
): Notification {
    val card = spikeFrom == "card"
    val builder =
        NotificationCompat
            .Builder(context, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_color))
            .setContentTitle(if (spikeView == null) content.title else "[${spikeFrom.uppercase()}] ${content.title}")
            .setContentText(content.text)
            .setContentIntent(open)
            .setOngoing(!card)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    if (spikeView != null) spikeActions(context, builder, spikeView, spikeFrom)
    if (spikeView != null && !card) {
        builder.addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
    }
    if (card) builder.extend(NotificationCompat.WearableExtender().setDismissalId("kettle-session"))
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

// Spike for #543: Done/Back/+30s while running, Resume while paused, nothing in the count-in, as decided in #542.
private fun spikeActions(
    context: Context,
    builder: NotificationCompat.Builder,
    view: RunnerView.Active,
    from: String,
) {
    val running = view as? RunnerView.Running ?: return
    val visit = running.snapshot.visit
    fun action(
        label: String,
        cmd: String,
        code: Int,
    ) {
        val intent =
            android.content.Intent()
                .setClassName(context, "com.casco.kettle.shell.runner.SpikeActionReceiver")
                .putExtra("cmd", cmd)
                .putExtra("visit", visit)
                .putExtra("from", from)
                .putExtra("postedAt", System.currentTimeMillis())
        val pending =
            PendingIntent.getBroadcast(
                context,
                code + if (from == "card") 10 else 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        builder.addAction(NotificationCompat.Action.Builder(null, label, pending).build())
    }
    if (running.snapshot.clock.paused) {
        action("Resume", "pause", 4)
    } else {
        action("Done", "advance", 1)
        action("Back", "prev", 2)
        action("+30s", "add30", 3)
    }
}
