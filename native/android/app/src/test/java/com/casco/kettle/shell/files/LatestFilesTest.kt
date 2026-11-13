package com.casco.kettle.shell.files

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.nav.Navigator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // Unconfined, so a read lands before the call that asked for it returns. Lazy, since the folder is
    // only made once the test starts.
    private val files by lazy {
        LatestFiles(
            DiskFiles(StoragePaths(folder.root)),
            CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )
    }

    private val navigator by lazy { Navigator(onRunnerClosed = files::reread) }

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

    /** #194's hand-back, now the runner's slot popping: what it wrote shows on the screen it covered. */
    @Test
    fun `a session written while the runner shows is read once its slot pops`() {
        files.reread()
        navigator.openRunner(RunnerStart.Adhoc)

        writeSession("finished")

        assertEquals(emptyList<String>(), ids())

        navigator.closeRunner()

        assertEquals(listOf("finished"), ids())
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
                onRead = { log, preferences -> read += log.sessions.map { it.id } to preferences?.unitSystem },
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

    /** History's Delete (#202): native's one write to the log in M4, and the list that shows it. */
    @Test
    fun `deleting a session removes its file and drops it from the log`() {
        writeSession("kept")
        writeSession("deleted")
        files.reread()

        runBlocking { files.deleteSession("deleted") }

        assertEquals(listOf("kept"), ids())
        assertFalse(folder.root.resolve("exercise-tracker/sessions/deleted.yaml").exists())
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

        assertThrows(IOException::class.java) { runBlocking { files.deleteSession("stuck") } }
        assertTrue(sessions.resolve("stuck.yaml").exists())
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

        runBlocking { files.writeSession(session.copy(workout = "renamed")) }

        assertEquals(listOf("renamed"), files.sessionLog?.sessions?.map { it.workout })
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
                onRead = { _, preferences -> heard += preferences },
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
     * The read after a write is launched, not awaited: until it lands, the library shown must already
     * be the one written, or a second write built on it drops the first (#242's quick-add, then Save).
     */
    @Test
    fun `a library write shows at once, before the read after it lands`() {
        val main = Tasks()
        val io = Tasks()
        val scope = CoroutineScope(main.asCoroutineDispatcher())
        val files = LatestFiles(FakeFiles(), scope, io.asCoroutineDispatcher())
        val written = Library(1, emptyList(), emptyList(), emptyList())

        scope.launch { files.writeLibrary(written) }
        main.run()
        io.run()
        main.run()

        assertEquals(written, files.library)
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

        override fun sessionLog() = log

        override fun deleteSession(id: String) = Unit

        override fun writeSession(session: Session) = Unit

        /** A full disk or a revoked folder. */
        var refusing = false

        override fun writeLibrary(library: Library) = Unit

        override fun writePreferences(preferences: Preferences) {
            if (refusing) throw IOException("disk full")
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
