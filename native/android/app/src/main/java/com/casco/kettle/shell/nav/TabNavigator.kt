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

    /** The RN tab the latest tap asked for, until RN reports arriving there. */
    private var pending: Tab? = null

    /**
     * RN's report of where it is now, a pathname. RN can move without being asked, so this is what
     * keeps the bar true rather than the taps alone.
     */
    fun onRoute(pathname: String) {
        val tab = Tab.at(pathname)
        barShown = tab != null
        // Reports of the moves before the latest tap are stale. Anything else, including a report from
        // above the tabs (which no tap could have asked for), means RN has moved on by itself, and is
        // followed.
        if (tab != null && pending != null && tab != pending) return
        pending = null
        if (tab != null) current = tab
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
        if (routes[tab] != Screen.Rn) return
        pending = tab
        navigate(tab.path)
    }
}
