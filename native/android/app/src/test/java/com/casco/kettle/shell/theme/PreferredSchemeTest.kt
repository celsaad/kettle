package com.casco.kettle.shell.theme

import com.casco.kettle.domain.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The file's side of it. What its text means is `conformance/preferences.yaml`'s. */
class PreferredSchemeTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `no file yet follows the device`() {
        assertEquals(ThemePreference.SYSTEM, readThemePreference(folder.root.resolve("preferences.json")))
    }

    @Test
    fun `a pinned scheme is read from the file RN writes`() {
        val file =
            folder.newFile("preferences.json").apply {
                writeText("""{"unitSystem":"metric","themePreference":"dark"}""")
            }

        assertEquals(ThemePreference.DARK, readThemePreference(file))
    }

    @Test
    fun `a file RN would refuse follows the device, whatever its scheme says`() {
        val file = folder.newFile("preferences.json").apply { writeText("""{"themePreference":"dark"}""") }

        assertEquals(ThemePreference.SYSTEM, readThemePreference(file))
    }
}
