package com.casco.kettle.shell.nav

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Taps on the tab already showing, as [TabNavigator]'s `onReselect` hears them. An event rather than
 * state: a count kept as state would scroll a tab to its top again each time it came back on screen.
 */
val LocalTabReselects = staticCompositionLocalOf<Flow<Tab>> { emptyFlow() }
