package com.casco.kettle.shell.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** The YAML view's colouring (#444): the serializer's one line shape, cut into its pieces. */
class YamlPartsTest {
    @Test
    fun `cuts a key and a number`() {
        assertEquals(
            listOf(
                "      " to YamlPart.Punctuation,
                "sets" to YamlPart.Key,
                ": " to YamlPart.Punctuation,
                "3" to YamlPart.Number,
            ),
            yamlParts("      sets: 3"),
        )
    }

    @Test
    fun `reads a list item's key and a text value`() {
        assertEquals(
            listOf(
                "  - " to YamlPart.Punctuation,
                "id" to YamlPart.Key,
                ": " to YamlPart.Punctuation,
                "squat" to YamlPart.Text,
            ),
            yamlParts("  - id: squat"),
        )
    }

    // An empty list is structure, not a value.
    @Test
    fun `reads an empty list as punctuation`() {
        assertEquals(YamlPart.Punctuation, yamlParts("workouts: []").last().second)
    }

    // A long note wraps onto lines of its own; one starting with a link isn't a key.
    @Test
    fun `reads a wrapped line that starts with a link as text`() {
        assertEquals(
            listOf("      https://example.com/cue" to YamlPart.Text),
            yamlParts("      https://example.com/cue"),
        )
    }

    @Test
    fun `reads a key with no value, and folded text, whole`() {
        assertEquals(listOf("exercises" to YamlPart.Key, ":" to YamlPart.Punctuation), yamlParts("exercises:"))
        assertEquals(listOf("      Hold it at your chest." to YamlPart.Text), yamlParts("      Hold it at your chest."))
    }
}
