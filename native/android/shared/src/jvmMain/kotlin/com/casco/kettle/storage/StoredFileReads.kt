package com.casco.kettle.storage

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.domain.parseSessionYaml
import java.io.File
import java.io.IOException

/**
 * The session files in [dir], `sessions/` beside the library. In name order before the sort, so the
 * errors, and sessions that started together, come out the same on every read.
 */
fun readSessionLog(dir: File): SessionLog {
    val sessions = mutableListOf<Session>()
    val errors = mutableListOf<String>()
    val files = dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".yaml") }
    for (file in files.sortedBy { it.name }) {
        // Deleted since the listing: gone from the log, as the next read would find it.
        val text = file.readTextOrNull() ?: continue
        when (val result = parseSessionYaml(text)) {
            is ParseResult.Ok -> sessions += result.value

            // The line `listSessions` writes, kinds spelled as RN spells them.
            is ParseResult.InvalidYaml -> errors += "${file.name} (invalidYaml): ${result.detail}"

            is ParseResult.SchemaMismatch -> errors += "${file.name} (schemaMismatch): ${result.detail}"
        }
    }
    return SessionLog(sessions.sortedByDescending { it.startedAt }, errors)
}

/**
 * The library in [file], `exercises.yaml`, or null where there is none to read: RN seeds a missing
 * file and repairs or reseeds an unreadable one on its next load, and native writes neither.
 */
fun readLibrary(file: File): Library? {
    val text = file.readTextOrNull() ?: return null
    return (parseLibraryYaml(text) as? ParseResult.Ok)?.value
}

private fun File.readTextOrNull(): String? =
    try {
        readText()
    } catch (_: IOException) {
        null
    }
