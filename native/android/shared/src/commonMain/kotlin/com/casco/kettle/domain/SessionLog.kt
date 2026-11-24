package com.casco.kettle.domain

/**
 * Every session file, as `listSessions` in `src/storage/session-files.ts` reads them: the sessions,
 * newest first, and a line per file that couldn't be read, which History shows rather than hides.
 *
 * [files] is the file each session was read from, in step with [sessions]. The reader takes any
 * `*.yaml`, not only `<id>.yaml`, and orders ties and names errors by file, so [withSession] and
 * [withoutSession] need the names to land where a fresh read of the folder would put them (#324).
 */
data class SessionLog(
    val sessions: List<Session>,
    val errors: List<String>,
    val files: List<String> = sessions.map { sessionFileName(it.id) },
) {
    init {
        require(files.size == sessions.size) { "A file name per session" }
    }

    /**
     * [loggedSessions] of [sessions]: what every training view reads, where RN's read `useLoggedSessions`.
     * Raw [sessions] are for handing over the user's own files. Worked out once per read of the log.
     */
    val logged: List<Session> by lazy { loggedSessions(sessions) }

    companion object {
        val Empty = SessionLog(emptyList(), emptyList())
    }
}

/** The name a session's file is written under, which the writer and these updates share. */
fun sessionFileName(id: String) = "$id.yaml"

/**
 * This log once [session]'s file has been written: whatever its file held before goes, a session or
 * an unreadable file's error, and [session] takes the place a read would give it. Newest `startedAt`
 * first, and a tie in file-name order, as the reader's name-order listing and stable sort leave it.
 * Inserted rather than sorted, so a log handed in some other order keeps it.
 */
fun SessionLog.withSession(session: Session): SessionLog {
    val name = sessionFileName(session.id)
    val rest = withoutSession(session.id)
    val at =
        rest.sessions.indices.firstOrNull { i ->
            val other = rest.sessions[i].startedAt
            other < session.startedAt || (other == session.startedAt && rest.files[i] > name)
        } ?: rest.sessions.size
    return SessionLog(
        sessions = rest.sessions.toMutableList().apply { add(at, session) },
        errors = rest.errors,
        files = rest.files.toMutableList().apply { add(at, name) },
    )
}

/**
 * This log once session [id]'s file has been deleted: the session it held, or the error it read as,
 * goes. Keyed by the file, not the id, as the delete is, so a session read from a file of another
 * name stays, as it would on the disk.
 */
fun SessionLog.withoutSession(id: String): SessionLog {
    val name = sessionFileName(id)
    val kept = files.indices.filter { files[it] != name }
    // The reader's two prefixes exactly: a bare "$name (" would also take a file named "$name (copy).yaml".
    val errorPrefixes = listOf("$name (invalidYaml): ", "$name (schemaMismatch): ")
    return SessionLog(
        sessions = kept.map { sessions[it] },
        errors = errors.filter { line -> errorPrefixes.none { line.startsWith(it) } },
        files = kept.map { files[it] },
    )
}
