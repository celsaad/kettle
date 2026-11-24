package com.casco.kettle.shell.files

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.withSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executor

/** What native screens show of the log, the library and the preferences, and when it is read again. */
class LatestFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val disk by lazy { CountingFiles(DiskFiles(StoragePaths(folder.root))) }

    private val shown = mutableListOf<List<String>>()

    // Unconfined, so a read lands before the call that asked for it returns. Lazy, since the folder is
    // only made once the test starts.
    private val files by lazy {
        LatestFiles(
            disk,
            CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
            onShown = { log, _ -> shown += log.sessions.map { it.id } },
        )
    }

    private fun writeSession(id: String) =
        folder.root.resolve("exercise-tracker/sessions").apply { mkdirs() }.resolve("$id.yaml").writeText(
            "version: 1\nid: $id\nworkout: w\nstarted_at: 2026-10-27T08:00:00.000Z\n" +
                "ended_at: 2026-10-27T08:30:00.000Z\nentries: []\n",
        )

    private fun ids() = files.sessionLog?.sessions?.map { it.id }

    private fun writeWorkout(name: String) =
        folder.root.resolve("exercise-tracker/exercises.yaml").apply { parentFile?.mkdirs() }.writeText(
            "version: 1\nexercises: []\nworkouts: [{ id: w, name: $name, blocks: [] }]\nprograms: []\n",
        )

    private fun names() = files.library?.workouts?.map { it.name }

    private fun session(
        id: String,
        workout: String = "w",
    ) = Session(1, id, workout, null, null, null, "2026-10-27T08:00:00.000Z", null, emptyList())

    /** The runner's service's write as it lands: on the disk, under the lock, and stamped. */
    private fun runnerWrites(session: Session): Stamped<Session> =
        runBlocking {
            SessionWrites.write {
                disk.writeSession(session)
                session
            }
        }

    /** What a fresh read of the folder says, past the counter. */
    private fun onDisk() = DiskFiles(StoragePaths(folder.root)).sessionLog()

    /**
     * Each set the runner logs writes the session (#297), and the screens show it without listing and
     * parsing the whole folder again for it (#324); the rest-day reminder hears it too (#236).
     */
    @Test
    fun `a session the runner writes shows, and is handed on, with no read of the log`() {
        writeSession("earlier")
        files.reread()
        val reads = disk.sessionLogReads

        files.sessionWritten(runnerWrites(session("running")))
        files.sessionWritten(runnerWrites(session("running", workout = "renamed")))

        assertEquals(reads, disk.sessionLogReads)
        assertEquals(onDisk(), files.sessionLog)
        assertEquals(listOf("earlier", "running"), shown.last())
        assertEquals(1, files.reads)
    }

    /**
     * The runner's write arrives by a post after its lock is let go, so a screen's write to the same file
     * can land after it on the disk and be shown before it. The older one, arriving late, is not shown.
     */
    @Test
    fun `a runner write arriving after a newer write to the same session is not shown`() {
        files.reread()
        val late = runnerWrites(session("s", workout = "runner's"))

        runBlocking { files.writeSession(session("s", workout = "editor's")) }
        files.sessionWritten(late)

        assertEquals(listOf("editor's"), files.sessionLog?.sessions?.map { it.workout })
        assertEquals(onDisk(), files.sessionLog)
    }

    @Test
    fun `a runner write arriving after a read that already had a newer one is not shown`() {
        val late = runnerWrites(session("s", workout = "first"))
        files.sessionWritten(runnerWrites(session("s", workout = "second")))
        files.reread()

        files.sessionWritten(late)

        assertEquals(listOf("second"), files.sessionLog?.sessions?.map { it.workout })
    }

    /** Writes to different sessions don't stand in each other's way, whatever order they're heard in. */
    @Test
    fun `a late runner write to another session is still shown`() {
        files.reread()
        val late = runnerWrites(session("running"))

        runBlocking { files.writeSession(session("edited")) }
        files.sessionWritten(late)

        assertEquals(onDisk(), files.sessionLog)
    }

    /** The cold start's read hasn't landed: there's no log to put a write into, and that read finds it. */
    @Test
    fun `a write before the first read lands shows nothing, and the read finds it`() {
        files.sessionWritten(runnerWrites(session("running")))
        assertNull(files.sessionLog)

        files.reread()

        assertEquals(listOf("running"), ids())
    }

    /** The return to the app reads everything again, for a file changed behind its back. */
    @Test
    fun `a full read finds a session file put in the folder behind the app's back`() {
        files.reread()

        writeSession("restored")
        files.reread()

        assertEquals(listOf("restored"), ids())
        assertEquals(2, files.reads)
    }

    private fun writeUnits(units: String) =
        folder.root.resolve("exercise-tracker/preferences.json").apply { parentFile?.mkdirs() }.writeText(
            """{"unitSystem":"$units"}""",
        )

    /** A weight on a native screen follows what the file last said, whoever wrote it. */
    @Test
    fun `the unit system in the preferences file is read again with the log`() {
        writeUnits("metric")
        files.reread()

        writeUnits("imperial")
        files.reread()

        assertEquals(UnitSystem.IMPERIAL, files.preferences?.unitSystem)
    }

    /** The rest-day reminder reconciles from every read (#236), so a session the runner logs moves it. */
    @Test
    fun `every read is handed on, with the log and the preferences it read`() {
        val read = mutableListOf<Pair<List<String>, UnitSystem?>>()
        val files =
            LatestFiles(
                DiskFiles(StoragePaths(folder.root)),
                CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
                onShown = { log, preferences -> read += log.sessions.map { it.id } to preferences?.unitSystem },
            )
        writeUnits("metric")
        files.reread()

        writeSession("logged")
        files.reread()

        assertEquals(listOf(emptyList<String>() to UnitSystem.METRIC, listOf("logged") to UnitSystem.METRIC), read)
    }

    /** RN heals an unreadable library on its next load, and every name on screen meanwhile is worth keeping. */
    @Test
    fun `a library that can't be read keeps the last one that could`() {
        writeWorkout("Push")
        files.reread()

        folder.root.resolve("exercise-tracker/exercises.yaml").writeText("version: 1\nexercises: [\n")
        files.reread()

        assertEquals(listOf("Push"), names())
    }

    /**
     * A first launch: the shell's read is asked for before the seed is written, as it will be once RN
     * starts lazily (#223) and nothing in `onCreate` waits any more. It waits for the seed instead of
     * landing with no library and leaving Home empty until the next read.
     */
    @Test
    fun `a read waits for the launch's seed, and then finds it`() {
        val seeded = CompletableDeferred<Unit>()
        val files =
            LatestFiles(
                DiskFiles(StoragePaths(folder.root)),
                CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
                prepared = { seeded.await() },
            )

        files.reread()
        assertNull(files.sessionLog)

        writeWorkout("Seeded")
        seeded.complete(Unit)

        assertEquals(listOf("Seeded"), files.library?.workouts?.map { it.name })
    }

    /** History must not show "no sessions yet" for the moment before it has looked. */
    @Test
    fun `there is no log until the first read lands, and then an empty folder is an empty one`() {
        assertNull(files.sessionLog)

        files.reread()

        assertEquals(SessionLog.Empty, files.sessionLog)
    }

    /** History's Delete (#202), and the list that shows it, with no read of the log (#324). */
    @Test
    fun `deleting a session removes its file and drops it from the log`() {
        writeSession("kept")
        writeSession("deleted")
        files.reread()
        val reads = disk.sessionLogReads

        runBlocking { files.deleteSession("deleted") }

        assertEquals(listOf("kept"), ids())
        assertFalse(folder.root.resolve("exercise-tracker/sessions/deleted.yaml").exists())
        assertEquals(reads, disk.sessionLogReads)
        assertEquals(listOf("kept"), shown.last())
    }

    // As RN's: a file already gone is what the caller wanted, so it's no failure.
    @Test
    fun `deleting a session that is already gone is no failure`() {
        files.reread()

        runBlocking { files.deleteSession("never-written") }

        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun `a delete the disk refuses throws, and keeps the session`() {
        writeSession("stuck")
        // A directory where the file is: File.delete() refuses a non-empty one, as it would a locked file.
        val sessions = folder.root.resolve("exercise-tracker/sessions")
        sessions.resolve("stuck.yaml").delete()
        sessions
            .resolve("stuck.yaml")
            .apply { mkdirs() }
            .resolve("inside")
            .writeText("")
        files.reread()

        val before = files.sessionLog
        val heard = shown.size

        assertThrows(IOException::class.java) { runBlocking { files.deleteSession("stuck") } }
        assertTrue(sessions.resolve("stuck.yaml").exists())
        assertSame(before, files.sessionLog)
        assertEquals(heard, shown.size)
    }

    @Test
    fun `a session write the disk refuses throws, and leaves the log as it was`() {
        writeSession("kept")
        files.reread()
        val before = files.sessionLog
        val heard = shown.size

        // An id naming a file outside the log, which the writer refuses before touching the disk.
        assertThrows(IOException::class.java) { runBlocking { files.writeSession(session("../exercises")) } }

        assertSame(before, files.sessionLog)
        assertEquals(heard, shown.size)
    }

    @Test
    fun `refuses to delete by an id that names a file outside the log`() {
        val library =
            folder.root.resolve("exercise-tracker/exercises.yaml").apply {
                parentFile?.mkdirs()
                writeText("library")
            }
        files.reread()

        assertThrows(IOException::class.java) { runBlocking { files.deleteSession("../exercises") } }
        assertEquals("library", library.readText())
    }

    /** The session editor's Save (#239): the file rewritten where RN keeps it, and the log that shows it. */
    @Test
    fun `writing a session replaces its file and the log shows the edit`() {
        writeSession("edited")
        files.reread()
        val session = files.sessionLog!!.sessions.single()
        val reads = disk.sessionLogReads

        runBlocking { files.writeSession(session.copy(workout = "renamed")) }

        assertEquals(listOf("renamed"), files.sessionLog?.sessions?.map { it.workout })
        assertEquals(onDisk(), files.sessionLog)
        assertEquals(reads, disk.sessionLogReads)
        assertEquals(
            listOf("edited.yaml"),
            folder.root
                .resolve("exercise-tracker/sessions")
                .list()
                ?.toList(),
        )
    }

    /** Native Settings' write (#237): the whole file where RN reads it, and shown before anything rereads. */
    @Test
    fun `writing preferences replaces the whole file and shows them at once`() {
        val chosen = Preferences(UnitSystem.IMPERIAL, ThemePreference.DARK, true, false, "content://tree/a")

        runBlocking { files.writePreferences(chosen) }

        assertEquals(chosen, files.preferences)
        assertEquals(chosen, DiskFiles(StoragePaths(folder.root)).preferences())
        assertEquals(
            listOf("preferences.json"),
            folder.root
                .resolve("exercise-tracker")
                .list()
                ?.toList(),
        )
    }

    /**
     * As RN's store did: a write the disk refused still holds for the run, so the next read of the old
     * file mustn't put the old setting back, and the next launch is what loses it.
     */
    @Test
    fun `a refused preferences write holds the choice for the run`() {
        // A directory where the file goes: the rename onto it fails, and reading it reads as none.
        folder.root.resolve("exercise-tracker/preferences.json").mkdirs()
        val heard = mutableListOf<Preferences?>()
        val files =
            LatestFiles(
                DiskFiles(StoragePaths(folder.root)),
                CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
                onShown = { _, preferences -> heard += preferences },
            )
        files.reread()
        val chosen = Preferences(UnitSystem.IMPERIAL, ThemePreference.DARK, true, false, null)

        assertThrows(IOException::class.java) { runBlocking { files.writePreferences(chosen) } }
        files.reread()

        assertEquals(chosen, files.preferences)
        // And the reminder reconciles from the same choice the screen shows, or a tab tap cancels the
        // alarm the toggle just set.
        assertEquals(chosen, heard.last())
    }

    /**
     * A read under way carries the library and the log as well as the preferences, so a write that the
     * disk then refuses mustn't have cancelled it on the way: nothing would read them again.
     */
    @Test
    fun `a refused preferences write leaves a read under way to land`() {
        val main = Tasks()
        val io = Tasks()
        val disk = FakeFiles().apply { refusing = true }
        val scope = CoroutineScope(main.asCoroutineDispatcher())
        val files = LatestFiles(disk, scope, io.asCoroutineDispatcher())
        files.reread()
        main.run()
        disk.log = log("written behind the sheet")

        scope.launch {
            runCatching {
                files.writePreferences(
                    Preferences(UnitSystem.METRIC, ThemePreference.DARK, false, true, null),
                )
            }
        }
        main.run()
        io.run()
        main.run()

        assertEquals(log("written behind the sheet"), files.sessionLog)
    }

    /**
     * The library written shows as the write returns, or a second write built on it drops the first
     * (#242's quick-add, then Save), and nothing reads the files again for it (#324).
     */
    @Test
    fun `a library write shows at once, and reads nothing`() {
        val main = Tasks()
        val io = Tasks()
        val scope = CoroutineScope(main.asCoroutineDispatcher())
        val disk = FakeFiles()
        val files = LatestFiles(disk, scope, io.asCoroutineDispatcher())
        val written = Library(1, emptyList(), emptyList(), emptyList())

        scope.launch { files.writeLibrary(written) }
        main.run()
        io.run()
        main.run()

        assertEquals(written, files.library)
        assertEquals(0, disk.reads)
    }

    /**
     * The runner's adopt writes the library from its own thread and posts it over (#220): an editor's
     * save landing after it on the disk but shown first must not be undone, since the next save writes
     * back whatever library is shown.
     */
    @Test
    fun `an adopt arriving after a newer library write is not shown`() {
        val files = LatestFiles(FakeFiles(), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        val adopted = Library(1, emptyList(), emptyList(), emptyList())
        val saved = Library(2, emptyList(), emptyList(), emptyList())
        val late = runBlocking { LibraryWrites.write { adopted } }

        runBlocking { files.writeLibrary(saved) }
        files.libraryWritten(late)

        assertEquals(saved, files.library)
    }

    @Test
    fun `an adopt shows the library it wrote, with no read`() {
        val disk = FakeFiles()
        val files = LatestFiles(disk, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        files.reread()
        val adopted = Library(1, emptyList(), emptyList(), emptyList())

        files.libraryWritten(runBlocking { LibraryWrites.write { adopted } })

        assertEquals(adopted, files.library)
        assertEquals(1, disk.reads)
    }

    /**
     * The return to the app reads the folder while a set is being logged: the read listed the files
     * before the set's write landed, and must not land after it and put the old file back.
     */
    @Test
    fun `a write shown while a read is under way has that read made again`() {
        val main = Tasks()
        val io = Tasks()
        val disk = FakeFiles()
        val scope = CoroutineScope(main.asCoroutineDispatcher())
        val files = LatestFiles(disk, scope, io.asCoroutineDispatcher())
        files.reread()
        main.run()
        io.run()
        main.run()

        files.reread()
        main.run()
        scope.launch { files.writeSession(session("logged")) }
        main.run()
        // The read lists the folder first, then the write lands; the write is shown first.
        io.run()
        main.runLast()
        main.run()
        io.run()
        main.run()

        assertEquals(listOf("logged"), files.sessionLog?.sessions?.map { it.id })
        assertEquals(3, disk.reads)
    }

    @Test
    fun `a read that finishes after a newer one is dropped`() {
        val main = Tasks()
        val io = Tasks()
        val disk = FakeFiles()
        val files = LatestFiles(disk, CoroutineScope(main.asCoroutineDispatcher()), io.asCoroutineDispatcher())
        files.reread()
        main.run()
        files.reread()
        main.run()

        // The newer read is quicker: it lands first, and the older one after it.
        disk.log = log("newer")
        io.runLast()
        main.run()
        disk.log = log("older")
        io.run()
        main.run()

        assertEquals(log("newer"), files.sessionLog)
    }

    private fun log(error: String) = SessionLog(emptyList(), listOf(error))

    private class FakeFiles : FakeStoredFiles() {
        var log = SessionLog.Empty

        var reads = 0

        override fun sessionLog(): SessionLog {
            reads++
            return log
        }

        override fun deleteSession(id: String) = Unit

        override fun writeSession(session: Session) {
            log = log.withSession(session)
        }

        /** A full disk or a revoked folder. */
        var refusing = false

        override fun writeLibrary(library: Library) = Unit

        override fun writePreferences(preferences: Preferences) {
            if (refusing) throw IOException("disk full")
        }
    }

    /** The real files, counting each read of the log. */
    private class CountingFiles(
        private val files: StoredFiles,
    ) : StoredFiles by files {
        var sessionLogReads = 0

        override fun sessionLog(): SessionLog {
            sessionLogReads++
            return files.sessionLog()
        }
    }

    /** A dispatcher that runs nothing until told to, so a test chooses the order reads finish in. */
    private class Tasks : Executor {
        private val queued = ArrayDeque<Runnable>()

        override fun execute(task: Runnable) {
            queued += task
        }

        fun run() {
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }

        fun runLast() = queued.removeLast().run()
    }
}
