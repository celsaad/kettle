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
    var tab by mutableStateOf(Tab.Workouts)
        private set

    companion object {
        /**
         * The current activity's navigator, which it clears as it goes: the debug build's
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

    /** Whether the runner shows, which is dark in either scheme (`RunnerColors`). */
    val runnerShown: Boolean get() = runner != null

    /** The bar shows on a tab, with nothing over it. */
    val barShown: Boolean get() = sheets.isEmpty() && runner == null

    val layer: Layer
        get() =
            when (place) {
                Place.Runner -> Layer.NativeRunner
                Place.Sheet -> Layer.NativeSheet
                Place.Tab -> Layer.NativeTab
            }

    /** Hears every move, before and after: the sheet animates from it. The current activity's sheet. */
    var moves: MoveListener? = null

    private val place: Place get() = placeOf(sheets, runner)

    /** A tap on the bar. */
    fun select(tab: Tab) {
        if (runnerShown) return
        if (tab == this.tab) return onReselect(tab)
        go(MoveKind.Navigate, tab = tab)
    }

    /** Opens [route] over what shows: a sheet stacks, and a tab's route goes to that tab. */
    fun push(route: Route) {
        if (runnerShown) return
        if (!route.sheet) return navigate(route)
        go(MoveKind.Push, sheets = sheets + route)
    }

    /** Opens [route] in the top sheet's place, so back skips the sheet it replaced. */
    fun replace(route: Route) {
        if (runnerShown) return
        if (!route.sheet) return navigate(route)
        go(MoveKind.Replace, sheets = sheets.dropLast(1) + route)
    }

    /**
     * Goes to [route] from the tabs, whatever is over them: a link, `NavigateReceiver`, a delete. The
     * runner's route is the exception, opening over what shows as [openRunner] does.
     */
    fun navigate(route: Route) {
        if (runnerShown) return
        if (route.destination.presentation == Presentation.Runner) return openRunner(route.runnerStart)
        go(
            MoveKind.Navigate,
            tab = Tab.of(route.destination) ?: tab,
            sheets = if (route.sheet) listOf(route) else emptyList(),
        )
    }

    /** Back: the top sheet, else Workouts. False when there is nowhere left, and the app should leave. */
    fun back(): Boolean {
        when {
            runnerShown -> Unit
            sheets.isNotEmpty() -> go(MoveKind.Back, sheets = sheets.dropLast(1))
            tab != Tab.Workouts -> go(MoveKind.Back, tab = Tab.Workouts)
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
        if (runnerShown) return
        go(MoveKind.Push, runner = RunnerSlot(start))
    }

    /** The runner is done, or left before it ran: the slot pops, back to what it opened over. */
    fun closeRunner() {
        if (!runnerShown) return
        go(MoveKind.Back, runner = null)
        onRunnerClosed()
    }

    private fun go(
        kind: MoveKind,
        tab: Tab = this.tab,
        sheets: List<Route> = this.sheets,
        runner: RunnerSlot? = this.runner,
    ) {
        val move = Move(place, placeOf(sheets, runner), kind)
        moves?.willMove(move)
        this.tab = tab
        this.sheets = sheets
        this.runner = runner
        moves?.moved(move)
    }
}

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

/**
 * The shell's layers, stacked in one frame: a tab's screen, a sheet's (#197), and the runner's, full
 * screen over everything (#302). [ShellLayout] shows one at a time, and says why.
 */
enum class Layer { NativeTab, NativeSheet, NativeRunner }

/** The current activity's [Navigator], for every screen that opens, closes or starts something. */
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator: the shell provides one") }
