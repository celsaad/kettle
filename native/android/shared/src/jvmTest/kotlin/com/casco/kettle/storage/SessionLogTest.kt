package com.casco.kettle.storage

import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.parseSessionYaml
import com.casco.kettle.domain.withSession
import com.casco.kettle.domain.withoutSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The disk's side of `SessionLog`'s reading. What a file means is the corpus's. */
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

    @Test
    fun `sessions that started together come out in file-name order, and so do the errors`() {
        write("b.yaml", session("b", "2026-10-01T08:00:00.000Z"))
        write("a.yaml", session("a", "2026-10-01T08:00:00.000Z"))
        write("a-b.yaml", session("a-b", "2026-10-01T08:00:00.000Z"))
        write("z.yaml", "version: [\n")
        write("m.yaml", "version: [\n")

        val log = readSessionLog(dir)

        // By name, "a-b.yaml" sorts before "a.yaml", though the id "a" sorts before "a-b".
        assertEquals(listOf("a-b", "a", "b"), log.sessions.map { it.id })
        assertEquals(listOf("m.yaml", "z.yaml"), log.errors.map { it.substringBefore(" ") })
        assertEquals(listOf("a-b.yaml", "a.yaml", "b.yaml"), log.files)
    }

    /**
     * The log the shell keeps in memory (#324): advanced by [withSession] and [withoutSession] as each
     * file is written or deleted, never read again, and still what a fresh read of the folder returns.
     */
    @Nested
    inner class InMemory {
        private var log = SessionLog.Empty

        private fun parsed(
            id: String,
            startedAt: String,
        ): Session = (parseSessionYaml(session(id, startedAt)) as ParseResult.Ok).value

        private fun written(session: Session) {
            writeSessionFile(dir, session)
            log = log.withSession(session)
            assertEquals(readSessionLog(dir), log)
        }

        private fun deleted(id: String) {
            dir.resolve("$id.yaml").delete()
            log = log.withoutSession(id)
            assertEquals(readSessionLog(dir), log)
        }

        private fun read() {
            log = readSessionLog(dir)
        }

        @Test
        fun `a new session, an overwrite that moves it, and a delete`() {
            written(parsed("a", "2026-10-01T08:00:00.000Z"))
            written(parsed("c", "2026-10-03T08:00:00.000Z"))
            written(parsed("b", "2026-10-02T08:00:00.000Z"))
            written(parsed("a", "2026-10-04T08:00:00.000Z"))
            deleted("c")
            deleted("a")
            deleted("b")
        }

        @Test
        fun `sessions that started together land in file-name order`() {
            val at = "2026-10-01T08:00:00.000Z"
            written(parsed("a", at))
            written(parsed("b", at))
            written(parsed("a-b", at))
            written(parsed("0a", at))
            written(parsed("a", at))
            deleted("a-b")
        }

        @Test
        fun `overwriting or deleting an unreadable file drops its error and keeps the rest in order`() {
            write("a.yaml", "version: [\n")
            write("b.yaml", "version: 1\nid: b\n")
            write("c.yaml", "version: [\n")
            write("d.yaml", session("d", "2026-10-01T08:00:00.000Z"))
            read()
            assertEquals(3, log.errors.size)

            written(parsed("b", "2026-10-02T08:00:00.000Z"))
            deleted("a")
            assertEquals(listOf("c.yaml"), log.errors.map { it.substringBefore(" ") })
        }

        @Test
        fun `an error naming a longer file is not taken for this one's`() {
            write("a.yaml (copy).yaml", "version: [\n")
            read()

            written(parsed("a", "2026-10-01T08:00:00.000Z"))
            deleted("a")
            assertEquals(1, log.errors.size)
        }

        @Test
        fun `a session read from a file of another name stays beside its namesake`() {
            write("copy.yaml", session("a", "2026-10-01T08:00:00.000Z"))
            read()

            // The write lands at a.yaml, beside copy.yaml, as the disk has it; the delete leaves copy.yaml.
            written(parsed("a", "2026-10-02T08:00:00.000Z"))
            assertEquals(2, log.sessions.size)
            deleted("a")
            assertEquals(listOf("copy.yaml"), log.files)
        }

        @Test
        fun `deleting a session that isn't there changes nothing`() {
            written(parsed("a", "2026-10-01T08:00:00.000Z"))
            deleted("gone")
        }
    }
}
