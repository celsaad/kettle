package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The history export and backup's archive (#237): `yaml-mapping.test.ts`'s "session archive", case for
 * case, plus the oldest-first order `exportSessions` and `backUpNow` both sort into.
 */
class SessionArchiveTest {
    private fun session(
        id: String,
        startedAt: String,
    ) = Session(
        1,
        id,
        "leg-day",
        null,
        null,
        null,
        startedAt,
        null,
        listOf(SessionEntry.Reps("back-squat", listOf(RepsSetLog(reps = 8, weightKg = 60.0, restTakenSec = 90.0)))),
    )

    private val june = session("june", "2026-06-01T09:00:00.000Z")
    private val july = session("july", "2026-07-01T09:00:00.000Z")

    private fun load(text: String) = (loadYaml(text) as YamlLoad.Loaded).value as Map<*, *>

    // The promise the archive makes: it's a container, not a second format.
    @Test
    fun `holds each session exactly as its own file would`() {
        val archive = load(serializeSessionArchiveYaml(listOf(june, july), "2026-08-02T12:00:00.000Z"))

        val sessions = archive["sessions"] as List<*>
        assertEquals(load(serializeSessionYaml(june)), sessions[0])
        assertEquals(load(serializeSessionYaml(july)), sessions[1])
    }

    @Test
    fun `reads back as one plain document, not a stream`() {
        val archive = load(serializeSessionArchiveYaml(listOf(june), "2026-08-02T12:00:00.000Z"))

        assertEquals(1, archive["version"])
        assertEquals("2026-08-02T12:00:00.000Z", archive["exported_at"])
        assertEquals(1, (archive["sessions"] as List<*>).size)
    }

    @Test
    fun `writes snake_case at the container level too`() {
        val yaml = serializeSessionArchiveYaml(listOf(june), "2026-08-02T12:00:00.000Z")

        assertTrue(yaml.contains("exported_at:"))
        assertFalse(yaml.contains("exportedAt"))
    }

    // An archive read top to bottom in some other app runs forward in time, whatever order the log is in.
    @Test
    fun `orders the archived log oldest-first regardless of the order it was given`() {
        val archive = load(serializeSessionArchiveYaml(listOf(july, june), "2026-08-02T12:00:00.000Z"))

        assertEquals(listOf("june", "july"), (archive["sessions"] as List<*>).map { (it as Map<*, *>)["id"] })
    }
}
