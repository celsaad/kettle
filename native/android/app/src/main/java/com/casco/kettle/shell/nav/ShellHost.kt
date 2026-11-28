package com.casco.kettle.shell.nav

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
 * sheet covers is the live one under its scrim, not a picture of it.
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
    // Through the dispatcher, as hardware back goes, so a screen's own back handler hears it first.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val requestBack: () -> Unit = { if (dispatcher != null) dispatcher.onBackPressed() else navigator.back() }
    SheetBack(navigator, motion, endward)

    val shown = shownBy(navigator.stack)
    val playing = motion.motion
    val runner = navigator.runnerShown
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        SplashExit(LocalLatestFiles.current, LocalSplashExit.current)
        // The bar stays under what covers the tab, as it was under the sheet's scrim when that was a picture.
        Inert(navigator.stack.isNotEmpty() || runner) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { Destinations(navigator, content) }
                TabBar(navigator.tab, navigator::select)
            }
        }
        for (layer in listOfNotNull(shown.beneath, shown.pushed, playing?.leavingPushed)) {
            key(layer) {
                val role =
                    when (layer) {
                        playing?.leavingPushed -> Stacking.Leaving
                        shown.pushed -> Stacking.Shown
                        else -> Stacking.Beneath
                    }
                PushedScreen(role, role == Stacking.Shown && shown.sheet == null && !runner, motion, endward) {
                    content(layer.route)
                }
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
                content,
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
    endward: Float,
) {
    PredictiveBackHandler(enabled = navigator.place.stacked) { events ->
        motion.grab()
        val pushed = navigator.place == Place.Pushed
        try {
            events.collect { motion.followBack(it.progress, pushed, endward) }
        } catch (cancelled: CancellationException) {
            motion.settleBack()
            throw cancelled
        }
        val stack = navigator.stack
        navigator.back()
        // A back the navigator drops moves nothing, and the shrink has to come undone.
        if (navigator.stack === stack) motion.settleBack()
    }
}

/** Where a pushed screen is in its stack: the one showing, the one under it, or one leaving. */
private enum class Stacking { Beneath, Shown, Leaving }

/**
 * A pushed screen (#489): full frame from the top, under the status bar with its Back row first, with no
 * chrome and no scrim, opaque in the theme's background.
 */
@Composable
private fun PushedScreen(
    role: Stacking,
    interactive: Boolean,
    motion: ShellMotion,
    endward: Float,
    content: @Composable () -> Unit,
) {
    val playing = motion.motion
    Inert(
        !interactive,
        Modifier.fillMaxSize().graphicsLayer {
            if (role != Stacking.Beneath) {
                draw(
                    pushedLook(
                        playing,
                        motion.progress.floatValue,
                        role == Stacking.Leaving,
                        endward,
                        motion.backX.floatValue,
                    ),
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(KettleTheme.colors.background).statusBarsPadding()) { content() }
    }
}

/** Material's scrim, black at 32%, over what the sheet covers. It takes every touch and does nothing with it. */
@Composable
private fun Scrim(motion: ShellMotion) {
    val playing = motion.motion
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
    shown: Layer?,
    leaving: Layer?,
    interactive: Boolean,
    motion: ShellMotion,
    frameHeight: Float,
    requestBack: () -> Unit,
    content: @Composable (Route) -> Unit,
) {
    val playing = motion.motion
    val full = playing?.transition.growing()
    val density = LocalDensity.current
    val gap = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TOP_GAP
    val frame = Frame(offscreen = frameHeight, top = with(density) { gap.toPx() })
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
            for (layer in listOfNotNull(shown, leaving)) {
                key(layer) { SheetScreen(layer == leaving, interactive, motion) { content(layer.route) } }
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
    val playing = motion.motion
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
 * [content], or while [inert], [content] drawn as it is but out of reach: no touch lands in it, and
 * TalkBack neither reads it nor moves into it. What a layer above covers, or a screen on its way out.
 */
@Composable
private fun Inert(
    inert: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.then(if (inert) Modifier.clearAndSetSemantics {} else Modifier)) {
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
