package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The disk's side of reading `exercises.yaml`, which RN writes until M5. */
class StoredLibraryTest {
    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("exercises.yaml")

    @Test
    fun `the library is read from the file RN writes`() {
        file.writeText("version: 1\nexercises: []\nworkouts: [{ id: w, name: Push day, blocks: [] }]\nprograms: []\n")

        assertEquals(listOf("Push day"), readLibrary(file)?.workouts?.map { it.name })
    }

    /** RN seeds a missing file and repairs or reseeds a broken one, and native is no second writer. */
    @Test
    fun `no file, or one RN would refuse, is no library`() {
        assertNull(readLibrary(file))

        file.writeText("version: 1\nexercises: [\n")

        assertNull(readLibrary(file))
    }
}
