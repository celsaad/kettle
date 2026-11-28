package com.casco.kettle.shell.importer

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.DiffValue
import com.casco.kettle.domain.UnitSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Import's diff rows, in `ja`, the one bundle whose list separator isn't ", ": pt's would match a
 * separator hardcoded in English.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ja")
class DiffTextTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `joins equipment items with the locale's separator, known ones by name`() {
        val items = DiffValue.Items(listOf("Barbell", "bench", "fat grips"))
        compose.setContent { Text(valueText(items, UnitSystem.METRIC)) }
        compose.onNodeWithText("バーベル、ベンチ、fat grips").assertExists()
    }

    // An unbounded value was measured first and left the label no width, one letter a line.
    @Test
    fun `keeps the label's width beside a value too long for the row`() {
        val long = "Stop two reps shy of failure on every set, and hold the top for a beat ".repeat(4)
        compose.setContent {
            DiffRowLayout(
                Modifier.width(400.dp),
                label = { Text("メモ", it.testTag("label")) },
            ) { Text(long) }
        }
        compose.onNodeWithTag("label").assertWidthIsAtLeast(120.dp)
    }
}
