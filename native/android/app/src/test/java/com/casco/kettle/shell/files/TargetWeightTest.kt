package com.casco.kettle.shell.files

import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The runner's adopt (#220), from the message to the file: what's written, and when nothing is. */
class TargetWeightTest {
    private val bench =
        Exercise(
            "bench",
            "Bench",
            ExerciseConfig.Reps(sets = 3, targetRepsMin = 5, targetWeightKg = 60.0, restSec = 120.0),
        )

    private class Files(
        val stored: Library?,
        val refuse: Boolean = false,
    ) : FakeStoredFiles() {
        val written = mutableListOf<Library>()

        override fun library() = stored

        override fun writeLibrary(library: Library) {
            if (refuse) throw IOException("exercises.yaml")
            written += library
        }
    }

    private fun library(vararg exercises: Exercise) = Library(1, exercises.toList(), emptyList(), emptyList())

    @Test
    fun `writes the adopted load over the file as it is`() {
        val files = Files(library(bench))

        assertTrue(files.adoptTargetWeight("bench", 62.5))

        val config = bench.config as ExerciseConfig.Reps
        assertEquals(listOf(library(bench.copy(config = config.copy(targetWeightKg = 62.5)))), files.written)
    }

    @Test
    fun `writes nothing where there's nothing to set`() {
        val plank = Exercise("plank", "Plank", ExerciseConfig.TimedHold(sets = 3, holdSecMin = 45.0, restSec = 60.0))

        val cases = listOf(Files(null) to "bench", Files(library(plank)) to "plank", Files(library(bench)) to "gone")
        for ((files, id) in cases) {
            assertFalse(files.adoptTargetWeight(id, 62.5))
            assertEquals(emptyList<Library>(), files.written)
        }
    }

    @Test
    fun `a disk that refuses is silent, and says nothing changed`() {
        assertFalse(Files(library(bench), refuse = true).adoptTargetWeight("bench", 62.5))
    }
}
