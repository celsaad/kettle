package com.casco.kettle.storage

import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** The session editor's write (#239): the one file, replaced whole or not at all. */
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
}
