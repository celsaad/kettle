package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The disk's side of `listSessions` in `src/storage/session-files.ts`. What a file means is the corpus's. */
class SessionLogTest {
    @TempDir
    lateinit var dir: File

    private fun session(
        id: String,
        startedAt: String,
    ) = "version: 1\nid: $id\nworkout: w\nstarted_at: $startedAt\nended_at: null\nentries: []\n"

    private fun write(
        name: String,
        text: String,
    ) = dir.resolve(name).writeText(text)

    @Test
    fun `every session file is read, newest first`() {
        write("a.yaml", session("a", "2026-10-01T08:00:00.000Z"))
        write("c.yaml", session("c", "2026-10-03T08:00:00.000Z"))
        write("b.yaml", session("b", "2026-10-02T08:00:00.000Z"))

        assertEquals(listOf("c", "b", "a"), readSessionLog(dir).sessions.map { it.id })
    }

    @Test
    fun `a file that can't be read is named among the errors, and the rest still are`() {
        write("good.yaml", session("good", "2026-10-01T08:00:00.000Z"))
        write("broken.yaml", "version: [\n")
        write("wrong.yaml", "version: 1\nid: wrong\n")

        val log = readSessionLog(dir)

        assertEquals(listOf("good"), log.sessions.map { it.id })
        assertEquals(
            listOf("broken.yaml (invalidYaml)", "wrong.yaml (schemaMismatch)"),
            log.errors.map { it.substringBefore(":") },
        )
    }

    @Test
    fun `only yaml files are sessions`() {
        write("a.yaml", session("a", "2026-10-01T08:00:00.000Z"))
        write("notes.txt", "not a session")
        dir.resolve("folder.yaml").mkdir()

        val log = readSessionLog(dir)

        assertEquals(listOf("a"), log.sessions.map { it.id })
        assertEquals(emptyList<String>(), log.errors)
    }

    /** RN creates the folder on its first storage call, which a native read can beat on a first launch. */
    @Test
    fun `no folder yet is an empty log`() {
        assertEquals(SessionLog.Empty, readSessionLog(dir.resolve("sessions")))
    }
}
