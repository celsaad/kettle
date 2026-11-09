package com.casco.kettle.shell.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The vector that stands in for a glyph: as big as the glyph's em was, at every text size. */
@RunWith(RobolectricTestRunner::class)
class TypeIconTest {
    @get:Rule
    val compose = createComposeRule()

    private var fontScale by mutableFloatStateOf(1f)

    private fun show() =
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale)) {
                Box(Modifier.testTag(BOX)) {
                    TypeIcon(R.drawable.icon_chevron, TextStyle(fontSize = 16.sp), Color.Gray)
                }
            }
        }

    /**
     * Through the same sp conversion a `Text` gets, so Android 14's nonlinear scaling too: 16sp is
     * 20.2dp at 1.3x and 28dp at 2x, read off the platform's tables (`FontScaleConverterFactory`), not
     * 16 times the scale. A fixed dp size would stay 16 at both.
     */
    @Test
    fun `it is one em of its style across, and grows with the reader's text size`() {
        show()
        for ((scale, size) in listOf(1f to 16.dp, 1.3f to 20.2.dp, 2f to 28.dp)) {
            fontScale = scale
            compose.onNodeWithTag(BOX).assertWidthIsEqualTo(size).assertHeightIsEqualTo(size)
        }
    }

    @Test
    fun `it says nothing to accessibility, leaving the name to its control`() {
        show()
        compose
            .onNode(hasParent(hasTestTag(BOX)), useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    private companion object {
        const val BOX = "box"

        /** Enough pixels per dp that 1.3x of 16dp isn't rounded to a whole dp. */
        const val DENSITY = 4f
    }
}
