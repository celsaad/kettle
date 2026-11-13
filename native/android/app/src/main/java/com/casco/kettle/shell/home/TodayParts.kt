package com.casco.kettle.shell.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.formatLongDate
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.RowStartButton
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

// Today's own pieces (#412): the date over the title, "Off the plan"'s heading and its empty session.

/**
 * The date, "Today", and the gear that is the app's only way to Settings: a bordered 36dp square inside
 * a 48dp target, icon-only, so it carries the name. The date in the locale's order, read at [now].
 */
@Composable
internal fun TitleRow(
    now: Instant,
    onSettings: () -> Unit,
) {
    val colors = KettleTheme.colors
    val settings = stringResource(R.string.common_settings)
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                formatLongDate(now, locale, TimeZone.currentSystemDefault()),
                style = KettleType.small,
                color = colors.textSecondary,
            )
            Text(
                stringResource(R.string.today_title),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = colors.text,
            )
        }
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onSettings)
                .semantics { contentDescription = settings },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(GearShape)
                    .background(colors.backgroundElement)
                    .border(1.dp, colors.border, GearShape),
                contentAlignment = Alignment.Center,
            ) {
                TypeIcon(R.drawable.icon_settings, KettleType.default, colors.textSecondary)
            }
        }
    }
}

/** "Off the plan", and "All workouts" beside it once there are any, opening Library's Workouts. */
@Composable
internal fun OffThePlanHeader(
    hasWorkouts: Boolean,
    onAllWorkouts: () -> Unit,
) {
    Row(Modifier.padding(top = Spacing.four), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.today_offThePlan),
            Modifier.weight(1f).semantics { heading() },
            style = KettleType.heading,
            color = KettleTheme.colors.text,
        )
        if (hasWorkouts) HomeLink(stringResource(R.string.today_allWorkouts), Modifier, onAllWorkouts)
    }
}

/** Empty session, as a row like the workouts beside it, its start a plus: it begins with nothing. */
@Composable
internal fun EmptySessionRow(onStart: () -> Unit) {
    val colors = KettleTheme.colors
    ListRow {
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.today_emptySession), style = KettleType.heading, color = colors.text)
            Text(
                stringResource(R.string.today_emptySessionDetail),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
        RowStartButton(stringResource(R.string.today_startEmpty), plus = true, onClick = onStart)
    }
}

private val GearShape = RoundedCornerShape(10.dp)
