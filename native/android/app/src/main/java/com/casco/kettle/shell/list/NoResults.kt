package com.casco.kettle.shell.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.emptyStateCard

/**
 * What a search that matched nothing shows: a blank body under intact chrome reads as a bug, not an
 * answer. Only the no-match half, since a first run's empty state is its screen's own: "no workouts
 * yet" is wrong in front of someone with forty who mistyped one. A blank [query] means a filter
 * emptied the list rather than typing, which only Library's type chips can do, and quoting an empty
 * string back explains nothing.
 */
@Composable
fun NoResults(query: String) {
    val colors = KettleTheme.colors
    Column(
        Modifier.emptyStateCard(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.search_noMatchTitle), style = KettleType.heading, color = colors.text)
        val trimmed = query.trim()
        Text(
            if (trimmed.isEmpty()) {
                stringResource(R.string.search_noMatchFilterBody)
            } else {
                stringResource(R.string.search_noMatchBody, trimmed)
            },
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}
