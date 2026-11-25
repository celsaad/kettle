package com.casco.kettle.shell.nav

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.Spacing

/**
 * A pushed screen's top row (#489): a round Back at the start, as the boards draw it, then the screen's
 * own [content]: Settings' title, or a detail's actions pushed to the end by a weighted spacer. Back
 * says only "Back", never the screen beneath: Exercise detail opens from Library and Progress both,
 * and a name read off the stack would go stale with the next way in.
 *
 * The tap is the system's back, through the activity's dispatcher, so a screen that asks before it is
 * left asks here too, and the navigator pops it as any other back.
 */
@Composable
fun BackRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit = {},
) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val label = stringResource(R.string.common_back)
    val colors = KettleTheme.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .sizeIn(minWidth = TARGET, minHeight = TARGET)
                .clickable(role = Role.Button) { dispatcher?.onBackPressed() }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            // An edge with no fill of its own, so lineStrong's 3:1 (theme-notes.md), as Today's round Start.
            Box(
                Modifier.size(CIRCLE).border(1.dp, colors.lineStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.icon_chevron_back), null, Modifier.size(20.dp), tint = colors.text)
            }
        }
        content()
    }
}

/** The board's 44dp circle, in the 48dp target every other control here takes. */
private val CIRCLE = 44.dp
private val TARGET = 48.dp
