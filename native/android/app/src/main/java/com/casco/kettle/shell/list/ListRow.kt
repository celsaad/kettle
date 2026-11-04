package com.casco.kettle.shell.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.Spacing

// A list of peers is rows, not cards. These used to be cards: a filled surface with a border and a
// radius, one per item. A card is for one privileged object, and twenty-six bordered boxes down a
// screen is twenty-six frames competing with the names inside them, while the fill bought nothing,
// since nothing sits behind a row that it needs lifting off. So: no fill, no border, a hairline between
// neighbours, and no horizontal padding: the card's padding sat on top of the screen's, insetting
// every name past the title and search above it. The metrics are shared rather than retyped per screen,
// since sibling lists that look subtly unlike each other read as a bug, not a distinction. RN's
// `list-row.tsx` held this until its last list went native (#245).

/** For a row that can't be a [ListRow]: share these rather than retyping them. */
val ListRowMinHeight = 56.dp
val ListRowVerticalPadding = Spacing.two + 2.dp

/**
 * One row of a list. `heightIn(min =)`, never a fixed height, so it grows with the reader's text size,
 * and no horizontal padding, so it lines up with the screen's own inset.
 */
@Composable
fun ListRow(content: @Composable RowScope.() -> Unit) =
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowMinHeight)
            .padding(vertical = ListRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )

/** The hairline between two rows. Decorative geometry, so a fixed height is right. */
@Composable
fun ListRowSeparator(modifier: Modifier = Modifier) =
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KettleTheme.colors.border),
    )

/** The hairline that ends a list's header and starts the list, carrying the gap above it. */
@Composable
fun ListHeaderRule() = ListRowSeparator(Modifier.padding(top = Spacing.three))
