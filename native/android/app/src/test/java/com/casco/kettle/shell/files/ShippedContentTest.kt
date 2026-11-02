package com.casco.kettle.shell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * That `src/content/` reaches the APK as assets and reads back. What the content says is held by the
 * RN tests and the conformance corpus, so this checks the packaging and the reader, not the content.
 */
@RunWith(RobolectricTestRunner::class)
class ShippedContentTest {
    private val content = ShippedContent(RuntimeEnvironment.getApplication().assets)

    @Test
    fun theSeedStartsOnTheNoEquipmentProgram() {
        assertEquals(
            "foundations",
            content
                .seed("en")
                .programs
                .first()
                .id,
        )
    }

    @Test
    fun theSeedReadsInTheDeviceLanguage() {
        val english =
            content
                .seed("en")
                .exercises
                .first { it.id == "pushups" }
                .name
        val portuguese =
            content
                .seed("pt-BR")
                .exercises
                .first { it.id == "pushups" }
                .name
        assertNotEquals(english, portuguese)
        assertEquals(
            portuguese,
            content
                .seed("pt")
                .exercises
                .first { it.id == "pushups" }
                .name,
        )
        assertEquals(
            english,
            content
                .seed("de")
                .exercises
                .first { it.id == "pushups" }
                .name,
        )
    }

    @Test
    fun everyListedPackReadsWithItsPrefix() {
        val packs = content.packs()
        assertEquals(listOf("steady-strength", "barbell-gym", "kettlebell-basics"), packs.map { it.id })
        packs.forEach { pack ->
            listOf("en", "pt", "ja").forEach { language ->
                val library = content.pack(pack.id, language)
                assertTrue(pack.id, library.exercises.all { it.id.startsWith(pack.idPrefix) })
            }
        }
    }
}
