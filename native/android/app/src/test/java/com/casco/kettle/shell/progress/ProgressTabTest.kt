package com.casco.kettle.shell.progress

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Navigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Section
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.tabRoute
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Progress tab (#414): its heading, then Overview and Sessions as tabs, over stand-ins for the
 * screens. In `pt`, so a hardcoded English label would show (AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ProgressTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val navigator = Navigator()

    private fun show() =
        compose.setContent {
            KettleTheme(darkTheme = false) {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    ProgressTab(navigator.tabRoute(Tab.Progress)) { Text("screen ${it.name}") }
                }
            }
        }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun `opens on Overview, under its heading, with Sessions beside it`() {
        navigator.select(Tab.Progress)
        show()

        compose.onNodeWithText("Progresso").assert(isHeading())
        tab("Visão geral").assertIsSelected().assertHeightIsAtLeast(48.dp)
        tab("Sessões").assertIsNotSelected()
        compose.onNodeWithText("screen OVERVIEW").assertExists()
    }

    @Test
    fun `a tab moves the route to its section`() {
        navigator.select(Tab.Progress)
        show()

        tab("Sessões").performClick()

        assertEquals(Route.of(Section.SESSIONS), navigator.tabRoute(Tab.Progress))
    }
}
