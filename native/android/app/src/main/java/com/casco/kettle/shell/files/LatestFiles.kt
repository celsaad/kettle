package com.casco.kettle.shell.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.storage.readLibrary
import com.casco.kettle.storage.readSessionLog
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

/**
 * Where RN keeps its files, under expo-file-system's `Paths.document`, which is [filesDir].
 * `src/storage/paths.ts` names them. The two share no code, so change both.
 */
class StoragePaths(
    filesDir: File,
) {
    private val root = filesDir.resolve("exercise-tracker")
    val sessions = root.resolve("sessions")
    val library = root.resolve("exercises.yaml")
    val preferences = root.resolve("preferences.json")
}

/** [StoredFiles] where RN keeps them. */
class DiskFiles(
    private val paths: StoragePaths,
) : StoredFiles {
    override fun sessionLog() = readSessionLog(paths.sessions)

    override fun library() = readLibrary(paths.library)
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
    /** Null until the first read lands, so a screen can tell "not read yet" from "no sessions". */
    var sessionLog by mutableStateOf<SessionLog?>(null)
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
