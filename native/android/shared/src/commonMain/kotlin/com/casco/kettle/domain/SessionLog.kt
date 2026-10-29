package com.casco.kettle.domain

/**
 * Every session file, as `listSessions` in `src/storage/session-files.ts` reads them: the sessions,
 * newest first, and a line per file that couldn't be read, which History shows rather than hides.
 */
data class SessionLog(
    val sessions: List<Session>,
    val errors: List<String>,
) {
    /**
     * [loggedSessions] of [sessions]: what every training view reads, where RN's read `useLoggedSessions`.
     * Raw [sessions] are for handing over the user's own files. Worked out once per read of the log.
     */
    val logged: List<Session> by lazy { loggedSessions(sessions) }

    companion object {
        val Empty = SessionLog(emptyList(), emptyList())
    }
}
