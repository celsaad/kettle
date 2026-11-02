package com.casco.kettle.shell.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.emptyStateCard

/**
 * The way into the bundled starter packs, in its two homes: under the first-run card, and in the empty
 * state, never both. It names what arrives, not "Import", the machinery it arrives through.
 */
@Composable
internal fun StarterPackLink(
    modifier: Modifier,
    onClick: () -> Unit,
) = HomeLink(stringResource(R.string.today_starterPacks), modifier, onClick)

/**
 * A link that is only its words, left on the text column rather than centred under a button, where it
 * read as a weaker second button. A 48dp floor, never a height, so it survives a large text size.
 */
@Composable
internal fun HomeLink(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
) = Text(
    text,
    modifier
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .wrapContentHeight(Alignment.CenterVertically),
    style = KettleType.smallMedium,
    color = KettleTheme.colors.accentText,
)

/** A library with no workouts at all, and the two ways out of it: build one, or add a starter pack. */
@Composable
internal fun NoWorkouts(onStarterPacks: () -> Unit) {
    val colors = KettleTheme.colors
    Column(Modifier.emptyStateCard(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.build_emptyTitle), style = KettleType.heading, color = colors.text)
        Text(stringResource(R.string.build_emptyBody), style = KettleType.default, color = colors.textSecondary)
        StarterPackLink(Modifier, onStarterPacks)
    }
}
