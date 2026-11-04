package com.casco.kettle.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The disk's side of reading `exercises.yaml`. */
class StoredLibraryTest {
    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("exercises.yaml")

    @Test
    fun `the library is read from the file`() {
        file.writeText("version: 1\nexercises: []\nworkouts: [{ id: w, name: Push day, blocks: [] }]\nprograms: []\n")

        assertEquals(listOf("Push day"), readLibrary(file)?.workouts?.map { it.name })
    }

    /** Reading is only reading: seeding and repairing are prepareLibrary's. */
    @Test
    fun `no file, or one the parser refuses, is no library`() {
        assertNull(readLibrary(file))

        file.writeText("version: 1\nexercises: [\n")

        assertNull(readLibrary(file))
    }
}
