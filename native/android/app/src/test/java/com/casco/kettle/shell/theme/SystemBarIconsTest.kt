package com.casco.kettle.shell.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowInsetsControllerCompat
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
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

    private fun show(
        latest: () -> Preferences? = { null },
        runnerShown: () -> Boolean = { false },
        splashExits: () -> Int = { 0 },
    ) {
        compose.setContent {
            PreferredKettleTheme(preferences, latest(), reread = false, runnerShown(), splashExits()) {}
        }
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
        show(runnerShown = { runner })

        assertEquals(false to false, darkIcons())

        runner = false

        assertEquals(true to true, darkIcons())
    }

    /** Native Settings' write (#237): the chrome follows it with the sheet still up, no reread needed. */
    @Test
    fun `follows the scheme native Settings writes, at once`() {
        var latest by mutableStateOf<Preferences?>(null)
        show(latest = { latest })

        latest = Preferences(UnitSystem.METRIC, ThemePreference.DARK, false, true, null)

        assertEquals(false to false, darkIcons())
    }

    /**
     * On API 31 and 32 the splash's exit resets the icons from the window's theme, which leaves them
     * light on the light scheme, and nothing recomposes to fix them: the exit has to put them back.
     */
    @Test
    fun `puts the icons back after the splash's exit resets them`() {
        var exits by mutableStateOf(0)
        show(splashExits = { exits })
        compose.runOnIdle {
            val window = compose.activity.window
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }

        exits++

        assertEquals(true to true, darkIcons())
    }

    /**
     * The shell is four Compose views on one window, each setting the icons. Any one recomposing (the
     * preferences reread on Resume) set the runner's light ones back over its orange field (#470).
     */
    @Test
    fun `over the orange field, another view recomposing keeps the icons dark`() {
        var latest by mutableStateOf<Preferences?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalRunnerBars provides RunnerBars()) {
                PreferredKettleTheme(preferences, null, reread = false, runnerShown = true, splashExits = 0) {
                    RunnerBarIcons(darkIcons = true)
                }
                PreferredKettleTheme(preferences, latest, reread = false, runnerShown = true, splashExits = 0) {}
            }
        }
        compose.waitForIdle()
        assertEquals(true to true, darkIcons())

        latest = Preferences(UnitSystem.METRIC, ThemePreference.LIGHT, false, true, null)

        assertEquals(true to true, darkIcons())
    }

    /** Once the runner leaves, the scheme decides again, not the black field it last asked for. */
    @Test
    fun `the runner leaving hands the icons back to the scheme, whatever its field asked`() {
        var runner by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalRunnerBars provides RunnerBars()) {
                PreferredKettleTheme(preferences, null, reread = false, runnerShown = runner, splashExits = 0) {
                    if (runner) RunnerBarIcons(darkIcons = false)
                }
            }
        }
        compose.waitForIdle()
        assertEquals(false to false, darkIcons())

        runner = false

        assertEquals(true to true, darkIcons())
    }
}
