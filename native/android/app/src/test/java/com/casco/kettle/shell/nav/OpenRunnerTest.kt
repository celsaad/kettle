package com.casco.kettle.shell.nav

import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.shell.rn.Stacking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one way into a runner (#302): RN's with the flip off, as Play has it, and the native one with it
 * on, as `.dev` has it. The flag is passed in, since the unit tests run on debug alone.
 */
class OpenRunnerTest {
    private fun opened(
        start: RunnerStart,
        nativeRunner: Boolean,
    ): Triple<Stacking, String, Map<String, String>> {
        val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()
        openRunner(start, { how, path, params -> opened += Triple(how, path, params) }, nativeRunner)
        return opened.single()
    }

    private val workout = RunnerStart.Planned(SessionParams.Workout("bench"))
    private val slot = RunnerStart.Planned(SessionParams.ProgramSlot("base", 1, "Day 1"))

    @Test
    fun `with the flip off, each start pushes RN's runner with the params session tsx reads`() {
        assertEquals(Triple(Stacking.Push, "/session", mapOf("workoutId" to "bench")), opened(workout, false))
        assertEquals(
            Triple(Stacking.Push, "/session", mapOf("programId" to "base", "week" to "1", "day" to "Day 1")),
            opened(slot, false),
        )
        assertEquals(Triple(Stacking.Push, "/session", mapOf("adhoc" to "1")), opened(RunnerStart.Adhoc, false))
    }

    @Test
    fun `with the flip on, each start pushes the native runner's stub with the same params`() {
        assertEquals(Triple(Stacking.Push, "/session-native", mapOf("workoutId" to "bench")), opened(workout, true))
        assertEquals(
            Triple(Stacking.Push, "/session-native", mapOf("programId" to "base", "week" to "1", "day" to "Day 1")),
            opened(slot, true),
        )
        assertEquals(
            Triple(Stacking.Push, "/session-native", mapOf("adhoc" to "1")),
            opened(RunnerStart.Adhoc, true),
        )
    }

    // RN's push left an undefined `day` out, and the runner reads its absence as "no day".
    @Test
    fun `a slot with no day is pushed without one`() {
        val start = RunnerStart.Planned(SessionParams.ProgramSlot("plain", 2, null))

        assertEquals(mapOf("programId" to "plain", "week" to "2"), opened(start, false).third)
    }

    // The schema takes `day: ""`, and the push carries it, as RN's did.
    @Test
    fun `a slot with an empty day still carries it`() {
        val start = RunnerStart.Planned(SessionParams.ProgramSlot("blank", 3, ""))

        assertEquals(mapOf("programId" to "blank", "week" to "3", "day" to ""), opened(start, false).third)
    }

    /** The placeholder, and #306's runner after it, read the start back from RN's report of the route. */
    @Test
    fun `a start reads back from the params it was pushed with`() {
        val starts =
            listOf(
                workout,
                slot,
                RunnerStart.Planned(SessionParams.ProgramSlot("plain", 2, null)),
                RunnerStart.Planned(SessionParams.ProgramSlot("blank", 3, "")),
                RunnerStart.Adhoc,
            )
        for (start in starts) assertEquals(start, runnerStartFrom(opened(start, true).third))
    }

    @Test
    fun `params that name no start read back as none`() {
        assertEquals(null, runnerStartFrom(emptyMap()))
        assertEquals(null, runnerStartFrom(mapOf("programId" to "base", "week" to "one")))
    }
}
