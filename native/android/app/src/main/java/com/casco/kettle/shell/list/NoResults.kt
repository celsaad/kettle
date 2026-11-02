package com.casco.kettle.shell.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card

/**
 * What a search that matched nothing shows, as RN's `NoResults`: a blank body under intact chrome
 * reads as a bug, not an answer.
 */
@Composable
fun NoResults(query: String) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth()
            .card(16.dp)
            .padding(Spacing.three),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.search_noMatchTitle), style = KettleType.heading, color = colors.text)
        Text(
            stringResource(R.string.search_noMatchBody, query.trim()),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}
