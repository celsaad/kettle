package com.casco.kettle.shell.runner

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the runner's screen observes (#297), held across the service's late answers (#317's review). */
class RunnerHostTest {
    private val start = started(PLANK).state
    private val running = RunnerView.Running(planOf(PLANK), start.snapshot, start.session)
    private val completed = RunnerView.Completed(planOf(PLANK), start.session, FinishBackupState.PENDING)

    @After
    fun idle() = RunnerHost.publish(RunnerView.Idle)

    @Test
    fun `a backup answering after Done doesn't bring the completion back`() {
        // The finish backup answers by republishing the completion with its outcome. Landing after Done,
        // it put the completion back, and the next runner opened on it rather than on its own start.
        RunnerHost.publish(running)
        RunnerHost.publish(completed)
        RunnerHost.dismiss()
        RunnerHost.publish(completed.copy(backup = FinishBackupState.OK))

        assertEquals(RunnerView.Idle, RunnerHost.view.value)
    }

    @Test
    fun `a backup answering before Done updates the completion on screen`() {
        RunnerHost.publish(running)
        RunnerHost.publish(completed)
        RunnerHost.publish(completed.copy(backup = FinishBackupState.FAILED))

        assertEquals(FinishBackupState.FAILED, (RunnerHost.view.value as RunnerView.Completed).backup)
    }
}
