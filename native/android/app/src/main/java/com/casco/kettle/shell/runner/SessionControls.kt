package com.casco.kettle.shell.runner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.casco.kettle.domain.RunnerCommand

// Session controls (docs/watch-remote-plan.md): the buttons on the runner's ongoing notification, which a
// Wear OS 7 watch shows once the notification is promoted to a Live Update. A tap can arrive late, from a
// card the phone has already moved past, so every one carries the session and the step it was drawn for.

/** What a session-control button asks of the engine. */
sealed interface SessionControl {
    data object Advance : SessionControl

    data object Prev : SessionControl

    data object AddRest : SessionControl

    data object Resume : SessionControl

    /** An AMRAP's "Round N done": [rounds] is the count it sets, absolute, as the screen's does. */
    data class Rounds(
        val rounds: Long,
    ) : SessionControl
}

/** One button: its label, the runner's own word for it, and what it does. */
data class SessionAction(
    val label: String,
    val control: SessionControl,
)

/** The session and the step ([visit]) a card's buttons were drawn for. */
data class SessionAim(
    val sessionId: String,
    val visit: Long,
)

/** A tap read back from its intent. */
data class SessionTap(
    val aim: SessionAim,
    val control: SessionControl,
)

/**
 * The tap's identity. The aim lives in the URI, not in extras: a `PendingIntent` is matched on its
 * intent's data, never its extras, so with the visit in an extra every re-post rewrote it inside the
 * token an older card still held, and a late tap arrived aimed at the step on screen (#549).
 */
fun sessionControlUri(
    aim: SessionAim,
    control: SessionControl,
): Uri {
    val builder =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(aim.sessionId)
            .appendPath("${aim.visit}")
    when (control) {
        SessionControl.Advance -> builder.appendPath(ADVANCE)
        SessionControl.Prev -> builder.appendPath(PREV)
        SessionControl.AddRest -> builder.appendPath(ADD_REST)
        SessionControl.Resume -> builder.appendPath(RESUME)
        is SessionControl.Rounds -> builder.appendPath(ROUNDS).appendPath("${control.rounds}")
    }
    return builder.build()
}

/** The tap [uri] carries, or null for anything that isn't one. */
fun parseSessionTap(uri: Uri): SessionTap? {
    val segments = uri.pathSegments.takeIf { uri.scheme == SCHEME && uri.authority == HOST }.orEmpty()
    val sessionId = segments.getOrNull(0)
    val visit = segments.getOrNull(1)?.toLongOrNull()
    val control = controlOf(segments.drop(2))
    return if (sessionId != null && visit != null && control != null) {
        SessionTap(SessionAim(sessionId, visit), control)
    } else {
        null
    }
}

/** The control a URI's path names after its aim: one segment, or `rounds` and its count. */
private fun controlOf(path: List<String>): SessionControl? =
    when (path.firstOrNull()) {
        ADVANCE -> SessionControl.Advance
        PREV -> SessionControl.Prev
        ADD_REST -> SessionControl.AddRest
        RESUME -> SessionControl.Resume
        ROUNDS -> path.getOrNull(1)?.toLongOrNull()?.let { SessionControl.Rounds(it) }
        else -> null
    }

/**
 * The command a tap sends to the session running as [sessionId] with [roundsCompleted] counted on the
 * step on screen, or null to drop it. Every step command goes aimed at the card's visit, and the engine
 * refuses it once that step is gone. Two checks are left to here: a tap from another session, since
 * visits start again at 0 in every one; and a "Round N done" whose N isn't the next round, since the
 * visit doesn't move inside an AMRAP and an absolute count from a lagging card would take back rounds
 * counted since. That check is only as fresh as [RunnerHost.active], so a round counted on the phone in
 * the instant before the tap lands can still be taken back. Resume can't be aimed: pausing doesn't move
 * the visit.
 */
fun SessionTap.command(
    sessionId: String,
    roundsCompleted: Long,
): RunnerCommand? {
    if (aim.sessionId != sessionId) return null
    return when (control) {
        SessionControl.Advance -> {
            RunnerCommand.Advance(aim.visit)
        }

        SessionControl.Prev -> {
            RunnerCommand.Prev(aim.visit)
        }

        SessionControl.AddRest -> {
            RunnerCommand.AddRestSeconds(REST_BUMP_SEC, aim.visit)
        }

        SessionControl.Resume -> {
            RunnerCommand.TogglePause
        }

        is SessionControl.Rounds -> {
            RunnerCommand.SetRounds(control.rounds, aim.visit).takeIf { control.rounds == roundsCompleted + 1 }
        }
    }
}

/**
 * Sends the tap [uri] carries to the session [view] shows, through [send]; whether it went, for the debug
 * receiver's check. Nothing goes without a running session: a count-in has no step to aim at.
 */
fun sendSessionTap(
    uri: Uri?,
    view: RunnerView? = RunnerHost.active,
    send: (RunnerCommand) -> Unit = RunnerHost::send,
): Boolean {
    val snapshot = (view as? RunnerView.Running)?.snapshot
    val command =
        snapshot?.let {
            uri?.let(::parseSessionTap)?.command(it.sessionId, it.inputs.roundsCompleted)
        }
    command?.let(send)
    return command != null
}

/**
 * The buttons' receiver. Not exported: only the notification's own `PendingIntent`s reach it. Its taps
 * are broadcasts, never service intents: `RunnerService` enters the foreground on any intent before
 * reading it, so a late tap delivered to a stopped service would raise a placeholder nothing stops. And
 * a broadcast can't open an activity, which keeps a tap on the wrist from pulling the phone out.
 */
class SessionControlReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        sendSessionTap(intent.data)
    }
}

private const val SCHEME = "kettle"
private const val HOST = "session-control"
private const val ADVANCE = "advance"
private const val PREV = "prev"
private const val ADD_REST = "add30"
private const val RESUME = "resume"
private const val ROUNDS = "rounds"
