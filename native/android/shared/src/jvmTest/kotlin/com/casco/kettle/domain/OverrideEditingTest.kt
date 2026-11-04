package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The program editor's overrides panel (#243), as `program-override-editor.test.tsx` holds it, less
 * what is the screen's (`ProgramEditorScreenTest`).
 *
 * The conversion is the point: an override's `config` is a partial, snake_case patch, while the form
 * works in the same strings any exercise form holds. So every assertion is on the patch emitted, which
 * is what ends up in the user's YAML, not on which fields were filled.
 */
class OverrideEditingTest {
    private val pullUps =
        Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))
    private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))
    private val finisher =
        WorkoutBlock.Circuit(
            id = "finisher",
            rounds = 3,
            restBetweenExercisesSec = 15.0,
            restBetweenRoundsSec = 60.0,
            members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")),
        )
    private val workout = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups"), finisher))
    private val library = Library(1, listOf(pullUps, dips), listOf(workout), emptyList())
    private val kg = UnitSystem.METRIC

    private fun sets(n: Double) = mapOf("sets" to OverrideValue.Numeric(n))

    private fun exercise(
        id: String,
        config: Map<String, OverrideValue>,
    ) = ProgramOverride.ExerciseOverride(id, config)

    private fun adding(target: OverrideEditTarget) = OverrideForm.adding(target, kg)

    private fun OverrideForm.typed(
        key: ConfigKey,
        text: String,
    ) = copy(values = values + (key to text))

    private fun OverrideForm.confirmed(overrides: List<ProgramOverride> = emptyList()) =
        (confirmOverride(this, overrides, kg) as OverrideConfirm.Done).overrides

    private val pullUpsTarget = OverrideEditTarget.Exercise(pullUps)
    private val finisherTarget = OverrideEditTarget.Circuit(finisher, "finisher")

    @Nested
    inner class Targets {
        // Dips is eligible through the circuit; an untagged circuit has no id to name, so isn't offered.
        @Test
        fun `offers only the exercises and id-tagged circuits the workout contains`() {
            val untagged = finisher.copy(id = null)
            val withUntagged = workout.copy(blocks = workout.blocks + untagged)
            assertEquals(
                listOf(OverrideEditTarget.Exercise(pullUps), OverrideEditTarget.Exercise(dips), finisherTarget),
                overrideTargets(library, withUntagged),
            )
        }

        @Test
        fun `offers nothing without a workout`() {
            assertEquals(emptyList<OverrideEditTarget>(), overrideTargets(library, null))
        }
    }

    @Nested
    inner class Adding {
        // A patch, not the whole config: writing every field would freeze the week against later edits.
        @Test
        fun `emits only the changed key, in the snake_case the YAML uses`() {
            val form = adding(pullUpsTarget)
            assertEquals("4", form.values[ConfigKey.Sets])
            assertEquals(
                listOf(exercise("pull-ups", sets(5.0))),
                form.typed(ConfigKey.Sets, "5").confirmed(),
            )
        }

        @Test
        fun `emits a block override keyed by the circuit id`() {
            val form = adding(finisherTarget)
            assertEquals("3", form.values[ConfigKey.Rounds])
            assertEquals(
                listOf(ProgramOverride.BlockOverride("finisher", mapOf("rounds" to OverrideValue.Numeric(4.0)))),
                form.typed(ConfigKey.Rounds, "4").confirmed(),
            )
        }

        @Test
        fun `adds alongside an existing override rather than replacing it`() {
            val existing = exercise("pull-ups", sets(5.0))
            assertEquals(
                listOf(existing, exercise("dips", sets(2.0))),
                adding(OverrideEditTarget.Exercise(dips)).typed(ConfigKey.Sets, "2").confirmed(listOf(existing)),
            )
        }

        // An override with no keys saves, renders as a blank row, and changes nothing.
        @Test
        fun `adds nothing when nothing changed`() {
            assertEquals(emptyList<ProgramOverride>(), adding(pullUpsTarget).confirmed())
        }

        // 100 kg shows as 220.5 lb, which converts back to 100.02: an edit to the sets alone mustn't
        // invent a weight override.
        @Test
        fun `does not invent a weight override from an untouched pound field`() {
            val loaded = pullUps.copy(config = (pullUps.config as ExerciseConfig.Reps).copy(targetWeightKg = 100.0))
            val form = OverrideForm.adding(OverrideEditTarget.Exercise(loaded), UnitSystem.IMPERIAL)
            assertEquals("220.5", form.values[ConfigKey.TargetWeightKg])
            val done = confirmOverride(form.typed(ConfigKey.Sets, "5"), emptyList(), UnitSystem.IMPERIAL)
            assertEquals(OverrideConfirm.Done(listOf(exercise("pull-ups", sets(5.0)))), done)
        }

        @Test
        fun `converts a weight typed in pounds to the kilograms stored`() {
            val form = OverrideForm.adding(pullUpsTarget, UnitSystem.IMPERIAL).typed(ConfigKey.TargetWeightKg, "22.05")
            val done = confirmOverride(form, emptyList(), UnitSystem.IMPERIAL) as OverrideConfirm.Done
            val weight =
                (
                    done.overrides
                        .single()
                        .config
                        .getValue("target_weight") as OverrideValue.Numeric
                ).value
            assertEquals(10.0, weight, 0.001)
        }
    }

    @Nested
    inner class Editing {
        private val existing = exercise("pull-ups", sets(5.0))

        // From base + patch, or reopening shows the old numbers and a Save quietly discards the patch.
        @Test
        fun `seeds an edit from the effective value, and replaces in place`() {
            val form = OverrideForm.editing(0, existing, library, workout, kg)!!
            assertEquals("5", form.values[ConfigKey.Sets])
            assertEquals(
                listOf(exercise("pull-ups", sets(6.0))),
                form.typed(ConfigKey.Sets, "6").confirmed(listOf(existing)),
            )
        }

        @Test
        fun `will not open an override whose target has gone`() {
            assertNull(OverrideForm.editing(0, exercise("ghost", sets(5.0)), library, workout, kg))
        }

        @Test
        fun `will not open a circuit override the workout no longer has`() {
            val gone = ProgramOverride.BlockOverride("gone", mapOf("rounds" to OverrideValue.Numeric(2.0)))
            assertNull(OverrideForm.editing(0, gone, library, workout, kg))
        }

        @Test
        fun `saves against the override it opened, not whatever now sits at that index`() {
            val two = listOf(existing, exercise("dips", sets(3.0)))
            val form = OverrideForm.editing(1, two[1], library, workout, kg)!!.typed(ConfigKey.Sets, "6")
            assertEquals(listOf(two[0], exercise("dips", sets(6.0))), form.confirmed(two))
        }

        // The crash RN had: the list shrank under the open panel, and Save dereferenced past the end.
        @Test
        fun `changes nothing when the override it opened is no longer there`() {
            val two = listOf(existing, exercise("dips", sets(3.0)))
            val form = OverrideForm.editing(1, two[1], library, workout, kg)!!.typed(ConfigKey.Sets, "6")
            assertEquals(listOf(existing), form.confirmed(listOf(existing)))
        }

        @Test
        fun `removes an override that ends up with nothing in it rather than saving a blank row`() {
            val dipsAt5 = exercise("dips", sets(5.0))
            val form = OverrideForm.editing(0, dipsAt5, library, workout, kg)!!.typed(ConfigKey.Sets, "4")
            assertEquals(emptyList<ProgramOverride>(), form.confirmed(listOf(dipsAt5)))
        }
    }

    /**
     * An override's `config` is a free record, so a file written before the merge was validated still
     * imports. The editor has to be the way out of such a patch, not what destroys it.
     */
    @Nested
    inner class Refused {
        private val authoredZero = exercise("pull-ups", sets(0.0))

        // 0 is what the user wrote; 4 is the base the merge fell back to.
        @Test
        fun `opens showing what was authored, not the base it fell back to`() {
            assertEquals("0", OverrideForm.editing(0, authoredZero, library, workout, kg)!!.values[ConfigKey.Sets])
        }

        @Test
        fun `can be corrected and saved, instead of being emptied`() {
            val form = OverrideForm.editing(0, authoredZero, library, workout, kg)!!.typed(ConfigKey.Sets, "6")
            assertEquals(listOf(exercise("pull-ups", sets(6.0))), form.confirmed(listOf(authoredZero)))
        }

        // The form has nowhere to show `reps` on a reps exercise; dropping it on Save would erase it.
        @Test
        fun `keeps a key the form cannot show instead of erasing it on save`() {
            val typo = exercise("pull-ups", mapOf("reps" to OverrideValue.Numeric(12.0)))
            val form = OverrideForm.editing(0, typo, library, workout, kg)!!.typed(ConfigKey.Sets, "5")
            assertEquals(
                listOf(exercise("pull-ups", mapOf("reps" to OverrideValue.Numeric(12.0)) + sets(5.0))),
                form.confirmed(listOf(typo)),
            )
        }

        // Spread over the raw block, `exercises: 2` replaced the member list with a number.
        @Test
        fun `opens a circuit override carrying a structural key`() {
            val structural = ProgramOverride.BlockOverride("finisher", mapOf("exercises" to OverrideValue.Numeric(2.0)))
            val form = OverrideForm.editing(0, structural, library, workout, kg)!!
            assertEquals(finisherTarget, form.target)
            assertEquals("3", form.values[ConfigKey.Rounds])
        }
    }

    /**
     * `validateConfig` and `validateBlockConfig` gate the confirm, since the merge now refuses an
     * invalid patch and silently keeps the base: unvalidated, it would save, show, and do nothing.
     */
    @Nested
    inner class Validation {
        @Test
        fun `refuses an exercise override the schema would reject`() {
            assertEquals(
                OverrideConfirm.Refused(ConfigProblem.MustBeAtLeast(FieldName.Sets, 1.0)),
                confirmOverride(adding(pullUpsTarget).typed(ConfigKey.Sets, "0"), emptyList(), kg),
            )
            assertEquals(
                OverrideConfirm.Refused(ConfigProblem.MustBeAtMost(FieldName.Sets, 500.0)),
                confirmOverride(adding(pullUpsTarget).typed(ConfigKey.Sets, "501"), emptyList(), kg),
            )
        }

        @Test
        fun `refuses a negative circuit rest rather than saving one the merge will drop`() {
            assertEquals(
                OverrideConfirm.Refused(ConfigProblem.MustBeAtLeast(FieldName.RestPerExercise, 0.0)),
                confirmOverride(adding(finisherTarget).typed(ConfigKey.RestBetweenExercisesSec, "-5"), emptyList(), kg),
            )
        }

        @Test
        fun `still saves a circuit patch the schema accepts`() {
            assertEquals(
                listOf(blockOverride("rest_between_exercises_sec" to 0.0)),
                adding(finisherTarget).typed(ConfigKey.RestBetweenExercisesSec, "0").confirmed(),
            )
        }
    }

    /**
     * The seed/diff asymmetry: the form seeds a concrete value where the circuit has none, and "the
     * form couldn't show it" read the same as "the user didn't change it".
     */
    @Nested
    inner class CircuitRests {
        @Test
        fun `does not invent a rest override on a circuit that declares none`() {
            val bare = WorkoutBlock.Circuit(id = "bare", rounds = 3, members = finisher.members)
            val form = adding(OverrideEditTarget.Circuit(bare, "bare"))
            assertEquals("0", form.values[ConfigKey.RestBetweenExercisesSec])
            assertEquals(
                listOf(ProgramOverride.BlockOverride("bare", mapOf("rounds" to OverrideValue.Numeric(4.0)))),
                form.typed(ConfigKey.Rounds, "4").confirmed(),
            )
        }

        // Clearing means "no rest", which is 0, not "unchanged", which emitted nothing.
        @Test
        fun `clears a rest the circuit does declare, rather than saving an empty override`() {
            assertEquals(
                listOf(blockOverride("rest_between_exercises_sec" to 0.0)),
                adding(finisherTarget).typed(ConfigKey.RestBetweenExercisesSec, "").confirmed(),
            )
        }
    }

    // The stepper's bounds, as RN's: 1 at the bottom, the schema's ceiling at the top.
    @Nested
    inner class Rounds {
        @Test
        fun `steps the rounds, within 1 and the ceiling`() {
            val form = adding(finisherTarget)
            assertEquals("4", form.steppingRounds(1).values[ConfigKey.Rounds])
            assertEquals("1", form.typed(ConfigKey.Rounds, "1").steppingRounds(-1).values[ConfigKey.Rounds])
            assertEquals("500", form.typed(ConfigKey.Rounds, "500").steppingRounds(1).values[ConfigKey.Rounds])
        }

        // RN's `Number(raw) || 1`.
        @Test
        fun `steps from 1 when the rounds can't be read`() {
            assertEquals(
                "2",
                adding(finisherTarget).typed(ConfigKey.Rounds, "").steppingRounds(1).values[ConfigKey.Rounds],
            )
        }
    }

    /**
     * `rawKey` spells the file's keys a second time, so it is held to the writer's own: a field whose
     * key drifted would seed blank and emit a patch the merge ignores as unknown.
     */
    @Nested
    inner class RawKeys {
        private val everyField =
            listOf(
                ExerciseConfig.Hiit(30.0, 30.0, 4),
                ExerciseConfig.Emom(60.0, 10.0, 8),
                ExerciseConfig.Amrap(720.0),
                ExerciseConfig.Reps(
                    sets = 3,
                    targetRepsMin = 8,
                    targetRepsMax = 12,
                    targetWeightKg = 20.0,
                    restSec = 60.0,
                ),
                ExerciseConfig.TimedHold(sets = 3, holdSecMin = 20.0, holdSecMax = 45.0, restSec = 45.0),
                ExerciseConfig.Cardio(600.0, 2000.0),
                ExerciseConfig.Rest(90.0),
            )

        @Test
        fun `spells every exercise field as the writer does`() {
            assertEquals(FORM_TYPES.toSet(), everyField.map { it.type }.toSet())
            for (config in everyField) {
                assertEquals(
                    config.toRaw().second.keys,
                    configFields(config.type).map { it.key.rawKey }.toSet(),
                    "${config.type}",
                )
            }
        }

        @Test
        fun `spells a circuit's own params as the writer does`() {
            val keys = BLOCK_CONFIG_FIELDS.map { it.key.rawKey }
            assertTrue(finisher.toRaw().keys.containsAll(keys), "$keys in ${finisher.toRaw().keys}")
        }
    }

    private fun blockOverride(vararg config: Pair<String, Double>) =
        ProgramOverride.BlockOverride(
            "finisher",
            config.associate { (key, value) ->
                key to OverrideValue.Numeric(value)
            },
        )
}
