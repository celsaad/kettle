package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * The loader's limits and refusals, through the session parser. The corpus pins most of the loader
 * through the library suite, which isn't ported yet, so these hold the same rules here meanwhile.
 * Each expected kind was read off js-yaml 5 with the same input, not reasoned out.
 */
class YamlTest {
    private val session = "version: 1\nid: s\nworkout: w\nstarted_at: x\nended_at: null\nentries: []\n"

    private fun withUnused(value: String) = "${session}unused: $value\n"

    private fun assertLoads(text: String) = assertInstanceOf(ParseResult.Ok::class.java, parseSessionYaml(text))

    private fun assertInvalidYaml(text: String) =
        assertInstanceOf(ParseResult.InvalidYaml::class.java, parseSessionYaml(text))

    @Test
    fun `1000 aliases load and the 1001st is a YAML error`() {
        // Aliases to a scalar, which the engine's own limit doesn't count.
        assertLoads(withUnused("[&a 1${",*a".repeat(1000)}]"))
        assertInvalidYaml(withUnused("[&a 1${",*a".repeat(1001)}]"))
    }

    @Test
    fun `nesting 100 nodes deep loads and 101 is a YAML error`() {
        // The top-level mapping, 98 sequences and the scalar are 100 nodes.
        assertLoads(withUnused("${"[".repeat(98)}1${"]".repeat(98)}"))
        assertInvalidYaml(withUnused("${"[".repeat(99)}1${"]".repeat(99)}"))
    }

    @Test
    fun `a stream with no document is a YAML error`() {
        assertInvalidYaml("")
        assertInvalidYaml("# only a comment\n")
    }

    @Test
    fun `an explicit empty document is null, which the schema refuses at the root`() {
        assertEquals(listOf("(root)"), (parseSessionYaml("---\n") as ParseResult.SchemaMismatch).paths)
    }

    @Test
    fun `a second document is a YAML error`() {
        assertInvalidYaml("$session---\n$session")
    }

    @Test
    fun `duplicate keys and tab indentation are YAML errors`() {
        assertInvalidYaml("${session}id: t\n")
        assertInvalidYaml("version: 1\n\tid: s\n")
    }

    @Test
    fun `an unknown tag is a YAML error, even under a key the schema drops`() {
        assertInvalidYaml(withUnused("!foo 3"))
        assertInvalidYaml(withUnused("!!binary aGk="))
    }

    @Test
    fun `every refused field is reported, sorted, each path once`() {
        val text =
            "version: 1\nid: s\nworkout: w\nstarted_at: x\nended_at: null\n" +
                "entries: [{ exercise: a, type: reps, sets: [{ reps: x, rpe: 11 }] }]\n"
        assertEquals(
            listOf("entries.0.sets.0.reps", "entries.0.sets.0.rest_taken_sec", "entries.0.sets.0.rpe"),
            (parseSessionYaml(text) as ParseResult.SchemaMismatch).paths,
        )
    }
}
