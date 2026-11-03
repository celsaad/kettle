package com.casco.kettle.storage

import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import org.junit.jupiter.api.Assertions.assertEquals
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
}
