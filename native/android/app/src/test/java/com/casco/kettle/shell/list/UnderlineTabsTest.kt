package com.casco.kettle.shell.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [UnderlineTabs] at the reader's text size (#480): equal thirds while the labels fit, and at 200% a row
 * that scrolls at the labels' own widths, since equal thirds broke "Exercícios" mid-word. Layout itself
 * is the device's to check; what these pin is the decision and that the selected tab stays in view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
// Native graphics: legacy mode measures text far narrower than a device, so nothing overflows in it.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnderlineTabsTest {
    @get:Rule
    val compose = createComposeRule()

    private var selected by mutableStateOf("Exercícios")

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                KettleTheme(darkTheme = false) {
                    UnderlineTabs(listOf("Treinos", "Programas", "Exercícios").map { it to it }, selected) {
                        selected = it
                    }
                }
            }
        }
    }

    private val scrolls = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

    @Test
    fun `at the default size the tabs share the width and don't scroll`() {
        show(fontScale = 1f)
        compose.onNode(scrolls).assertDoesNotExist()
    }

    // The last tab selected is the one a fixed row would have pushed off the edge.
    @Test
    fun `at double text size the tabs scroll, keeping the selected one in view`() {
        show(fontScale = 2f)
        compose.onNode(scrolls).assertExists()
        // Wholly in view, not only partly: a row left at its start shows the last tab cut off at the edge.
        val tab = compose.onNodeWithText("Exercícios").getUnclippedBoundsInRoot()
        val row = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(tab.left >= row.left && tab.right <= row.right)
    }
}
