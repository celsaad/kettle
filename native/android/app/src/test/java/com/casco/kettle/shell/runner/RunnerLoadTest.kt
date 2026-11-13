package com.casco.kettle.shell.runner

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reps set's load (#410): "Try" for double progression, and both it and last time's adopt writing
 * the library only once the set is logged with the load picked. In `pt`, as [RunnerScreenTest] is.
 */
@RunWith(RobolectricTestRunner::class)
// A phone's size, as RunnerScreenTest's: the runner's screens don't scroll.
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerLoadTest : RunnerScreenHarness() {
    /** Last session's bench: every set at [reps], at 60 kg. */
    private fun lastBench(reps: Long) =
        Session(
            1,
            "old",
            "w",
            null,
            null,
            null,
            "2026-08-01T10:00:00.000Z",
            "2026-08-01T10:30:00.000Z",
            listOf(SessionEntry.Reps("bench", List(2) { RepsSetLog(reps, 60.0, restTakenSec = 90.0) })),
        )

    @Test
    fun `Try offers the next load once every set reached the top, and logging it adopts it`() {
        prior = listOf(lastBench(12))
        show(running(plan(block(bench))))
        button("Tentar 62.5 kg").assertIsTarget().performClick()
        assertEquals(RunnerCommand.SetWeight(62.5), sent.last())
        assertEquals(emptyList<Pair<String, Double>>(), adopted)

        show(running(plan(block(bench)), started(plan(block(bench))).after(RunnerCommand.SetWeight(62.5))))
        button("Registrar série → Descanso").performClick()
        assertEquals(listOf("bench" to 62.5), adopted)
    }

    @Test
    fun `Try's load changed before logging is never adopted`() {
        prior = listOf(lastBench(12))
        show(running(plan(block(bench))))
        button("Tentar 62.5 kg").performClick()

        show(running(plan(block(bench)), started(plan(block(bench))).after(RunnerCommand.SetWeight(65.0))))
        button("Registrar série → Descanso").performClick()
        assertEquals(emptyList<Pair<String, Double>>(), adopted)
    }

    @Test
    fun `adding a set today keeps Try, since it's last session that has to reach the top`() {
        prior = listOf(lastBench(12))
        show(running(plan(block(bench)), started(plan(block(bench))).after(RunnerCommand.AddSet)))
        button("Tentar 62.5 kg").assertExists()
    }

    @Test
    fun `no Try when a set fell short of the top last time`() {
        prior = listOf(lastBench(11))
        show(running(plan(block(bench))))
        compose.onNodeWithText("Tentar 62.5 kg").assertDoesNotExist()
        compose.onNodeWithText("Da última vez · 60 kg × 11 repetições").assertExists()
    }
}
