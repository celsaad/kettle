package com.casco.kettle.shell.nav

import com.casco.kettle.shell.exerciseeditor.EXERCISE_EDITOR_PATH
import com.casco.kettle.shell.importer.IMPORT_PATH
import com.casco.kettle.shell.programeditor.PROGRAM_EDITOR_PATH
import com.casco.kettle.shell.sessioneditor.SESSION_EDITOR_PATH
import com.casco.kettle.shell.workouteditor.WORKOUT_EDITOR_PATH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the shell draws natively. Every writer screen is native in every build since the library flip
 * (#245), so one that fell back to RN on Play would be RN's second writer of its file again; the one
 * gate left is the native runner's (#302).
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

    /** M5's landmark, and Play's until the flip release (#308): RN's runner stays RN's, flipped or not. */
    @Test
    fun `the runner is RN's`() {
        assertEquals(Screen.Rn, routesFor(nativeRunner = false).screenAt(RUNNER_PATH))
        assertEquals(Screen.Rn, routesFor(nativeRunner = true).screenAt(RUNNER_PATH))
    }

    /** The flag is passed in rather than read from the build: the unit tests run on debug alone. */
    @Test
    fun `the native runner is registered with the flip on, as on dev`() {
        assertTrue(routesFor(nativeRunner = true).screenAt(NATIVE_RUNNER_PATH) is Screen.Native)
    }

    @Test
    fun `the native runner is unregistered with the flip off, as on Play`() {
        assertFalse(NATIVE_RUNNER_PATH in routesFor(nativeRunner = false))
    }

    @Test
    fun `the flip registers the native runner and nothing else`() {
        assertEquals(routesFor(nativeRunner = false), routesFor(nativeRunner = true) - NATIVE_RUNNER_PATH)
    }
}
