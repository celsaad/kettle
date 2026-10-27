package com.casco.kettle.domain

/**
 * Every session file, as `listSessions` in `src/storage/session-files.ts` reads them: the sessions,
 * newest first, and a line per file that couldn't be read, which History shows rather than hides.
 */
data class SessionLog(
    val sessions: List<Session>,
    val errors: List<String>,
) {
    companion object {
        val Empty = SessionLog(emptyList(), emptyList())
    }
}
