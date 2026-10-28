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
 * Only one [Layer] shows: the surface for an RN tab or anything RN draws above the tabs, the
 * destinations for a native tab, and the [nativeSheet] for a native route in the sheet. The others are
 * invisible, since a view left showing under another still takes touches and TalkBack's focus.
 *
 * [theme] wraps every Compose view's content, each a separate composition.
 */
@SuppressLint("ViewConstructor") // Built in code, never inflated.
class ShellLayout(
    context: Context,
    private val surface: View,
    navigator: TabNavigator,
    routes: Map<String, Screen>,
    theme: @Composable (content: @Composable () -> Unit) -> Unit,
    sheet: (content: FrameLayout, shell: ShellLayout) -> ModalSheet,
) : LinearLayout(context) {
    private val destinations = composeView { theme { Destinations(navigator, routes, onLayer = ::showLayer) } }

    /**
     * A native route's screen in the sheet (#197). A view of its own rather than a destination, beside the
     * surface in the tree, so the sheet shapes and moves it as it does the surface, under the same chrome.
     */
    internal val nativeSheet: ComposeView =
        composeView { theme { NativeSheet(navigator, routes, onComposed = { nativeFrames.onComposed(it) }) } }

    /** When the native sheet has composed the route the sheet arrives at. */
    internal val nativeFrames: NativeFrames = NativeFrames(nativeSheet)

    /**
     * RN's modal routes, presented over the tabs as a native sheet around the same surface (#155),
     * built by [sheet] inside the frame the surface is in.
     */
    val sheet: ModalSheet

    init {
        orientation = VERTICAL
        (surface.parent as? ViewGroup)?.removeView(surface)

        // The surface first, so a native destination draws over it, and the native sheet straight after,
        // where the sheet's own views around the surface take it in too.
        val content = FrameLayout(context)
        content.addView(surface, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(nativeSheet, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(destinations, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        nativeSheet.visibility = INVISIBLE
        addView(content, LayoutParams(MATCH_PARENT, 0, 1f))
        this.sheet = sheet(content, this)
        // Wrapping its content, so the bar takes no room at all while RN has something above the tabs.
        val bar = composeView { theme { if (navigator.barShown) TabBar(navigator.current, navigator::select) } }
        addView(bar, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /**
     * Shows [layer] alone. The destinations call it as the tab changes; the sheet calls it as well, at
     * the frame a route arrives, since a recomposition would land a frame after the transition starts.
     * Invisible rather than gone, so the hidden layers stay laid out, ready to show.
     */
    fun showLayer(layer: Layer) {
        surface.visibility = if (layer == Layer.Surface) VISIBLE else INVISIBLE
        destinations.visibility = if (layer == Layer.NativeTab) VISIBLE else INVISIBLE
        nativeSheet.visibility = if (layer == Layer.NativeSheet) VISIBLE else INVISIBLE
    }

    private fun composeView(content: @Composable () -> Unit) =
        ComposeView(context).apply {
            // A ComposeView consumes the window insets by default, which would leave none for the
            // views after it: the bar needs the navigation bar's, and RN reads them for its own.
            consumeWindowInsets = false
            setContent(content)
        }
}
