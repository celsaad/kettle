package com.casco.kettle.shell.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.serializePreferences
import com.casco.kettle.storage.readLibrary
import com.casco.kettle.storage.readPreferences
import com.casco.kettle.storage.readSessionLog
import com.casco.kettle.storage.writeAtomically
import com.casco.kettle.storage.writeLibraryFile
import com.casco.kettle.storage.writeSessionFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The files native screens read and write, as a boundary a screen's test can fake. */
interface StoredFiles {
    fun sessionLog(): SessionLog

    fun library(): Library?

    fun preferences(): Preferences?

    /**
     * Deletes session [id]'s file: native's one write to the log in M4 (#187), History's Delete. A file
     * already gone is no failure, as in RN; one the disk won't remove throws [IOException].
     */
    fun deleteSession(id: String)

    /**
     * Replaces [session]'s file with it, whole or not at all: the session editor's Save (#239), where
     * native is the only writer of an edited session and RN's runner still writes new ones. Throws
     * [IOException] when the disk refuses, and the last file stays as it was.
     */
    fun writeSession(session: Session)

    /**
     * Replaces `preferences.json` with [preferences], every field, whole or not at all: native Settings'
     * write (#237), where native is the file's only writer from the preferences flip. Throws
     * [IOException] when the disk refuses, and the last file stays as it was.
     */
    fun writePreferences(preferences: Preferences)

    /**
     * Replaces `exercises.yaml` with [library], whole or not at all: native's library editors and
     * Import, and the runner's adopt, from the library flip (#245) on, when native became the file's
     * only writer. Throws [IOException] when the disk refuses, and the last library stays as it was.
     */
    fun writeLibrary(library: Library)
}

/**
 * Where RN keeps its files, under expo-file-system's `Paths.document`, which is [filesDir].
 * `src/storage/paths.ts` names the ones RN still reads. The two share no code, so change both.
 */
class StoragePaths(
    filesDir: File,
) {
    private val root = filesDir.resolve("exercise-tracker")
    val sessions = root.resolve("sessions")
    val library = root.resolve("exercises.yaml")
    val preferences = root.resolve("preferences.json")
    val supporter = root.resolve("supporter.json")
    val backupTargets = root.resolve("backup-targets.json")
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

    override fun writeSession(session: Session) = writeSessionFile(paths.sessions, session)

    override fun writePreferences(preferences: Preferences) =
        writeAtomically(paths.preferences, serializePreferences(preferences))

    override fun writeLibrary(library: Library) = writeLibraryFile(paths.library, library)
}

/**
 * The session log, the library and the preferences as native screens show them, read again by [reread].
 *
 * RN's runner writes new sessions, and nothing else from the library flip (#245): native's session
 * editor edits finished sessions ([writeSession], #240), native Settings writes the preferences
 * ([writePreferences], #238), and native writes the library ([writeLibrary]), the runner's adopt
 * included, which RN asks for by message. The runner is above the tabs, so the shell rereads when RN
 * hands the tabs back, on a tap onto a native tab or a native sheet coming up (`TabNavigator`'s
 * `reread`), whenever the app starts or stops being seen (`MainActivity`), and once an adopt lands
 * (`JsBridge.onLibraryWritten`). RN's half of the rule is `filesChanged`.
 */
class LatestFiles(
    private val files: StoredFiles,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Each read as it lands, on [scope]'s thread: the rest-day reminder reconciles from it (#236). */
    private val onRead: (SessionLog, Preferences?) -> Unit = { _, _ -> },
    /**
     * What every read waits for first: the launch's seed and repair of the library (`LibraryGate`).
     * Without it a first launch's read could land before the seed did and show no library until the
     * next one. Today `onCreate` has already waited before the first read, for RN's sake, but RN's
     * start goes lazy (#223) and this mustn't depend on it.
     */
    private val prepared: suspend () -> Unit = {},
) {
    /** Null until the first read lands, so a screen can tell "not read yet" from "no sessions". */
    var sessionLog by mutableStateOf<SessionLog?>(null)
        private set

    /**
     * Null until the first read lands. Native seeds and repairs the file before anything reads it
     * (`LibraryGate`), so an unreadable one is a write some other hand made mid-run, and keeps the last
     * that wasn't: the next launch heals it, and the names on screen meanwhile are still the user's.
     */
    var library by mutableStateOf<Library?>(null)
        private set

    /**
     * Null while there is none RN would read: no setting changed yet, or a file it refuses. Either way
     * RN shows the device's defaults, and a native screen falls back to them too.
     */
    var preferences by mutableStateOf<Preferences?>(null)
        private set

    /**
     * How many reads have landed. A read of unchanged files sets nothing, since the files compare
     * equal, so this is what a screen keys on when its answer also depends on the clock: Home's card,
     * which a turned day moves on (#235).
     */
    var reads by mutableIntStateOf(0)
        private set

    private var reading: Job? = null

    private val writingPreferences = Mutex()

    /** The newest choice whose write hasn't landed, held over what a read finds on the disk. */
    private var unsaved: Preferences? = null

    /**
     * Deletes session [id] and reads the log again, so the row goes as the file does. Throws
     * [IOException] when the disk refuses, and the log is left as it was.
     */
    suspend fun deleteSession(id: String) {
        withContext(io) { files.deleteSession(id) }
        reread()
    }

    /**
     * Writes [session] over its file and reads the log again, so the edit shows as the file has it.
     * Throws [IOException] when the disk refuses, and the log is left as it was.
     */
    suspend fun writeSession(session: Session) {
        withContext(io) { files.writeSession(session) }
        reread()
    }

    /**
     * Writes [library] over the file, shows it once the write lands, and reads the files again. Shown
     * before the read, not after it: the read is launched, not awaited, and an editor writing again in
     * between would otherwise build on the library from before this write and drop it, as the workout
     * editor's Save did a quick-added exercise (#242). Throws [IOException] when the disk refuses, and
     * the library is left as it was.
     */
    suspend fun writeLibrary(library: Library) {
        LibraryWrites.lock.withLock { withContext(io) { files.writeLibrary(library) } }
        this.library = library
        reread()
    }

    /**
     * Shows [preferences] at once and writes them over the file: a control that waited on the disk would
     * feel broken, and the theme follows at once. One write at a time, in the order asked, since two
     * taps write the same file. Until a write lands, the choice is held over every read, so a read that
     * raced it can't put the old setting back. Throws [IOException] when the disk refuses, and the
     * choice still holds for the run, as RN's store held it: the next launch is what loses it.
     */
    suspend fun writePreferences(preferences: Preferences) {
        unsaved = preferences
        this.preferences = preferences
        writingPreferences.withLock { withContext(io) { files.writePreferences(preferences) } }
        // Only this write's own: a newer tap waiting its turn holds its choice until it lands too.
        if (unsaved === preferences) unsaved = null
        // A read that took the file before this landed would put the old one back; this one replaces it,
        // and reads the library and the log again too, so nothing it carried is lost.
        reread()
    }

    /** Reads all three files again. A read still under way is dropped, so an older one can't land last. */
    fun reread() {
        reading?.cancel()
        reading =
            scope.launch {
                prepared()
                val (log, library, preferences) =
                    withContext(io) { Triple(files.sessionLog(), files.library(), files.preferences()) }
                // The held choice for the reminder too, so the alarm agrees with the toggle on screen.
                val shown = unsaved ?: preferences
                sessionLog = log
                this@LatestFiles.preferences = shown
                if (library != null) this@LatestFiles.library = library
                reads++
                onRead(log, shown)
            }
    }
}

/**
 * The process's one lock on writing `exercises.yaml`. The runner's adopt reads the file and writes it
 * back (`adoptTargetWeight`), so an editor's or Import's save landing between the two would be lost,
 * or would lose the adopt. Every library write takes it, one after another.
 */
object LibraryWrites {
    val lock = Mutex()
}

/** The shell's [LatestFiles], for a native screen to read. */
val LocalLatestFiles = staticCompositionLocalOf<LatestFiles> { error("No LatestFiles provided") }
