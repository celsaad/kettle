package com.casco.kettle.shell.nav

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.runner.RecoveryPrompt
import com.casco.kettle.shell.theme.KettleTheme
import kotlin.coroutines.cancellation.CancellationException

/**
 * The app's chrome in one composition (#377): the tab with its bar, the pushed screens, the sheet over a
 * scrim and the runner, siblings stacked in that order and all following the [navigator]. [content] is
 * the screen each route draws: the route table, or a test's stand-ins.
 *
 * Everything under the top layer stays drawn, live, and is [Inert]: it takes no touch and TalkBack
 * doesn't reach it. The sheet's transitions ([ShellMotion]) move these layers in place, so the screen a
 * sheet covers is the live one under its scrim, not a picture of it. A stacked screen none of them
 * shows stays composed too, undrawn ([Covered]), so back to it finds it as it was left (#569).
 *
 * Every dismissal (the ×, hardware and predictive back, a grabber drag) is a back. Two of Material's are
 * left out on purpose: a scrim tap and a drag from the content would each drop an edit, and the editors
 * have no confirm.
 */
@Composable
internal fun Shell(
    navigator: Navigator,
    content: @Composable (Route) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = remember(navigator) { ShellMotion(navigator, scope) }
    DisposableEffect(navigator, motion) {
        navigator.moves = motion
        onDispose { if (navigator.moves === motion) navigator.moves = null }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Where a pushed screen comes from and leaves by: the end, which is the left in a right-to-left layout,
    // as Back's chevron mirrors.
    val endward = size.width * if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    SideEffect { motion.endward = endward }
    // Through the dispatcher, as hardware back goes, so a screen's own back handler hears it first.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val requestBack: () -> Unit = { if (dispatcher != null) dispatcher.onBackPressed() else navigator.back() }
    SheetBack(navigator, motion)
    val latestContent = rememberUpdatedState(content)
    val screens = remember { StackedScreens(latestContent) }

    val shown = shownBy(navigator.entries)
    val playing = motion.playing
    val runner = navigator.runnerShown
    val drawn = listOfNotNull(shown.beneath, shown.pushed, shown.sheet, playing?.leavingPushed, playing?.leavingSheet)
    // Ids are never reused, so a screen in neither list now is gone for good.
    SideEffect { screens.keepOnly((navigator.entries + drawn).mapTo(HashSet()) { it.id }) }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Covered(navigator.entries.filter { it !in drawn }, screens)
        SplashExit(LocalLatestFiles.current, LocalSplashExit.current)
        // The bar stays under what covers the tab, as it was under the sheet's scrim when that was a picture.
        Inert(navigator.sheets.isNotEmpty() || runner) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { Destinations(navigator, content) }
                TabBar(navigator.tab, navigator::select)
            }
        }
        // Keyed by the opening's id, not the route: a route opened again while it's still leaving is a new screen.
        for (entry in listOfNotNull(shown.beneath, shown.pushed, playing?.leavingPushed)) {
            key(entry.id) {
                val stacking =
                    when (entry) {
                        playing?.leavingPushed -> Stacking.Leaving
                        shown.pushed -> Stacking.Shown
                        else -> Stacking.Beneath
                    }
                val interactive = stacking == Stacking.Shown && shown.sheet == null && !runner
                PushedScreen(stacking, interactive, motion, endward) { screens.of(entry)(interactive) }
            }
        }
        if (shown.sheet != null || playing?.leavingSheet != null) {
            Scrim(motion)
            SheetFrame(
                shown.sheet,
                playing?.leavingSheet,
                interactive = shown.sheet != null && !runner,
                motion,
                frameHeight = size.height.toFloat(),
                requestBack,
                screens,
            )
        }
        // The runner (#302), full screen over everything, the sheet it grew from included.
        NativeRunner(navigator, content)
        // The launch's Resume / Finish prompt (#307): a dialog, a window of its own over every layer.
        RecoveryPrompt()
    }
}

/**
 * Back while a sheet or a pushed screen shows, with Material's predictive motion first: a sheet shrinks
 * towards its bottom, a pushed screen starts out by its side. Composed before any screen, so a screen's
 * own back handler, the runner's, still comes first; the activity's, for the tabs, comes after.
 */
@Composable
private fun SheetBack(
    navigator: Navigator,
    motion: ShellMotion,
) {
    PredictiveBackHandler(enabled = navigator.place.stacked) { events ->
        // A transition cut short leaves the sheet where it had got to, and the gesture goes on from there.
        motion.grab()
        val pushed = navigator.place == Place.Pushed
        val startX = motion.backX.floatValue
        val startScale = motion.backScale.floatValue
        try {
            events.collect { motion.followBack(it.progress, pushed, startX, startScale) }
        } catch (cancelled: CancellationException) {
            motion.settleBack()
            throw cancelled
        }
        // Enabled only while a sheet or a pushed screen shows, but the runner can open in the same turn, and
        // the navigator then drops the back: the shrink has to come undone rather than stay part way.
        val before = navigator.entries
        navigator.back()
        if (navigator.entries === before) motion.settleBack()
    }
}

/** Where a pushed screen is in its stack: the one showing, the one under it, or one leaving. */
private enum class Stacking { Beneath, Shown, Leaving }

/**
 * A pushed screen (#489): full frame from the top, under the status bar with its Back row first, with no
 * chrome and no scrim, opaque in the theme's background.
 *
 * It stays composed under a sheet, under the screen pushed over it and under the runner, so it isn't
 * composed afresh when it shows again ([StackedScreens]).
 */
@Composable
private fun PushedScreen(
    stacking: Stacking,
    interactive: Boolean,
    motion: ShellMotion,
    endward: Float,
    content: @Composable () -> Unit,
) {
    val playing = motion.playing
    Inert(
        !interactive,
        Modifier.fillMaxSize().graphicsLayer {
            if (stacking != Stacking.Beneath) {
                val leaving = stacking == Stacking.Leaving
                draw(pushedLook(playing, motion.progress.floatValue, leaving, endward, motion.backX.floatValue))
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(KettleTheme.colors.background).statusBarsPadding()) { content() }
    }
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
 * Each stacked screen's content by its opening's id, as movable content (#569): a screen keeps one
 * composition, what it remembers and the holders it keeps, an editor's draft among them, as it moves
 * between the pushed layers, the sheet's frame and [Covered], where each layer's own keyed loop would
 * have composed it afresh. Each provides [LocalUncovered] from its own count, which moves with it.
 *
 * Not snapshot state: [of] makes a screen's content once, the first time it's composed, and [keepOnly]
 * lets go of the screens no layer holds any more.
 */
private class StackedScreens(
    private val content: State<@Composable (Route) -> Unit>,
) {
    private val made = HashMap<Long, @Composable (Boolean) -> Unit>()

    /** [entry]'s screen, given whether it can be reached now: what [LocalUncovered] counts. */
    fun of(entry: StackEntry): @Composable (Boolean) -> Unit =
        made.getOrPut(entry.id) {
            movableContentOf { interactive: Boolean -> Uncovering(interactive) { content.value(entry.route) } }
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
private fun Covered(
    entries: List<StackEntry>,
    screens: StackedScreens,
) {
    if (entries.isEmpty()) return
    Inert(true, Modifier.fillMaxSize().graphicsLayer { alpha = 0f }) {
        for (entry in entries) key(entry.id) { screens.of(entry)(false) }
    }
}

/** Material's scrim, black at 32%, over what the sheet covers. It takes every touch and does nothing with it. */
@Composable
private fun Scrim(motion: ShellMotion) {
    val playing = motion.playing
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scrimAlpha(playing, motion.progress.floatValue) }
            .background(SCRIM)
            .pointerInput(Unit) {},
    )
}

/**
 * The sheet (#155, #179): its top at the status bar plus [TOP_GAP], capped at [MAX_WIDTH] and centred,
 * the top corners rounded, with the grabber and × over its strip. A sheet over a sheet replaces it inside
 * the one frame, the [leaving] screen live until it has gone. Growing into the runner and back, it's laid
 * out full frame, square, with no chrome.
 */
@Suppress("LongParameterList") // Each is what one part of the frame draws from; a holder would serve one call site.
@Composable
private fun SheetFrame(
    shown: StackEntry?,
    leaving: StackEntry?,
    interactive: Boolean,
    motion: ShellMotion,
    frameHeight: Float,
    requestBack: () -> Unit,
    screens: StackedScreens,
) {
    val playing = motion.playing
    val full = playing?.transition.growing()
    val density = LocalDensity.current
    val gap = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TOP_GAP
    val frame = Frame(offscreen = frameHeight, top = with(density) { gap.toPx() })
    SideEffect { motion.frame = frame }
    val radius = with(density) { RADIUS.toPx() }
    val flingAt = with(density) { ShellMotion.FLING_DP_PER_S.dp.toPx() }
    val place = if (full) Modifier.fillMaxSize() else Modifier.padding(top = gap).widthIn(max = MAX_WIDTH).fillMaxSize()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            place
                .graphicsLayer {
                    val p = motion.progress.floatValue
                    draw(frameLook(playing, p, frame, motion.dragY.floatValue, motion.backScale.floatValue))
                    val corner = radius * cornerFraction(playing, p)
                    shape = RoundedCornerShape(topStart = corner, topEnd = corner, bottomEnd = 0f, bottomStart = 0f)
                    clip = true
                }.background(KettleTheme.colors.background)
                // The × and the grabber read first in here, then the screen (SheetChrome's traversal indices).
                .semantics { isTraversalGroup = true },
        ) {
            for (entry in listOfNotNull(shown, leaving)) {
                key(entry.id) {
                    SheetScreen(entry == leaving, interactive, motion) {
                        screens.of(entry)(entry != leaving && interactive)
                    }
                }
            }
            if (!full) {
                val drag =
                    GrabberDrag(
                        onDrag = motion::drag,
                        onStarted = motion::grab,
                        onStopped = { velocity -> motion.letGo(velocity, frameHeight, flingAt, requestBack) },
                        immediately = motion.settling,
                    )
                Inert(!interactive) { SheetChrome(requestBack, drag) }
            }
        }
    }
}

/**
 * One sheet's screen inside the sheet, arriving or [leaving], its own traversal group so its order is
 * its own. It starts below the strip the chrome draws over, as RN's `ModalHeader` left it.
 */
@Composable
private fun SheetScreen(
    leaving: Boolean,
    interactive: Boolean,
    motion: ShellMotion,
    content: @Composable () -> Unit,
) {
    val playing = motion.playing
    val shift = with(LocalDensity.current) { ShellMotion.SHARED_AXIS_DP.dp.toPx() }
    Inert(
        leaving || !interactive,
        Modifier
            .fillMaxSize()
            .graphicsLayer { draw(sheetContentLook(playing, motion.progress.floatValue, leaving, shift)) }
            .semantics { isTraversalGroup = true },
    ) {
        Box(Modifier.fillMaxSize().background(KettleTheme.colors.background).padding(top = SheetStripHeight)) {
            content()
        }
    }
}

/**
 * [content], or while [inert], [content] drawn as it is but out of reach: no touch lands in it, TalkBack
 * neither reads it nor moves into it, and focus leaves it and can't move back in. What a layer above
 * covers, or a screen on its way out.
 *
 * The focus it held is cleared as it turns inert, in the same frame, before the layer now on top can
 * focus anything of its own; with it goes the keyboard a covered field had up. That clear runs as an
 * effect of the frame the layer turns inert in, so a screen arriving on top that requests focus from a
 * SideEffect or a DisposableEffect composed before this layer would have it cleared: screens request
 * focus from a LaunchedEffect, which runs after.
 */
@Composable
private fun Inert(
    inert: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // Not state: read as the layer turns inert, never drawn.
    val focused = remember { booleanArrayOf(false) }
    DisposableEffect(inert) {
        if (inert && focused[0]) focusManager.clearFocus(force = true)
        onDispose {}
    }
    Box(
        modifier
            .then(if (inert) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (inert) cancelFocusChange() } }
            .onFocusChanged { focused[0] = it.hasFocus }
            .focusGroup(),
    ) {
        content()
        if (inert) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
            )
        }
    }
}

/** Sets a layer to [look]. */
private fun GraphicsLayerScope.draw(look: Look) {
    translationX = look.translationX
    translationY = look.translationY
    scaleX = look.scale
    scaleY = look.scale
    alpha = look.alpha
    transformOrigin = look.origin
}

private val TOP_GAP = 12.dp
private val MAX_WIDTH = 640.dp
private val RADIUS = 16.dp

/** Material's scrim: black at 32%. Decorative, with nothing set on it, so no contrast pairing. */
private val SCRIM = Color(red = 0, green = 0, blue = 0, alpha = 82)
