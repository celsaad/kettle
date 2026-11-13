package com.casco.kettle.shell.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.casco.kettle.domain.RunnerStart

/**
 * The shell's back stack (#309): which tab shows, the sheets stacked over it, and the runner's slot over
 * everything. Compose state, so the bar, the tabs, the sheet and the runner each follow it.
 *
 * While the runner's slot is filled, every navigation but [closeRunner] is dropped, back included: the
 * runner owns its exits and the questions they ask (#297), which is what RN's stub enforced with
 * `usePreventRemove` until #309.
 *
 * [onReselect] hears a tap on the tab already showing, which a tab's screen answers by scrolling to its
 * top. [onRunnerClosed] hears the slot pop: what the runner wrote is read again then (#194).
 */
class Navigator(
    private val onReselect: (Tab) -> Unit = {},
    private val onRunnerClosed: () -> Unit = {},
) {
    var tab by mutableStateOf(Tab.Today)
        private set

    /** Each tab's route as last navigated to, which carries its section (#411); a tab not yet is bare. */
    var tabRoutes by mutableStateOf(emptyMap<Tab, Route>())
        private set

    companion object {
        /**
         * The current activity's navigator, which it clears as it goes: the debug and benchmark builds'
         * `NavigateReceiver` steers it, since a broadcast has no activity of its own to ask.
         */
        var current: Navigator? = null
    }

    private var sheets by mutableStateOf(emptyList<Route>())

    /** The sheet showing, the top of the stack; null on a tab. */
    val sheet: Route? get() = sheets.lastOrNull()

    /** The runner, over everything while it shows; null when it doesn't. */
    var runner by mutableStateOf<RunnerSlot?>(null)
        private set

    /** Whether the runner shows, in its own colours whatever the scheme (`RunnerColors`). */
    val runnerShown: Boolean get() = runner != null

    /** The bar shows on a tab, with nothing over it. */
    val barShown: Boolean get() = sheets.isEmpty() && runner == null

    /** Hears every move, before and after: the sheet animates from it. The current activity's sheet. */
    var moves: MoveListener? = null

    /** Where the shell is: which of the tabs, the sheet and the runner shows. */
    val place: Place get() = placeOf(sheets, runner)

    /** A tap on the bar. */
    fun select(tab: Tab) {
        if (runnerShown) return
        if (tab == this.tab) return onReselect(tab)
        go(MoveKind.Navigate, tab = tab)
    }

    /** Opens [route] over what shows: a sheet stacks, and a tab's route goes to that tab. */
    fun push(route: Route) {
        if (!route.sheet) return navigate(route)
        go(MoveKind.Push, sheets = sheets + route)
    }

    /** Opens [route] in the top sheet's place, so back skips the sheet it replaced. */
    fun replace(route: Route) {
        if (!route.sheet) return navigate(route)
        go(MoveKind.Replace, sheets = sheets.dropLast(1) + route)
    }

    /**
     * Goes to [route] from the tabs, whatever is over them: a link, `NavigateReceiver`, a delete. The
     * runner's route is the exception, opening over what shows as [openRunner] does.
     */
    fun navigate(route: Route) {
        if (route.destination.presentation == Presentation.Runner) return openRunner(route.runnerStart)
        val to = Tab.of(route.destination)
        go(
            MoveKind.Navigate,
            tab = to ?: tab,
            sheets = if (route.sheet) listOf(route) else emptyList(),
            tabRoutes = if (to != null) tabRoutes + (to to route) else tabRoutes,
        )
    }

    /** Back: the top sheet, else Today. False when there is nowhere left, and the app should leave. */
    fun back(): Boolean {
        when {
            runnerShown -> Unit
            sheets.isNotEmpty() -> go(MoveKind.Back, sheets = sheets.dropLast(1))
            tab != Tab.Today -> go(MoveKind.Back, tab = Tab.Today)
            else -> return false
        }
        return true
    }

    /**
     * The one way into the runner (#297): over whatever shows, with [start], or none for the resolver's
     * fallback. Dropped while the runner already shows; attaching to a running session is the runner
     * screen's, whatever start it was opened with.
     */
    fun openRunner(start: RunnerStart?) {
        go(MoveKind.Push, runner = RunnerSlot(start))
    }

    /** The runner is done, or left before it ran: the slot pops, back to what it opened over. */
    fun closeRunner() {
        if (!runnerShown) return
        go(MoveKind.Back, runner = null)
        onRunnerClosed()
    }

    /**
     * The back stack as text an activity can save: the tab's route, section and all, then each sheet's
     * path. The runner's slot isn't in it: a running session is attached to again at launch
     * (`MainActivity`), and a finished one has nothing to come back to.
     */
    fun saved(): List<String> = listOf(tabRoute(tab).path) + sheets.map { it.path }

    /**
     * Puts back a stack [saved] wrote, as a navigate, so the sheet hears it: what no longer parses (a
     * route since renamed) is left out, and a tab that doesn't is Today. The four-tab shell saved a tab's
     * name instead of its path (#411); one of those lands where its screen now is.
     */
    fun restore(saved: List<String>) {
        val first = saved.firstOrNull()
        val tabRoute = (LEGACY_TABS[first] ?: first)?.let(Route::parse)?.takeIf { Tab.of(it.destination) != null }
        val to = tabRoute?.let { Tab.of(it.destination) } ?: Tab.Today
        go(
            MoveKind.Navigate,
            tab = to,
            sheets = saved.drop(1).mapNotNull(Route::parse).filter { it.sheet },
            tabRoutes = if (tabRoute != null) tabRoutes + (to to tabRoute) else tabRoutes,
        )
    }

    private fun go(
        kind: MoveKind,
        tab: Tab = this.tab,
        sheets: List<Route> = this.sheets,
        runner: RunnerSlot? = this.runner,
        tabRoutes: Map<Tab, Route> = this.tabRoutes,
    ) {
        // The slot's rule, in one place: while it's filled, the only move is the one that empties it.
        if (this.runner != null && runner != null) return
        val move = Move(place, placeOf(sheets, runner), kind)
        moves?.willMove(move)
        this.tab = tab
        this.tabRoutes = tabRoutes
        this.sheets = sheets
        this.runner = runner
        moves?.moved(move)
    }
}

/** The route [tab] shows: its section, once one has been picked or linked to. */
fun Navigator.tabRoute(tab: Tab): Route = tabRoutes[tab] ?: Route(tab.destination)

/** The tab names the four-tab shell saved, as the paths that now lead where each went (#411). */
private val LEGACY_TABS =
    mapOf("Workouts" to "/", "Library" to "/library", "History" to "/history", "Programs" to "/programs")

/** Where a back stack of [sheets] under the runner's [runner] slot is: the runner covers everything. */
private fun placeOf(
    sheets: List<Route>,
    runner: RunnerSlot?,
): Place =
    when {
        runner != null -> Place.Runner
        sheets.isNotEmpty() -> Place.Sheet
        else -> Place.Tab
    }

/** What the runner's slot holds: the start it was opened with, null for none. */
data class RunnerSlot(
    val start: RunnerStart?,
)

/** How a move was asked for, which decides how the sheet animates it (`transitionBetween`). */
enum class MoveKind {
    /** Something opened over what showed: a sheet, or the runner. */
    Push,

    /** Back, or the runner's slot popping. */
    Back,

    /** A sheet in the top one's place. */
    Replace,

    /** From the tabs, as a tap on the bar, a link or a delete goes. */
    Navigate,
}

/** One move between [Place]s, as [MoveListener] hears it. */
data class Move(
    val from: Place,
    val to: Place,
    val kind: MoveKind,
)

/** The sheet's ears on the [Navigator]. */
interface MoveListener {
    /** Before [move] lands: the screen still shows where it starts, so this is when to picture it. */
    fun willMove(move: Move)

    /** After [move] landed in the navigator's state, before anything has drawn it. */
    fun moved(move: Move)
}

/** The current activity's [Navigator], for every screen that opens, closes or starts something. */
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator: the shell provides one") }
