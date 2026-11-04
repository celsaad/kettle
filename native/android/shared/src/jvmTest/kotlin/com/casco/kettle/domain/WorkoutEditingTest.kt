package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * What the workout editor (#242) does to its draft and to the library: the state `workout-editor.tsx`
 * and `workout-circuit-block.tsx` kept inline, and the store's `saveWorkout`/`deleteWorkout`. The
 * screen's own cases are `WorkoutEditorScreenTest`'s; these are the rules under them.
 */
class WorkoutEditingTest {
    private val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))

    @Nested
    inner class Submitting {
        @Test
        fun `refuses a blank name`() {
            assertEquals(
                WorkoutSubmit.Refused(WorkoutProblem.NameRequired),
                submitWorkout(WorkoutDraft().copy(name = "   "), null),
            )
        }

        // A name of only emoji slugs to "", which no lookup by id could ever reach.
        @Test
        fun `refuses a name that yields no id`() {
            assertEquals(
                WorkoutSubmit.Refused(WorkoutProblem.NoId),
                submitWorkout(WorkoutDraft().copy(name = "🏋️🏋️"), null),
            )
        }

        @Test
        fun `derives the id from the name and trims it`() {
            assertEquals(
                WorkoutSubmit.Built(Workout("push-day", "Push day", emptyList())),
                submitWorkout(WorkoutDraft().copy(name = "  Push day  "), null),
            )
        }

        // Program weeks point at workouts by id: re-deriving it on a rename would orphan them.
        @Test
        fun `keeps an existing id through a rename`() {
            val draft = workoutDraftOf(pushDay).copy(name = "Upper body")
            assertEquals(
                WorkoutSubmit.Built(pushDay.copy(name = "Upper body")),
                submitWorkout(draft, pushDay),
            )
        }
    }

    @Nested
    inner class Blocks {
        private val three = WorkoutDraft().adding("a").adding("b").adding("c")

        private fun WorkoutDraft.ids() = blocks.map { (it.block as WorkoutBlock.ExerciseBlock).exerciseId }

        @Test
        fun `adds a picked exercise at the end`() {
            assertEquals(listOf("a", "b", "c"), three.ids())
        }

        @Test
        fun `removes a block by its key, and only that one`() {
            // The same exercise twice is legal, so a removal by exercise id would take both.
            val twice = WorkoutDraft().adding("a").adding("a")
            assertEquals(listOf(twice.blocks[1]), twice.removing(twice.blocks[0].key).blocks)
        }

        @Test
        fun `moves a block from one index to another, as a drag does`() {
            assertEquals(listOf("c", "a", "b"), three.moving(2, 0).ids())
            assertEquals(listOf("b", "c", "a"), three.moving(0, 2).ids())
        }

        @Test
        fun `moves a block one place by its key, as a screen reader's action does`() {
            val c = three.blocks[2].key
            assertEquals(listOf("a", "c", "b"), three.movingBy(c, -1).ids())
        }

        // RN's slot keys turned a second "move up" into an undo of the first.
        @Test
        fun `keeps the key with the block, so two moves up move the same block`() {
            val c = three.blocks[2].key
            assertEquals(listOf("c", "a", "b"), three.movingBy(c, -1).movingBy(c, -1).ids())
        }

        @Test
        fun `does nothing moving the first up or the last down`() {
            assertEquals(three, three.movingBy(three.blocks[0].key, -1))
            assertEquals(three, three.movingBy(three.blocks[2].key, 1))
        }

        @Test
        fun `never reuses a key, even after a removal`() {
            val draft = three.removing(three.blocks[2].key).adding("d")
            assertEquals(
                3,
                draft.blocks
                    .map { it.key }
                    .distinct()
                    .size,
            )
        }

        @Test
        fun `keys the blocks of an existing workout apart`() {
            val draft = workoutDraftOf(pushDay.copy(blocks = pushDay.blocks + pushDay.blocks)).adding("dips")
            assertEquals(
                3,
                draft.blocks
                    .map { it.key }
                    .distinct()
                    .size,
            )
        }
    }

    @Nested
    inner class Circuits {
        private val two = listOf(CircuitMember("pull-ups"), CircuitMember("dips"))
        private val three = two + CircuitMember("rows")

        private fun circuit(members: List<CircuitMember> = two) = WorkoutBlock.Circuit(rounds = 3, members = members)

        // A round-robin of one is a plain block with extra rest settings, and the schema refuses it.
        @Test
        fun `will not build a circuit from a single exercise`() {
            assertEquals(WorkoutDraft(), WorkoutDraft().addingCircuit(listOf("pull-ups")))
        }

        @Test
        fun `builds one from two, with RN's defaults`() {
            assertEquals(
                listOf(WorkoutBlock.Circuit(null, 3, 15.0, 60.0, two)),
                WorkoutDraft().addingCircuit(listOf("pull-ups", "dips")).blocks.map { it.block },
            )
        }

        @Test
        fun `selects a member once, and deselects it on a second tap`() {
            assertEquals(listOf("pull-ups", "dips"), toggledMember(listOf("pull-ups"), "dips"))
            assertEquals(listOf("dips"), toggledMember(listOf("pull-ups", "dips"), "pull-ups"))
        }

        // -1 rounds builds no steps, a workout that starts and at once ends.
        @Test
        fun `keeps rounds at one or more`() {
            assertEquals(1L, circuit().withRounds(-1).rounds)
        }

        // 501 is a library that fails to parse on the next launch, and was silently reseeded.
        @Test
        fun `keeps rounds at or below the schema ceiling`() {
            assertEquals(500L, circuit().withRounds(501).rounds)
        }

        @Test
        fun `removes a member while there is one to spare`() {
            assertEquals(true, circuit(three).canRemoveMember)
            assertEquals(listOf(two[0], three[2]), circuit(three).withoutMember(1).members)
        }

        @Test
        fun `refuses to take a two-member circuit down to one`() {
            assertEquals(false, circuit().canRemoveMember)
            assertEquals(circuit(), circuit().withoutMember(1))
        }

        // `Number('-5') || 0` is -5, and the schema is non-negative, so a minus sign reached the file.
        @Test
        fun `reads a rest as seconds, never below zero`() {
            assertEquals(0.0, restSeconds("-5"))
            assertEquals(0.0, restSeconds(""))
            assertEquals(0.0, restSeconds("abc"))
            assertEquals(20.0, restSeconds(" 20 "))
        }

        @Test
        fun `treats a blank id as none`() {
            assertEquals(null, circuit().withId("  ").id)
            assertEquals("finisher", circuit().withId(" finisher ").id)
        }

        @Test
        fun `changes only the circuit it is keyed to`() {
            val draft = WorkoutDraft().adding("a").addingCircuit(listOf("pull-ups", "dips"))
            val edited = draft.withCircuit(draft.blocks[1].key) { it.withRounds(5) }
            assertEquals(draft.blocks[0], edited.blocks[0])
            assertEquals(5L, (edited.blocks[1].block as WorkoutBlock.Circuit).rounds)
        }
    }

    @Nested
    inner class TheLibrary {
        private val legDay = Workout("leg-day", "Leg day", emptyList())

        private fun library(programs: List<Program> = emptyList()) =
            Library(1, emptyList(), listOf(pushDay, legDay), programs)

        @Test
        fun `replaces an edited workout where it stands, and adds a new one at the end`() {
            val renamed = pushDay.copy(name = "Upper body")
            val core = Workout("core", "Core", emptyList())
            assertEquals(listOf(renamed, legDay), library().withWorkout(renamed).workouts)
            assertEquals(listOf(pushDay, legDay, core), library().withWorkout(core).workouts)
        }

        @Test
        fun `deletes by id and leaves the rest`() {
            assertEquals(listOf(legDay), library().withoutWorkout("push-day").workouts)
        }

        // A program left resolving a week to a workout that no longer exists.
        @Test
        fun `names the programs a training week of which points at the workout`() {
            val base = Program("base", "Base", listOf(ProgramWeek.Training(week = 1, workoutId = "push-day")))
            val other = Program("other", "Other", listOf(ProgramWeek.Training(week = 1, workoutId = "leg-day")))
            assertEquals(listOf(base), programsUsing(library(listOf(base, other)), "push-day"))
        }

        @Test
        fun `does not count a rest week, which points at no workout`() {
            val restOnly = Program("rest", "Rest", listOf(ProgramWeek.RestDay(week = 1)))
            assertEquals(emptyList<Program>(), programsUsing(library(listOf(restOnly)), "push-day"))
        }
    }
}
