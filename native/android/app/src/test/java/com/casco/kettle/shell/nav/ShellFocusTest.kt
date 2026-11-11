package com.casco.kettle.shell.nav

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 * Who gives a text field focus on Android 8, where [ShellLayout] refuses the focus the platform hands
 * out on its own (#380): on a return to the app and as a layer shows again, but not a tap's or a
 * FocusRequester's. Each test composes in touch mode, the only mode the shell refuses anything in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ShellFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val files =
        LatestFiles(FakeStoredFiles(), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)

    private lateinit var shell: ShellLayout

    /** The shell with one text field, which focuses itself as it composes when [autofocus] says so. */
    private fun show(autofocus: Boolean = false) {
        val navigator = Navigator()
        compose.runOnUiThread {
            val activity = compose.activity
            // Before the shell composes, so an autofocus runs in touch mode too. As the first touch on a
            // device does: Robolectric's setInTouchMode doesn't reach a window already attached, and the
            // view root that keeps the mode is hidden API.
            val root = ReflectionHelpers.callInstanceMethod<Any>(activity.window.decorView, "getViewRootImpl")
            ReflectionHelpers.callInstanceMethod<Boolean>(
                root,
                "ensureTouchMode",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true),
            )
            assertTrue("the shell only refuses focus in touch mode", activity.window.decorView.isInTouchMode)
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
                        val focus = remember { FocusRequester() }
                        BasicTextField(text, { text = it }, Modifier.testTag(FIELD).focusRequester(focus))
                        if (autofocus) LaunchedEffect(Unit) { focus.requestFocus() }
                    },
                ) { content, shell -> ModalSheet(content, shell, navigator, { LightColors }, {}) }
            navigator.moves = shell.sheet
            activity.setContentView(shell)
        }
        compose.waitForIdle()
    }

    /** As `Activity.makeVisible` does on a return to the app: the decor hidden, then shown again. */
    private fun returnToTheApp() {
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            decor.visibility = View.INVISIBLE
            decor.visibility = View.VISIBLE
        }
        compose.waitForIdle()
    }

    @Test
    fun `a return to the app focuses no text field`() {
        show()
        returnToTheApp()
        compose.onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    fun `closing a sheet focuses no text field`() {
        show()
        compose.runOnUiThread {
            shell.show(Place.Sheet)
            shell.show(Place.Tab)
        }
        compose.waitForIdle()
        compose.onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    fun `a tap still focuses the field`() {
        show()
        returnToTheApp()
        compose.onNodeWithTag(FIELD).performClick()
        compose.onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun `a field that focuses itself still does`() {
        show(autofocus = true)
        compose.onNodeWithTag(FIELD).assertIsFocused()
    }

    private companion object {
        const val FIELD = "field"
    }
}
