package com.casco.kettle.shell.importer

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.domain.FieldUnit
import com.casco.kettle.domain.UnitSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [measuredText], Import's value-with-unit. In `ja`, the one bundle whose spacing differs: 秒 and 分 sit
 * against the number, and a Latin unit keeps its space. A field label's suffix joined with a space
 * read "90 秒", which is what this replaced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ja")
class MeasuredTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun assertReads(
        unit: FieldUnit,
        expected: String,
        units: UnitSystem = UnitSystem.METRIC,
    ) {
        compose.setContent { Text(measuredText("90", unit, units)) }
        compose.onNodeWithText(expected).assertExists()
    }

    @Test
    fun `sets seconds against the number`() = assertReads(FieldUnit.Seconds, "90秒")

    @Test
    fun `sets minutes against the number`() = assertReads(FieldUnit.Minutes, "90分")

    @Test
    fun `keeps the space before metres`() = assertReads(FieldUnit.Metres, "90 m")

    @Test
    fun `writes a weight in the reader's unit`() = assertReads(FieldUnit.Weight, "90 lb", UnitSystem.IMPERIAL)
}
