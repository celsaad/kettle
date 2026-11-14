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
import com.casco.kettle.domain.PlanSummary
import com.casco.kettle.domain.PlannedExercise
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.planSummary
import com.casco.kettle.domain.toDisplayWeight
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.i18n.weightUnit
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card

/**
 * What Today queues up (#412): the next slot of the active program, or a scheduled rest day, each a
 * card as the boards draw it. The week itself is the program row's above, so a card names only its day.
 *
 * It says what the workout holds, the exercises and their loads, and no duration: the estimate it
 * carried counted a reps set as its rest alone. Everything that can grow is capped by lines or rows,
 * never by a height, which would clip the card at a large text size. [start] hears the session params
 * of whichever button is pressed.
 */
@Composable
fun NextUpCard(
    nextUp: NextUp,
    units: UnitSystem,
    start: (SessionParams) -> Unit,
    preview: (SessionParams) -> Unit,
) = when (nextUp) {
    is NextUp.Queued -> QueuedCard(nextUp, units, start, preview)
    is NextUp.Rest -> RestDayCard(nextUp, start)
}

/**
 * Everything above Start opens the preview (#280), so the exercises past the fourth are one tap away
 * without starting anything. Its words name it, kicker to loads, so it takes no label of its own, which
 * would only drift from what's on screen; the click label is what a reader hears it does. Start stays a
 * separate target, never inside this one.
 */
@Composable
private fun QueuedCard(
    queued: NextUp.Queued,
    units: UnitSystem,
    start: (SessionParams) -> Unit,
    preview: (SessionParams) -> Unit,
) {
    val colors = KettleTheme.colors
    val summary = remember(queued.workout, queued.exercises) { planSummary(queued.workout, queued.exercises) }
    Column(
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth()
            .card(HeroRadius)
            .padding(Spacing.three),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // A floor, never a height: its text sets it at any real size, but it is a control.
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.today_previewAction),
                    role = Role.Button,
                ) { preview(queued.sessionParams) },
        ) {
            Text(heroKicker(queued, summary), style = KettleType.smallMedium, color = colors.textSecondary)
            // User data, so its length is theirs, and two lines names any workout.
            Text(
                queued.workout.name,
                Modifier.padding(top = Spacing.one),
                style = KettleType.subtitle,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            WeekNotes(queued.weekNotes)
            PlannedExercises(summary.exercises, units)
        }
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
 * "Next · Day 5 · 3 exercises, 12 sets": the slot as its day alone, since the program row above names
 * the week, or as the week when the day has no name; nothing for a workout no program queued. Sets only
 * when every piece of work is one ([PlanSummary.sets]).
 */
@Composable
private fun heroKicker(
    queued: NextUp.Queued,
    summary: PlanSummary,
): String {
    val slot = queued.weekNumber?.let { week -> queued.weekDay?.takeIf { it.isNotEmpty() } ?: weekLabel(week, null) }
    val count = summary.exercises.size
    val exercises =
        if (count ==
            0
        ) {
            null
        } else {
            pluralText(R.plurals.today_exercises, R.string.today_exercises_zero, count)
        }
    val contents =
        exercises?.let {
            summary.sets?.let { sets ->
                stringResource(
                    R.string.today_exercisesAndSets,
                    it,
                    pluralText(R.plurals.today_sets, R.string.today_sets_zero, sets),
                )
            } ?: it
        }
    return listOfNotNull(stringResource(R.string.today_next), slot, contents).joinToString(" · ")
}

/**
 * The first [HERO_EXERCISES], each name against its load, then "+N more": a long workout would push Off
 * the plan off the screen, and the preview this card opens lists them all. A name is the user's and
 * wraps; the load never does.
 */
@Composable
private fun PlannedExercises(
    exercises: List<PlannedExercise>,
    units: UnitSystem,
) {
    if (exercises.isEmpty()) return
    val colors = KettleTheme.colors
    Column(Modifier.padding(top = Spacing.two)) {
        exercises.take(HERO_EXERCISES).forEach { exercise ->
            ListRowSeparator()
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.two),
                horizontalArrangement = Arrangement.spacedBy(Spacing.two),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(exercise.name, Modifier.weight(1f), style = KettleType.default, color = colors.text)
                exercise.loadKg?.let { kg ->
                    Text(
                        stringResource(
                            R.string.format_weight,
                            stringResource(weightUnit(units)),
                            jsNumber(toDisplayWeight(kg, units)),
                        ),
                        style = KettleType.smallMedium,
                        color = colors.text,
                        maxLines = 1,
                    )
                }
            }
        }
        val more = exercises.size - HERO_EXERCISES
        if (more > 0) {
            ListRowSeparator()
            Text(
                pluralText(R.plurals.today_moreBlocks, R.string.today_moreBlocks_zero, more),
                Modifier.padding(top = Spacing.two),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * A scheduled rest day: deliberately no Start, since a filled primary action would argue with the
 * program. It names the next session, *what* and never *when*: a run of rest slots means it isn't
 * necessarily tomorrow, and the strip above shows how many are left. "Train anyway" is an outline in
 * ink, and jumps to that session; the empty session under the card stays as always.
 */
@Composable
private fun RestDayCard(
    rest: NextUp.Rest,
    start: (SessionParams) -> Unit,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth()
            .card(HeroRadius)
            .padding(Spacing.three),
    ) {
        Text(weekLabel(rest.weekNumber, rest.weekDay), style = KettleType.smallMedium, color = colors.accentCalmText)
        Text(
            stringResource(R.string.today_restDayTitle),
            Modifier.padding(top = Spacing.one),
            style = KettleType.subtitle,
            color = colors.text,
        )
        rest.skipTo?.let { next ->
            val session = listOfNotNull(weekLabel(next.week, next.day), rest.skipToWorkout).joinToString(" · ")
            Text(
                stringResource(R.string.today_restDayNext, session),
                Modifier.padding(top = Spacing.one),
                style = KettleType.default,
                color = colors.textSecondary,
            )
        }
        WeekNotes(rest.weekNotes)
        rest.skipTo?.let { skipTo ->
            Box(
                Modifier
                    .padding(top = Spacing.three)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(BigButtonShape)
                    .border(1.dp, colors.textSecondary, BigButtonShape)
                    .clickable(role = Role.Button) { start(skipTo) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.today_restDaySkip), style = KettleType.heading, color = colors.text)
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

/**
 * "Week 2 · Tuesday": the number translated, the day the user's own. An empty `day` is none, as RN
 * tested it for truth: the schema takes `day: ""`.
 */
@Composable
internal fun weekLabel(
    number: Long,
    day: String?,
): String {
    val week = stringResource(R.string.programs_week, number)
    return if (day.isNullOrEmpty()) week else "$week · $day"
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
 * Three steps for someone who has just installed: the seeded library gives a first run something to
 * start, but nothing said what the rest of the app is for. Nothing here is tappable, deliberately: each
 * step's affordance is one tap away elsewhere on the screen, the starter-pack link directly under this
 * card for step 3.
 *
 * Step 2 names no tab, since Build merged into this screen and "in Build" became an instruction to go
 * where you already are. Step 3 carries the claim the store listing is written on, a library that is a
 * file you own, and may not imply the app generates anything: it never contacts a model, which the
 * Play zero-data-collected declaration rests on, so the assistant stays outside ("have an AI draft
 * one"). Nor may it carry training intent (the ownership entry in `docs/decisions.md`): format, never
 * advice.
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
private val HeroRadius = 20.dp

/** How many exercises the hero card lists before "+N more", as the board draws three. */
private const val HERO_EXERCISES = 4
private val BigButtonShape = RoundedCornerShape(16.dp)
private val FirstRunRadius = 22.dp
private val BadgeSize = 24.dp
