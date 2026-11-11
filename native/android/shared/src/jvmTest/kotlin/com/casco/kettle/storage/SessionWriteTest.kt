package com.casco.kettle.storage

import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.serializeSessionYaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** The session editor's write (#239), and the runner's: the one file, replaced whole or not at all. */
class SessionWriteTest {
    @TempDir
    lateinit var dir: File

    private fun session(reps: Long) =
        Session(
            1,
            "2026-07-29-a",
            "leg-day",
            null,
            null,
            null,
            "2026-07-29T09:00:00.000Z",
            "2026-07-29T10:00:00.000Z",
            listOf(
                SessionEntry.Reps("back-squat", listOf(RepsSetLog(reps = reps, weightKg = 60.0, restTakenSec = 90.0))),
            ),
        )

    @Test
    fun `replaces the session's file with one that reads back as the session written`() {
        writeSessionFile(dir, session(8))

        writeSessionFile(dir, session(7))

        assertEquals(listOf(session(7)), readSessionLog(dir).sessions)
    }

    @Test
    fun `names the file as RN does, and leaves nothing else behind`() {
        writeSessionFile(dir, session(8))

        assertEquals(listOf("2026-07-29-a.yaml"), dir.list()?.toList())
    }

    @Test
    fun `throws when the disk refuses, and leaves the last file whole`() {
        writeSessionFile(dir, session(8))
        dir.setWritable(false)
        try {
            assertThrows<IOException> { writeSessionFile(dir, session(7)) }
        } finally {
            dir.setWritable(true)
        }

        assertEquals(listOf(session(8)), readSessionLog(dir).sessions)
    }

    private val fileName = "${session(0).id}.yaml"

    private fun write(
        reps: Long,
        beforeRename: () -> Unit = {},
        syncDirectory: (File) -> Unit = {},
    ) = writeAtomically(
        dir.resolve(fileName),
        serializeSessionYaml(session(reps)),
        beforeRename,
        syncDirectory,
    )

    /** What RN's create-then-write couldn't promise (#333): stopped short, the log reads as it was. */
    @Test
    fun `stopped between the write and the rename, leaves the last file whole and no empty one`() {
        writeSessionFile(dir, session(8))

        assertThrows<IOException> { write(7, beforeRename = { throw IOException("killed") }) }

        assertEquals(listOf(session(8)), readSessionLog(dir).sessions)
        assertEquals(listOf(fileName), dir.list()?.toList())
    }

    /** A kill skips the cleanup above, and leaves the `.partial`; the log never reads it as a session. */
    @Test
    fun `a partial a kill left behind isn't read as a session`() {
        writeSessionFile(dir, session(8))
        dir.resolve("$fileName.partial").writeText("")

        val log = readSessionLog(dir)

        assertEquals(listOf(session(8)), log.sessions)
        assertEquals(emptyList<Any>(), log.errors)
    }

    @Test
    fun `syncs the directory once the rename has landed`() {
        val synced = mutableListOf<File>()

        write(7, syncDirectory = { folder ->
            assertTrue(folder.resolve(fileName).exists())
            synced += folder
        })

        assertEquals(listOf(dir), synced)
    }

    /** Best effort: the file is already renamed into place, so a sync that can't run loses nothing. */
    @Test
    fun `lands the write when the directory can't be synced, or the platform has no way to`() {
        listOf(
            IOException("refused"),
            SecurityException(),
            UnsupportedOperationException(),
        ).forEachIndexed { i, failure ->
            write(i.toLong(), syncDirectory = { throw failure })

            assertEquals(listOf(session(i.toLong())), readSessionLog(dir).sessions)
        }
    }
}
