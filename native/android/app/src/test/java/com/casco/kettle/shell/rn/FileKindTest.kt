package com.casco.kettle.shell.rn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The names are a contract with src/state/files-changed.ts, which ignores any it doesn't know: a typo
 * here would fail silently, with RN simply never re-reading.
 */
class FileKindTest {
    @Test
    fun `each kind goes over the wire by the name JS knows it by`() {
        assertEquals(listOf("library", "sessions", "preferences"), FileKind.entries.map { it.wire })
    }

    @Test
    fun `a name it doesn't know is no kind at all`() {
        assertEquals(FileKind.SESSIONS, FileKind.fromWire("sessions"))
        assertNull(FileKind.fromWire("tips"))
    }
}
