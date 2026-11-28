package com.casco.kettle.shell.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What covers a layer puts it out of reach (#377): the tab under a sheet, a pushed screen or the runner
 * stays drawn and composed, but takes no touch, and holds no focus nor lets focus move into it. Each
 * route draws a text field named after it, its focus recorded, over a box that counts taps.
 */
@RunWith(RobolectricTestRunner::class)
class ShellInertTest {
    @get:Rule
    val compose = shellRule()

    private lateinit var navigator: Navigator

    /** Each stand-in's field, whether it holds focus now. */
    private val focused = mutableMapOf<String, Boolean>()

    /** Taps that landed on each stand-in's screen. */
    private val taps = mutableMapOf<String, Int>()
    private lateinit var focusManager: FocusManager

    private fun show() {
        navigator = Navigator()
        compose.showShell(navigator) { route ->
            val name = standInName(route)
            focusManager = LocalFocusManager.current
            // Only a tab's screen counts taps: the layers over it take none of their own, so a tap that
            // reaches the tab got through whatever covers it.
            val counts = Tab.of(route.destination) != null
            Box(
                Modifier.fillMaxSize().then(
                    if (counts) Modifier.clickable { taps[name] = (taps[name] ?: 0) + 1 } else Modifier,
                ),
            ) {
                Column {
                    var text by remember { mutableStateOf("") }
                    BasicTextField(
                        text,
                        { text = it },
                        Modifier.testTag(name).onFocusChanged { focused[name] = it.isFocused },
                    )
                }
            }
        }
    }

    private fun go(move: Navigator.() -> Unit) {
        compose.runOnUiThread { navigator.move() }
        compose.waitForIdle()
    }

    private fun focusToday() {
        compose.onNodeWithTag(TODAY).performClick()
        compose.waitForIdle()
        assertEquals(true, focused[TODAY])
    }

    @Test
    fun `a focused tab field loses its focus to a pushed screen`() {
        show()
        focusToday()

        go { push(Route(Destination.Settings)) }

        assertEquals(false, focused[TODAY])
    }

    @Test
    fun `a focused tab field loses its focus to a sheet`() {
        show()
        focusToday()

        go { push(Route(Destination.Support)) }

        assertEquals(false, focused[TODAY])
    }

    @Test
    fun `a focused tab field loses its focus to the runner`() {
        show()
        focusToday()

        go { openRunner(RunnerStart.Planned(SessionParams.Workout("bench"))) }

        assertEquals(false, focused[TODAY])
    }

    /** Keyboard navigation from inside a sheet goes round inside it and never into the tab beneath. */
    @Test
    fun `moving focus on from inside a sheet stays inside it`() {
        show()
        go { push(Route(Destination.Support)) }
        compose.onNodeWithTag(SUPPORT).performClick()
        compose.waitForIdle()
        assertEquals(true, focused[SUPPORT])

        repeat(FOCUS_STEPS) {
            compose.runOnUiThread { focusManager.moveFocus(FocusDirection.Next) }
            compose.waitForIdle()
            assertEquals("step ${it + 1}", false, focused[TODAY] ?: false)
        }
    }

    // Touches.

    /** Where Progress sits in the bar, read while the bar can be reached. */
    private fun progressTab(): Rect =
        compose
            .onNode(hasText("Progress") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNode()
            .boundsInRoot

    private fun tapAt(point: Offset) {
        compose.onRoot().performTouchInput { click(point) }
        compose.waitForIdle()
    }

    /** A pushed screen's own empty space and the bar under it take no tap meant for what it covers. */
    @Test
    fun `taps on a pushed screen reach neither the tab nor the bar under it`() {
        show()
        val progress = progressTab()
        go { push(Route(Destination.Settings)) }

        tapAt(progress.center)
        val middle =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.center
        tapAt(middle)

        assertEquals(Tab.Today, navigator.tab)
        assertEquals(listOf(Route(Destination.Settings)), navigator.sheets)
        assertEquals(null, taps[TODAY])
    }

    /** The control: with nothing over it, the same taps do reach the tab and the bar. */
    @Test
    fun `the same taps reach the tab and the bar with nothing over them`() {
        show()
        val middle =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.center
        tapAt(middle)
        assertEquals(1, taps[TODAY])

        tapAt(progressTab().center)
        assertEquals(Tab.Progress, navigator.tab)
    }

    @Test
    fun `taps on the runner reach neither the tab nor the bar under it`() {
        show()
        val progress = progressTab()
        go { openRunner(RunnerStart.Planned(SessionParams.Workout("bench"))) }

        tapAt(progress.center)
        val middle =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.center
        tapAt(middle)

        assertEquals(Tab.Today, navigator.tab)
        assertEquals(null, taps[TODAY])
    }

    private companion object {
        const val TODAY = "Screen Today"
        const val SUPPORT = "Screen Support"
        const val FOCUS_STEPS = 4
    }
}
