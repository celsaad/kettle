package com.casco.kettle.storage

import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MAX_YAML_CODE_POINTS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

/**
 * Native's launch-time read of `exercises.yaml` (#220, #221), RN's `loadLibrary` recovery until the
 * library flip: `library-file-recovery.test.ts`' cases, ported. The repair's own arithmetic is the
 * conformance suite's.
 */
class PrepareLibraryTest {
    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("exercises.yaml")

    private val seed = Library(1, emptyList(), emptyList(), emptyList())

    private val at = Instant.parse("2026-11-04T12:30:15.042Z")

    private fun prepare() = prepareLibrary(file, { seed }) { at }

    private fun libraryWith(
        sets: Int,
        extra: String = "",
    ) = """
        |version: 1
        |exercises:
        |  - id: bench
        |    name: Bench
        |    type: reps
        |    config: { sets: $sets, target_reps_min: 5, rest_sec: 60 }
        |workouts: []
        |programs: []
        |$extra
        """.trimMargin()

    @Test
    fun `a first launch writes the seed`() {
        assertEquals(PreparedLibrary.Seeded, prepare())

        assertEquals(seed, readLibrary(file))
    }

    @Test
    fun `leaves a library that reads completely alone`() {
        val text = libraryWith(5)
        file.writeText(text)

        assertEquals(PreparedLibrary.Found, prepare())

        assertEquals(text, file.readText())
        assertEquals(listOf("exercises.yaml"), dir.list()?.toList())
    }

    @Test
    fun `clamps a library the ceilings refuse rather than replacing it, and saves the repair`() {
        file.writeText(libraryWith(200_000))

        assertInstanceOf(PreparedLibrary.Repaired::class.java, prepare())

        val bench = readLibrary(file)?.exercises?.single()
        assertEquals("Bench", bench?.name)
        assertEquals(500L, (bench?.config as ExerciseConfig.Reps).sets)
        assertEquals(listOf("exercises.yaml"), dir.list()?.toList())
    }

    // The repair rewrites the raw tree, keys the schema ignores included, which the ordinary parse never
    // walks: a self-referencing alias there overflowed the stack as an Error, past the launch's catch,
    // and crashed every launch with the file still in place.
    @Test
    fun `moves a library aside when its repair can't be finished, rather than crashing`() {
        val text = libraryWith(200_000, "junk: &a [*a]\n")
        file.writeText(text)

        val prepared = prepare() as PreparedLibrary.Reseeded

        assertEquals(text, prepared.quarantined?.readText())
        assertEquals(seed, readLibrary(file))
    }

    // Read whole, a file this large could exhaust the heap before the parser's cap refused it.
    @Test
    fun `sets a library too large to parse aside unread, and reseeds`() {
        file.writeText(libraryWith(5, "notes: ${"x".repeat(4 * MAX_YAML_CODE_POINTS)}\n"))
        val bytes = file.length()

        val prepared = prepare() as PreparedLibrary.Reseeded

        // Set aside by its size, not refused by the parser after a read.
        assertEquals("tooLarge: $bytes bytes", prepared.why)
        assertEquals(bytes, prepared.quarantined?.length())
        assertEquals(seed, readLibrary(file))
    }

    @Test
    fun `moves a library broken some other way aside before the seed replaces it`() {
        val text = libraryWith(3, "  - id: broken\n")
        file.writeText(text)

        val prepared = prepare() as PreparedLibrary.Reseeded

        assertEquals(dir.resolve("exercises.invalid-2026-11-04T12-30-15-042Z.yaml"), prepared.quarantined)
        assertEquals(text, prepared.quarantined?.readText())
        assertEquals(seed, readLibrary(file))
    }

    @Test
    fun `quarantines a file that is not YAML at all`() {
        file.writeText("exercises: [unclosed")

        val prepared = prepare() as PreparedLibrary.Reseeded

        assertEquals("exercises: [unclosed", prepared.quarantined?.readText())
    }

    @Test
    fun `still reseeds when the rescue itself fails`() {
        file.writeText("exercises: [unclosed")
        // A directory where the rescue's file would go, so its rename can't land.
        dir.resolve("exercises.invalid-2026-11-04T12-30-15-042Z.yaml").resolve("occupied").mkdirs()

        val prepared = prepare() as PreparedLibrary.Reseeded

        assertNull(prepared.quarantined)
        assertEquals(seed, readLibrary(file))
    }
}
