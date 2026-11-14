package com.casco.kettle.shell.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.home.WorkoutsRoute
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Section
import com.casco.kettle.shell.nav.SectionedTabFrame
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.section
import com.casco.kettle.shell.programs.ProgramsRoute
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.TypeIcon

/** The Library tab (#413): "Library" and a + that makes whatever the section shows, over its sections. */
@Composable
internal fun LibraryTab(route: Route) {
    val navigator = LocalNavigator.current
    val section = route.section(Tab.Library) ?: return
    val new = NEW.getValue(section)
    SectionedTabFrame(
        Tab.Library,
        section,
        header = {
            Header(
                stringResource(new.label),
                onFile = { navigator.push(Route(Destination.YourFile)) },
                onNew = { navigator.push(Route(new.editor)) },
            )
        },
    ) {
        when (section) {
            Section.WORKOUTS -> WorkoutsRoute()
            Section.PROGRAMS -> ProgramsRoute()
            else -> LibraryRoute()
        }
    }
}

/**
 * "Library", the file's name under it, which opens the file sheet (#413), and the +. The name is the
 * link's own words, so it needs no label: it's what Voice Access matches, and the sheet it opens is
 * titled the same.
 */
@Composable
private fun Header(
    newLabel: String,
    onFile: () -> Unit,
    onNew: () -> Unit,
) {
    val colors = KettleTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.library_title),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = colors.text,
            )
            Row(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onFile),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    LIBRARY_FILE_NAME,
                    style = KettleType.small.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textSecondary,
                )
                Chevron()
            }
        }
        NewButton(newLabel, onNew)
    }
}

/**
 * The +, named for what it makes ("New workout"), since a plus says nothing. A 40dp accent disc inside
 * a 48dp target; white on `accent` is 3.64:1, which clears the 3:1 a meaningful graphic needs.
 */
@Composable
private fun NewButton(
    label: String,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            TypeIcon(R.drawable.icon_add, KettleType.heading, colors.onAccent)
        }
    }
}

/** What the + makes on a section: its name, and the editor it opens empty. */
private class NewItem(
    @StringRes val label: Int,
    val editor: Destination,
)

private val NEW =
    mapOf(
        Section.WORKOUTS to NewItem(R.string.build_newWorkout, Destination.WorkoutEditor),
        Section.PROGRAMS to NewItem(R.string.programs_newProgram, Destination.ProgramEditor),
        Section.EXERCISES to NewItem(R.string.library_newExercise, Destination.ExerciseEditor),
    )
