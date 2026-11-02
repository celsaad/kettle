package com.casco.kettle.shell.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.NextUp
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.workoutShape
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card

/**
 * What Home queues up, as RN's `components/next-up-card.tsx`, which holds why it shows no block chips
 * and why it is the page's heading rather than a box on it. Everything that can grow is capped by
 * lines, never by a height, which would clip the card at a large text size. [start] hears the
 * session params of whichever button is pressed.
 */
@Composable
fun NextUpCard(
    nextUp: NextUp,
    start: (SessionParams) -> Unit,
) = when (nextUp) {
    is NextUp.Queued -> QueuedCard(nextUp, start)
    is NextUp.Rest -> RestDayCard(nextUp, start)
}

@Composable
private fun QueuedCard(
    queued: NextUp.Queued,
    start: (SessionParams) -> Unit,
) {
    val colors = KettleTheme.colors
    val shape = remember(queued.workout, queued.exercises) { workoutShape(queued.workout, queued.exercises) }
    Column(Modifier.padding(top = Spacing.three)) {
        val label = queued.weekNumber?.let { stringResource(R.string.today_nextUpWeek, weekLabel(it, queued.weekDay)) }
        Text(label ?: stringResource(R.string.today_nextUp), style = KettleType.label, color = colors.accentText)
        // User data, so its length is theirs, and two lines names any workout.
        Text(
            queued.workout.name,
            Modifier.padding(top = Spacing.one),
            style = KettleType.subtitle,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Two lines, not one: a mixed workout's summary drops its tail, the duration, at phone width,
        // and the duration is the part you decide on.
        Text(
            workoutShapeText(shape),
            Modifier.padding(top = Spacing.two),
            style = KettleType.default,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        WeekNotes(queued.weekNotes)
        Row(
            Modifier
                .padding(top = Spacing.three)
                .fillMaxWidth()
                .heightIn(min = BigButtonHeight)
                .clip(BigButtonShape)
                .background(colors.accent)
                .clickable(role = Role.Button) { start(queued.sessionParams) },
            horizontalArrangement = Arrangement.spacedBy(Spacing.two, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayTriangle()
            Text(stringResource(R.string.today_startSession), style = KettleType.heading, color = colors.onAccent)
        }
    }
}

/**
 * A scheduled rest day: no summary and deliberately no Start, since a filled primary action would
 * argue with the program. "Train anyway" is text weight in an outline, and jumps to the next slot that
 * runs something; the empty session under the card stays as always. The copy claims nothing about
 * *when*, because a run of rest slots means the next workout isn't necessarily tomorrow.
 */
@Composable
private fun RestDayCard(
    rest: NextUp.Rest,
    start: (SessionParams) -> Unit,
) {
    val colors = KettleTheme.colors
    Column(Modifier.padding(top = Spacing.three)) {
        Text(
            stringResource(R.string.today_restDayWeek, weekLabel(rest.weekNumber, rest.weekDay)),
            style = KettleType.label,
            color = colors.textSecondary,
        )
        Text(
            stringResource(R.string.today_restDayTitle),
            Modifier.padding(top = Spacing.one),
            style = KettleType.subtitle,
            color = colors.text,
        )
        Text(
            stringResource(R.string.today_restDayBody),
            Modifier.padding(top = Spacing.one),
            style = KettleType.default,
            color = colors.textSecondary,
        )
        WeekNotes(rest.weekNotes)
        rest.skipTo?.let { skipTo ->
            Box(
                Modifier
                    .padding(top = Spacing.three)
                    .fillMaxWidth()
                    .heightIn(min = BigButtonHeight)
                    .clip(BigButtonShape)
                    .border(1.dp, colors.border, BigButtonShape)
                    .clickable(role = Role.Button) { start(skipTo) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.today_restDaySkip), style = KettleType.heading, color = colors.accentText)
            }
        }
    }
}

/**
 * The week's own note, user data rendered verbatim and often the only thing telling a recovery day
 * from a day off. Two lines: it is unbounded, and the one field that could push the list off screen.
 */
@Composable
private fun WeekNotes(notes: String?) {
    if (notes.isNullOrEmpty()) return
    Text(
        notes,
        Modifier.padding(top = Spacing.two),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "Week 2 · Tuesday": the number translated, the day the user's own. */
@Composable
private fun weekLabel(
    number: Long,
    day: String?,
): String {
    val week = stringResource(R.string.programs_week, number)
    return if (day == null) week else "$week · $day"
}

/** Decorative geometry, so a fixed size: the button around it is what grows. */
@Composable
private fun PlayTriangle() {
    val color = KettleTheme.colors.onAccent
    Canvas(Modifier.size(13.dp, 16.dp)) {
        val triangle =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(0f, size.height)
                close()
            }
        drawPath(triangle, color)
    }
}

/**
 * Three steps for someone who has just installed, as RN's `components/first-run-card.tsx`, which holds
 * why its wording is what it is. Nothing here is tappable, deliberately: each step's affordance is one
 * tap away elsewhere on the screen, the starter-pack link directly under this card for step 3.
 */
@Composable
fun FirstRunCard() {
    val colors = KettleTheme.colors
    val steps =
        listOf(
            R.string.today_firstRun_step1Title to R.string.today_firstRun_step1Body,
            R.string.today_firstRun_step2Title to R.string.today_firstRun_step2Body,
            R.string.today_firstRun_step3Title to R.string.today_firstRun_step3Body,
        )
    Column(
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth()
            .card(FirstRunRadius)
            .padding(Spacing.three),
        verticalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Text(stringResource(R.string.today_firstRun_title), style = KettleType.label, color = colors.accentText)
        steps.forEachIndexed { index, (title, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                // A minimum rather than a size: it holds a numeral, which has to grow with the text.
                Box(
                    Modifier
                        .sizeIn(minWidth = BadgeSize, minHeight = BadgeSize)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.accentSoft)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = KettleType.smallMedium, color = colors.accentText)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = KettleType.smallMedium, color = colors.text)
                    Text(stringResource(body), style = KettleType.small, color = colors.textSecondary)
                }
            }
        }
    }
}

/** A minimum, as every control's height is, so the label survives a large text size. */
private val BigButtonHeight = 56.dp
private val BigButtonShape = RoundedCornerShape(16.dp)
private val FirstRunRadius = 22.dp
private val BadgeSize = 24.dp
