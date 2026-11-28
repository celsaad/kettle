package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * An exercise's equipment (#393): the field as the file spells it, matching it against the known
 * items, and a workout's, combined. The corpus holds the same rules as data (`conformance/library.yaml`'s
 * `equipment-*` cases and the `equipment` suite); these are the ones about the model and the writer
 * it doesn't see.
 */
class EquipmentTest {
    private val known = setOf("dumbbell", "pull_up_bar", "bench", "barbell", "kettlebell", "mat")

    private fun exercise(
        id: String,
        vararg equipment: String,
    ) = Exercise(id, id.uppercase(), ExerciseConfig.Rest(1.0), equipment = equipment.toList())

    private fun parsed(text: String): Library = (parseLibraryYaml(text) as ParseResult.Ok).value

    @Nested
    inner class Reading {
        @Test
        fun `reads two known items and one of the user's own, the repeated one once`() {
            val library =
                parsed(
                    """
                    version: 1
                    exercises:
                      - id: a
                        name: A
                        type: rest
                        equipment: [Dumbbell, pull-up bar, dumbbell, Foam Thing]
                        config: { duration_sec: 1 }
                    workouts: []
                    programs: []
                    """.trimIndent(),
                )
            val equipment = library.exercises.single().equipment
            assertEquals(listOf("Dumbbell", "pull-up bar", "Foam Thing"), equipment)
            assertEquals(listOf("dumbbell", "pull_up_bar", null), equipment.map { knownEquipment(it, known) })
        }

        @Test
        fun `writes nothing for an exercise with none, from an empty list or no key`() {
            val library =
                parsed(
                    """
                    version: 1
                    exercises:
                      - { id: a, name: A, type: rest, equipment: [], config: { duration_sec: 1 } }
                      - { id: b, name: B, type: rest, config: { duration_sec: 1 } }
                    workouts: []
                    programs: []
                    """.trimIndent(),
                )
            assertEquals(listOf(emptyList<String>(), emptyList()), library.exercises.map { it.equipment })
            assertFalse("equipment" in serializeLibraryYaml(library))
        }

        @Test
        fun `writes the list back as it was read`() {
            val library = Library(1, listOf(exercise("a", "Bench", "Foam Thing")), emptyList(), emptyList())
            assertEquals(ParseResult.Ok(library), parseLibraryYaml(serializeLibraryYaml(library)))
        }
    }

    @Nested
    inner class Matching {
        @Test
        fun `folds case, spaces, hyphens and the whitespace around it`() {
            assertEquals("pull_up_bar", knownEquipment("\u00A0Pull-Up Bar\t", known))
        }

        @Test
        fun `knows no plurals, synonyms or abbreviations`() {
            assertEquals(
                listOf(null, null, null),
                listOf("dumbbells", "DB", "bar bell").map { knownEquipment(it, known) },
            )
        }

        @Test
        fun `trims what JavaScript trims, not what Kotlin does`() {
            // U+FEFF is whitespace to JS's trim and not to Kotlin's; U+001F the other way round.
            assertEquals("bench", equipmentMatchKey("\uFEFFbench"))
            assertEquals("\u001Fbench", equipmentMatchKey("\u001Fbench"))
        }
    }

    @Nested
    inner class Workouts {
        private fun library(vararg exercises: Exercise) = Library(1, exercises.toList(), emptyList(), emptyList())

        private fun block(id: String) = WorkoutBlock.ExerciseBlock(id)

        private fun circuit(vararg ids: String) =
            WorkoutBlock.Circuit(rounds = 3, members = ids.map { CircuitMember(it) })

        @Test
        fun `combines in the order the workout first reaches each item, a circuit's members included`() {
            val lib =
                library(
                    exercise("swing", "Kettlebell"),
                    exercise("row", "mat", "Dumbbell"),
                    exercise("press", "dumbbell", "bench"),
                    exercise("plank"),
                )
            val workout =
                Workout("w", "W", listOf(block("plank"), circuit("swing", "row"), block("press"), block("swing")))
            assertEquals(listOf("Kettlebell", "mat", "Dumbbell", "bench"), workoutEquipment(lib, workout))
        }

        @Test
        fun `shares an item between a straight block and a circuit after it`() {
            val lib = library(exercise("squat", "barbell"), exercise("lunge", "Barbell"), exercise("jump"))
            val workout = Workout("w", "W", listOf(block("squat"), circuit("jump", "lunge")))
            assertEquals(listOf("barbell"), workoutEquipment(lib, workout))
        }

        @Test
        fun `is empty when no exercise needs anything, or the block names no exercise`() {
            val lib = library(exercise("plank"))
            assertEquals(emptyList<String>(), workoutEquipment(lib, Workout("w", "W", listOf(block("plank")))))
            assertEquals(emptyList<String>(), workoutEquipment(lib, Workout("w", "W", listOf(block("gone")))))
        }
    }
}
