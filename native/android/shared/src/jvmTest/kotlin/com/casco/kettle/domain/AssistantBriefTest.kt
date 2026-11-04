package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The brief an assistant sees before it writes a line of YAML (#244), from RN's
 * `assistant-brief.test.ts`. The words are the screen's, passed in, so what's pinned here is the
 * shape: the user's real ids, the schema carried whole, and nothing of the trainer's in it.
 */
class AssistantBriefTest {
    private val words =
        AssistantBrief.Words(
            prompt = "PROMPT",
            inventoryHeading = "## Ids",
            exercisesLabel = "exercises:",
            workoutsLabel = "workouts:",
            programsLabel = "programs:",
            none = "(none yet)",
            schemaHeading = "## Schema",
        )
    private val schema = "{\n  \"type\": \"object\"\n}"

    private val library =
        Library(
            1,
            listOf(
                Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, restSec = 90.0)),
                Exercise("plank", "Plank", ExerciseConfig.TimedHold(3, holdSecMin = 30.0, restSec = 60.0)),
            ),
            listOf(Workout("push-day", "Push day", emptyList())),
            listOf(Program("base-6", "Base 6", emptyList())),
        )

    // Merge is by id, so this list is the difference between a generated program landing and being refused.
    @Test
    fun `names every id with the type the file spells and the user's own name`() {
        val brief = AssistantBrief.build(library, words, schema)

        assertTrue("  pull-ups (reps) — Pull-ups" in brief)
        assertTrue("  plank (timed_hold) — Plank" in brief)
        assertTrue("  push-day — Push day" in brief)
        assertTrue("  base-6 — Base 6" in brief)
    }

    // A blank stretch under a heading reads as a copy that truncated, and an assistant fills it with invented ids.
    @Test
    fun `says so for a collection that is empty, rather than leaving a silent gap`() {
        val brief = AssistantBrief.build(Library(1, emptyList(), emptyList(), emptyList()), words, schema)

        assertEquals(3, Regex("""\(none yet\)""").findAll(brief).count())
    }

    @Test
    fun `lays out the prompt, the ids and the schema as RN's does`() {
        val brief =
            AssistantBrief.build(
                Library(1, emptyList(), listOf(Workout("w", "W", emptyList())), emptyList()),
                words,
                schema,
            )

        assertEquals(
            listOf(
                "PROMPT",
                "",
                "## Ids",
                "exercises:",
                "  (none yet)",
                "workouts:",
                "  w — W",
                "programs:",
                "  (none yet)",
                "",
                "## Schema",
                "```json",
                "{",
                "  \"type\": \"object\"",
                "}",
                "```",
            ).joinToString("\n"),
            brief,
        )
    }

    // The file ends in a newline, as any formatted file does; the fence goes straight after the schema.
    @Test
    fun `carries the schema whole, without the file's trailing newline`() {
        val brief = AssistantBrief.build(library, words, "$schema\n")

        assertTrue(brief.endsWith("```json\n$schema\n```"))
    }

    // The assistant writes back what the brief spells, so it has to be the file's own spelling.
    @Test
    fun `spells each type as the file does`() {
        val configs =
            listOf(
                ExerciseConfig.Hiit(30.0, 15.0, 4),
                ExerciseConfig.Emom(60.0, 10.0),
                ExerciseConfig.Amrap(600.0),
                ExerciseConfig.Reps(3, 8, restSec = 90.0),
                ExerciseConfig.TimedHold(3, holdSecMin = 30.0, restSec = 60.0),
                ExerciseConfig.Cardio(durationSec = 600.0),
                ExerciseConfig.Rest(60.0),
            )
        assertEquals(ExerciseType.entries.toSet(), configs.map { it.type }.toSet())
        for (config in configs) {
            val yaml = serializeLibraryYaml(Library(1, listOf(Exercise("x", "X", config)), emptyList(), emptyList()))
            assertTrue("type: ${config.type.fileName}\n" in yaml, "${config.type}: $yaml")
        }
    }
}
