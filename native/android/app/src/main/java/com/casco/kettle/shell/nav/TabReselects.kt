package com.casco.kettle.shell.nav

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

/**
 * Taps on the tab already showing, as [Navigator]'s `onReselect` hears them. An event rather than
 * state: a count kept as state would scroll a tab to its top again each time it came back on screen.
 */
val LocalTabReselects = staticCompositionLocalOf<Flow<Tab>> { emptyFlow() }

/** Re-tap-to-top, which #153 dropped with RN's bar and each tab's port brings back for its own list. */
@Composable
fun ScrollsToTopOnReselect(
    tab: Tab,
    reselected: Flow<Tab>,
    listState: LazyListState,
) = LaunchedEffect(tab, reselected, listState) {
    reselected.filter { it == tab }.collect { listState.animateScrollToItem(0) }
}
