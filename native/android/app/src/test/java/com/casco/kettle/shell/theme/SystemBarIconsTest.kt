package com.casco.kettle.shell.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The system bars' icons against the scheme the app draws (#248): dark icons on the light scheme, light
 * on the dark one, as Settings → Appearance pins it rather than as the device is.
 */
@RunWith(RobolectricTestRunner::class)
class SystemBarIconsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val preferences get() = folder.root.resolve("preferences.json")

    private fun pin(scheme: String) = preferences.writeText("""{"unitSystem":"metric","themePreference":"$scheme"}""")

    private fun show(runnerShown: () -> Boolean = { false }) {
        compose.setContent { PreferredKettleTheme(preferences, reread = false, runnerShown()) {} }
        compose.waitForIdle()
    }

    /** True for dark icons, which is what Android calls a light status bar. */
    private fun darkIcons(): Pair<Boolean, Boolean> =
        compose.runOnIdle {
            val window = compose.activity.window
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars to controller.isAppearanceLightNavigationBars
        }

    @Test
    fun `a light device gets dark icons`() {
        show()

        assertEquals(true to true, darkIcons())
    }

    @Test
    @Config(qualifiers = "night")
    fun `a dark device gets light icons`() {
        show()

        assertEquals(false to false, darkIcons())
    }

    @Test
    fun `Dark pinned on a light device gets light icons`() {
        pin("dark")

        show()

        assertEquals(false to false, darkIcons())
    }

    @Test
    @Config(qualifiers = "night")
    fun `Light pinned on a dark device gets dark icons`() {
        pin("light")

        show()

        assertEquals(true to true, darkIcons())
    }

    /** The runner is dark whatever the scheme, and the icons follow it there and back. */
    @Test
    fun `the runner gets light icons on the light scheme, until it leaves`() {
        var runner by mutableStateOf(true)
        show { runner }

        assertEquals(false to false, darkIcons())

        runner = false

        assertEquals(true to true, darkIcons())
    }
}
