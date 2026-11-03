package com.casco.kettle.domain

import com.casco.kettle.domain.ConfigKey.DistanceMeters
import com.casco.kettle.domain.ConfigKey.DurationSec
import com.casco.kettle.domain.ConfigKey.HoldSecMax
import com.casco.kettle.domain.ConfigKey.HoldSecMin
import com.casco.kettle.domain.ConfigKey.IntervalSec
import com.casco.kettle.domain.ConfigKey.RestSec
import com.casco.kettle.domain.ConfigKey.Rounds
import com.casco.kettle.domain.ConfigKey.Sets
import com.casco.kettle.domain.ConfigKey.TargetReps
import com.casco.kettle.domain.ConfigKey.TargetRepsMax
import com.casco.kettle.domain.ConfigKey.TargetRepsMin
import com.casco.kettle.domain.ConfigKey.TargetWeightKg
import com.casco.kettle.domain.ConfigKey.TimeCapSec
import com.casco.kettle.domain.ConfigKey.TotalMinutes
import com.casco.kettle.domain.ConfigKey.WorkSec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * `exercise-form.test.ts`, ported with the form (#241). jest asserted the English message; here the
 * problem is a descriptor the screen puts into words, so each case asserts the descriptor whose
 * message jest named. `validateBlockConfig`'s cases stay behind with the override editor, which is
 * the only form that uses it.
 */
class ExerciseFormTest {
    private val metric = UnitSystem.METRIC
    private val imperial = UnitSystem.IMPERIAL

    @Nested
    inner class ValidateConfig {
        @Test
        fun `rejects a required field left blank`() {
            assertEquals(
                ConfigProblem.Required(FieldName.Sets),
                validateConfig(ExerciseType.Reps, mapOf(Sets to "", TargetRepsMin to "10", RestSec to "45")),
            )
        }

        @Test
        fun `rejects a required field of 0`() {
            assertEquals(
                ConfigProblem.MustBeAtLeast(FieldName.Work, 1.0),
                validateConfig(ExerciseType.Hiit, mapOf(WorkSec to "0", RestSec to "20", Rounds to "4")),
            )
        }

        @Test
        fun `rejects a non-numeric value`() {
            assertEquals(
                ConfigProblem.MustBeNumber(FieldName.Interval),
                validateConfig(ExerciseType.Emom, mapOf(IntervalSec to "abc", TotalMinutes to "10")),
            )
        }

        // Required fields default to a floor of 1; the rest lengths declare 0, as the schema allows.
        @Test
        fun `accepts 0 for rest-length fields`() {
            assertNull(validateConfig(ExerciseType.Hiit, mapOf(WorkSec to "40", RestSec to "0", Rounds to "4")))
            assertNull(validateConfig(ExerciseType.Reps, mapOf(Sets to "3", TargetRepsMin to "10", RestSec to "0")))
            assertNull(validateConfig(ExerciseType.TimedHold, mapOf(Sets to "3", HoldSecMin to "15", RestSec to "0")))
            assertNull(validateConfig(ExerciseType.Rest, mapOf(DurationSec to "0")))
        }

        @Test
        fun `accepts an omitted optional field`() {
            assertNull(validateConfig(ExerciseType.Emom, mapOf(IntervalSec to "60", TotalMinutes to "10")))
            assertNull(validateConfig(ExerciseType.Cardio, emptyMap()))
        }

        @Test
        fun `returns null for a fully valid config`() {
            val values =
                mapOf(
                    Sets to "3",
                    TargetRepsMin to "10",
                    TargetRepsMax to "12",
                    TargetWeightKg to "60",
                    RestSec to "90",
                )
            assertNull(validateConfig(ExerciseType.Reps, values))
        }

        @Test
        fun `accepts a hold with no target, which is how a max-effort hold is written`() {
            assertNull(validateConfig(ExerciseType.TimedHold, mapOf(Sets to "3", RestSec to "60")))
        }

        @Test
        fun `rejects a hold maximum with no minimum, which is a range with one end`() {
            assertEquals(
                ConfigProblem.HoldMaxNeedsMin,
                validateConfig(ExerciseType.TimedHold, mapOf(Sets to "3", HoldSecMax to "25", RestSec to "60")),
            )
        }

        @Test
        fun `rejects a hold maximum below the minimum`() {
            val values = mapOf(Sets to "3", HoldSecMin to "25", HoldSecMax to "15", RestSec to "60")
            assertEquals(ConfigProblem.HoldMaxBelowMin, validateConfig(ExerciseType.TimedHold, values))
        }

        @Test
        fun `accepts a hold maximum equal to the minimum, as the schema does`() {
            val values = mapOf(Sets to "3", HoldSecMin to "20", HoldSecMax to "20", RestSec to "60")
            assertNull(validateConfig(ExerciseType.TimedHold, values))
        }
    }

    @Nested
    inner class Ceilings {
        @Test
        fun `rejects a set count past the ceiling, and accepts the ceiling itself`() {
            assertEquals(
                ConfigProblem.MustBeAtMost(FieldName.Sets, 500.0),
                validateConfig(ExerciseType.Reps, mapOf(Sets to "501", TargetRepsMin to "5", RestSec to "60")),
            )
            assertNull(validateConfig(ExerciseType.Reps, mapOf(Sets to "500", TargetRepsMin to "5", RestSec to "60")))
        }

        @Test
        fun `rejects a round count past the ceiling`() {
            assertEquals(
                ConfigProblem.MustBeAtMost(FieldName.Rounds, 500.0),
                validateConfig(ExerciseType.Hiit, mapOf(WorkSec to "40", RestSec to "20", Rounds to "501")),
            )
        }

        @Test
        fun `rejects a block longer than the day-length ceiling`() {
            assertEquals(
                ConfigProblem.MustBeAtMost(FieldName.Total, 1440.0),
                validateConfig(ExerciseType.Emom, mapOf(IntervalSec to "300", TotalMinutes to "1441")),
            )
        }

        // EMOM's bound is on the product, which the per-field loop can't see: both values are in range.
        @Test
        fun `rejects an emom whose interval count multiplies out past the ceiling`() {
            assertEquals(
                ConfigProblem.TooManyIntervals(500),
                validateConfig(ExerciseType.Emom, mapOf(IntervalSec to "1", TotalMinutes to "60")),
            )
            assertNull(validateConfig(ExerciseType.Emom, mapOf(IntervalSec to "60", TotalMinutes to "500")))
        }
    }

    /** The schema's `int()`: a fractional `sets` written here failed the next launch's parse and reseeded. */
    @Nested
    inner class WholeNumbers {
        @Test
        fun `rejects a fractional count`() {
            assertEquals(
                ConfigProblem.MustBeWhole(FieldName.Sets),
                validateConfig(ExerciseType.Reps, mapOf(Sets to "2.5", TargetRepsMin to "5", RestSec to "60")),
            )
            assertEquals(
                ConfigProblem.MustBeWhole(FieldName.TargetReps),
                validateConfig(ExerciseType.Reps, mapOf(Sets to "3", TargetRepsMin to "5.5", RestSec to "60")),
            )
            assertEquals(
                ConfigProblem.MustBeWhole(FieldName.Sets),
                validateConfig(ExerciseType.TimedHold, mapOf(Sets to "1.5", HoldSecMin to "30", RestSec to "60")),
            )
            assertEquals(
                ConfigProblem.MustBeWhole(FieldName.Rounds),
                validateConfig(ExerciseType.Hiit, mapOf(WorkSec to "40", RestSec to "20", Rounds to "4.5")),
            )
            assertEquals(
                ConfigProblem.MustBeWhole(FieldName.TargetReps),
                validateConfig(
                    ExerciseType.Emom,
                    mapOf(IntervalSec to "60", TotalMinutes to "10", TargetReps to "3.5"),
                ),
            )
        }

        // The fields the schema leaves as plain numbers stay fractional: a 2.5-second interval is legal.
        @Test
        fun `leaves the non-integer fields alone`() {
            assertNull(validateConfig(ExerciseType.Hiit, mapOf(WorkSec to "40.5", RestSec to "20.5", Rounds to "4")))
            assertNull(
                validateConfig(ExerciseType.TimedHold, mapOf(Sets to "3", HoldSecMin to "30.5", RestSec to "60")),
            )
        }
    }

    @Test
    fun `rejects a rep range whose maximum is below its minimum`() {
        val values = mapOf(Sets to "3", TargetRepsMin to "10", TargetRepsMax to "5", RestSec to "60")
        assertEquals(ConfigProblem.RepsMaxBelowMin, validateConfig(ExerciseType.Reps, values))
    }

    @Test
    fun `accepts a rep range that is equal at both ends, as the schema does`() {
        val values = mapOf(Sets to "3", TargetRepsMin to "5", TargetRepsMax to "5", RestSec to "60")
        assertNull(validateConfig(ExerciseType.Reps, values))
    }

    // "Bodyweight, no added load" spelled out: legal in the schema, so it must be saveable here.
    @Test
    fun `accepts a target weight of 0, which the schema allows`() {
        val values = mapOf(Sets to "3", TargetRepsMin to "5", TargetWeightKg to "0", RestSec to "60")
        assertNull(validateConfig(ExerciseType.Reps, values))
    }

    /**
     * The form's copy of the EMOM interval count has to floor as the schema's does. The repair writes
     * exactly `intervalSec * 500 / 60` when it clamps an over-long EMOM; that file loads, so Save must
     * accept it too.
     */
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 4, 8, 16, 32, 64, 125])
    fun `accepts what the repair writes`(intervalSec: Int) {
        val totalMinutes = intervalSec * 500.0 / 60
        val library =
            """
            version: 1
            exercises:
              - id: e
                name: E
                type: emom
                config: { interval_sec: $intervalSec, total_minutes: $totalMinutes }
            workouts: []
            programs: []
            """.trimIndent()

        assertEquals(true, parseLibraryYaml(library) is ParseResult.Ok)
        val values = mapOf(IntervalSec to "$intervalSec", TotalMinutes to jsNumber(totalMinutes))
        assertNull(validateConfig(ExerciseType.Emom, values))
    }

    @Test
    fun `accepts a partial trailing interval, exactly as the schema does`() {
        assertNull(validateConfig(ExerciseType.Emom, mapOf(IntervalSec to "45", TotalMinutes to "375.5")))
    }

    @Nested
    inner class BuildExercise {
        @Test
        fun `builds a hiit exercise and trims notes`() {
            val draft =
                ExerciseDraft(
                    "Burpees",
                    ExerciseType.Hiit,
                    mapOf(
                        WorkSec to "40",
                        RestSec to "20",
                        Rounds to "4",
                    ),
                    "  go hard  ",
                )
            assertEquals(
                Exercise("burpees", "Burpees", ExerciseConfig.Hiit(40.0, 20.0, 4), "go hard"),
                buildExercise("burpees", draft, metric),
            )
        }

        @Test
        fun `omits optional fields left blank`() {
            val values =
                mapOf(
                    Sets to "3",
                    TargetRepsMin to "10",
                    TargetRepsMax to "",
                    TargetWeightKg to "",
                    RestSec to "45",
                )
            assertEquals(
                Exercise("pushups", "Push-ups", ExerciseConfig.Reps(sets = 3, targetRepsMin = 10, restSec = 45.0)),
                buildExercise("pushups", ExerciseDraft("Push-ups", ExerciseType.Reps, values, ""), metric),
            )
        }

        @Test
        fun `builds an emom exercise with the optional target reps supplied`() {
            val values = mapOf(IntervalSec to "30", TotalMinutes to "10", TargetReps to "3")
            assertEquals(
                ExerciseConfig.Emom(30.0, 10.0, 3),
                buildExercise("clean", ExerciseDraft("Clean", ExerciseType.Emom, values, ""), metric).config,
            )
        }

        @Test
        fun `builds a bare cardio exercise with no distance or duration`() {
            assertEquals(
                ExerciseConfig.Cardio(),
                buildExercise("walk", ExerciseDraft("Walk", ExerciseType.Cardio, emptyMap(), ""), metric).config,
            )
        }

        @Test
        fun `builds a rest exercise`() {
            val draft = ExerciseDraft("Rest", ExerciseType.Rest, mapOf(DurationSec to "90"), "")
            assertEquals(ExerciseConfig.Rest(90.0), buildExercise("rest", draft, metric).config)
        }
    }

    /** The imperial pass is what pins the conversion: the weight is the only field that changes on the way. */
    @ParameterizedTest
    @ValueSource(strings = ["METRIC", "IMPERIAL"])
    fun `round-trips every type through the form`(system: UnitSystem) {
        val cases =
            listOf(
                ExerciseDraft("N", ExerciseType.Hiit, mapOf(WorkSec to "40", RestSec to "20", Rounds to "4"), "Fast"),
                ExerciseDraft(
                    "N",
                    ExerciseType.Emom,
                    mapOf(IntervalSec to "60", TotalMinutes to "10", TargetReps to "3"),
                    "",
                ),
                ExerciseDraft("N", ExerciseType.Emom, mapOf(IntervalSec to "90", TotalMinutes to "9"), ""),
                ExerciseDraft("N", ExerciseType.Amrap, mapOf(TimeCapSec to "600"), ""),
                ExerciseDraft(
                    "N",
                    ExerciseType.Reps,
                    mapOf(
                        Sets to "5",
                        TargetRepsMin to "3",
                        TargetRepsMax to "5",
                        TargetWeightKg to "80",
                        RestSec to "180",
                    ),
                    "Brace",
                ),
                ExerciseDraft("N", ExerciseType.Reps, mapOf(Sets to "3", TargetRepsMin to "12", RestSec to "45"), ""),
                ExerciseDraft(
                    "N",
                    ExerciseType.TimedHold,
                    mapOf(Sets to "4", HoldSecMin to "15", HoldSecMax to "25", RestSec to "60"),
                    "",
                ),
                ExerciseDraft("N", ExerciseType.Cardio, mapOf(DurationSec to "480", DistanceMeters to "2000"), ""),
                ExerciseDraft("N", ExerciseType.Cardio, emptyMap(), ""),
                ExerciseDraft("N", ExerciseType.Rest, mapOf(DurationSec to "90"), ""),
            )
        for (draft in cases) {
            val built = buildExercise("id", draft, system)
            val reopened = draftOf(built, system)
            val previous = (built.config as? ExerciseConfig.Reps)?.targetWeightKg
            assertEquals(built, buildExercise("id", reopened, system, previous), "${draft.type}")
        }
    }

    /** Storage is always kilograms; the form is the only place that isn't. */
    @Nested
    inner class Weight {
        private fun repsWith(kg: Double) =
            Exercise(
                "bench",
                "Bench",
                ExerciseConfig.Reps(sets = 5, targetRepsMin = 5, targetWeightKg = kg, restSec = 120.0),
            )

        private fun build(
            weight: String,
            system: UnitSystem,
            previousWeightKg: Double? = null,
        ): Double? {
            val values = mapOf(Sets to "5", TargetRepsMin to "5", TargetWeightKg to weight, RestSec to "120")
            val built =
                buildExercise("bench", ExerciseDraft("Bench", ExerciseType.Reps, values, ""), system, previousWeightKg)
            return (built.config as ExerciseConfig.Reps).targetWeightKg
        }

        @Test
        fun `the weight field is the one in the reader's unit, and no other is`() {
            val units = configFields(ExerciseType.Reps).associate { it.key to it.unit }
            assertEquals(FieldUnit.Weight, units[TargetWeightKg])
            assertEquals(FieldUnit.Seconds, units[RestSec])
            assertEquals(FieldUnit.Minutes, configFields(ExerciseType.Emom).single { it.key == TotalMinutes }.unit)
        }

        @Test
        fun `shows a stored kilogram value in pounds`() {
            assertEquals("132.3", draftOf(repsWith(60.0), imperial).values[TargetWeightKg])
            assertEquals("60", draftOf(repsWith(60.0), metric).values[TargetWeightKg])
        }

        @Test
        fun `stores a pound entry as kilograms`() {
            assertEquals(61.23, build("135", imperial))
        }

        /** A value typed in pounds comes back as the number typed, not as 134.99. */
        @ParameterizedTest
        @ValueSource(strings = ["135", "45", "2.5", "225", "137.5"])
        fun `redisplays a pound entry as itself after a trip through kg`(entered: String) {
            val values = mapOf(Sets to "5", TargetRepsMin to "5", TargetWeightKg to entered, RestSec to "120")
            val built = buildExercise("bench", ExerciseDraft("Bench", ExerciseType.Reps, values, ""), imperial)
            assertEquals(entered, draftOf(built, imperial).values[TargetWeightKg])
        }

        /** 100 kg shows as 220.5 lb, which converts back to 100.02: an untouched field must not. */
        @Test
        fun `leaves a kilogram-authored weight untouched when the field was not edited`() {
            val shown = draftOf(repsWith(100.0), imperial).values.getValue(TargetWeightKg)
            assertEquals("220.5", shown)
            assertEquals(100.0, build(shown, imperial, 100.0))
        }

        @Test
        fun `does convert once the field actually changes`() {
            assertEquals(102.06, build("225", imperial, 100.0))
        }

        @Test
        fun `keeps a blank weight blank rather than storing a zero load`() {
            assertNull(build("", imperial, 100.0))
        }
    }

    @Nested
    inner class Submit {
        private val pullUps =
            Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 90.0))

        private fun draft(name: String) =
            ExerciseDraft(name, ExerciseType.Reps, mapOf(Sets to "4", TargetRepsMin to "6", RestSec to "90"), "")

        @Test
        fun `refuses a blank name before anything else`() {
            assertEquals(
                ExerciseSubmit.Refused(ExerciseProblem.NameRequired),
                submitExercise(draft("   ").copy(values = emptyMap()), null, metric),
            )
        }

        @Test
        fun `refuses a name that yields no id`() {
            assertEquals(ExerciseSubmit.Refused(ExerciseProblem.NoId), submitExercise(draft("🏋️"), null, metric))
        }

        @Test
        fun `refuses an invalid config`() {
            val zeroSets = draft("Pull-ups").let { it.copy(values = it.values + (Sets to "0")) }
            assertEquals(
                ExerciseSubmit.Refused(ExerciseProblem.Config(ConfigProblem.MustBeAtLeast(FieldName.Sets, 1.0))),
                submitExercise(zeroSets, null, metric),
            )
        }

        @Test
        fun `derives a new exercise's id from its trimmed name`() {
            assertEquals(
                ExerciseSubmit.Built(
                    Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0)),
                ),
                submitExercise(draft("  Pull-ups "), null, metric),
            )
        }

        // An id is the join key every workout holds: a rename must not orphan them.
        @Test
        fun `keeps an existing exercise's id through a rename`() {
            val built = submitExercise(draft("Chin-ups"), pullUps, metric) as ExerciseSubmit.Built
            assertEquals("pull-ups", built.exercise.id)
            assertEquals("Chin-ups", built.exercise.name)
        }

        // Each type keys a different config: a switched type would read the form under the wrong keys.
        @Test
        fun `keeps an existing exercise's type whatever the draft says`() {
            val asHiit = draft("Pull-ups").copy(type = ExerciseType.Hiit)
            val built = submitExercise(asHiit, pullUps, metric) as ExerciseSubmit.Built
            assertEquals(ExerciseType.Reps, built.exercise.config.type)
        }

        @Test
        fun `keeps an untouched stored weight exactly, through the existing exercise`() {
            val bench =
                Exercise(
                    "bench",
                    "Bench",
                    ExerciseConfig.Reps(sets = 5, targetRepsMin = 5, targetWeightKg = 100.0, restSec = 120.0),
                )
            val reopened = draftOf(bench, imperial)
            val built = submitExercise(reopened.copy(values = reopened.values + (Sets to "6")), bench, imperial)
            assertEquals(
                ExerciseConfig.Reps(sets = 6, targetRepsMin = 5, targetWeightKg = 100.0, restSec = 120.0),
                (built as ExerciseSubmit.Built).exercise.config,
            )
        }
    }
}
