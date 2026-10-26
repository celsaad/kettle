package com.casco.kettle.shell.theme

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Robolectric for the platform's org.json, which a plain unit test only has as stubs. */
@RunWith(RobolectricTestRunner::class)
class ThemePreferenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun preferences(json: String): File = folder.newFile("preferences.json").apply { writeText(json) }

    @Test
    fun `a pinned scheme is read from the file RN writes`() {
        val file = preferences("""{"unitSystem":"metric","themePreference":"dark","restDayReminder":false}""")

        assertEquals(ThemePreference.Dark, ThemePreference.read(file))
    }

    @Test
    fun `no file yet follows the device`() {
        assertEquals(ThemePreference.System, ThemePreference.read(folder.root.resolve("preferences.json")))
    }

    /** RN's schema defaults the field: it arrived after the file was already being written. */
    @Test
    fun `a file from before Appearance existed follows the device`() {
        assertEquals(ThemePreference.System, ThemePreference.read(preferences("""{"unitSystem":"imperial"}""")))
    }

    /**
     * RN's `loadPreferences` refuses the whole file when any field fails its schema, and then follows
     * the device. A pinned scheme read from that file anyway would draw the bar dark over a light app.
     */
    @Test
    fun `a file RN would refuse follows the device, whatever its scheme says`() {
        val refused =
            listOf(
                """{"themePreference":"dark"}""",
                """{"unitSystem":"stones","themePreference":"dark"}""",
                """{"unitSystem":"metric","themePreference":"dusk"}""",
                """{"unitSystem":"metric","themePreference":"dark","sessionSounds":"yes"}""",
                """{"unitSystem":"metric","themePreference":"dark","restDayReminder":1}""",
                """{"unitSystem":"metric","themePreference":"dark","backupFolderUri":7}""",
                """{"unitSystem":"metric","themePreference":"dark"""",
                "[]",
            )

        for (json in refused) {
            folder.root.resolve("preferences.json").delete()
            assertEquals(json, ThemePreference.System, ThemePreference.read(preferences(json)))
        }
    }

    @Test
    fun `a file RN accepts is read, with a null backup folder and keys it doesn't know`() {
        val file =
            preferences("""{"unitSystem":"metric","themePreference":"light","backupFolderUri":null,"later":1}""")

        assertEquals(ThemePreference.Light, ThemePreference.read(file))
    }
}
