package com.casco.kettle.shell.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.Spacing

// `src/components/list-row.tsx`, which holds the reasoning: a list of peers is rows, with no fill, no
// border and no horizontal padding, and a hairline between neighbours. The metrics are shared rather
// than retyped per screen, since sibling lists that look subtly unlike each other read as a bug.

/** A row's floor, as `heightIn(min =)`: the row grows with the reader's text size. */
val ListRowMinHeight = 56.dp
val ListRowVerticalPadding = Spacing.two + 2.dp

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
