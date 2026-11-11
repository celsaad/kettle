package com.casco.kettle.shell.nav

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.runner.RecoveryPrompt

/**
 * The app's chrome: the tabs, the sheet's view and the runner's, stacked in one frame, and the tab bar
 * under them, all following the [navigator].
 *
 * Plain views rather than one Compose tree, because the sheet ([ModalSheet]) shapes and moves views.
 *
 * Only one [Place] shows: the destinations for a tab, the [nativeSheet] for a sheet, and the
 * [nativeRunner] for the runner. The others are invisible, since a view left showing under another
 * still takes touches and TalkBack's focus.
 *
 * [theme] wraps every Compose view's content, each a separate composition. The destinations' also
 * let the system splash go ([SplashExit]).
 */
@SuppressLint("ViewConstructor") // Built in code, never inflated.
class ShellLayout(
    context: Context,
    navigator: Navigator,
    theme: @Composable (content: @Composable () -> Unit) -> Unit,
    /** The screen each route draws: the route table, or a test's stand-ins. */
    content: @Composable (Route) -> Unit = { RouteContent(it) },
    sheet: (content: FrameLayout, shell: ShellLayout) -> ModalSheet,
) : LinearLayout(context) {
    internal val destinations =
        composeView {
            theme {
                SplashExit(LocalLatestFiles.current, LocalSplashExit.current)
                Destinations(navigator, content, onPlace = ::show)
            }
        }

    /**
     * The sheet's screen (#197). A view of its own rather than a destination, so the sheet shapes and
     * moves it under its chrome.
     */
    internal val nativeSheet: ComposeView =
        composeView { theme { NativeSheet(navigator, content, onComposed = { nativeFrames.onComposed(it) }) } }

    /** The runner (#302), full screen over everything else and outside the sheet: no tab's or sheet's. */
    internal val nativeRunner: ComposeView = composeView { theme { NativeRunner(navigator, content) } }

    /** When the sheet's view has composed the route the sheet arrives at. */
    internal val nativeFrames: NativeFrames = NativeFrames(nativeSheet)

    /** The sheet routes, presented over the tabs as a sheet (#155), built by [sheet] inside the frame. */
    val sheet: ModalSheet

    init {
        orientation = VERTICAL

        // The sheet's view first, where the sheet's own views go in around it.
        val content = FrameLayout(context)
        content.addView(nativeSheet, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(destinations, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(nativeRunner, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        nativeSheet.visibility = INVISIBLE
        nativeRunner.visibility = INVISIBLE
        addView(content, LayoutParams(MATCH_PARENT, 0, 1f))
        this.sheet = sheet(content, this)
        // Wrapping its content, so the bar takes no room at all while something shows above the tabs.
        // The launch's Resume / Finish prompt (#307) rides in the bar's view, the one Compose view never
        // hidden, so whether it shows can't hang on which layer does. The dialog is a window of its own,
        // over every layer.
        val bar =
            composeView {
                theme {
                    if (navigator.barShown) TabBar(navigator.tab, navigator::select)
                    RecoveryPrompt()
                }
            }
        addView(bar, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /**
     * Shows [place] alone. The destinations call it as the tab changes; the sheet calls it as well, at
     * the frame a route arrives, since a recomposition would land a frame after the transition starts.
     * Invisible rather than gone, so the hidden layers stay laid out, ready to show.
     */
    fun show(place: Place) {
        destinations.visibility = if (place == Place.Tab) VISIBLE else INVISIBLE
        nativeSheet.visibility = if (place == Place.Sheet) VISIBLE else INVISIBLE
        nativeRunner.visibility = if (place == Place.Runner) VISIBLE else INVISIBLE
    }

    /**
     * Below API 28 the platform gives focus to a window with none, touch mode or not, as a view becomes
     * visible or the focused one is cleared; API 28 stopped that in touch mode. A Compose layer handed it
     * focuses its first text field, and the keyboard rises with nothing tapped (#380). A tap, a
     * FocusRequester and keyboard navigation focus a layer directly, so they still land; a
     * `requestFocus` of this view or the decor in touch mode below 28 does not.
     */
    private val refusesPlatformFocus get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.P && isInTouchMode

    override fun requestFocus(
        direction: Int,
        previouslyFocusedRect: Rect?,
    ): Boolean = !refusesPlatformFocus && super.requestFocus(direction, previouslyFocusedRect)

    override fun focusableViewAvailable(v: View?) {
        if (!refusesPlatformFocus) super.focusableViewAvailable(v)
    }

    private fun composeView(content: @Composable () -> Unit) =
        ComposeView(context).apply {
            // A ComposeView consumes the window insets by default, which would leave none for the
            // views after it: the bar needs the navigation bar's.
            consumeWindowInsets = false
            setContent(content)
        }
}
