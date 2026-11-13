package com.casco.kettle.shell.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The labels' fit (#265). Robolectric measures no real glyph widths, so which size a label settles at is
 * a device check; what this holds is the mechanism: one line, whatever the scale, as jest read
 * `numberOfLines`.
 */
@RunWith(RobolectricTestRunner::class)
class TabBarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showAt(fontScale: Float) =
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                KettleTheme(darkTheme = false) { TabBar(current = Tab.Today, onSelect = {}) }
            }
        }

    private fun layout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onNode(hasText(label), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private val labels = listOf("Today", "Library", "Progress")

    /** Wrapped, four labels in the bar's width broke mid-word at 2x in every locale. */
    @Test
    fun `every label is laid out on one line at a large font scale, selected or not`() {
        showAt(2f)

        for (label in labels) assertEquals(label, 1, layout(label).layoutInput.maxLines)
    }

    @Test
    fun `at 1x a label is on one line too, at its own size`() {
        showAt(1f)

        for (label in labels) assertEquals(label, 1, layout(label).layoutInput.maxLines)
    }
}
