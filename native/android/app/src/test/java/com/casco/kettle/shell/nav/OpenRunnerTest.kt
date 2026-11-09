package com.casco.kettle.shell.nav

import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.shell.rn.Stacking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one way into the runner (#302), the native one in every build since the flip release (#308). */
class OpenRunnerTest {
    private fun opened(start: RunnerStart): Triple<Stacking, String, Map<String, String>> {
        val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()
        openRunner(start) { how, path, params -> opened += Triple(how, path, params) }
        return opened.single()
    }

    private val workout = RunnerStart.Planned(SessionParams.Workout("bench"))
    private val slot = RunnerStart.Planned(SessionParams.ProgramSlot("base", 1, "Day 1"))

    @Test
    fun `each start pushes the native runner's stub with the params it reads back`() {
        assertEquals(Triple(Stacking.Push, "/session-native", mapOf("workoutId" to "bench")), opened(workout))
        assertEquals(
            Triple(Stacking.Push, "/session-native", mapOf("programId" to "base", "week" to "1", "day" to "Day 1")),
            opened(slot),
        )
        assertEquals(
            Triple(Stacking.Push, "/session-native", mapOf("adhoc" to "1")),
            opened(RunnerStart.Adhoc),
        )
    }

    // RN's push left an undefined `day` out, and the runner reads its absence as "no day".
    @Test
    fun `a slot with no day is pushed without one`() {
        val start = RunnerStart.Planned(SessionParams.ProgramSlot("plain", 2, null))

        assertEquals(mapOf("programId" to "plain", "week" to "2"), opened(start).third)
    }

    // The schema takes `day: ""`, and the push carries it, as RN's did.
    @Test
    fun `a slot with an empty day still carries it`() {
        val start = RunnerStart.Planned(SessionParams.ProgramSlot("blank", 3, ""))

        assertEquals(mapOf("programId" to "blank", "week" to "3", "day" to ""), opened(start).third)
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
        for (start in starts) assertEquals(start, runnerStartFrom(opened(start).third))
    }

    @Test
    fun `params that name no start read back as none`() {
        assertEquals(null, runnerStartFrom(emptyMap()))
        assertEquals(null, runnerStartFrom(mapOf("programId" to "base", "week" to "one")))
    }
}
