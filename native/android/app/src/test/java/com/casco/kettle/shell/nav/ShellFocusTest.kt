package com.casco.kettle.shell.nav

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.LightColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Who gives a text field focus (#380). Below API 28 the platform hands focus down from the window's root
 * whenever its decor is made visible again, touch mode or not, and Compose passes it on to the first
 * text field on the screen, which raises the keyboard over Home on every return to the app.
 */
@RunWith(RobolectricTestRunner::class)
class ShellFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val files =
        LatestFiles(FakeStoredFiles(), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)

    private lateinit var shell: ShellLayout

    private fun show() {
        val navigator = Navigator()
        compose.runOnUiThread {
            val activity = compose.activity
            shell =
                ShellLayout(
                    activity,
                    navigator,
                    theme = {
                        CompositionLocalProvider(
                            LocalLatestFiles provides files,
                            LocalSplashExit provides {},
                            LocalNavigator provides navigator,
                        ) { KettleTheme(darkTheme = false, it) }
                    },
                    content = {
                        var text by remember { mutableStateOf("") }
                        BasicTextField(text, { text = it }, Modifier.testTag(FIELD))
                    },
                ) { content, shell -> ModalSheet(content, shell, navigator, { LightColors }, {}) }
            navigator.moves = shell.sheet
            activity.setContentView(shell)
        }
        // As the first touch on a device does. Robolectric's setInTouchMode doesn't reach a window already
        // attached, and the view root that keeps the mode is hidden API.
        compose.runOnUiThread {
            val root = ReflectionHelpers.callInstanceMethod<Any>(compose.activity.window.decorView, "getViewRootImpl")
            ReflectionHelpers.callInstanceMethod<Boolean>(
                root,
                "ensureTouchMode",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true),
            )
        }
        compose.waitForIdle()
    }

    /** As `Activity.makeVisible` does on a return to the app: the decor hidden, then shown again. */
    private fun returnToTheApp() {
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            assertTrue("the platform only hands focus down in touch mode here", decor.isInTouchMode)
            decor.visibility = View.INVISIBLE
            decor.visibility = View.VISIBLE
        }
        compose.waitForIdle()
    }

    @Test
    @Config(sdk = [26])
    fun `a return to the app on Android 8 focuses no text field`() {
        show()
        returnToTheApp()
        compose.onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    @Config(sdk = [26])
    fun `the tabs shown again on Android 8, as a sheet closes, focus no text field`() {
        show()
        compose.runOnUiThread {
            shell.show(Place.Sheet)
            shell.show(Place.Tab)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    @Config(sdk = [26])
    fun `a tap on Android 8 still focuses the field`() {
        show()
        returnToTheApp()
        compose.onNodeWithTag(FIELD).performClick()
        compose.onNodeWithTag(FIELD).assertIsFocused()
    }

    private companion object {
        const val FIELD = "field"
    }
}
