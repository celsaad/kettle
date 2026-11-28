package com.casco.kettle.shell.nav

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView

/**
 * The app's one view (#377): the [Shell] composition, the tabs, the pushed screens, the sheet and the
 * runner as siblings in it, following the [navigator]. [theme] wraps it; [content] is the screen each
 * route draws, the route table or a test's stand-ins.
 */
@SuppressLint("ViewConstructor") // Built in code, never inflated.
class ShellView(
    context: Context,
    private val navigator: Navigator,
    private val theme: @Composable (content: @Composable () -> Unit) -> Unit,
    private val content: @Composable (Route) -> Unit = { RouteContent(it) },
) : AbstractComposeView(context) {
    @Composable
    override fun Content() = theme { Shell(navigator, content) }

    /**
     * Below API 28 the platform gives focus to a window with none, touch mode or not, as a view becomes
     * visible or the focused one is cleared; API 28 stopped that in touch mode. Compose handed it focuses
     * its first text field, and the keyboard rises with nothing tapped (#380). A tap, a FocusRequester
     * and keyboard navigation focus a node directly, so they still land; a `requestFocus` of this view or
     * the decor in touch mode below 28 does not.
     */
    private val refusesPlatformFocus get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.P && isInTouchMode

    override fun requestFocus(
        direction: Int,
        previouslyFocusedRect: Rect?,
    ): Boolean = !refusesPlatformFocus && super.requestFocus(direction, previouslyFocusedRect)

    override fun focusableViewAvailable(v: View?) {
        if (!refusesPlatformFocus) super.focusableViewAvailable(v)
    }
}
