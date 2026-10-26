package com.casco.kettle.shell.nav

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell around the RN surface, laid out as the app lays it out. The surface is a stand-in: what it
 * draws is RN's, and whether it shows at all is what these tests hold.
 */
@RunWith(RobolectricTestRunner::class)
class ShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<String>()

    private lateinit var surface: View

    private fun show(routes: Map<Tab, Screen> = shellRoutes): TabNavigator {
        val navigator = TabNavigator(routes, sent::add)
        compose.runOnUiThread {
            val activity = compose.activity
            surface = View(activity)
            val shell = ShellLayout(activity, surface, navigator, routes) { KettleTheme(darkTheme = false, it) }
            activity.setContentView(shell)
        }
        compose.waitForIdle()
        return navigator
    }

    private fun assertSurfaceShown(shown: Boolean) = compose.runOnIdle { assertEquals(shown, surface.isShown) }

    /** A tab by its name, which is its label while selected and its icon's description otherwise. */
    private fun tab(label: String) = compose.onNode(hasText(label) or hasContentDescription(label))

    @Test
    fun `the bar opens on Workouts, with every tab a selectable Tab`() {
        show()

        tab("Workouts").assertIsSelected()
        for (label in listOf("Library", "History", "Programs")) {
            tab(label)
                .assertIsNotSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        assertSurfaceShown(true)
    }

    /**
     * Inside Compose, in an AndroidView, RN's whole tree was missing from the accessibility tree on a
     * device: TalkBack and a uiautomator dump saw an empty screen. As a plain view it's all there.
     */
    @Test
    fun `the surface is a view of its own, never inside Compose`() {
        show()

        val ancestors = generateSequence(surface.parent) { it.parent }
        assertEquals(false, ancestors.any { it is AbstractComposeView })
    }

    @Test
    fun `a tap selects the tab and steers RN to it`() {
        show()

        tab("History").performClick()

        tab("History").assertIsSelected()
        tab("Workouts").assertIsNotSelected()
        assertEquals(listOf("/history"), sent)
    }

    /**
     * Only the selected tab shows its label, as on RN's bar. The others have nothing on screen to name
     * them, so their icon does, and only theirs: the selected one is named by its label already.
     *
     * The hidden labels are still composed, at alpha 0, which is why this can't assert them absent.
     * TalkBack never sees them: Compose leaves transparent nodes out of the accessibility tree, and a
     * device's uiautomator dump has no text for an unselected tab at all.
     */
    @Test
    fun `an unselected tab is named by its icon, and the selected one by its label alone`() {
        show()
        compose.mainClock.advanceTimeBy(ANIMATIONS_SETTLED)

        compose.onNodeWithText("Workouts").assertExists()
        compose.onNodeWithContentDescription("Workouts").assertDoesNotExist()
        for (label in listOf("Library", "History", "Programs")) {
            compose.onNodeWithContentDescription(label).assertExists()
        }
    }

    @Test
    fun `every tab is at least 48dp each way`() {
        show()

        for (label in listOf("Workouts", "Library", "History", "Programs")) {
            tab(label).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    /**
     * Back goes to RN first, so it can close what it has above the tabs, and the tabs' back reaches the
     * navigator only after that. A back stack the shell popped itself would take the press first, and
     * the bar wouldn't move (seen on a device).
     */
    @Test
    fun `the shell never takes back for itself, however many tabs it has been through`() {
        val navigator = show()

        for (tab in listOf(Tab.History, Tab.Library, Tab.Programs, Tab.Workouts, Tab.History)) {
            compose.runOnIdle { navigator.onRoute(tab.path) }
            compose.waitForIdle()
        }
        tab("Programs").performClick()
        compose.waitForIdle()

        assertEquals(false, compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `the bar leaves while RN shows something above the tabs`() {
        val navigator = show()

        compose.runOnIdle { navigator.onRoute("/settings") }

        tab("Workouts").assertDoesNotExist()
        assertSurfaceShown(true)
    }

    /** The M3 landmark: a route goes native by changing its registration, and nothing else. */
    @Test
    fun `a tab registered native shows its screen in place of the surface`() {
        show(shellRoutes + (Tab.History to Screen.Native { Text("Native history") }))

        tab("History").performClick()

        compose.onNodeWithText("Native history").assertExists()
        assertSurfaceShown(false)
        assertEquals(emptyList<String>(), sent)

        tab("Library").performClick()

        compose.onNodeWithText("Native history").assertDoesNotExist()
        assertSurfaceShown(true)
    }

    /** RN presents its modals inside the surface, so one opened from a native tab needs it showing. */
    @Test
    fun `the surface shows over a native tab while RN has something above the tabs`() {
        val navigator = show(shellRoutes + (Tab.History to Screen.Native { Text("Native history") }))
        tab("History").performClick()

        compose.runOnIdle { navigator.onRoute("/settings") }

        assertSurfaceShown(true)

        compose.runOnIdle { navigator.onRoute("/history") }

        assertSurfaceShown(false)
        compose.onNodeWithText("Native history").assertExists()
    }

    @Test
    @Config(qualifiers = "pt")
    fun `the tabs are labelled in Portuguese`() {
        show()
        for (label in listOf("Treinos", "Biblioteca", "Histórico", "Programas")) tab(label).assertExists()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `the tabs are labelled in Japanese`() {
        show()
        for (label in listOf("ワークアウト", "ライブラリ", "履歴", "プログラム")) tab(label).assertExists()
    }

    private companion object {
        const val ANIMATIONS_SETTLED = 1_000L
    }
}
