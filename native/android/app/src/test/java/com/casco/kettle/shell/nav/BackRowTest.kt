package com.casco.kettle.shell.nav

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

/** A pushed screen's Back (#489), in `pt` so a hardcoded "Back" would show. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class BackRowTest {
    @get:Rule
    val compose = createComposeRule()

    /** The system's back, so the navigator pops the screen and a screen that asks first still asks. */
    @Test
    fun `Back is a named 48dp button that goes back as the system's back does`() {
        var backs = 0
        compose.setContent {
            KettleTheme(darkTheme = false) {
                BackHandler { backs++ }
                BackRow { Text("Configurações") }
            }
        }

        compose
            .onNode(
                hasContentDescription("Voltar") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, backs)
        compose.onNodeWithText("Configurações").assertExists()
    }
}
