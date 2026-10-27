package com.casco.kettle.shell.nav

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * The app's chrome around the one RN surface (Q3 in docs/native-migration-plan.md): the destinations
 * [routes] registers, stacked over [surface], and the tab bar under both.
 *
 * Plain views rather than one Compose tree, because the surface can't go inside Compose. Hosted in an
 * `AndroidView`, RN's whole tree was missing from the accessibility tree on a device, so TalkBack, and
 * a uiautomator dump, saw an empty screen (#153). As a sibling of the Compose views it is an ordinary
 * view again, as it was under the fragment that hosted it before.
 *
 * Only one of the two layers shows: the surface for an RN tab, the destinations for a native one. The
 * other is invisible, since a view left showing under another still takes touches and TalkBack's focus.
 *
 * [theme] wraps both Compose views' content, which are separate compositions.
 */
@SuppressLint("ViewConstructor") // Built in code, never inflated.
class ShellLayout(
    context: Context,
    private val surface: View,
    navigator: TabNavigator,
    routes: Map<Tab, Screen>,
    theme: @Composable (content: @Composable () -> Unit) -> Unit,
    requestBack: () -> Unit,
) : LinearLayout(context) {
    // PROTOTYPE (#177): set in init, once the content frame exists.
    lateinit var sheet: SheetPrototype
    private val destinations = composeView { theme { Destinations(navigator, routes, onRnShown = ::show) } }

    init {
        orientation = VERTICAL
        (surface.parent as? ViewGroup)?.removeView(surface)

        // The surface first, so a native destination draws over it.
        val content = FrameLayout(context)
        content.addView(surface, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(destinations, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(content, LayoutParams(MATCH_PARENT, 0, 1f))
        sheet = SheetPrototype(content, surface, requestBack)
        // Wrapping its content, so the bar takes no room at all while RN has something above the tabs.
        val bar = composeView { theme { if (navigator.barShown) TabBar(navigator.current, navigator::select) } }
        addView(bar, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) sheet.capture()
        return super.dispatchTouchEvent(ev)
    }

    private fun show(rn: Boolean) {
        surface.visibility = if (rn) VISIBLE else INVISIBLE
        destinations.visibility = if (rn) INVISIBLE else VISIBLE
    }

    private fun composeView(content: @Composable () -> Unit) =
        ComposeView(context).apply {
            // A ComposeView consumes the window insets by default, which would leave none for the
            // views after it: the bar needs the navigation bar's, and RN reads them for its own.
            consumeWindowInsets = false
            setContent(content)
        }
}
