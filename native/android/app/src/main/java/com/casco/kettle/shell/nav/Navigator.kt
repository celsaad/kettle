package com.casco.kettle.shell.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.casco.kettle.domain.RunnerStart

/**
 * The shell's back stack (#309): which tab shows, the sheets and pushed screens stacked over it, and the
 * runner's slot over everything. Compose state, so the bar, the tabs, the sheet and the runner each follow it.
 *
 * While the runner's slot is filled, every navigation but [closeRunner] is dropped, back included: the
 * runner owns its exits and the questions they ask (#297), which is what RN's stub enforced with
 * `usePreventRemove` until #309.
 *
 * [onReselect] hears a tap on the tab already showing, which a tab's screen answers by scrolling to its
 * top.
 */
@Suppress("TooManyFunctions") // A function per kind of move, each through the private `go` that holds the slot's rule.
class Navigator(
    private val onReselect: (Tab) -> Unit = {},
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

    /**
     * The stack over the tabs, bottom first, each screen with the id it was opened with, which the shell
     * keys its composition by: read anywhere, moved only through this navigator's moves.
     */
    var entries by mutableStateOf(emptyList<StackEntry>())
        private set

    /** The id the next screen opened takes: none is ever used twice by one navigator. */
    private var nextId = 0L

    /** The sheet stack's routes, bottom first: its sheets and pushed screens. */
    val sheets: List<Route> get() = entries.map { it.route }

    /** The top of the stack, a sheet or a pushed screen; null on a tab. */
    val sheet: Route? get() = entries.lastOrNull()?.route

    /** The runner, over everything while it shows; null when it doesn't. */
    var runner by mutableStateOf<RunnerSlot?>(null)
        private set

    /** Whether the runner shows, in its own colours whatever the scheme (`RunnerColors`). */
    val runnerShown: Boolean get() = runner != null

    /** The bar shows on a tab, with nothing over it. */
    val barShown: Boolean get() = entries.isEmpty() && runner == null

    /** Hears every move, before and after: the shell animates from it ([ShellMotion]). */
    var moves: MoveListener? = null

    /** Where the shell is: which of the tabs, the sheet and the runner shows. */
    val place: Place get() = placeOf(entries, runner)

    /** A tap on the bar. */
    fun select(tab: Tab) {
        if (runnerShown) return
        if (tab == this.tab) return onReselect(tab)
        go(MoveKind.Navigate, tab = tab)
    }

    /** Opens [route] over what shows: a sheet or a pushed screen stacks, and a tab's route goes to that tab. */
    fun push(route: Route) {
        if (!route.stacked) return navigate(route)
        go(MoveKind.Push, entries = entries + StackEntry(nextId++, route))
    }

    /** Opens [route] in the top sheet's place, so back skips the sheet it replaced. */
    fun replace(route: Route) {
        if (!route.stacked) return navigate(route)
        go(MoveKind.Replace, entries = entries.dropLast(1) + StackEntry(nextId++, route))
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
            entries = if (route.stacked) listOf(StackEntry(nextId++, route)) else emptyList(),
            tabRoutes = if (to != null) tabRoutes + (to to route) else tabRoutes,
        )
    }

    /**
     * Opens [route] as it arrives from outside the app, a `kettle://` link or a share (#569): over the sheet
     * or pushed screen showing, so back returns to it with what was typed there still in it, where
     * [navigate] would throw the stack away. A tab's route still goes to its tab, since no tab shows over a
     * sheet, and the runner's opens over everything, as through [navigate]. On a tab it is [navigate]'s
     * fade, as a link and a share have always come in.
     *
     * Arrivals keep one screen of each destination on the stack: one equal to the top is no move, and any
     * other of the route's destination leaves as the route opens on top, in the top's place when it was
     * the top. So shares never stack Imports, links can't grow the stack past the route table, and back
     * never walks through look-alikes. The price is a link to the destination of an editor that's open,
     * which takes that editor's place, draft and all: that link names that editor.
     */
    fun arrive(route: Route) {
        if (!route.stacked || entries.isEmpty()) return navigate(route)
        if (route == sheet) return
        val kind = if (sheet?.destination == route.destination) MoveKind.Replace else MoveKind.Push
        go(kind, entries = entries.filter { it.route.destination != route.destination } + StackEntry(nextId++, route))
    }

    /** Back: the top sheet, else Today. False when there is nowhere left, and the app should leave. */
    fun back(): Boolean {
        when {
            runnerShown -> Unit
            entries.isNotEmpty() -> go(MoveKind.Back, entries = entries.dropLast(1))
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
    }

    /**
     * The back stack as text an activity can save: the tab's route, section and all, then each sheet's
     * path. The runner's slot isn't in it: a running session is attached to again at launch
     * (`MainActivity`), and a finished one has nothing to come back to.
     */
    fun saved(): List<String> = listOf(tabRoute(tab).path) + entries.map { it.route.path }

    /**
     * Puts back a stack [saved] wrote, as a navigate, so the sheet hears it: what no longer parses (a
     * route since renamed) is left out, and a tab that doesn't is Today. The four-tab shell saved a tab's
     * name instead of its path (#411); one of those lands where its screen now is.
     *
     * Each screen takes back the id [ids] saved for it, so state saved under it finds its screen again;
     * one with none (a stack saved before ids were) takes a new one. Ids opened later start past them all.
     */
    fun restore(
        saved: List<String>,
        ids: LongArray? = null,
    ) {
        val first = saved.firstOrNull()
        val tabRoute = (LEGACY_TABS[first] ?: first)?.let(Route::parse)?.takeIf { Tab.of(it.destination) != null }
        val to = tabRoute?.let { Tab.of(it.destination) } ?: Tab.Today
        val paths = saved.drop(1)
        nextId = maxOf(nextId, (ids?.maxOrNull() ?: -1) + 1)
        val restored =
            paths.mapIndexedNotNull { i, path ->
                Route.parse(path)?.takeIf { it.stacked }?.let { StackEntry(ids?.getOrNull(i) ?: nextId++, it) }
            }
        go(
            MoveKind.Navigate,
            tab = to,
            entries = restored,
            tabRoutes = if (tabRoute != null) tabRoutes + (to to tabRoute) else tabRoutes,
        )
    }

    private fun go(
        kind: MoveKind,
        tab: Tab = this.tab,
        entries: List<StackEntry> = this.entries,
        runner: RunnerSlot? = this.runner,
        tabRoutes: Map<Tab, Route> = this.tabRoutes,
    ) {
        // The slot's rule, in one place: while it's filled, the only move is the one that empties it.
        if (this.runner != null && runner != null) return
        val move = Move(place, placeOf(entries, runner), kind)
        moves?.willMove(move)
        this.tab = tab
        this.tabRoutes = tabRoutes
        this.entries = entries
        this.runner = runner
        moves?.moved(move)
    }
}

/** The route [tab] shows: its section, once one has been picked or linked to. */
fun Navigator.tabRoute(tab: Tab): Route = tabRoutes[tab] ?: Route(tab.destination)

/** Each stacked screen's id, in [Navigator.saved]'s order: a screen's saved state is keyed by it (#377). */
fun Navigator.savedIds(): LongArray = entries.map { it.id }.toLongArray()

/**
 * The tab names the four-tab shell saved, as the paths that now lead where each went (#411). Library was
 * Exercises alone then, so it names that section: a bare `/library` opens on Workouts since #412.
 */
private val LEGACY_TABS =
    mapOf(
        "Workouts" to "/",
        "Library" to "/library?section=exercises",
        "History" to "/history",
        "Programs" to "/programs",
    )

/**
 * Where a back stack of [entries] under the runner's [runner] slot is: the runner covers everything, and
 * otherwise the top of the stack says whether a sheet or a pushed screen shows.
 */
private fun placeOf(
    entries: List<StackEntry>,
    runner: RunnerSlot?,
): Place {
    val top = entries.lastOrNull()?.route
    return when {
        runner != null -> Place.Runner
        top == null -> Place.Tab
        top.pushed -> Place.Pushed
        else -> Place.Sheet
    }
}

/**
 * One screen on the stack: its [route], and an [id] no other opening shares (#377). The shell keys a
 * screen's composition by it, so a route opened again, even while its last opening is still leaving,
 * is a new screen with nothing remembered from the last.
 */
data class StackEntry(
    val id: Long,
    val route: Route,
)

/** What the runner's slot holds: the start it was opened with, null for none. */
data class RunnerSlot(
    val start: RunnerStart?,
)

/** How a move was asked for, which decides how the sheet animates it (`transitionBetween`). */
enum class MoveKind {
    /** Something opened over what showed: a sheet, a pushed screen, or the runner. */
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
    /** Before [move] lands: the navigator still holds where it starts, so this is when to read what shows. */
    fun willMove(move: Move)

    /** After [move] landed in the navigator's state, before anything has drawn it. */
    fun moved(move: Move)
}

/** The current activity's [Navigator], for every screen that opens, closes or starts something. */
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator: the shell provides one") }
