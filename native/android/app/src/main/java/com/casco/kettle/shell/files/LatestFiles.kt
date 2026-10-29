package com.casco.kettle.shell.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.storage.readLibrary
import com.casco.kettle.storage.readPreferences
import com.casco.kettle.storage.readSessionLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The files native screens read and RN writes, as a boundary a screen's test can fake. */
interface StoredFiles {
    fun sessionLog(): SessionLog

    fun library(): Library?

    fun preferences(): Preferences?

    /**
     * Deletes session [id]'s file: native's one write to the log in M4 (#187), History's Delete. A file
     * already gone is no failure, as in RN; one the disk won't remove throws [IOException].
     */
    fun deleteSession(id: String)
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
    val supporter = root.resolve("supporter.json")
}

/** [StoredFiles] where RN keeps them. */
class DiskFiles(
    private val paths: StoragePaths,
) : StoredFiles {
    override fun sessionLog() = readSessionLog(paths.sessions)

    override fun library() = readLibrary(paths.library)

    override fun preferences() = readPreferences(paths.preferences)

    override fun deleteSession(id: String) {
        // Named as `sessionFile` in src/storage/paths.ts names it. The two share no code, so change both.
        val file = paths.sessions.resolve("$id.yaml")
        // File.delete() says only no; the name is what the reader can act on.
        if (file.exists() && !file.delete()) throw IOException(file.name)
    }
}

/**
 * The session log, the library and the preferences as native screens show them, read again by [reread].
 *
 * RN writes all three until M5: the runner writes new sessions, the session editor edits finished
 * ones, the library editors write the library, and Settings the preferences. Each of those is above
 * the tabs, so the shell rereads when RN hands the tabs back, on a tap onto a native tab or a native
 * sheet coming up (`TabNavigator`'s `reread`) and on a cold start, and nothing else would. RN's half
 * of the rule is `filesChanged`.
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

    /**
     * Null while there is none RN would read: no setting changed yet, or a file it refuses. Either way
     * RN shows the device's defaults, and a native screen falls back to them too.
     */
    var preferences by mutableStateOf<Preferences?>(null)
        private set

    private var reading: Job? = null

    /**
     * Deletes session [id] and reads the log again, so the row goes as the file does. Throws
     * [IOException] when the disk refuses, and the log is left as it was.
     */
    suspend fun deleteSession(id: String) {
        withContext(io) { files.deleteSession(id) }
        reread()
    }

    /** Reads all three files again. A read still under way is dropped, so an older one can't land last. */
    fun reread() {
        reading?.cancel()
        reading =
            scope.launch {
                val (log, library, preferences) =
                    withContext(io) { Triple(files.sessionLog(), files.library(), files.preferences()) }
                sessionLog = log
                this@LatestFiles.preferences = preferences
                if (library != null) this@LatestFiles.library = library
            }
    }
}

/** The shell's [LatestFiles], for a native screen to read. */
val LocalLatestFiles = staticCompositionLocalOf<LatestFiles> { error("No LatestFiles provided") }
