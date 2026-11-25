package com.casco.kettle.repochecks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Play's character limits, against the copy that actually gets uploaded. These went stale silently,
 * which is why this exists: after one editing pass both full descriptions were over 4000 and their
 * annotated lengths wrong, and the same pass left a release note at 507 of 500. Over-limit copy is a
 * rejected upload, not a truncated one, so the cost lands at the worst moment.
 *
 * AGENTS.md says never to quote a hand-maintained count. `store/README.md`'s headings do anyway
 * (`### Full — en-US (3880)`), since the headroom helps while editing, so the count is checked here
 * rather than trusted.
 */
class StoreCopyTest {
    private val listing = repoText("store/README.md")

    /** Every language the listing carries: copy in the README under a language not named here is unchecked. */
    private val languages = listOf("en-US", "pt-BR", "ja-JP")

    private val limits = mapOf("Title" to TITLE_LIMIT, "Short" to SHORT_LIMIT, "Full" to FULL_LIMIT)

    /** The heading line that starts with [prefix], count included, so the count itself isn't hardcoded. */
    private fun heading(prefix: String): String =
        listing.lines().firstOrNull { it.startsWith(prefix) }
            ?: error("no heading starting \"$prefix\" in store/README.md")

    /** The fenced block under a heading, which is what gets pasted into Play. */
    private fun blockUnder(prefix: String): String {
        val open = listing.indexOf("```", listing.indexOf(heading(prefix)))
        val close = listing.indexOf("```", open + FENCE.length)
        return listing.substring(listing.indexOf('\n', open) + 1, close).removeSuffix("\n")
    }

    /** Each listing block as `(label, its declared length, its text)`. */
    private fun blocks() =
        limits.keys.flatMap { kind ->
            languages.map { language ->
                val prefix = "### $kind — $language"
                val declared =
                    Regex("""\((\d+)\)""")
                        .find(heading(prefix))
                        ?.groupValues
                        ?.get(1)
                        ?.toInt()
                Triple(prefix, declared, blockUnder(prefix))
            }
        }

    @Test
    fun `every listing block is within its Play limit`() {
        val failures =
            blocks().mapNotNull { (prefix, _, text) ->
                val limit = limits.getValue(prefix.removePrefix("### ").substringBefore(" "))
                "$prefix: ${text.length} of $limit".takeIf { text.length > limit }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `every listing heading declares its block's real length`() {
        // The annotation is what an editor reads to decide whether a sentence fits, so a wrong one is
        // worse than none: it invites the edit that breaks the limit.
        val failures =
            blocks().mapNotNull { (prefix, declared, text) ->
                "$prefix declares $declared, is ${text.length}".takeIf { declared != text.length }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `every full description keeps room to edit, not just room to upload`() {
        // pt-BR runs longer than English and has come closest twice: once at 4048, once 14 spare.
        val failures =
            languages.mapNotNull { language ->
                val length = blockUnder("### Full — $language").length
                "$language: $length".takeIf { FULL_LIMIT - length < FULL_HEADROOM }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `every release note in the changelog is within Play's limit`() {
        val changelog = repoText("CHANGELOG.md")
        val notes =
            languages.flatMap { language ->
                Regex("<$language>\n([\\s\\S]*?)\n</$language>").findAll(changelog).mapIndexed { index, match ->
                    "$language block ${index + 1}" to match.groupValues[1]
                }
            }
        // A renamed tag would otherwise make this pass by checking nothing. Global rather than per
        // language: ja-JP has never carried a release note, since Play's listing doesn't (CHANGELOG.md).
        assertTrue(notes.isNotEmpty(), "found no tagged release notes in CHANGELOG.md")
        val failures =
            notes.mapNotNull { (label, text) -> "$label: ${text.length}".takeIf { text.length > RELEASE_NOTE_LIMIT } }
        assertEquals(emptyList<String>(), failures)
    }

    private companion object {
        const val TITLE_LIMIT = 30
        const val SHORT_LIMIT = 80
        const val FULL_LIMIT = 4000
        const val FULL_HEADROOM = 30
        const val RELEASE_NOTE_LIMIT = 500
        const val FENCE = "```"
    }
}
