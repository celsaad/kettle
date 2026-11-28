package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration

/** A chat reply's one fenced block (#569), and every text that isn't one, as CommonMark reads fences. */
class FencedYamlTest {
    private val yaml = "version: 1\nexercises: []\nworkouts: []\nprograms: []\n"

    @Test
    fun `takes the one yaml block out of a reply's prose`() {
        val reply = "Sure! Here's your plan:\n\n```yaml\n$yaml```\n\nLet me know how it goes."
        assertEquals(yaml, fencedYaml(reply))
    }

    @Test
    fun `takes a block tagged yml, YAML with an info string, untagged, or fenced with tildes`() {
        assertEquals(yaml, fencedYaml("```yml\n$yaml```"))
        assertEquals(yaml, fencedYaml("```YAML title=\"lib.yaml\"\n$yaml```"))
        assertEquals(yaml, fencedYaml("Here:\n```\n$yaml```"))
        assertEquals(yaml, fencedYaml("~~~yaml\n$yaml~~~"))
        assertEquals(yaml, fencedYaml("```yaml\ttitle\n$yaml```"))
    }

    @Test
    fun `takes a block indented as a chat renders it, with Windows line ends`() {
        assertEquals("a: 1\r\n", fencedYaml("  ```yaml\r\na: 1\r\n  ```"))
    }

    /** A note's block scalar holding ``` sits inside a longer fence, which only as long a run closes. */
    @Test
    fun `closes only on as long a fence of the same character`() {
        val inner = "notes: |\n  ```\n  not a fence\n  ```\n"
        assertEquals(inner, fencedYaml("````yaml\n$inner````"))
    }

    /** A truncated reply's block runs to the end, as CommonMark has an unclosed fence. */
    @Test
    fun `takes an unclosed block to the end of the text`() {
        assertEquals(yaml, fencedYaml("```yaml\n$yaml"))
    }

    /**
     * Plain YAML, two blocks, or one in another language: reviewed as it came. A closing fence read as an
     * opener once gave a block of the prose between two, so every block counts, whatever its language.
     */
    @Test
    fun `gives nothing for no block, more than one, or one in another language`() {
        assertNull(fencedYaml(yaml))
        assertNull(fencedYaml("```yaml\na: 1\n```\nor\n```yaml\nb: 2\n```"))
        assertNull(fencedYaml("```yaml\na: 1\n```\nrun:\n```bash\nls\n```\n"))
        assertNull(fencedYaml("```json\n{}\n```\nHere's the yaml:\n```yaml\nversion: 1\n```"))
        assertNull(fencedYaml("```bash\nls\n```"))
        // Empty, or cut off at its opener: reviewed as it came, rather than nothing at all.
        assertNull(fencedYaml("Sure, here it is:\n```yaml"))
        assertNull(fencedYaml("```yaml\n```"))
    }

    /** One pass: 512 KB of unclosed fences took minutes through a lazy regex, on the main thread. */
    @Test
    fun `reads Import's largest text of fences in a blink`() {
        val fences = "```yaml\n".repeat(MAX_IMPORT_BYTES / 8)
        assertTimeoutPreemptively(Duration.ofSeconds(1)) { fencedYaml(fences) }
    }
}
