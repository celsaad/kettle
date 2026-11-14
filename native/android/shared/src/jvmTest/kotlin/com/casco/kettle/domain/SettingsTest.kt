package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Settings' selectors (#237): the file a first change writes. */
class SettingsTest {
    /**
     * No file yet is "never answered": the device's units, the system's scheme, the reminder off and the
     * sounds on, as RN's store defaulted them. The first change writes all of it, not just its own field.
     */
    @Test
    fun `starts a fresh install's file from the device's units and the defaults`() {
        assertEquals(
            Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null),
            preferencesOrDefault(null, UnitSystem.IMPERIAL),
        )
        val stored = Preferences(UnitSystem.METRIC, ThemePreference.DARK, true, false, "content://tree/a")
        assertEquals(stored, preferencesOrDefault(stored, UnitSystem.IMPERIAL))
    }
}
