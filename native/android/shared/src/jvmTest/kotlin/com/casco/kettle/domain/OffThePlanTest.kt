package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Today's "Off the plan" (#412): what was run most recently, then the library, never the card's own. */
class OffThePlanTest {
    private val library =
        Library(
            1,
            RUNNER_EXERCISES,
            listOf("a", "b", "c", "d").map { Workout(it, it.uppercase(), emptyList()) },
            emptyList(),
        )

    private fun ran(vararg workouts: String?) =
        workouts.mapIndexed {
            i,
            id,
            ->
            Session(1, "s$i", id, null, null, null, "2026-11-0${9 - i}T09:00:00.000Z", null, emptyList())
        }

    private fun ids(list: List<Workout>) = list.map { it.id }

    @Test
    fun `the two run most recently, newest first, each once`() {
        assertEquals(listOf("c", "a"), ids(offThePlan(library, ran("c", "c", "a", "b"), except = null)))
    }

    @Test
    fun `leaves out the workout the card offers`() {
        assertEquals(listOf("a", "b"), ids(offThePlan(library, ran("c", "a", "b"), except = "c")))
    }

    @Test
    fun `fills up from the library in file order, with no history or not enough of it`() {
        assertEquals(listOf("a", "b"), ids(offThePlan(library, emptyList(), except = null)))
        assertEquals(listOf("d", "a"), ids(offThePlan(library, ran("d"), except = null)))
    }

    @Test
    fun `passes over ad-hoc sessions and workouts the library no longer has`() {
        assertEquals(listOf("b", "a"), ids(offThePlan(library, ran(null, "gone", "b"), except = null)))
    }
}
