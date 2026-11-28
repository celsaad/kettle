package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A chat reply's one fenced block (#569), and every text that isn't one. */
class FencedYamlTest {
    private val yaml = "version: 1\nexercises: []\nworkouts: []\nprograms: []\n"

    @Test
    fun `takes the one yaml block out of a reply's prose`() {
        val reply = "Sure! Here's your plan:\n\n```yaml\n$yaml```\n\nLet me know how it goes."
        assertEquals(yaml, fencedYaml(reply))
    }

    @Test
    fun `takes a block tagged yml, or not tagged at all`() {
        assertEquals(yaml, fencedYaml("```yml\n$yaml```"))
        assertEquals(yaml, fencedYaml("Here:\n```\n$yaml```"))
    }

    @Test
    fun `takes a block indented as a chat renders it, with Windows line ends`() {
        assertEquals("a: 1\r\n", fencedYaml("  ```yaml\r\na: 1\r\n  ```"))
    }

    // Plain YAML, or two blocks, which could be a plan and its alternative: reviewed as it came.
    @Test
    fun `gives nothing for no block, or more than one`() {
        assertNull(fencedYaml(yaml))
        assertNull(fencedYaml("```yaml\na: 1\n```\nor\n```yaml\nb: 2\n```"))
    }
}
