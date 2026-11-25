package com.casco.kettle.shell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * That `content/` reaches the APK as assets and reads back. What the content says is held by
 * `ShippedLibrariesTest`, `ShippedSeedTest` and `ContentPacksTest`, and by the conformance corpus, so
 * this checks the packaging and the reader, not the content.
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
}
