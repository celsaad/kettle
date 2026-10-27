package com.casco.kettle.shell.files

import com.casco.kettle.shell.nav.Screen
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.TabNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** RN writes the log and the library until M5, so native reads them again whenever RN hands back the tabs. */
class LatestFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    // Unconfined, so a read lands before the call that asked for it returns. Lazy, since the folder is
    // only made once the test starts.
    private val files by lazy {
        LatestFiles(DiskFiles(folder.root), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)
    }

    private val navigator by lazy {
        TabNavigator(Tab.entries.associateWith { Screen.Rn }, navigate = {}, reread = files::reread)
    }

    private fun writeSession(id: String) =
        folder.root.resolve("sessions").apply { mkdirs() }.resolve("$id.yaml").writeText(
            "version: 1\nid: $id\nworkout: w\nstarted_at: 2026-10-27T08:00:00.000Z\n" +
                "ended_at: 2026-10-27T08:30:00.000Z\nentries: []\n",
        )

    private fun ids() = files.sessionLog.sessions.map { it.id }

    private fun writeWorkout(name: String) =
        folder.root.resolve("exercises.yaml").writeText(
            "version: 1\nexercises: []\nworkouts: [{ id: w, name: $name, blocks: [] }]\nprograms: []\n",
        )

    private fun names() = files.library?.workouts?.map { it.name }

    @Test
    fun `a session RN writes while it is above the tabs shows once the tabs come back`() {
        files.reread()
        navigator.onRoute("/session")

        writeSession("finished")

        assertEquals(emptyList<String>(), ids())

        navigator.onRoute("/history")

        assertEquals(listOf("finished"), ids())
    }

    /** The names a log-reading screen shows come from it, and RN's editors write it until M5. */
    @Test
    fun `the library RN edits above the tabs is read again with the log`() {
        writeWorkout("Push")
        files.reread()
        navigator.onRoute("/edit-workout")

        writeWorkout("Push day")
        navigator.onRoute("/")

        assertEquals(listOf("Push day"), names())
    }

    /** RN heals an unreadable library on its next load, and every name on screen meanwhile is worth keeping. */
    @Test
    fun `a library that can't be read keeps the last one that could`() {
        writeWorkout("Push")
        files.reread()

        folder.root.resolve("exercises.yaml").writeText("version: 1\nexercises: [\n")
        files.reread()

        assertEquals(listOf("Push"), names())
    }
}
