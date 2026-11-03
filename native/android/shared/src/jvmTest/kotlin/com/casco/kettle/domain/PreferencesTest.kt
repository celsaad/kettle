package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Native Settings' write of `preferences.json` (#237). How it's read is `conformance/preferences.yaml`'s;
 * this holds the write to what RN's `JSON.stringify` wrote, so RN's parse-only read after the flip,
 * and an older build's, read it the same.
 */
class PreferencesTest {
    private val preferences =
        Preferences(
            unitSystem = UnitSystem.IMPERIAL,
            themePreference = ThemePreference.DARK,
            restDayReminder = true,
            sessionSounds = false,
            backupFolderUri = FOLDER,
        )

    @Test
    fun `writes every field, as RN's store wrote the whole object`() {
        assertEquals(
            """{"unitSystem":"imperial","themePreference":"dark","restDayReminder":true,"sessionSounds":false,""" +
                """"backupFolderUri":"$FOLDER"}""",
            serializePreferences(preferences),
        )
    }

    // Forgetting the folder writes it as null rather than leaving it out, as RN's did.
    @Test
    fun `writes a forgotten folder as null`() {
        assertEquals(
            """{"unitSystem":"metric","themePreference":"system","restDayReminder":false,"sessionSounds":true,""" +
                """"backupFolderUri":null}""",
            serializePreferences(
                Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, null),
            ),
        )
    }

    @Test
    fun `reads back as what was written`() {
        assertEquals(preferences, parsePreferences(serializePreferences(preferences)))
        val forgotten = preferences.copy(backupFolderUri = null, themePreference = ThemePreference.LIGHT)
        assertEquals(forgotten, parsePreferences(serializePreferences(forgotten)))
    }
}

private const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FKettle"
