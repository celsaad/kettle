package com.casco.kettle.shell.nav

import android.view.View
import android.view.View.MeasureSpec
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import com.casco.kettle.shell.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The sheet's own controls. They're native views over RN's content, so RN's a11y rules are theirs to
 * keep: a label from the bundles, a 48dp target (#155), and TalkBack's sheet actions on the grabber.
 */
@RunWith(RobolectricTestRunner::class)
class SheetChromeTest {
    private var backs = 0

    private fun chrome() =
        SheetChrome(RuntimeEnvironment.getApplication(), { backs++ }, {}, { _, _ -> }).apply {
            paint(LightColors)
            place(WIDTH_PX)
            measure(
                MeasureSpec.makeMeasureSpec(WIDTH_PX, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(stripHeight, MeasureSpec.EXACTLY),
            )
            layout(0, 0, WIDTH_PX, stripHeight)
        }

    private fun SheetChrome.labelled(label: String): View =
        (0 until childCount).map(::getChildAt).single { it.contentDescription == label }

    private fun View.node() = createAccessibilityNodeInfo()

    @Test
    fun `the close button is a labelled 48dp button that goes back`() {
        val close = chrome().labelled("Close")

        assertEquals(Button::class.java.name, close.node().className)
        assertTrue(close.width >= TARGET_PX && close.height >= TARGET_PX)
        close.performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the grabber is a labelled 48dp target that TalkBack can close the sheet from`() {
        val grabber = chrome().labelled("Drag handle")

        assertTrue(grabber.width >= TARGET_PX && grabber.height >= TARGET_PX)
        assertEquals(Button::class.java.name, grabber.node().className)
        val actions = grabber.node().actionList.map { it.id }
        assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id in actions)
        grabber.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id, null)
        assertEquals(1, backs)
    }

    /** As on Material's handle. A finger's tap does nothing, so a stray one can't drop an edit. */
    @Test
    fun `a TalkBack double-tap on the grabber closes the sheet`() {
        val grabber = chrome().labelled("Drag handle")

        grabber.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id, null)

        assertEquals(1, backs)
    }

    /** Not by an English assertion, which a hardcoded label would pass. */
    @Test
    @Config(qualifiers = "pt")
    fun `both labels come from the bundles`() {
        val chrome = chrome()

        chrome.labelled("Fechar")
        chrome.labelled("Alça de arrastar")
    }

    @Test
    fun `TalkBack reads the close button before the grabber`() {
        val chrome = chrome()

        assertEquals(chrome.labelled("Drag handle").id, chrome.labelled("Close").accessibilityTraversalBefore)
    }

    private companion object {
        const val WIDTH_PX = 1080

        /** Robolectric's default density is 1, so 48dp is 48px. */
        const val TARGET_PX = 48
    }
}
