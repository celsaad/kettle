package com.casco.kettle.shell.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.rankByName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.export.logShareRefused
import com.casco.kettle.shell.export.rememberFileShare
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.NewItemButton
import com.casco.kettle.shell.list.NewItemButtonClearance
import com.casco.kettle.shell.list.NoResults
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.ScrollsToTopOnReselect
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/** What the route table draws at `Destination.Library`, the tab. */
@Composable
fun LibraryRoute() {
    val share = rememberFileShare()
    val navigator = LocalNavigator.current
    LibraryScreen(LocalLatestFiles.current, LocalTabReselects.current, navigator::push) {
        logShareRefused(share?.library())
    }
}

/**
 * Library (#234), the library's exercises as rows, as RN's `(tabs)/library.tsx`, now the empty stub at
 * this path: a name search, the type filter, and the header's Export and Import. [open] is
 * a push and [export] the library's share, parameters so a test hears each one.
 *
 * [reselected] carries taps on the tab already showing; one on Library scrolls back to the top.
 */
@Composable
fun LibraryScreen(
    files: LatestFiles,
    reselected: Flow<Tab>,
    open: (Route) -> Unit,
    export: () -> Unit,
) {
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(Tab.Library, reselected, listState)
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(LocalConfiguration.current.locales[0])

    var query by rememberSaveable { mutableStateOf("") }
    // An index into Filters, since a class doesn't survive saved state.
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val library = files.library
    val all = remember(library) { library?.exercises.orEmpty().filter { it.config !is ExerciseConfig.Rest } }
    val visible =
        remember(all, filter, query) {
            val type = Filters[filter].type
            rankByName(if (type == null) all else all.filter { type.isInstance(it.config) }, query) { it.name }
        }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = Spacing.three,
                    end = Spacing.three,
                    top = Spacing.two,
                    bottom = NewItemButtonClearance,
                ),
        ) {
            item(key = "header") {
                Header(export) { open(Route(Destination.Import)) }
                // What the filter left rather than the whole library: with Reps chosen and nothing typed,
                // the placeholder describes that subset, and the honest count is its own.
                SearchBar(
                    query,
                    { query = it },
                    pluralText(
                        R.plurals.library_searchPlaceholder,
                        R.string.library_searchPlaceholder_zero,
                        visible.size,
                    ),
                )
                FilterChips(filter) { filter = it }
                ListHeaderRule()
            }
            itemsIndexed(visible, key = { _, exercise -> exercise.id }) { index, exercise ->
                if (index > 0) ListRowSeparator()
                ExerciseRow(exercise, units) { open(Route(Destination.ExerciseEditor, mapOf("id" to exercise.id))) }
            }
            // No first-run card, unlike Programs: a library with no exercises is reachable only by deleting
            // every seeded one, and RN never had one. A search or a filter that empties the list does say
            // so, since a blank body under intact chrome reads as a broken screen.
            if (all.isNotEmpty() && visible.isEmpty()) item(key = "no-results") { NoResults(query) }
        }
        NewItemButton(stringResource(R.string.library_newExercise), Modifier.align(Alignment.BottomEnd)) {
            open(Route(Destination.ExerciseEditor))
        }
    }
}

/** The title, then Export and Import. */
@Composable
private fun Header(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val colors = KettleTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.library_title),
            Modifier.weight(1f).semantics { heading() },
            style = KettleType.subtitle,
            color = colors.text,
        )
        TextAction(stringResource(R.string.common_export), KettleType.smallMedium, colors.textSecondary, onExport)
        TextAction(stringResource(R.string.common_import), KettleType.smallMedium, colors.accentText, onImport)
    }
}

/** A type chip: its label and the config class it keeps, null for All. */
private class Filter(
    @StringRes val label: Int,
    val type: KClass<out ExerciseConfig>?,
)

/**
 * The type shortcuts, in the order a library tends to hold them: every type but rest, which is block
 * structure rather than something anyone browses for, and never lists here.
 */
private val Filters =
    listOf(
        Filter(R.string.library_filterAll, null),
        Filter(R.string.library_filterHiit, ExerciseConfig.Hiit::class),
        Filter(R.string.library_filterReps, ExerciseConfig.Reps::class),
        Filter(R.string.library_filterHold, ExerciseConfig.TimedHold::class),
        Filter(R.string.library_filterEmom, ExerciseConfig.Emom::class),
        Filter(R.string.library_filterAmrap, ExerciseConfig.Amrap::class),
        Filter(R.string.library_filterCardio, ExerciseConfig.Cardio::class),
    )

/**
 * The chips as one row that scrolls sideways, as RN's: seven wrap to two rows on a phone, three at a
 * large text size, so the row that saves room would take it. Each pill keeps RN's size inside a 48dp
 * target, which is why the row sits [ChipRowTop] below the field rather than RN's 16.
 */
@Composable
private fun FilterChips(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = KettleTheme.colors
    Row(
        Modifier.padding(top = ChipRowTop).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Filters.forEachIndexed { index, item ->
            val active = index == selected
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onSelect(index) }
                    .semantics { this.selected = active },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(item.label),
                    Modifier
                        .clip(ChipShape)
                        .then(
                            if (active) {
                                Modifier.background(colors.text)
                            } else {
                                Modifier.background(colors.backgroundElement).border(1.dp, colors.border, ChipShape)
                            },
                        ).padding(ChipPadding),
                    style = KettleType.small,
                    color = if (active) colors.onAccent else colors.textSecondary,
                )
            }
        }
    }
}

/** One exercise: its name, its target in the reader's units, and its type. */
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    units: UnitSystem,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = KettleType.heading, color = colors.text)
                Text(exerciseSummary(exercise.config, units), style = KettleType.small, color = colors.textSecondary)
            }
            ExerciseBadge(exercise.config)
        }
    }
}

private val ChipRowTop = Spacing.two
private val ChipShape = RoundedCornerShape(percent = 50)
private val ChipPadding = PaddingValues(horizontal = Spacing.three - 3.dp, vertical = 7.dp)
