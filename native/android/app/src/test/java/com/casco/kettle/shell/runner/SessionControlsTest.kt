package com.casco.kettle.shell.runner

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerTransition
import com.casco.kettle.domain.reduceRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Session controls' taps (docs/watch-remote-plan.md, "Aiming every tap"): each one carries the session
 * and the step its card was drawn for, in the intent's identity, and goes nowhere else.
 */
@RunWith(RobolectricTestRunner::class)
class SessionControlsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val aim = SessionAim("2026-11-26T09-00-00-000Z", 4)

    private fun tap(control: SessionControl) = parseSessionTap(sessionControlUri(aim, control))

    @Test
    fun `reads every control back from its URI`() {
        listOf(
            SessionControl.Advance,
            SessionControl.Prev,
            SessionControl.AddRest,
            SessionControl.Resume,
            SessionControl.Rounds(3),
        ).forEach { assertEquals(SessionTap(aim, it), tap(it)) }
    }

    @Test
    fun `reads nothing from a URI that isn't a tap`() {
        assertNull(parseSessionTap(Uri.parse("https://example.com/session/x/4/advance")))
        assertNull(parseSessionTap(Uri.parse("kettle://session-control/x/four/advance")))
        assertNull(parseSessionTap(Uri.parse("kettle://session-control/x/4/finish")))
        assertNull(parseSessionTap(Uri.parse("kettle://session-control/x/4/rounds")))
    }

    @Test
    fun `sends the step's commands aimed at the card's visit, for the engine to refuse if stale`() {
        val later = 9L
        assertEquals(RunnerCommand.Advance(4), tap(SessionControl.Advance)?.command(aim.sessionId, later, 0))
        assertEquals(RunnerCommand.Prev(4), tap(SessionControl.Prev)?.command(aim.sessionId, later, 0))
        assertEquals(
            RunnerCommand.AddRestSeconds(30.0, 4),
            tap(SessionControl.AddRest)?.command(aim.sessionId, later, 0),
        )
        assertEquals(RunnerCommand.TogglePause, tap(SessionControl.Resume)?.command(aim.sessionId, later, 0))
    }

    /** Visits start again at 0 in every session, so a visit alone would let a late tap land in the next one. */
    @Test
    fun `drops a tap from another session`() {
        assertNull(tap(SessionControl.Prev)?.command("2026-11-26T10-00-00-000Z", aim.visit, 0))
    }

    /** The engine doesn't aim SetRounds, so a late "Round N done" would set the next AMRAP's rounds. */
    @Test
    fun `sets the rounds only on the step the card was drawn for`() {
        assertEquals(RunnerCommand.SetRounds(3), tap(SessionControl.Rounds(3))?.command(aim.sessionId, aim.visit, 2))
        assertNull(tap(SessionControl.Rounds(3))?.command(aim.sessionId, aim.visit + 1, 2))
    }

    /**
     * Inside an AMRAP the visit doesn't move, so a card that lagged the phone's own counting would send its
     * absolute count and take back rounds counted since. Remove the count check in `command` and this fails.
     */
    @Test
    fun `never takes back rounds counted since the card was drawn`() {
        assertNull(tap(SessionControl.Rounds(3))?.command(aim.sessionId, aim.visit, 3))
        assertNull(tap(SessionControl.Rounds(3))?.command(aim.sessionId, aim.visit, 4))
    }

    @Test
    fun `sends nothing without a running session, as in the count-in`() {
        val sent = mutableListOf<RunnerCommand>()
        val countIn = RunnerView.CountingIn(planOf(GRINDER), null, CountIn(T0))
        val uri = sessionControlUri(aim, SessionControl.Advance)

        assertFalse(sendSessionTap(uri, countIn, sent::add))
        assertFalse(sendSessionTap(uri, null, sent::add))
        assertEquals(emptyList<RunnerCommand>(), sent)
    }

    /** The receiver checks a round against the step on screen, not against the visit the tap carries. */
    @Test
    fun `sends a round only while the session is still on the card's step`() {
        val sent = mutableListOf<RunnerCommand>()
        val amrap = started(GRINDER)
        val view = RunnerView.Running(planOf(GRINDER), amrap.state.snapshot, amrap.state.session)
        val own = SessionAim(amrap.state.session.id, amrap.state.snapshot.visit)

        assertTrue(sendSessionTap(sessionControlUri(own, SessionControl.Rounds(1)), view, sent::add))
        assertFalse(
            sendSessionTap(
                sessionControlUri(own.copy(visit = own.visit + 1), SessionControl.Rounds(1)),
                view,
                sent::add,
            ),
        )
        assertEquals(listOf<RunnerCommand>(RunnerCommand.SetRounds(1)), sent)
    }

    /** What a Live Update and the watch read off the built notification, not just the content behind it. */
    @Test
    fun `asks to be a Live Update, with the chip, the workout and the buttons in order`() {
        val (_, rest) = setThenRest(started(PULLUPS))
        val open = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val notification = ongoingNotification(context, rest, open)
        val set = ongoingNotification(context, ongoingContent(context.resources, view(started(PULLUPS))), open)

        assertTrue(NotificationCompat.isRequestPromotedOngoing(notification))
        assertTrue(NotificationCompat.hasPromotableCharacteristics(notification))
        assertEquals("Morning", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        assertEquals(listOf("Skip rest", "Back", "+30s"), notification.actions.map { it.title.toString() })
        assertEquals("Set 1/3", NotificationCompat.getShortCriticalText(set))
    }

    /**
     * The spike's bug (#549): the visit in an extra, one request code and FLAG_UPDATE_CURRENT, so posting
     * the next step's card rewrote the token the last one still held, and its late tap arrived aimed at
     * the new step. Put the visit back in an extra and this fails.
     */
    @Test
    fun `keeps an older card's buttons aimed at its own step once the next card is posted`() {
        val (set, rest) = setThenRest(started(PULLUPS))
        val older = actionIntent(set)
        val newer = actionIntent(rest)

        assertNotEquals(older, newer)
        assertEquals(set.aim, savedData(older)?.let(::parseSessionTap)?.aim)
    }

    @Test
    fun `sends each tap to the session-control receiver alone`() {
        val tap = shadowOf(actionIntent(ongoingContent(context.resources, view(started(PULLUPS)))))

        assertEquals(SessionControlReceiver::class.java.name, tap.savedIntent.component?.className)
        assertTrue(tap.isBroadcast)
    }

    private fun view(transition: RunnerTransition) =
        RunnerView.Running(planOf(PULLUPS), transition.state.snapshot, transition.state.session)

    /** The first set's card, then the rest's after it. */
    private fun setThenRest(set: RunnerTransition): Pair<OngoingContent, OngoingContent> {
        val rest =
            reduceRunner(set.state, RunnerCommand.Advance(set.state.snapshot.visit), T0 + 5000)
        return ongoingContent(context.resources, view(set)) to ongoingContent(context.resources, view(rest))
    }

    /** The card's first button, as the notification it builds carries it. */
    private fun actionIntent(content: OngoingContent): PendingIntent {
        val open = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        return ongoingNotification(context, content, open).actions.first().actionIntent
    }

    private fun savedData(intent: PendingIntent): Uri? = shadowOf(intent).savedIntent.data
}
