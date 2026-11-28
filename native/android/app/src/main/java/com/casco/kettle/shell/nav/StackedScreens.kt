package com.casco.kettle.shell.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Which layer draws each stacked screen at one moment (#569): the pushed screens under the sheet's frame
 * and over it, the sheet in the frame, and the rest, [covered], composed out of sight.
 */
internal class StackLayers(
    entries: List<StackEntry>,
    playing: Motion?,
) {
    val shown = shownBy(entries)

    /**
     * The sheet in the frame: the top, or the one under a pushed screen a link opened over it, which stays
     * there so that screen slides off it and a predictive back uncovers it, as over a tab.
     */
    val framed = shown.sheet ?: sheetUnderPushed(entries)

    private val pushed = listOfNotNull(shown.beneath, shown.pushed, playing?.leavingPushed)

    // Over the frame: the pushed screen over a sheet, and one leaving off a sheet or off a screen over one.
    private val overFrame =
        if (framed != null) setOfNotNull(shown.pushed, playing?.leavingPushed) else emptySet()

    val pushedUnderFrame = pushed.filter { it !in overFrame }
    val pushedOverFrame = pushed.filter { it in overFrame }

    /** Every stacked screen some layer draws, those leaving included. */
    val drawn = pushed + listOfNotNull(framed, playing?.leavingSheet)

    /** The stacked screens no layer draws. */
    val covered = entries.filter { it !in drawn }
}

/**
 * How many times the stacked screen this is read in has shown again after something covered it: a sheet,
 * a screen pushed over it, or the runner (#377). Before #377 each of those composed a pushed screen
 * afresh as it showed again, and before #569 a sheet under a sheet; a screen that reads something once
 * keys that read on this instead.
 *
 * Every stacked screen provides it ([StackedScreens]). On a tab and in the runner it stays 0: a tab was
 * never composed afresh as it showed again.
 */
val LocalUncovered = compositionLocalOf { 0 }

/**
 * Whether the screen this is read in can be reached now (#569): false while something covers it, a
 * sheet, a pushed screen, the runner or a share that arrived over it, or while it leaves. A covered
 * screen stays composed, so whatever opens a window of its own, a dialog, shows only while this is true:
 * the window would otherwise float over what covers it, live. The state that opened it is kept, so it
 * shows again when its screen does. True outside the shell.
 */
val LocalReachable = compositionLocalOf { true }

/**
 * The opening the screen this is read in was composed for (#569), which [Navigator.close] takes, so a
 * screen closes itself and not whatever is on top by then. Null outside the stack.
 */
val LocalStackEntry = staticCompositionLocalOf<StackEntry?> { null }

/**
 * What closes the screen this is read in: its own opening ([Navigator.close]), not whatever is on top
 * when a write lands. Outside the stack, back.
 */
@Composable
fun rememberCloseThis(): () -> Unit {
    val navigator = LocalNavigator.current
    val entry = LocalStackEntry.current
    return remember(navigator, entry) {
        {
            if (entry != null) navigator.close(entry.id) else navigator.back()
        }
    }
}

/** Whether a stacked screen can be reached, as [StackedScreens] hands it to its content. */
@JvmInline
internal value class Reach(
    val reachable: Boolean,
)

/**
 * Each stacked screen's content by its opening's id, as movable content (#569): a screen keeps one
 * composition, what it remembers and the holders it keeps, an editor's draft among them, as it moves
 * between the pushed layers, the sheet's frame and [Covered], where each layer's own keyed loop would
 * have composed it afresh. Each provides [LocalUncovered] from its own count, which moves with it.
 *
 * Not snapshot state: [of] makes a screen's content once, the first time it's composed, and [keepOnly]
 * lets go of the screens no layer holds any more.
 */
internal class StackedScreens(
    private val content: State<@Composable (Route) -> Unit>,
) {
    private val made = HashMap<Long, @Composable (Reach) -> Unit>()

    /** [entry]'s screen, given whether it can be reached now: what [LocalUncovered] counts. */
    fun of(entry: StackEntry): @Composable (Reach) -> Unit =
        made.getOrPut(entry.id) {
            movableContentOf { reach: Reach ->
                Uncovering(reach.reachable) {
                    CompositionLocalProvider(
                        LocalReachable provides reach.reachable,
                        LocalStackEntry provides entry,
                    ) { content.value(entry.route) }
                }
            }
        }

    /** Lets go of every screen but those in [ids]. */
    fun keepOnly(ids: Set<Long>) {
        made.keys.retainAll(ids)
    }
}

/** [content] with [LocalUncovered] counting each time it turns [interactive] again. */
@Composable
private fun Uncovering(
    interactive: Boolean,
    content: @Composable () -> Unit,
) {
    var uncovered by remember { mutableIntStateOf(0) }
    val covered = remember { booleanArrayOf(!interactive) }
    LaunchedEffect(interactive) {
        if (interactive && covered[0]) uncovered++
        covered[0] = !interactive
    }
    CompositionLocalProvider(LocalUncovered provides uncovered, content)
}

/**
 * The stacked screens no layer shows (#569): a sheet under a sheet, a sheet under a pushed screen, or a
 * pushed screen deeper than the one under the top. Composed, so each keeps what it holds for back, but
 * neither drawn nor reachable, under everything else.
 */
@Composable
internal fun Covered(
    entries: List<StackEntry>,
    screens: StackedScreens,
) {
    if (entries.isEmpty()) return
    Inert(true, Modifier.fillMaxSize().graphicsLayer { alpha = 0f }) {
        for (entry in entries) key(entry.id) { screens.of(entry)(Reach(reachable = false)) }
    }
}
