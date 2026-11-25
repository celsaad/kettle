package com.casco.kettle.shell.files

import com.casco.kettle.domain.ExerciseConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the seed owes beyond [ShippedLibrariesTest]: it is what a fresh install runs, in every language. */
@RunWith(RobolectricTestRunner::class)
class ShippedSeedTest {
    private val shipped = ShippedLibraries()

    @Test
    fun demonstratesAllSevenExerciseTypes() {
        // The seed is where each type first shows what it does; one with no live example isn't taught.
        val types =
            setOf(
                ExerciseConfig.Hiit::class,
                ExerciseConfig.Emom::class,
                ExerciseConfig.Amrap::class,
                ExerciseConfig.Reps::class,
                ExerciseConfig.TimedHold::class,
                ExerciseConfig.Cardio::class,
                ExerciseConfig.Rest::class,
            )
        assertEquals(
            types,
            shipped.seed
                .read("en")
                .exercises
                .map { it.config::class }
                .toSet(),
        )
    }

    @Test
    fun putsTheNoEquipmentProgramFirst() {
        // The active program falls back to the first until a session exists, and there is no picker.
        for (language in shipped.languages) {
            assertEquals(
                language,
                "foundations",
                shipped.seed
                    .read(language)
                    .programs
                    .first()
                    .id,
            )
        }
    }
}
