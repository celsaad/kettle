package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerState
import com.casco.kettle.domain.reduceRunner
import com.casco.kettle.domain.startRunner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a load picked by Last time or Try reaches the library (#410): only as its set is logged with it. */
class PendingAdoptTest {
    private val plan = planOf(PULLUPS, PLANK)
    private val start = startRunner(plan, null, now = T0).state

    private fun view(state: RunnerState) = RunnerView.Running(plan, state.snapshot, state.session)

    private fun RunnerState.after(command: RunnerCommand) = reduceRunner(this, command, T0).state

    private val picked = start.after(RunnerCommand.SetWeight(25.0))
    private val pending = PendingAdopt("pullups", 25.0, picked.snapshot.visit)

    @Test
    fun `logging the set, or ending the session on it, adopts the load picked`() {
        assertTrue(adoptsOn(pending, RunnerCommand.Advance(pending.visit), view(picked)))
        assertTrue(adoptsOn(pending, RunnerCommand.Finish(pending.visit), view(picked)))
    }

    @Test
    fun `a changed load, another visit, another command or nothing pending adopts nothing`() {
        assertFalse(
            adoptsOn(pending, RunnerCommand.Advance(pending.visit), view(picked.after(RunnerCommand.SetWeight(30.0)))),
        )
        assertFalse(adoptsOn(pending, RunnerCommand.Advance(pending.visit + 1), view(picked)))
        assertFalse(adoptsOn(pending, RunnerCommand.Prev, view(picked)))
        assertFalse(adoptsOn(null, RunnerCommand.Advance(pending.visit), view(picked)))
        assertFalse(adoptsOn(pending.copy(exerciseId = "dips"), RunnerCommand.Advance(pending.visit), view(picked)))
    }
}
