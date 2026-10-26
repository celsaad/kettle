package com.casco.kettle.shell.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which tab the shell is on, and what that means for the RN surface.
 *
 * [navigate] steers the surface to a route path (`JsBridge.navigate`).
 */
class TabNavigator(
    private val routes: Map<Tab, Screen>,
    private val navigate: (String) -> Unit,
) {
    var current by mutableStateOf(Tab.Workouts)
        private set

    /**
     * False while RN shows something above the tabs, a modal or the runner. Those are still RN's, inside
     * the surface (#155 is what would make them native sheets), and they cover the whole screen today.
     */
    var barShown by mutableStateOf(true)
        private set

    /**
     * Whether the RN surface is what should show: on an RN tab, and over any tab while RN has something
     * above the tabs, since RN presents its modals and the runner inside the surface.
     */
    val rnShown: Boolean get() = !barShown || current.isRn()

    /**
     * The RN tab the latest tap asked for, until RN reports arriving there. A tap's message isn't lost
     * on the way, which would leave this set: `JsChannel` holds it until JS is ready, and the one place
     * RN drops a navigate, over the runner, has the bar hidden. A report from above the tabs clears it.
     */
    private var pending: Tab? = null

    /**
     * Where RN last reported being. RN reports only when its route changes, so a tap to the tab it is
     * already on gets no answer and mustn't wait for one.
     */
    private var rnAt: String? = null

    /**
     * RN's report of where it is now, a pathname. RN can move without being asked, so this is what
     * keeps the bar true rather than the taps alone.
     */
    fun onRoute(pathname: String) {
        rnAt = pathname
        val tab = Tab.at(pathname)
        barShown = tab != null
        when {
            // Above the tabs, where no tap sends RN: whatever a tap was waiting on is moot.
            tab == null -> pending = null

            // Reports of the moves before the latest tap are stale, and the answer to it moves nothing,
            // since the tap already did. The user may have gone on to a native tab since.
            pending != null -> if (tab == pending) pending = null

            // RN moved by itself.
            else -> current = tab
        }
    }

    /**
     * Back, once RN has nothing left to dismiss. False means the shell has nowhere left to go either,
     * and the app should leave.
     */
    fun back(): Boolean {
        if (current == Tab.Workouts) return false
        select(Tab.Workouts)
        return true
    }

    /** A tap on the bar. */
    fun select(tab: Tab) {
        if (tab == current) return
        current = tab
        // A native tab leaves the surface where it is, and RN already on the tab has nowhere to go.
        if (!tab.isRn() || rnAt == tab.path) return
        pending = tab
        navigate(tab.path)
    }

    private fun Tab.isRn() = routes[this] == Screen.Rn
}
