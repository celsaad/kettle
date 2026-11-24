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
import com.casco.kettle.domain.sessionFileName
import com.casco.kettle.domain.withSession
import com.casco.kettle.domain.withoutSession
import com.casco.kettle.storage.readLibrary
import com.casco.kettle.storage.readPreferences
import com.casco.kettle.storage.readSessionLog
import com.casco.kettle.storage.sessionFile
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
import java.util.concurrent.atomic.AtomicLong

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
     * native is the only writer of an edited session, as its runner is of new ones (#308). Throws
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
 * Where the app keeps its files: `exercise-tracker` under [filesDir], the directory the RN app chose,
 * kept so every install's data stays where it is. session-check reads the same paths.
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

/** [StoredFiles] where [StoragePaths] puts them. */
class DiskFiles(
    private val paths: StoragePaths,
) : StoredFiles {
    override fun sessionLog() = readSessionLog(paths.sessions)

    override fun library() = readLibrary(paths.library)

    override fun preferences() = readPreferences(paths.preferences)

    override fun deleteSession(id: String) {
        // Named as the session writer names it, and refused as it refuses an id outside the log.
        val file = sessionFile(paths.sessions, id)
        // File.delete() says only no; the name is what the reader can act on.
        if (file.exists() && !file.delete()) throw IOException(file.name)
    }

    override fun writeSession(session: Session) = writeSessionFile(paths.sessions, session)

    override fun writePreferences(preferences: Preferences) =
        writeAtomically(paths.preferences, serializePreferences(preferences))

    override fun writeLibrary(library: Library) = writeLibraryFile(paths.library, library)
}

/**
 * The session log, the library and the preferences as native screens show them.
 *
 * Native writes every file since the flip release (#308): its runner writes new sessions, its session
 * editor edits finished ones ([writeSession], #240), native Settings writes the preferences
 * ([writePreferences], #238), and native writes the library ([writeLibrary]), the runner's adopt
 * included. Each write is put into what's shown once it has landed, with no read of the folder (#324):
 * a screen's own here, and the runner's service's and its adopt's as the shell hears of them
 * ([sessionWritten], [libraryWritten]). The files are read in full ([reread]) when the app comes back
 * into view (`MainActivity`), for whatever changed behind it: a restore, `adb run-as`, and on iOS the
 * Files app.
 *
 * Every write is stamped ([StampedWrites]), and a write is only ever shown over an older one: the
 * runner's arrive by a post after its lock is let go, so a screen's write can land on the disk after
 * one of them and be shown before it. A read that lands applies again the session writes shown while
 * it ran, which it may have listed the files before, rather than being made again for them.
 */
class LatestFiles(
    private val files: StoredFiles,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /**
     * The log and the preferences each time either changes, by a read or a write, on [scope]'s thread:
     * the rest-day reminder reconciles from it (#236).
     */
    private val onShown: (SessionLog, Preferences?) -> Unit = { _, _ -> },
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
     * How many full reads have landed. A read of unchanged files sets nothing, since the files compare
     * equal, so this is what a screen keys on when its answer also depends on the clock: Home's card,
     * which a turned day moves on (#235) once the app comes back into view.
     */
    var reads by mutableIntStateOf(0)
        private set

    private var reading: Job? = null

    private val writingPreferences = Mutex()

    /** The newest choice whose write hasn't landed, held over what a read finds on the disk. */
    private var unsaved: Preferences? = null

    /** The session writes the last read to land had already seen: none older is shown over it. */
    private var sessionsReadAt = 0L

    /** Each session file's newest write shown since that read, by file name. */
    private val sessionShownAt = mutableMapOf<String, Long>()

    /**
     * The session writes shown that the last read to land hadn't counted, oldest first: a read under way
     * may have taken the files before they landed, so each is applied again over what it found.
     */
    private val sessionsShownSince = mutableListOf<ShownSession>()

    /** How many preferences writes have landed, which a read compares to tell whether one raced it. */
    private var preferencesLanded = 0L

    /** The newest library write shown, by a read or by the write itself. */
    private var libraryShownAt = 0L

    /**
     * Deletes session [id], and the row goes as the file does. Throws [IOException] when the disk
     * refuses, and the log is left as it was.
     */
    suspend fun deleteSession(id: String) {
        val stamp = SessionWrites.write { withContext(io) { files.deleteSession(id) } }.stamp
        everywhere { showSession(sessionFileName(id), stamp) { it.withoutSession(id) } }
    }

    /**
     * Writes [session] over its file, and the edit shows as the file has it. Throws [IOException] when
     * the disk refuses, and the log is left as it was.
     */
    suspend fun writeSession(session: Session) {
        val written =
            SessionWrites.write {
                withContext(io) { files.writeSession(session) }
                session
            }
        everywhere { sessionWritten(written) }
    }

    /** A session the runner's service wrote, on [scope]'s thread, shown unless a newer write is. */
    fun sessionWritten(written: Stamped<Session>) =
        showSession(sessionFileName(written.value.id), written.stamp) { it.withSession(written.value) }

    /**
     * Writes [library] over the file, and shows it once the write lands, before this returns: an editor
     * writing again builds on the library from this write, not the one before it, which the workout
     * editor's Save once dropped a quick-added exercise by (#242). Throws [IOException] when the disk
     * refuses, and the library is left as it was.
     */
    suspend fun writeLibrary(library: Library) {
        val written =
            LibraryWrites.write {
                withContext(io) { files.writeLibrary(library) }
                library
            }
        everywhere { libraryWritten(written) }
    }

    /**
     * A library written outside a screen, the runner's adopt's, on [scope]'s thread. Shown unless a
     * newer write is: an editor saves the whole library it shows, so an older one shown last would be
     * written back over the newer file by the next save. A read under way is left to land: it keeps its
     * library only if it counted this write, and the log it carries is no older for it.
     */
    fun libraryWritten(written: Stamped<Library>) {
        if (written.stamp <= libraryShownAt) return
        libraryShownAt = written.stamp
        library = written.value
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
        preferencesLanded++
    }

    /**
     * Reads all three files again. A read still under way is dropped, so an older one can't land last.
     * Only the return to the app asks for this: a write never does, since a cancelled read still parses
     * the whole log to the end (its I/O never stops to check), and only its result is thrown away.
     */
    fun reread() {
        reading?.cancel()
        reading =
            scope.launch {
                prepared()
                val preferencesAt = preferencesLanded
                val read =
                    withContext(io) {
                        // Taken before the files are, so every write these count is in what's read.
                        val sessionsAt = SessionWrites.landed
                        val libraryAt = LibraryWrites.landed
                        Read(files.sessionLog(), files.library(), files.preferences(), sessionsAt, libraryAt)
                    }
                // The held choice for the reminder too, so the alarm agrees with the toggle on screen. A
                // write that landed while this read ran may not be in the file it took, and is the one shown.
                val shown =
                    unsaved ?: if (preferencesLanded == preferencesAt) read.preferences else preferences
                // Writes shown while this read ran, which it may have listed the files before, go back on.
                sessionsShownSince.removeAll { it.stamp <= read.sessionsAt }
                val log = sessionsShownSince.fold(read.log) { log, write -> write.change(log) }
                sessionLog = log
                sessionsReadAt = read.sessionsAt
                sessionShownAt.values.removeAll { it <= read.sessionsAt }
                this@LatestFiles.preferences = shown
                if (read.library != null && read.libraryAt >= libraryShownAt) {
                    this@LatestFiles.library = read.library
                    libraryShownAt = read.libraryAt
                }
                reads++
                onShown(log, shown)
            }
    }

    /**
     * [change] to the log, for a write to [file] stamped [stamp], unless what's shown is newer, and
     * kept for the next read to land to apply again ([sessionsShownSince]). Before the first read there
     * is no log to change, and that read applies it.
     */
    private fun showSession(
        file: String,
        stamp: Long,
        change: (SessionLog) -> SessionLog,
    ) {
        if (stamp <= sessionsReadAt || stamp <= (sessionShownAt[file] ?: 0L)) return
        sessionShownAt[file] = stamp
        sessionsShownSince += ShownSession(stamp, change)
        sessionLog?.let { log ->
            val next = change(log)
            sessionLog = next
            onShown(next, unsaved ?: preferences)
        }
    }

    /**
     * [show] here, and in the current activity's files if they aren't these. A screen's write runs on
     * past its screen, so one can land after its activity was recreated, and the new one's first read
     * may have listed the files before it did: unheard, it would show the old file until the next return
     * to the app, and an editor's next save would write the old library back over it.
     */
    private inline fun everywhere(show: LatestFiles.() -> Unit) {
        show()
        CurrentFiles.latest?.takeIf { it !== this }?.show()
    }

    /** A session write as shown: its stamp, and what it does to a log, which applies again unchanged. */
    private class ShownSession(
        val stamp: Long,
        val change: (SessionLog) -> SessionLog,
    )

    private class Read(
        val log: SessionLog,
        val library: Library?,
        val preferences: Preferences?,
        val sessionsAt: Long,
        val libraryAt: Long,
    )
}

/**
 * The current activity's [LatestFiles], on the main thread only: where a screen's write that outlived its
 * activity is shown too. `MainActivity` sets it, and clears it as it goes.
 */
object CurrentFiles {
    var latest: LatestFiles? = null
}

/** A write's [value] and its [stamp], the order it landed among its file's writes. */
data class Stamped<out T>(
    val value: T,
    val stamp: Long,
)

/**
 * One file's writes, taken one at a time under [lock] and stamped in the order they land, process-wide,
 * so a [LatestFiles] can tell a late-arriving write from a newer one ([Stamped]).
 */
open class StampedWrites {
    val lock = Mutex()

    private val landedCount = AtomicLong()

    /** How many writes have landed: a read begun after this sees every one of them. */
    val landed: Long get() = landedCount.get()

    /** [write] under [lock], stamped once it lands. One that throws lands nothing and takes no stamp. */
    suspend fun <T> write(write: suspend () -> T): Stamped<T> =
        lock.withLock {
            val value = write()
            Stamped(value, landedCount.incrementAndGet())
        }
}

/**
 * The process's one lock on writing `exercises.yaml`. The runner's adopt reads the file and writes it
 * back (`adoptTargetWeight`), so an editor's or Import's save landing between the two would be lost,
 * or would lose the adopt. Every library write takes it, one after another.
 */
object LibraryWrites : StampedWrites()

/**
 * The process's one lock on writing the session log. The runner's service writes the running session
 * from its own thread (#305) while History's Delete and the session editor's Save write from the
 * screens', so every session write takes it, one after another, as [LibraryWrites] does the library's.
 */
object SessionWrites : StampedWrites()

/** The shell's [LatestFiles], for a native screen to read. */
val LocalLatestFiles = staticCompositionLocalOf<LatestFiles> { error("No LatestFiles provided") }
