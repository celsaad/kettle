package com.casco.kettle.shell.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme

/**
 * The back stack of the destinations [routes] registers. Native ones draw their screen; an RN one draws
 * nothing, since the RN surface is a view of its own under this one ([ShellLayout]). [onRnShown] says
 * which of the two the current tab needs.
 */
@Composable
internal fun Destinations(
    navigator: TabNavigator,
    routes: Map<Tab, Screen>,
    onRnShown: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    LaunchedEffect(navigator.current) {
        val route = navigator.current.name
        val showing = navController.currentDestination?.route
        if (showing == route) return@LaunchedEffect
        navController.navigate(route) {
            // One entry at a time, so NavHost's own back handler never has anything to pop. Back is the
            // navigator's, and reaches it only after RN has dismissed whatever it has above the tabs.
            // The entry showing is the one replaced: popping to the start destination does nothing
            // once the start has itself been replaced, and the stack then grows.
            if (showing != null) {
                popUpTo(showing) {
                    inclusive = true
                    saveState = true
                }
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val rnShown = routes[navigator.current] == Screen.Rn
    SideEffect { onRnShown(rnShown) }

    NavHost(
        navController = navController,
        startDestination = Tab.Workouts.name,
        modifier = Modifier.fillMaxSize(),
        // As RN's tab bar did: a tab switch has no transition of its own.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        for ((tab, screen) in routes) {
            composable(tab.name) { if (screen is Screen.Native) screen.content() }
        }
    }
}

/** The tab bar, while RN shows nothing above the tabs. */
@Composable
internal fun Bar(navigator: TabNavigator) {
    if (navigator.barShown) TabBar(navigator.current, navigator::select)
}

@Composable
private fun TabBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
) {
    val colors = KettleTheme.colors
    // The pairing RN's bar used (src/app/(tabs)/_layout.tsx), so the contrast it was checked at holds.
    val itemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = colors.accentText,
            selectedTextColor = colors.accentText,
            indicatorColor = colors.backgroundSelected,
            unselectedIconColor = colors.textSecondary,
            unselectedTextColor = colors.textSecondary,
        )
    NavigationBar(containerColor = colors.backgroundElement) {
        for (tab in Tab.entries) {
            val selected = tab == current
            val name = stringResource(tab.label)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                // Only the selected tab shows its label, so only the others need the icon to name them.
                icon = { Icon(painterResource(tab.icon), contentDescription = name.takeUnless { selected }) },
                label = { Text(name) },
                // As RN's bar did: with four tabs, Material shows only the selected one's label, and
                // the icon moves up as it appears.
                alwaysShowLabel = false,
                colors = itemColors,
            )
        }
    }
}

private val Tab.label: Int
    @StringRes get() =
        when (this) {
            Tab.Workouts -> R.string.tabs_workouts
            Tab.Library -> R.string.tabs_library
            Tab.History -> R.string.tabs_history
            Tab.Programs -> R.string.tabs_programs
        }

private val Tab.icon: Int
    @DrawableRes get() =
        when (this) {
            Tab.Workouts -> R.drawable.tab_workouts
            Tab.Library -> R.drawable.tab_library
            Tab.History -> R.drawable.tab_history
            Tab.Programs -> R.drawable.tab_programs
        }
