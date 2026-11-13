package com.casco.kettle.storage

import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MAX_YAML_CODE_POINTS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** The exercise editor's write (#241): the library file, replaced whole or not at all. */
class LibraryWriteTest {
    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("exercises.yaml")

    private fun library(sets: Long) =
        Library(
            1,
            listOf(
                Exercise(
                    "bench",
                    "Bench",
                    ExerciseConfig.Reps(sets = sets, targetRepsMin = 5, targetWeightKg = 102.06, restSec = 120.0),
                ),
            ),
            emptyList(),
            emptyList(),
        )

    @Test
    fun `replaces the library with one that reads back as the library written`() {
        writeLibraryFile(file, library(5))

        writeLibraryFile(file, library(6))

        assertEquals(library(6), readLibrary(file))
        assertEquals(listOf("exercises.yaml"), dir.list()?.toList())
    }

    @Test
    fun `throws when the disk refuses, and leaves the last library whole`() {
        writeLibraryFile(file, library(5))
        dir.setWritable(false)
        try {
            assertThrows<IOException> { writeLibraryFile(file, library(6)) }
        } finally {
            dir.setWritable(true)
        }

        assertEquals(library(5), readLibrary(file))
    }

    // Past the parser's cap the next launch would quarantine the whole library: an import into one
    // already near it, or several, would have taken it there with no word at the time.
    @Test
    fun `refuses a library the parser couldn't read back, and leaves the last one whole`() {
        writeLibraryFile(file, library(5))
        val huge =
            library(5).let {
                it.copy(
                    exercises =
                        it.exercises.map { e ->
                            e.copy(notes = "x".repeat(MAX_YAML_CODE_POINTS))
                        },
                )
            }

        assertThrows<IOException> { writeLibraryFile(file, huge) }

        assertEquals(library(5), readLibrary(file))
        assertEquals(listOf("exercises.yaml"), dir.list()?.toList())
    }

    @Test
    fun `reads a stream whole up to the limit, and gives up past it`() {
        val bytes = ByteArray(10_000) { it.toByte() }

        assertEquals(bytes.toList(), bytes.inputStream().readAtMost(10_000)?.toList())
        assertNull(bytes.inputStream().readAtMost(9_999))
    }
}
