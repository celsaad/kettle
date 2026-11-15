package com.casco.kettle.shell.runner

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.shell.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the line under a clock's bar says (#471): HIIT's length and what it leads to, cardio's way of
 * ending, an EMOM's minute; and "Paused" over a stopped clock. In `pt`, as [RunnerScreenTest] is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h891dp")
class RunnerClockHintTest : RunnerScreenHarness() {
    private fun text(text: String) = compose.onNodeWithText(text)

    private fun absent(text: String) = compose.onNodeWithText(text, substring = true).assertDoesNotExist()

    @Test
    fun `a HIIT round says its length, then the rest after it`() {
        show(running(plan(block(burpees))))
        text("de 0:20").assertExists()
        text("Depois, descanso de 0:10").assertExists()
    }

    @Test
    fun `the last HIIT round names the exercise after it, and says nothing at the session's end`() {
        val hiit = plan(block(burpees), block(plank))
        // Round 1, its rest, then round 2: the last, with the plank next.
        show(running(hiit, started(hiit).advanced().advanced()))
        text("Depois: Prancha").assertExists()
        absent("descanso de")

        val alone = plan(block(burpees))
        show(running(alone, started(alone).advanced().advanced()))
        text("de 0:20").assertExists()
        absent("Depois")
    }

    @Test
    fun `cardio says it ends by itself`() {
        show(running(plan(block(run))))
        text("de 10:00 · termina sozinho").assertExists()
        absent("Depois")
    }

    @Test
    fun `an EMOM counts down what's left of the minute, its target left to the stepper`() {
        show(running(plan(block(everyMinute))))
        text("restantes neste minuto").assertExists()
        absent("meta de")
    }

    @Test
    fun `a stopped clock says Paused, and a running one doesn't`() {
        val hiit = plan(block(burpees))
        show(running(hiit))
        absent("Pausado")

        show(running(hiit, started(hiit).after(RunnerCommand.TogglePause)))
        text("Pausado").assertExists()
        // Paused, the line under the bar still says what's next.
        text("Depois, descanso de 0:10").assertExists()
    }

    @Test
    fun `a paused hold says so too`() {
        val hold = plan(block(plank))
        show(running(hold, started(hold).after(RunnerCommand.TogglePause)))
        text("Pausado").assertExists()
    }

    /** The column's width, as the boards draw it: it was a 220dp strip in the middle. */
    @Test
    fun `the bar runs the column's width`() {
        show(running(plan(block(burpees))))
        val bar = compose.onNodeWithTag(BAR_TAG).getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val column = root.right - root.left - Spacing.three * 2
        assertEquals(column.value, (bar.right - bar.left).value, 1.dp.value)
    }

    // Native graphics: legacy mode measures text far narrower than a device, so nothing overflows in it.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the line's two ends share it at the default size, and stack at a large one`() {
        show(running(plan(block(burpees))))
        var length = text("de 0:20").getUnclippedBoundsInRoot()
        var next = text("Depois, descanso de 0:10").getUnclippedBoundsInRoot()
        assertEquals(length.top.value, next.top.value, 1f)

        fontScale = 2f
        compose.waitForIdle()
        length = text("de 0:20").getUnclippedBoundsInRoot()
        next = text("Depois, descanso de 0:10").getUnclippedBoundsInRoot()
        assertTrue(length.bottom <= next.top)
    }
}
