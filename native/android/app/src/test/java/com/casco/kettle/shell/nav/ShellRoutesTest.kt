package com.casco.kettle.shell.nav

import com.casco.kettle.shell.exerciseeditor.EXERCISE_EDITOR_PATH
import com.casco.kettle.shell.importer.IMPORT_PATH
import com.casco.kettle.shell.programeditor.PROGRAM_EDITOR_PATH
import com.casco.kettle.shell.sessioneditor.SESSION_EDITOR_PATH
import com.casco.kettle.shell.workouteditor.WORKOUT_EDITOR_PATH
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the shell draws natively. Every writer screen is native in every build since the library flip
 * (#245), so one that fell back to RN on Play would be RN's second writer of its file again; the
 * runner, new sessions' writer, joined them in the flip release (#308).
 */
class ShellRoutesTest {
    @Test
    fun `every writer screen is native`() {
        val writers =
            listOf(
                EXERCISE_EDITOR_PATH,
                WORKOUT_EDITOR_PATH,
                PROGRAM_EDITOR_PATH,
                IMPORT_PATH,
                SESSION_EDITOR_PATH,
                "/settings",
            )
        for (path in writers) {
            assertTrue(path, shellRoutes.screenAt(path) is Screen.Native)
        }
    }

    @Test
    fun `the tabs are native`() {
        for (tab in Tab.entries) assertTrue(tab.path, shellRoutes.screenAt(tab.path) is Screen.Native)
    }

    /** M6's landmark (#308): no build opens RN's runner, because there is none left to open. */
    @Test
    fun `the runner is native`() {
        assertTrue(shellRoutes.screenAt(NATIVE_RUNNER_PATH) is Screen.Native)
    }
}
