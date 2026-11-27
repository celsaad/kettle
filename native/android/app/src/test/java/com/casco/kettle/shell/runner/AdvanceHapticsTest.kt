package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerCommand
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * When the runner buzzes: each advance of one session, and the one that ends it, while it's watched and
 * sounds are on.
 */
@RunWith(RobolectricTestRunner::class)
class AdvanceHapticsTest : RunnerScreenHarness() {
    private val sets = plan(block(squat))
    private val first = started(sets)

    private fun view(state: com.casco.kettle.domain.RunnerState) = running(sets, state).view

    /** Each view in turn, watched and not finishing unless said: which of them buzzed. */
    private fun AdvanceHaptics.feed(
        vararg views: RunnerView,
        watching: Boolean = true,
        finishing: Boolean = false,
        soundsOn: Boolean = true,
    ) = views.map { buzzes(it, watching, finishing, soundsOn) }

    @Test
    fun `an advance buzzes, the first view and a step back don't`() {
        val rest = first.advanced()
        val buzzed =
            AdvanceHaptics().feed(
                view(first),
                view(rest),
                view(rest.after(RunnerCommand.Prev(rest.snapshot.visit))),
            )
        assertEquals(listOf(false, true, false), buzzed)
    }

    @Test
    fun `an advance nobody watches buzzes nobody`() {
        assertEquals(listOf(false, false), AdvanceHaptics().feed(view(first), view(first.advanced()), watching = false))
    }

    @Test
    fun `the advance that ends the workout buzzes, a Finish doesn't`() {
        val done = RunnerView.Completed(sets, first.session, FinishBackupState.NONE)
        assertEquals(listOf(false, true), AdvanceHaptics().feed(view(first), done))
        assertEquals(listOf(false, false), AdvanceHaptics().feed(view(first), done, finishing = true))
    }

    /** One switch makes a session quiet (#301): with sounds off, no step buzzes either. */
    @Test
    fun `with sounds off no advance buzzes, the last one included`() {
        val done = RunnerView.Completed(sets, first.session, FinishBackupState.NONE)
        val buzzed = AdvanceHaptics().feed(view(first), view(first.advanced()), done, soundsOn = false)
        assertEquals(listOf(false, false, false), buzzed)
    }
}
