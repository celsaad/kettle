package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Settings' selectors (#237): its counts, and the file a first change writes. */
class SettingsTest {
    // Library's count leaves the built-in rest out, so the two screens agree.
    @Test
    fun `counts what the user wrote, leaving the built-in rest out`() {
        val library =
            Library(
                1,
                listOf(
                    Exercise("squat", "Squat", ExerciseConfig.Reps(sets = 3, targetRepsMin = 5, restSec = 90.0)),
                    Exercise("rest", "Rest", ExerciseConfig.Rest(durationSec = 60.0)),
                ),
                listOf(Workout("a", "A", emptyList()), Workout("b", "B", emptyList())),
                emptyList(),
            )

        assertEquals(
            SettingsCounts(exercises = 1, workouts = 2, programs = 0, sessions = 4),
            settingsCounts(library, 4),
        )
    }

    // Nothing read yet counts nothing, as RN's `?? 0` did.
    @Test
    fun `counts nothing before there is a library`() {
        assertEquals(SettingsCounts(0, 0, 0, 0), settingsCounts(null, 0))
    }

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
