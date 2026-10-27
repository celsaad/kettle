package com.casco.kettle.shell.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.readLibrary
import com.casco.kettle.domain.readSessionLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The files native screens read and RN writes, as a boundary a screen's test can fake. */
interface StoredFiles {
    fun sessionLog(): SessionLog

    fun library(): Library?
}

/** [StoredFiles] in [root], expo-file-system's `exercise-tracker` folder (`src/storage/paths.ts`). */
class DiskFiles(
    private val root: File,
) : StoredFiles {
    override fun sessionLog() = readSessionLog(root.resolve(SESSIONS))

    override fun library() = readLibrary(root.resolve(LIBRARY))

    private companion object {
        // src/storage/paths.ts names these. The two share no code, so change both.
        const val SESSIONS = "sessions"
        const val LIBRARY = "exercises.yaml"
    }
}

/**
 * The session log and the library as native screens show them, read again by [reread].
 *
 * RN writes both until M5: the runner writes new sessions, the session editor edits finished ones,
 * and the library editors write the library. Each of those is above the tabs, so the shell rereads
 * when RN hands the tabs back, on a tap onto a native tab (`TabNavigator`'s `reread`) and on a cold
 * start, and nothing else would. RN's half of the rule is `filesChanged`.
 */
class LatestFiles(
    private val files: StoredFiles,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    var sessionLog by mutableStateOf(SessionLog.Empty)
        private set

    /**
     * Null until RN has written a readable one, which it does on its first launch. An unreadable one
     * keeps the last that wasn't: RN heals it on its next load, and the names on screen meanwhile are
     * still the user's.
     */
    var library by mutableStateOf<Library?>(null)
        private set

    private var reading: Job? = null

    /** Reads both files again. A read still under way is dropped, so an older one can't land last. */
    fun reread() {
        reading?.cancel()
        reading =
            scope.launch {
                val (log, library) = withContext(io) { files.sessionLog() to files.library() }
                sessionLog = log
                if (library != null) this@LatestFiles.library = library
            }
    }
}

/** The shell's [LatestFiles], for a native screen to read. */
val LocalLatestFiles = staticCompositionLocalOf<LatestFiles> { error("No LatestFiles provided") }
