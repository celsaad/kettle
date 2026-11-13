package com.casco.kettle.shell.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.casco.kettle.shell.theme.KettleTheme

/**
 * The tabs, one entry at a time: NavHost switches between them and holds nothing else, since the back
 * stack is the [navigator]'s. [onPlace] is told which place to show.
 */
@Composable
internal fun Destinations(
    navigator: Navigator,
    content: @Composable (Route) -> Unit,
    onPlace: (Place) -> Unit,
) {
    val navController = rememberNavController()
    LaunchedEffect(navigator.tab) {
        val route = navigator.tab.name
        val showing = navController.currentDestination?.route
        if (showing == route) return@LaunchedEffect
        navController.navigate(route) {
            // One entry at a time, so NavHost's own back handler never has anything to pop. Back is the
            // navigator's. The entry showing is the one replaced: popping to the start destination does
            // nothing once the start has itself been replaced, and the stack then grows.
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
    val place = navigator.place
    SideEffect { onPlace(place) }

    NavHost(
        navController = navController,
        startDestination = Tab.Today.name,
        modifier = Modifier.fillMaxSize(),
        // As RN's tab bar did: a tab switch has no transition of its own.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        for (tab in Tab.entries) {
            composable(tab.name) {
                // Opaque to the top, strip included, as NativeSheet is: the window behind is Material's
                // gray, not the theme's (#227). Here and not in each screen, so every tab gets it.
                Box(Modifier.fillMaxSize().background(KettleTheme.colors.background)) {
                    content(navigator.tabRoute(tab))
                }
            }
        }
    }
}

/**
 * What the sheet shows (#197): the route on top of the [navigator]'s sheets, below the strip
 * [SheetChrome] draws over, which it leaves clear as RN's `ModalHeader` did. [onComposed] hears the route
 * composed, or null for nothing, after each composition, so the sheet can hold its arrival until the
 * screen it slides in is there.
 */
@Composable
internal fun NativeSheet(
    navigator: Navigator,
    content: @Composable (Route) -> Unit,
    onComposed: (Route?) -> Unit,
) {
    val route = navigator.sheet
    SideEffect { onComposed(route) }
    if (route == null) return
    // A fresh screen per route and params: progress for one exercise pushed over another's keeps none of
    // its state. Opaque to the top, strip included: the sheet's picture is this view's.
    key(route) {
        Box(
            Modifier
                .fillMaxSize()
                .background(KettleTheme.colors.background)
                .padding(top = SheetStripHeight),
        ) { content(route) }
    }
}

/**
 * What the runner's layer shows (#302): the runner, opened with the start its slot holds. Its own
 * colours whatever Settings' Appearance says (`RunnerColors`): `runnerShown` keeps the system bars'
 * icons light over it, which a light scheme here would leave unreadable, and its orange field darkens
 * them itself. A fresh screen per opening of the slot.
 */
@Composable
internal fun NativeRunner(
    navigator: Navigator,
    content: @Composable (Route) -> Unit,
) {
    val slot = navigator.runner ?: return
    // Through [content], as a tab's and a sheet's screens are, so a test can stand one in for the runner.
    val route = Route(Destination.Session, slot.start?.routeParams().orEmpty())
    key(slot) {
        KettleTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize().background(KettleTheme.colors.background)) { content(route) }
        }
    }
}

@Composable
internal fun TabBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
) {
    val colors = KettleTheme.colors
    // The canvas's bar (#411): the selected tab an ink-filled pill with its icon in the background's
    // colour, the inverse the theme's text and background already contrast at, and its label in ink and
    // bold; the others in the secondary text. On the screen's own background, a hairline above.
    val itemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = colors.background,
            selectedTextColor = colors.text,
            indicatorColor = colors.text,
            unselectedIconColor = colors.textSecondary,
            unselectedTextColor = colors.textSecondary,
        )
    // The theme's background under the hairline too: the border is a translucent ink, which over the
    // window behind the bar read as a dark rule in the light scheme.
    Column(Modifier.background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        NavigationBar(containerColor = colors.background) {
            for (tab in Tab.entries) {
                val selected = tab == current
                val name = stringResource(tab.label)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    // The label names every tab, so the icon names none: a description too would announce
                    // each tab twice.
                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                    label = { TabLabel(name, selected) },
                    // Every tab labelled, as Material 3's bar is (#265) and the canvas draws it.
                    alwaysShowLabel = true,
                    colors = itemColors,
                )
            }
        }
    }
}

/**
 * A tab's label on one line, at the size the device's font scale asks for while that fits its slot, and
 * shrunk until it does when it doesn't, never below its size at 1x (#265). Labels share the bar's
 * width, and wrapped they broke mid-word in every locale at 2x ("Workout/s", "Bibliote/ca",
 * "ワーク/アウト"). A fixed cap on the scale was tried and can't work: 1.3x fit on a 411dp phone, but on
 * a 360dp one ja's ワークアウト wrapped at 1.15x, and the cap that fits there throws large text away on
 * every wider phone, and on labels as short as 履歴. Only the label: the screens above the bar still scale
 * all the way.
 *
 * [BasicText] because Material's [Text] has no auto-size yet, so the item's style and colour are taken
 * from the locals [NavigationBarItem] sets for its label, as [Text] would.
 */
@Composable
private fun TabLabel(
    name: String,
    selected: Boolean,
) {
    val weight = if (selected) FontWeight.Bold else FontWeight.Medium
    val style = LocalTextStyle.current.merge(color = LocalContentColor.current, fontWeight = weight)
    val fontScale = LocalDensity.current.fontScale
    val autoSize =
        if (fontScale > 1f) {
            TextAutoSize.StepBased(minFontSize = style.fontSize / fontScale, maxFontSize = style.fontSize)
        } else {
            null
        }
    BasicText(name, style = style, maxLines = 1, autoSize = autoSize)
}
