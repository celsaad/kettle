package com.casco.kettle.shell.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The shell's layers, stacked in one frame: the RN surface, a native tab's screen, and a native sheet's
 * (#197). [ShellLayout] shows one at a time, and says why.
 */
enum class Layer { Surface, NativeTab, NativeSheet }

/**
 * Which tab the shell is on, and which [layer] shows: the RN surface, a native tab, or a native sheet.
 *
 * [navigate] steers the surface to a route path (`JsBridge.navigate`). [reread] is told whenever what
 * native read of RN's files may be stale: when RN hands the tabs back, since what it showed above them
 * may have written them, and when a tap brings up a native tab or RN opens a native sheet, since RN
 * also writes while a tab shows (a first launch's seeded library) and nothing reports that.
 * [onReselect] hears a tap on the tab already showing, which a native tab's screen answers by
 * scrolling to its top, as RN's bar did before #153.
 */
class TabNavigator(
    private val routes: Map<String, Screen>,
    private val navigate: (String) -> Unit,
    private val reread: () -> Unit = {},
    private val onReselect: (Tab) -> Unit = {},
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
     * Which layer should show: the surface on an RN tab, and over any tab while RN has something above
     * the tabs, since RN presents its modals and the runner inside the surface. The exception is a route
     * registered native above the tabs, whose stub RN shows while native draws it in the sheet.
     */
    val layer: Layer
        get() =
            when {
                barShown -> if (current.isRn()) Layer.Surface else Layer.NativeTab
                rnAt?.let(routes::screenAt) is Screen.Native -> Layer.NativeSheet
                else -> Layer.Surface
            }

    /** The path the native sheet draws, while one shows. */
    val nativeSheetPath: String? get() = rnAt.takeIf { layer == Layer.NativeSheet }

    /**
     * The tab the latest tap steered RN to, its stub for a native one, until RN reports arriving there.
     * A tap's message isn't lost on the way, which would leave this set: `JsChannel` holds it until JS
     * is ready, and the one place RN drops a navigate, over the runner, has the bar hidden. A report
     * from above the tabs clears it.
     */
    private var pending: Tab? = null

    /**
     * Where RN last reported being. RN reports only when its route changes, so a tap to the tab it is
     * already on gets no answer and mustn't wait for one. State, since it decides the [layer] and what
     * the native sheet draws.
     */
    private var rnAt by mutableStateOf<String?>(null)

    /** Each path's params as RN last reported them, which a native screen at that path reads. */
    private val paramsByPath = mutableStateMapOf<String, Map<String, String>>()

    /** The params RN last reported [path] with: what a native screen there was opened with. */
    fun params(path: String): Map<String, String> = paramsByPath[path].orEmpty()

    /**
     * RN's report of where it is now, a pathname and its [params]. RN can move without being asked, so
     * this is what keeps the bar true rather than the taps alone.
     */
    fun onRoute(
        pathname: String,
        params: Map<String, String> = emptyMap(),
    ) {
        val sheetBefore = nativeSheetPath
        rnAt = pathname
        paramsByPath[pathname] = params
        val tab = Tab.at(pathname)
        val handingBack = tab != null && !barShown
        barShown = tab != null
        // A native sheet coming up reads too, for a native tab's reason: it can open over an RN tab
        // that wrote a file without a hand-back.
        val sheetComingUp = nativeSheetPath != null && nativeSheetPath != sheetBefore
        if (handingBack || sheetComingUp) reread()
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
        if (tab == current) return onReselect(tab)
        current = tab
        if (!tab.isRn()) reread()
        // A native tab steers RN too, to its stub route (#190): a sheet it opens is then pushed over that
        // tab, and closing it reports that tab rather than whichever one RN was left on. RN already
        // headed for the tab has nowhere to go. Headed, not last seen: until RN answers a tap, it is on
        // its way to that tap's tab.
        if ((pending?.path ?: rnAt) == tab.path) return
        pending = tab
        navigate(tab.path)
    }

    private fun Tab.isRn() = routes.screenAt(path) == Screen.Rn
}
