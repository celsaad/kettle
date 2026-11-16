package com.casco.kettle.shell.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ExerciseProgress
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.formatFullDate
import com.casco.kettle.shell.i18n.formatMonthDay
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.Instant

/*
 * One exercise's reading, as Exercise detail's Progress draws it (#413): Getting stronger's own row, its
 * number and its chart, drawn from the row rather than read again, so the two never disagree.
 */

/** Where the exercise is now, how far it moved, and since when. */
@Composable
internal fun Headline(
    row: ExerciseProgress,
    readings: Readings,
    dates: Dates,
) = FlowRow(
    Modifier.padding(vertical = Spacing.three),
    horizontalArrangement = Arrangement.spacedBy(Spacing.two),
) {
    val colors = KettleTheme.colors
    Text(readings.reading(row.latest), Modifier.alignByBaseline(), style = KettleType.heading, color = colors.text)
    // Getting stronger's row's form, for its reason: no hue at all, since a dip is information and
    // achievement takes none either (#414). The arrow is drawn, so the signed change is what's said.
    // Flat goes secondary here, though Stats' row inks it: Stats folds its flat rows away, and a
    // headline's "no change" in ink would read as news (#491).
    val spoken = readings.delta(row.delta)
    Text(
        readings.change(row.delta),
        Modifier.alignByBaseline().semantics { contentDescription = spoken },
        style = KettleType.smallMedium,
        color = if (row.delta == 0.0) colors.textSecondary else colors.text,
    )
    Text(
        stringResource(R.string.exerciseProgress_since, dates.monthDay(row.sessions.first().at)),
        Modifier.alignByBaseline(),
        style = KettleType.small,
        color = colors.textSecondary,
    )
}

@Composable
internal fun ChartCard(
    row: ExerciseProgress,
    readings: Readings,
    dates: Dates,
    window: ClosedRange<Instant>,
    description: String? = null,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(Spacing.three),
    ) {
        ProgressChart(
            row.sessions,
            window,
            readings,
            dateLabel = dates::monthDay,
            description = description,
        )
        // The key is for the dots, so it's drawn only when there are dots other than the latest's.
        if (row.sessions.any { it.newBest }) {
            Row(
                Modifier.padding(top = Spacing.two),
                horizontalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(KeyDot)
                        .background(colors.accent, CircleShape),
                )
                Text(
                    stringResource(R.string.exerciseProgress_newBest),
                    style = KettleType.small,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** Dates in the reader's locale and zone. */
internal class Dates(
    val locale: Locale,
    private val zone: TimeZone,
) {
    fun monthDay(at: Instant) = formatMonthDay(at, locale, zone)

    fun fullDate(at: Instant) = formatFullDate(at, locale, zone)
}

private val KeyDot = 8.dp
