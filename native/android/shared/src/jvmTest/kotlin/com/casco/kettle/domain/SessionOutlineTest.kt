package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The whole session's outline, which Home's preview lists before anything has started (#280). */
class SessionOutlineTest {
    private fun outlineOf(workout: Workout) = sessionOutline(buildSteps(workout, RUNNER_EXERCISES))

    private fun describe(item: UpcomingItem): String? =
        when (item) {
            is UpcomingItem.Exercise -> {
                "${item.step.exerciseName} ${item.left} sets" +
                    if (item.started) " started" else ""
            }

            is UpcomingItem.Rest -> {
                "Rest ${item.seconds.toInt()}s"
            }

            else -> {
                item::class.simpleName
            }
        }

    @Test
    fun `starts at the first step, none of it started`() {
        val items = outlineOf(workoutOf(single("pullups"), single("rest"), single("dips")))
        assertEquals(listOf("Pull-ups 3 sets", "Rest 60s", "Dips 3 sets"), items.map(::describe))
    }

    /** What to pick up is the preview's question, so a circuit's members come with their targets. */
    @Test
    fun `spells a circuit out as its first round, every member with its target`() {
        val round =
            outlineOf(
                workoutOf(single("lsit"), circuitOf("pullups", "dips", rounds = 3)),
            )[1] as UpcomingItem.Round
        assertEquals(listOf(1L, 3L), listOf(round.round, round.rounds))
        assertEquals(
            listOf("Pull-ups" to 6L, "Dips" to 8L),
            round.members.map {
                it.step.exerciseName to
                    (it.step as RunnerStep.Reps).targetReps
            },
        )
    }

    @Test
    fun `is empty for a workout with nothing to run`() {
        assertEquals(emptyList<UpcomingItem>(), sessionOutline(emptyList()))
    }
}
