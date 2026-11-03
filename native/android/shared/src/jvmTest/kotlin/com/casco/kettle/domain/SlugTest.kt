package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.text.Normalizer

/** `slug.test.ts`, case for case: a new exercise's id is its name's slug (#241). */
class SlugTest {
    @Test
    fun `lowercases the name`() {
        assertEquals("pull-ups", slugify("Pull-Ups"))
    }

    @Test
    fun `collapses spaces and punctuation into single hyphens`() {
        assertEquals("barbell-row-pendlay", slugify("Barbell  Row (Pendlay)"))
        assertEquals("farmer-s-carry", slugify("Farmer's Carry"))
    }

    @Test
    fun `trims leading and trailing hyphens`() {
        assertEquals("overhead-press", slugify("  -- Overhead Press --  "))
    }

    @Test
    fun `keeps digits`() {
        assertEquals("tabata-20-10", slugify("Tabata 20/10"))
    }

    @Test
    fun `strips Latin diacritics rather than hyphenating them`() {
        assertEquals("flexao", slugify("Flexão"))
        assertEquals("elevation-du-genou", slugify("Élévation du Genou"))
    }

    @Test
    fun `keeps a name written in a non-Latin script`() {
        assertEquals("приседания", slugify("Приседания"))
        assertEquals("腕立て伏せ", slugify("腕立て伏せ"))
        assertEquals("планка-60-сек", slugify("Планка 60 сек"))
    }

    @Test
    fun `keeps combining marks that are letters rather than accents`() {
        assertEquals("योग", slugify("योग"))
        assertEquals("ท่าแพลงก์", slugify("ท่าแพลงก์"))
    }

    @Test
    fun `gives one id whether an accent was typed composed or decomposed`() {
        val composed = Normalizer.normalize("Flexão", Normalizer.Form.NFC)
        val decomposed = Normalizer.normalize("Flexão", Normalizer.Form.NFD)
        assertNotEquals(composed, decomposed)
        assertEquals(slugify(composed), slugify(decomposed))
    }

    // The emoji carry U+FE0F, a mark: kept unconditionally, they'd make an id of invisible characters.
    @Test
    fun `yields an empty string for a name with no letters or digits at all`() {
        assertEquals("", slugify("---"))
        assertEquals("", slugify("🔥🔥"))
        assertEquals("", slugify("🏋️🏋️"))
    }

    @Test
    fun `drops an emoji from a name that has real words too`() {
        assertEquals("push-day", slugify("Push 🏋️ day"))
    }
}
