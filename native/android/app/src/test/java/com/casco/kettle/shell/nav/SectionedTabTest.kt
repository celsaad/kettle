package com.casco.kettle.shell.nav

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Progress tab's switch (#411), over stand-ins for the screens it hosts. In `pt`, so a
 * hardcoded English label would show (AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class SectionedTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val navigator = Navigator()

    private fun show(tab: Tab) =
        compose.setContent {
            KettleTheme(darkTheme = false) {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    SectionedTab(tab, navigator.tabRoute(tab)) { Text("screen ${it.name}") }
                }
            }
        }

    private fun segment(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun `Progress names its sections in the reader's language, and opens on the one its route names`() {
        navigator.navigate(Route.of(Section.STATS))
        show(Tab.Progress)
        segment("Sessões").assertExists()
        segment("Estatísticas").assertIsSelected()
        compose.onNodeWithText("screen STATS").assertExists()
    }
}
