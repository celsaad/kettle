package com.casco.kettle.shell.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.shell.R
import com.casco.kettle.shell.art.ExerciseArt
import com.casco.kettle.shell.art.hasArt
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.unitsOrDevice
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.nav.BackRow
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon
import com.casco.kettle.shell.theme.card
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The param naming the exercise on Exercise detail and on its YAML view: `id`, the word the exercise
 * editor's route already reads, so Edit passes it on unchanged. Getting stronger's rows open it by it too.
 */
const val EXERCISE_PARAM = "id"

/** What the route table draws at `Destination.ExerciseDetail`, opened from Library's Exercises and Getting stronger. */
@Composable
fun ExerciseDetailRoute(params: Map<String, String>) {
    // Once per opening: the window doesn't creep under a reader who leaves the screen up.
    val now = remember { Clock.System.now() }
    ExerciseDetailScreen(
        params[EXERCISE_PARAM],
        LocalLatestFiles.current,
        now,
        TimeZone.currentSystemDefault(),
        LocalNavigator.current::push,
    )
}

/**
 * Exercise detail (#413), apart from the editor: what the exercise is (its type, target, drawing and
 * notes), how it has gone (Stats' own reading and chart, then the last few sessions), and what uses it.
 * YAML shows it as the file holds it, and Edit opens the form. [open] pushes a route, a parameter so a
 * test hears each.
 *
 * Nothing before the library is read, so it never says the exercise is gone before it has looked.
 */
@Composable
fun ExerciseDetailScreen(
    id: String?,
    files: LatestFiles,
    now: Instant,
    zone: TimeZone,
    open: (Route) -> Unit,
) {
    val library = files.library
    val exercise = library?.exercises?.firstOrNull { it.id == id }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            SheetColumn
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.six)
                .navigationBarsPadding(),
        ) {
            // Back before anything is read: a screen restored after process death is blank until then,
            // and has no × of its own to leave by.
            if (library == null) {
                BackRow()
                return@Column
            }
            if (exercise == null) {
                BackRow()
                // Gone from the library but not from the log: Getting stronger still lists it (#489), so
                // its name, by the id the log keeps, and how it went stay; there is nothing to edit.
                if (id != null) {
                    Text(
                        id,
                        Modifier.padding(top = Spacing.two).semantics { heading() },
                        style = KettleType.subtitle,
                        color = KettleTheme.colors.text,
                    )
                }
                Text(
                    stringResource(R.string.exerciseDetail_missing),
                    Modifier.padding(top = Spacing.one),
                    style = KettleType.default,
                    color = KettleTheme.colors.textSecondary,
                )
                if (id != null) {
                    ProgressSection(id, files, now, zone)
                    RecentSessions(id, files, zone)
                }
                return@Column
            }
            Actions(
                onYaml = { open(Route(Destination.LibraryYaml, mapOf(EXERCISE_PARAM to exercise.id))) },
                onEdit = { open(Route(Destination.ExerciseEditor, mapOf(EXERCISE_PARAM to exercise.id))) },
            )
            Identity(exercise, files)
            ProgressSection(exercise.id, files, now, zone)
            RecentSessions(exercise.id, files, zone)
            UsedIn(exercise, library, open)
        }
    }
}

/** Back, then YAML and Edit top right, as the board draws them: the two things to do with what's below. */
@Composable
private fun Actions(
    onYaml: () -> Unit,
    onEdit: () -> Unit,
) = BackRow {
    Spacer(Modifier.weight(1f))
    // The file's own name for it, in the file's face, with "</>" (#486), as the board draws it.
    OutlinedPill(stringResource(R.string.exerciseDetail_yaml), onYaml, code = true)
    OutlinedPill(stringResource(R.string.exerciseDetail_edit), onEdit)
}

/** Its type, its name, its target with the rest between sets, its drawing and its notes. */
@Composable
private fun Identity(
    exercise: Exercise,
    files: LatestFiles,
) {
    val colors = KettleTheme.colors
    typeLabel(exercise.config)?.let {
        Text(
            stringResource(it),
            Modifier
                .padding(top = Spacing.two)
                .clip(TagShape)
                .background(colors.backgroundSelected)
                .padding(horizontal = Spacing.two, vertical = 3.dp),
            style = KettleType.smallMedium,
            color = colors.textSecondary,
        )
    }
    // User data, verbatim.
    Text(
        exercise.name,
        Modifier.padding(top = Spacing.two).semantics { heading() },
        style = KettleType.subtitle,
        color = colors.text,
    )
    Text(
        exerciseSummary(exercise.config, files.unitsOrDevice()),
        Modifier.padding(top = Spacing.one),
        style = KettleType.default,
        color = colors.textSecondary,
    )
    // On a card when there is a drawing, and no empty card when there isn't: most exercises are the user's.
    if (hasArt(exercise.id)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.three)
                .card()
                .padding(Spacing.three),
            contentAlignment = Alignment.Center,
        ) {
            ExerciseArt(exercise.id)
        }
    }
    exercise.notes?.takeIf { it.isNotBlank() }?.let {
        Text(it, Modifier.padding(top = Spacing.three), style = KettleType.default, color = colors.text)
    }
}

/**
 * A small outlined button, named by its own words, in a 48dp target. [code] for one about the YAML: its
 * words in mono after a decorative "</>".
 */
@Composable
private fun OutlinedPill(
    label: String,
    onClick: () -> Unit,
    code: Boolean = false,
) {
    val colors = KettleTheme.colors
    val style = if (code) KettleType.smallMedium.copy(fontFamily = FontFamily.Monospace) else KettleType.smallMedium
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .border(1.dp, colors.textSecondary, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.three),
        horizontalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (code) TypeIcon(R.drawable.icon_code, style, colors.text)
        Text(label, style = style, color = colors.text)
    }
}

private val PillShape = RoundedCornerShape(percent = 50)
private val TagShape = RoundedCornerShape(6.dp)
