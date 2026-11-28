package com.casco.kettle.shell.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sheet's own controls and how it may be dismissed, in the shell as the app draws it: a label from
 * the bundles, a 48dp target (#155), TalkBack's sheet actions on the grabber and its reading order, and
 * only the grabber's drag, the × and back close it. A scrim tap and a drag from the content would each
 * drop an edit, and the editors have no confirm.
 */
@RunWith(RobolectricTestRunner::class)
class SheetChromeTest {
    @get:Rule
    val compose = shellRule()

    private lateinit var navigator: Navigator
    private lateinit var shell: ShellView

    private val import = Route(Destination.Import)
    private val support = Route(Destination.Support)

    /** The shell with [stack] pushed, each screen a stand-in filling its sheet. */
    private fun show(vararg stack: Route) {
        navigator = Navigator()
        shell =
            compose.showShell(navigator) { route ->
                Box(Modifier.fillMaxSize().testTag(standInName(route))) { Text(standInName(route)) }
            }
        for (route in stack) {
            compose.runOnUiThread { navigator.push(route) }
            compose.waitForIdle()
        }
    }

    private fun close(label: String = "Close") = compose.onNodeWithContentDescription(label)

    private fun grabber(label: String = "Drag handle") = compose.onNodeWithContentDescription(label)

    private val stack get() = compose.runOnIdle { navigator.sheets }

    @Test
    fun `the close button is a labelled 48dp button that goes back`() {
        show(import, support)

        close()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.waitForIdle()

        assertEquals(listOf(import), stack)
    }

    /** The × is one em of 20sp, growing with the text size as the glyph it replaced did (#267). */
    @Test
    fun `the close icon is drawn one em of 20sp across`() {
        show(support)

        compose
            .onNodeWithTag(
                CLOSE_ICON_TAG,
                useUnmergedTree = true,
            ).assertWidthIsEqualTo(20.dp)
            .assertHeightIsEqualTo(20.dp)
    }

    @Test
    @Config(fontScale = 1.5f)
    fun `the close icon grows with the text size`() {
        show(support)

        val width =
            compose
                .onNodeWithTag(CLOSE_ICON_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .size.width
        // Past 20dp, at Robolectric's density of 1; how far is the platform's (nonlinear from Android 14).
        assertTrue("the × is ${width}px", width > 20)
    }

    @Test
    fun `the grabber is a labelled 48dp button that TalkBack can close the sheet from`() {
        show(import, support)

        grabber()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()

        assertEquals(listOf(import), stack)
    }

    /** As on Material's handle: TalkBack's double-tap is a click, and closes it. */
    @Test
    fun `a TalkBack double-tap on the grabber closes the sheet`() {
        show(support)

        grabber().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(emptyList<Route>(), stack)
    }

    // What dismisses, and what doesn't.

    /** A finger's tap does nothing, so a stray one can't drop an edit. */
    @Test
    fun `a tap on the grabber closes nothing`() {
        show(support)

        grabber().performTouchInput { click() }
        compose.waitForIdle()

        assertEquals(listOf(support), stack)
    }

    @Test
    fun `a tap on the scrim closes nothing`() {
        show(support)

        // In the 12dp band above the sheet, where the scrim shows over the tab.
        compose.onRoot().performTouchInput { click(Offset(centerX, 4.dp.toPx())) }
        compose.waitForIdle()

        assertEquals(listOf(support), stack)
    }

    @Test
    fun `a drag from the content closes nothing, and moves nothing`() {
        show(support)
        val top =
            compose
                .onNodeWithTag("Screen Support")
                .fetchSemanticsNode()
                .boundsInRoot.top

        compose.onNodeWithTag("Screen Support").performTouchInput { swipeDown(durationMillis = SLOW) }
        compose.waitForIdle()

        assertEquals(listOf(support), stack)
        assertEquals(
            top,
            compose
                .onNodeWithTag("Screen Support")
                .fetchSemanticsNode()
                .boundsInRoot.top,
        )
    }

    /** Past a quarter of the frame it goes back, once: a sheet over a sheet goes back to the one beneath. */
    @Test
    fun `a grabber drag past a quarter of the frame goes back once`() {
        show(import, support)

        grabber().performTouchInput { swipeDown(endY = centerY + shell.height / 2f, durationMillis = SLOW) }
        compose.waitForIdle()

        assertEquals(listOf(import), stack)
    }

    @Test
    fun `a short grabber drag settles the sheet back where it was`() {
        show(support)
        val top =
            compose
                .onNodeWithTag("Screen Support")
                .fetchSemanticsNode()
                .boundsInRoot.top

        grabber().performTouchInput { swipeDown(endY = centerY + shell.height / 10f, durationMillis = SLOW) }
        compose.waitForIdle()

        assertEquals(listOf(support), stack)
        assertEquals(
            top,
            compose
                .onNodeWithTag("Screen Support")
                .fetchSemanticsNode()
                .boundsInRoot.top,
        )
    }

    /** Short, but quick: a flick down closes it. */
    @Test
    fun `a grabber flung down goes back`() {
        show(support)

        grabber().performTouchInput { swipeDown(endY = centerY + shell.height / 10f, durationMillis = QUICK) }
        compose.waitForIdle()

        assertEquals(emptyList<Route>(), stack)
    }

    /** While the sheet follows the finger, its screen moves with it. */
    @Test
    fun `the sheet follows the grabber down`() {
        show(support)
        val top =
            compose
                .onNodeWithTag("Screen Support")
                .fetchSemanticsNode()
                .boundsInRoot.top

        grabber().performTouchInput {
            down(center)
            moveBy(Offset(0f, DRAG_PX))
            moveBy(Offset(0f, DRAG_PX))
        }
        compose.waitForIdle()

        val moved =
            compose
                .onNodeWithTag("Screen Support")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("the sheet moved ${moved - top}px", moved - top > DRAG_PX)
        grabber().performTouchInput { up() }
    }

    // TalkBack's order.

    /** The sheet's frame: the traversal group the × and grabber sort first in. */
    private fun SemanticsNodeInteraction.group(): SemanticsNode {
        var node = fetchSemanticsNode().parent
        while (node != null && node.config.getOrNull(SemanticsProperties.IsTraversalGroup) != true) node = node.parent
        return node ?: error("The × is in no traversal group")
    }

    private fun SemanticsNode.order() = config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f

    /**
     * × first, then the grabber, then the screen: the order #155 asks TalkBack to read them in. Inside the
     * sheet's traversal group, by traversal index, since by position the grabber would come first, left
     * of the ×, and either before a screen whose top reaches into the strip.
     */
    private fun assertChromeReadsFirst(
        closeLabel: String,
        grabberLabel: String,
    ) {
        show(support)
        val group = close(closeLabel).group()
        val children = group.children
        val x = children.single { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(closeLabel) }
        val handle =
            children.single {
                it.config.getOrNull(SemanticsProperties.ContentDescription) ==
                    listOf(grabberLabel)
            }
        val content = children.single { it.config.getOrNull(SemanticsProperties.IsTraversalGroup) == true }

        assertTrue("× before the grabber", x.order() < handle.order())
        assertTrue("the grabber before the screen", handle.order() < content.order())
        assertTrue("the screen is in its own group", content.children.isNotEmpty())
    }

    @Test
    fun `TalkBack reads the close button, then the grabber, then the sheet's screen`() =
        assertChromeReadsFirst("Close", "Drag handle")

    /** Not by an English assertion, which a hardcoded label would pass. */
    @Test
    @Config(qualifiers = "pt")
    fun `in Portuguese, both labels come from the bundles and read in the same order`() =
        assertChromeReadsFirst("Fechar", "Alça de arrastar")

    private companion object {
        /** Slow enough that its release is no fling: a third of the frame over a second and a half. */
        const val SLOW = 1_500L
        const val QUICK = 50L
        const val DRAG_PX = 40f
    }
}
