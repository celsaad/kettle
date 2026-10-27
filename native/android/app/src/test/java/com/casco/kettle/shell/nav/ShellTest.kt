package com.casco.kettle.shell.nav

import android.annotation.SuppressLint
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

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
    private lateinit var shell: ShellLayout

    /** Every touch that reached the surface. */
    private var touches = 0

    private fun show(routes: Map<String, Screen> = shellRoutes): TabNavigator {
        val navigator = TabNavigator(routes, sent::add)
        compose.runOnUiThread {
            val activity = compose.activity
            surface = touchCountingView(activity)
            shell =
                ShellLayout(
                    activity,
                    surface,
                    navigator,
                    routes,
                    theme = { KettleTheme(darkTheme = false, it) },
                ) { content, shell -> ModalSheet(content, shell, surface, navigator, { LightColors }, {}) }
            activity.setContentView(shell)
        }
        compose.waitForIdle()
        return navigator
    }

    private fun assertSurfaceShown(shown: Boolean) = compose.runOnIdle { assertEquals(shown, surface.isShown) }

    @SuppressLint("ClickableViewAccessibility") // A stand-in for RN's surface, which takes raw touches.
    private fun touchCountingView(activity: ComponentActivity) =
        View(activity).apply {
            setOnTouchListener { _, _ ->
                touches++
                true
            }
        }

    private val withNativeSupport = shellRoutes + ("/support" to Screen.Native { Text("Native support") })

    /** RN's report of [pathname], through the sheet, as JsBridge hands it over; then its frames. */
    private fun report(
        pathname: String,
        modal: Boolean = true,
        params: Map<String, String> = emptyMap(),
        settle: Boolean = true,
    ) {
        compose.runOnUiThread { shell.sheet.onRoute(RouteReport(pathname, modal, params)) }
        // Frames by hand: Robolectric dispatches no pre-draw of its own here, and the sheet waits on them.
        repeat(FRAMES) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
            compose.runOnUiThread { shell.viewTreeObserver.dispatchOnPreDraw() }
            compose.waitForIdle()
        }
        if (!settle) return
        // The transition's animator, on the main looper rather than Compose's clock.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ARRIVED))
        compose.waitForIdle()
    }

    private fun closeButton(): View {
        val found = ArrayList<View>()
        shell.findViewsWithText(found, "Close", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        return found.single()
    }

    private fun View.parentView() = parent as View

    /** A tap in the middle of the sheet, sent to the shell as the window would. */
    private fun tapTheSheet() =
        compose.runOnIdle {
            val x = shell.width / 2f
            val y = shell.height / 2f
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 0, action, x, y, 0)
                shell.dispatchTouchEvent(event)
                event.recycle()
            }
        }

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

    /** The M3 landmark: a route goes native by its registration, and RN's stub route at the same path. */
    @Test
    fun `a tab registered native shows its screen in place of the surface`() {
        show(shellRoutes + ("/history" to Screen.Native { Text("Native history") }))

        tab("History").performClick()

        compose.onNodeWithText("Native history").assertExists()
        assertSurfaceShown(false)
        // RN goes to the stub route left at the same path, as it would to the RN screen (#190).
        assertEquals(listOf("/history"), sent)

        tab("Library").performClick()

        compose.onNodeWithText("Native history").assertDoesNotExist()
        assertSurfaceShown(true)
    }

    /** A native screen behind a stub reads its params from RN's report, whoever opened the route. */
    @Test
    fun `a native screen is given the params RN reported its route with`() {
        val navigator =
            show(shellRoutes + ("/history" to Screen.Native { params -> Text("Session ${params["id"]}") }))

        compose.runOnIdle { navigator.onRoute("/history", mapOf("id" to "2026-10-27-a")) }

        compose.onNodeWithText("Session 2026-10-27-a").assertExists()
    }

    /** RN presents its modals inside the surface, so one opened from a native tab needs it showing. */
    @Test
    fun `the surface shows over a native tab while RN has something above the tabs`() {
        val navigator = show(shellRoutes + ("/history" to Screen.Native { Text("Native history") }))
        tab("History").performClick()

        compose.runOnIdle { navigator.onRoute("/settings") }

        assertSurfaceShown(true)

        compose.runOnIdle { navigator.onRoute("/history") }

        assertSurfaceShown(false)
        compose.onNodeWithText("Native history").assertExists()
    }

    /**
     * RN shows only its stub at a native sheet's path (#197), so the stub goes invisible, as the surface
     * does under a native tab: covered but showing, it would still take touches and TalkBack's focus.
     */
    @Test
    fun `a native sheet shows its screen, and the stub under it is invisible`() {
        show(withNativeSupport)

        report("/support")

        compose.onNodeWithText("Native support").assertIsDisplayed()
        assertSurfaceShown(false)
        compose.runOnIdle { assertEquals(View.VISIBLE, shell.nativeSheet.visibility) }
    }

    /** The control first: without one, a test of touches never arriving passes on a broken dispatch. */
    @Test
    fun `a touch on a native sheet never reaches the stub, where one on an RN sheet does`() {
        show(withNativeSupport)
        report("/settings")

        tapTheSheet()

        compose.runOnIdle { assertEquals(A_TAP, touches) }

        report("/support")
        tapTheSheet()

        compose.runOnIdle { assertEquals(A_TAP, touches) }
    }

    /** Settings → Support and back, the hand-over M4's first port makes in both directions. */
    @Test
    fun `the sheet hands over from RN to native and back`() {
        show(withNativeSupport)
        report("/settings")
        assertSurfaceShown(true)

        report("/support")

        compose.onNodeWithText("Native support").assertIsDisplayed()
        assertSurfaceShown(false)

        report("/settings")

        compose.onNodeWithText("Native support").assertDoesNotExist()
        assertSurfaceShown(true)
        compose.runOnIdle { assertEquals(View.INVISIBLE, shell.nativeSheet.visibility) }
    }

    /**
     * What composed has to be what was reported, or the arrival waits out the backstop, the whole window
     * held. Within a few frames, well short of it, the transition has started and shown the chrome,
     * which is gone until one does.
     */
    @Test
    fun `a native sheet arrives within frames, not at the backstop`() {
        show(shellRoutes + ("/exercise-progress" to Screen.Native { params -> Text("Progress ${params["id"]}") }))

        report("/exercise-progress", params = mapOf("id" to "kb-swing"), settle = false)

        compose.runOnIdle { assertEquals(View.VISIBLE, closeButton().parentView().visibility) }
    }

    @Test
    fun `a native sheet is given the params RN reported its route with`() {
        show(shellRoutes + ("/exercise-progress" to Screen.Native { params -> Text("Progress ${params["id"]}") }))

        report("/exercise-progress", params = mapOf("id" to "kb-swing"))

        compose.onNodeWithText("Progress kb-swing").assertIsDisplayed()
    }

    /** Where an RN sheet's content starts, under RN's `ModalHeader` strip, which the chrome draws over. */
    @Test
    fun `a native sheet's content starts below the chrome's strip`() {
        show(withNativeSupport)

        report("/support")

        compose
            .onNodeWithText("Native support")
            .assertTopPositionInRootIsEqualTo(SheetStripHeight)
    }

    /** Closing it lands on the tab it opened over, with that tab's layer back. */
    @Test
    fun `closing a native sheet over a native tab shows the tab again`() {
        val routes =
            withNativeSupport + ("/history" to Screen.Native { Text("Native history") })
        show(routes)
        tab("History").performClick()
        report("/history", modal = false)

        report("/support")

        compose.onNodeWithText("Native support").assertIsDisplayed()
        compose.onNodeWithText("Native history").assertIsNotDisplayed()

        report("/history", modal = false)

        compose.onNodeWithText("Native history").assertIsDisplayed()
        compose.onNodeWithText("Native support").assertDoesNotExist()
        assertSurfaceShown(false)
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

        /** Past RnFrames' fallback, which is what lands a report without Fabric, and the transition. */
        const val ARRIVED = 1_000L
        const val FRAMES = 4

        /** Its down and its up. */
        const val A_TAP = 2
        const val FRAME_MS = 16L
    }
}
