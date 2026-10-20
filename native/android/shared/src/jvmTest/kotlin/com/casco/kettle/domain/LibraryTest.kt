package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Which refusals a document with more than one defect reports. The corpus can't pin this, since its
 * refusal cases carry one defect each on purpose, but it is still where two parsers would disagree:
 * zod skips a `.refine` once anything fatal was refused beneath it, and runs it past a refusal that
 * isn't. Every expected list was read off zod 4 and js-yaml 5 with the same input, not reasoned out.
 */
class LibraryTest {
    private fun library(
        exercises: String = "[]",
        workouts: String = "[{ id: w, name: W, blocks: [] }]",
        programs: String = "[]",
    ) = "version: 1\nexercises: $exercises\nworkouts: $workouts\nprograms: $programs\n"

    private fun paths(text: String): List<String> = (parseLibraryYaml(text) as ParseResult.SchemaMismatch).paths

    private fun reps(config: String) = library(exercises = "[{ id: a, name: A, type: reps, config: { $config } }]")

    private fun weeks(weeks: String) = library(programs = "[{ id: p, name: P, weeks: [$weeks] }]")

    private val config = "exercises.0.config"

    @Test
    fun `a range refusal doesn't stop the refinement, so both are reported`() {
        assertEquals(
            listOf("$config.sets", "$config.target_reps_max"),
            paths(reps("sets: 0, target_reps_min: 8, target_reps_max: 5, rest_sec: 60")),
        )
    }

    @Test
    fun `a wrong type, a missing field or a fraction stops the refinement`() {
        assertEquals(
            listOf("$config.sets"),
            paths(reps("sets: x, target_reps_min: 8, target_reps_max: 5, rest_sec: 60")),
        )
        assertEquals(listOf("$config.sets"), paths(reps("target_reps_min: 8, target_reps_max: 5, rest_sec: 60")))
        assertEquals(
            listOf("$config.sets"),
            paths(reps("sets: 2.5, target_reps_min: 8, target_reps_max: 5, rest_sec: 60")),
        )
    }

    @Test
    fun `an integer past the safe range is a range refusal, not a fatal one`() {
        assertEquals(
            listOf("$config.target_reps_max", "$config.target_reps_min"),
            paths(reps("sets: 3, target_reps_min: 1e300, target_reps_max: 5, rest_sec: 60")),
        )
    }

    @Test
    fun `a fatal refusal outside the refined object doesn't stop it`() {
        val reps = "{ sets: 3, target_reps_min: 8, target_reps_max: 5, rest_sec: 60 }"
        val text = library(exercises = "[{ id: a, name: A, notes: 5, type: reps, config: $reps }]")
        assertEquals(listOf("$config.target_reps_max", "exercises.0.notes"), paths(text))
    }

    @Test
    fun `an EMOM with a zero interval is refused once, at the interval`() {
        val text =
            library(exercises = "[{ id: a, name: A, type: emom, config: { interval_sec: 0, total_minutes: 10 } }]")
        assertEquals(listOf("$config.interval_sec"), paths(text))
    }

    /**
     * The paths can't tell this apart, since `interval_sec` is refused for its range either way, so it
     * reads the detail: zod's refinement fails on `0 / 0`, because NaN is never at most 500.
     */
    @Test
    fun `an EMOM of zero intervals over zero minutes fails the interval refinement too`() {
        val text =
            library(exercises = "[{ id: a, name: A, type: emom, config: { interval_sec: 0, total_minutes: 0 } }]")
        val refusal = parseLibraryYaml(text) as ParseResult.SchemaMismatch
        assertEquals(listOf("$config.interval_sec", "$config.total_minutes"), refusal.paths)
        assertEquals(true, "intervals" in refusal.detail, refusal.detail)
    }

    @Test
    fun `a program's refinements run past a refused week, but not past a fatal one`() {
        val program = "programs.0"
        assertEquals(
            listOf("$program.weeks", "$program.weeks.0.workout", "$program.weeks.1.workout"),
            paths(weeks("{ week: 1 }, { week: 1 }")),
        )
        assertEquals(
            listOf("$program.weeks", "$program.weeks.0.week", "$program.weeks.1.week"),
            paths(weeks("{ week: 0, workout: w }, { week: 0, workout: w }")),
        )
        assertEquals(
            listOf("$program.weeks.0.week", "$program.weeks.1.week"),
            paths(weeks("{ week: x, workout: w }, { week: x, workout: w }")),
        )
        assertEquals(
            listOf("$program.id"),
            paths(
                library(programs = "[{ id: 5, name: P, weeks: [{ week: 1, workout: w }, { week: 1, workout: w }] }]"),
            ),
        )
    }

    @Test
    fun `an empty id is a range refusal, so the program's refinements still run`() {
        assertEquals(
            listOf("programs.0.weeks", "programs.0.weeks.0.workout"),
            paths(weeks("""{ week: 1, workout: "" }, { week: 1, workout: w }""")),
        )
    }

    @Test
    fun `weeks are the same slot when they are the same number or an absent day meets an empty one`() {
        val repeated = listOf("programs.0.weeks")
        assertEquals(repeated, paths(weeks("{ week: 1, workout: w }, { week: 1.0, workout: w }")))
        assertEquals(repeated, paths(weeks("""{ week: 1, workout: w }, { week: 1, day: "", workout: w }""")))
        assertEquals(
            repeated + listOf("programs.0.weeks.0.week", "programs.0.weeks.1.week"),
            paths(weeks("{ week: 0, workout: w }, { week: -0.0, workout: w }")),
        )
    }

    @Test
    fun `a rest week naming a workout and overrides reports both, and a malformed rest_day neither`() {
        assertEquals(
            listOf("programs.0.weeks.1.overrides", "programs.0.weeks.1.workout"),
            paths(weeks("{ week: 1, workout: w }, { week: 2, rest_day: true, workout: w, overrides: [] }")),
        )
        assertEquals(listOf("programs.0.weeks.0.rest_day"), paths(weeks("{ week: 1, rest_day: yes }")))
    }

    @Test
    fun `an empty override target is no target, and a wrong-typed one stops the check`() {
        val override = "programs.0.weeks.0.overrides.0"
        assertEquals(
            listOf(override, "$override.exercise"),
            paths(weeks("""{ week: 1, workout: w, overrides: [{ exercise: "", config: {} }] }""")),
        )
        assertEquals(
            listOf("$override.exercise"),
            paths(weeks("{ week: 1, workout: w, overrides: [{ exercise: 5, config: {} }] }")),
        )
        assertEquals(
            listOf("$override.config.r", "$override.config.sets"),
            paths(weeks("{ week: 1, workout: w, overrides: [{ exercise: a, config: { sets: null, r: .inf } }] }")),
        )
    }

    @Test
    fun `override keys are spelled as JavaScript spells them`() {
        val text =
            weeks("{ week: 1, workout: w, overrides: [{ exercise: a, config: { 1: 2, 1.5: x, true: 3, ~: 4 } }] }")
        val week = ((parseLibraryYaml(text) as ParseResult.Ok).value.programs[0].weeks[0] as ProgramWeek.Training)
        assertEquals(setOf("1", "1.5", "true", "null"), week.overrides!![0].config.keys)
    }

    @Test
    fun `the writer spells a whole number as an integer, and keeps negative zero`() {
        val rest = Exercise("r", "Rest", ExerciseConfig.Rest(durationSec = 90.0))
        val zero = Exercise("z", "Zero", ExerciseConfig.Rest(durationSec = -0.0))
        val written = Library(1, listOf(rest, zero), emptyList(), emptyList())
        val text = serializeLibraryYaml(written)
        assertEquals(true, "duration_sec: 90\n" in text, text)
        assertEquals(ParseResult.Ok(written), parseLibraryYaml(text))
    }

    @Test
    fun `a circuit's length is checked even past a refused member`() {
        val text = library(workouts = "[{ id: w, name: W, blocks: [{ type: circuit, rounds: 2, exercises: [5] }] }]")
        assertEquals(listOf("workouts.0.blocks.0.exercises", "workouts.0.blocks.0.exercises.0"), paths(text))
    }
}
