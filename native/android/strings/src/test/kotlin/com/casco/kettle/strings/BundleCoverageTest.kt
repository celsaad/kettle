package com.casco.kettle.strings

import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.ULocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class BundleCoverageTest {
    private val en = """{ "a": { "k": "Hi" }, "s_one": "{{count}} session", "s_other": "{{count}} sessions" }"""
    private val pt =
        """{ "a": { "k": "Oi" }, "s_one": "{{count}} sessão",""" +
            """ "s_other": "{{count}} sessões", "s_zero": "{{count}} sessões" }"""
    private val ja = """{ "a": { "k": "やあ" }, "s_other": "{{count}}セッション" }"""

    private fun refusal(vararg bundles: Pair<String, String>) =
        assertThrows<IllegalArgumentException> { generateResources(mapOf(*bundles)) }.message

    @Test
    fun `bundles carrying English's keys in their own plural categories generate`() {
        assertDoesNotThrow { generateResources(mapOf("en" to en, "pt" to pt, "ja" to ja)) }
    }

    @Test
    fun `a key missing from a translation fails the build, rather than rendering in English`() {
        assertEquals(
            "pt is missing a.k (English's keys, in its own plural categories)",
            refusal("en" to en, "pt" to pt.replace(""""a": { "k": "Oi" }, """, "")),
        )
    }

    @Test
    fun `a key English doesn't have fails the build, since nothing renders it`() {
        assertEquals(
            "pt has a.gone, which English doesn't have or pt can't select",
            refusal("en" to en, "pt" to pt.replace(""""k": "Oi"""", """"k": "Oi", "gone": "x"""")),
        )
    }

    @Test
    fun `a Portuguese plural without its _zero fails the build, which would bring back 0 sessão`() {
        assertEquals(
            "pt is missing s_zero (English's keys, in its own plural categories)",
            refusal("en" to en, "pt" to pt.replace(""", "s_zero": "{{count}} sessões"""", "")),
        )
    }

    @Test
    fun `a Japanese _one fails the build, since every count there resolves to other`() {
        assertEquals(
            "ja has s_one, which English doesn't have or ja can't select",
            refusal("en" to en, "ja" to ja.replace(""""s_other"""", """"s_one": "1セッション", "s_other"""")),
        )
    }

    @Test
    fun `a Japanese plural missing its only form fails the build`() {
        assertEquals(
            "ja is missing s_other (English's keys, in its own plural categories)",
            refusal(
                "en" to en,
                "ja" to """{ "a": { "k": "やあ" } }""",
            ),
        )
    }

    @Test
    fun `English holds itself to its own categories, so its plural can't lose a form either`() {
        assertEquals(
            "en is missing s_one (English's keys, in its own plural categories)",
            refusal("en" to en.replace(""""s_one": "{{count}} session", """, "")),
        )
    }

    @Test
    fun `Portuguese's many is permitted, never required, since no count Kettle shows reaches a million`() {
        assertDoesNotThrow {
            generateResources(
                mapOf(
                    "en" to en,
                    "pt" to pt.replace(""""s_other"""", """"s_many": "{{count}} de sessões", "s_other""""),
                ),
            )
        }
    }

    @Test
    fun `a word in capitals that isn't an acronym fails the build`() {
        assertEquals(
            "pt a.k writes OLA in capitals, and it isn't an acronym",
            refusal("en" to en, "pt" to pt.replace("Oi", "OLA")),
        )
    }

    @Test
    fun `acronyms, two-letter capitals, placeholders and Latin glued to kana pass the casing rule`() {
        val english =
            """{ "a": { "k": "Your HIIT PR, OK? {{URL}}" },""" +
                """ "s_one": "{{count}} AMRAP", "s_other": "{{count}} AMRAPs" }"""
        assertDoesNotThrow { generateResources(mapOf("en" to english)) }
        assertEquals(
            "ja a.k writes ABCD in capitals, and it isn't an acronym",
            refusal("en" to en, "ja" to ja.replace("やあ", "ほかのABCD種目")),
        )
        assertDoesNotThrow { generateResources(mapOf("en" to en, "ja" to ja.replace("やあ", "ほかのHIIT種目"))) }
    }

    /**
     * The CLDR facts the two rules above rest on, pinned so a change in ICU shows up here rather than
     * as a bundle that suddenly fails or passes. If 0 ever moved to `other` for `pt`, its `_zero`
     * keys would become redundant rather than wrong.
     */
    @Test
    fun `CLDR gives Japanese one plural category and files a Portuguese zero under one`() {
        assertEquals(setOf("other"), pluralCategories("ja"))
        assertEquals(setOf("one", "other"), pluralCategories("en"))
        assertEquals("one", PluralRules.forLocale(ULocale.forLanguageTag("pt")).select(0.0))
    }
}
