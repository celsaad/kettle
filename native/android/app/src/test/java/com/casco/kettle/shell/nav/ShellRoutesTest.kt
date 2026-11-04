package com.casco.kettle.shell.nav

import com.casco.kettle.shell.exerciseeditor.EXERCISE_EDITOR_PATH
import com.casco.kettle.shell.importer.IMPORT_PATH
import com.casco.kettle.shell.programeditor.PROGRAM_EDITOR_PATH
import com.casco.kettle.shell.sessioneditor.SESSION_EDITOR_PATH
import com.casco.kettle.shell.workouteditor.WORKOUT_EDITOR_PATH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the shell draws natively, in every build: since the library flip (#245) there is no gate left,
 * so a writer screen that fell back to RN on Play would be RN's second writer of its file again.
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

    /** M5's landmark: the session runner is the one RN screen left, until M6. */
    @Test
    fun `the runner is RN's`() {
        assertEquals(Screen.Rn, shellRoutes.screenAt("/session"))
    }
}
