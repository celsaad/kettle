package com.casco.kettle.shell.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.casco.kettle.shell.theme.KettleTheme

/**
 * The back stack of the destinations [routes] registers. Native ones draw their screen; an RN one draws
 * nothing, since the RN surface is a view of its own under this one ([ShellLayout]). [onLayer] is told
 * which layer to show.
 */
@Composable
internal fun Destinations(
    navigator: TabNavigator,
    routes: Map<String, Screen>,
    onLayer: (Layer) -> Unit,
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
    val layer = navigator.layer
    SideEffect { onLayer(layer) }

    NavHost(
        navController = navController,
        startDestination = Tab.Workouts.name,
        modifier = Modifier.fillMaxSize(),
        // As RN's tab bar did: a tab switch has no transition of its own.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        for (tab in Tab.entries) {
            val screen = routes.screenAt(tab.path)
            composable(tab.name) {
                if (screen !is Screen.Native) return@composable
                // Opaque to the top, strip included, as NativeSheet is: the window behind is Material's
                // gray, not the theme's (#227). Here and not in each screen, so every port gets it.
                Box(Modifier.fillMaxSize().background(KettleTheme.colors.background)) {
                    screen.content(navigator.params(tab.path))
                }
            }
        }
    }
}

/**
 * What a native sheet shows (#197): the screen registered at the path RN's stub reports, given that
 * report's params, below the strip [SheetChrome] draws over, which it leaves clear as RN's `ModalHeader`
 * does. [onComposed] hears the path and params composed, or null for nothing, after each composition,
 * so the sheet can hold its arrival until the screen it slides in is there.
 */
@Composable
internal fun NativeSheet(
    navigator: TabNavigator,
    routes: Map<String, Screen>,
    onComposed: (SheetContent?) -> Unit,
) {
    val path = navigator.nativeSheetPath
    val screen = path?.let(routes::screenAt) as? Screen.Native
    val content = path?.let { SheetContent(it, navigator.params(it)) }
    SideEffect { onComposed(content) }
    if (screen == null || content == null) return
    // A fresh screen per route and params, as RN mounts one: progress for one exercise pushed over
    // another's keeps none of its state. Opaque to the top, strip included, as an RN screen paints its
    // own: the sheet's picture is this view's, and a see-through strip showed RN's stub as it closed.
    key(content) {
        Box(
            Modifier
                .fillMaxSize()
                .background(KettleTheme.colors.background)
                .padding(top = SheetStripHeight),
        ) { screen.content(content.params) }
    }
}

@Composable
internal fun TabBar(
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
