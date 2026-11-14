package com.casco.kettle.shell.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.rankByName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.FieldShape
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.NoResults
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.list.fieldFill
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

/** What the Library tab draws in its Exercises section. */
@Composable
fun LibraryRoute() = LibraryScreen(LocalLatestFiles.current, LocalTabReselects.current, LocalNavigator.current::push)

/**
 * The library's exercises as rows (#234), under Library's tabs (#413), which hold its title and New
 * exercise: a name search, the Type filter beside it, and rows that name each one's type before its
 * target. [open] is a push, a parameter so a test hears each one.
 *
 * [reselected] carries taps on the tab already showing; one on Library scrolls back to the top.
 */
@Composable
fun LibraryScreen(
    files: LatestFiles,
    reselected: Flow<Tab>,
    open: (Route) -> Unit,
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
                    bottom = Spacing.four,
                ),
        ) {
            item(key = "header") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                    // What the filter left rather than the whole library: with Reps chosen and nothing
                    // typed, the placeholder describes that subset, and the honest count is its own.
                    SearchBar(
                        query,
                        { query = it },
                        pluralText(
                            R.plurals.library_searchPlaceholder,
                            R.string.library_searchPlaceholder_zero,
                            visible.size,
                        ),
                        Modifier.weight(1f),
                    )
                    TypeFilter(filter) { filter = it }
                }
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
 * The Type button and its menu (#413), where seven chips scrolled sideways: "Type" until one is chosen,
 * then that type's name, so a filtered list says so. Its visible words are its name, which Voice Access
 * matches; what it's set to is its state ("all types", "Hold"), and it says whether the menu is open. Each
 * item is selectable, so a reader hears which is on.
 */
@Composable
private fun TypeFilter(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = KettleTheme.colors
    var open by remember { mutableStateOf(false) }
    val all = selected == ALL
    val chosen = stringResource(Filters[selected].label)
    val state = if (all) stringResource(R.string.library_filterAllTypes) else chosen
    Box(Modifier.padding(top = Spacing.three)) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clip(FieldShape)
                .background(fieldFill())
                .clickable(role = Role.Button) { open = true }
                .semantics {
                    stateDescription = state
                    if (open) {
                        collapse {
                            open = false
                            true
                        }
                    } else {
                        expand {
                            open = true
                            true
                        }
                    }
                }.padding(horizontal = Spacing.two + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (all) stringResource(R.string.library_filterType) else chosen,
                style = KettleType.smallMedium,
                color = colors.text,
            )
        }
        DropdownMenu(open, { open = false }, containerColor = colors.backgroundElement) {
            Filters.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(item.label),
                            style = KettleType.default,
                            color = colors.text,
                            fontWeight = if (index == selected) FontWeight.Bold else null,
                        )
                    },
                    onClick = {
                        onSelect(index)
                        open = false
                    },
                    modifier = Modifier.semantics { this.selected = index == selected },
                )
            }
        }
    }
}

/** One exercise: its name, then its type and its target in the reader's units, "Reps · 4 × 8–12 · 48.5 lb". */
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    units: UnitSystem,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    val type = Filters.firstOrNull { it.type?.isInstance(exercise.config) == true }?.label
    val summary = exerciseTarget(exercise.config, units)
    Box(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        ListRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = KettleType.heading, color = colors.text)
                Text(
                    // Placeholders are numbered alphabetically: summary, then type.
                    type?.let { stringResource(R.string.library_typedSummary, summary, stringResource(it)) } ?: summary,
                    style = KettleType.small,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** [Filters]' index for All, the filter that keeps everything. */
private const val ALL = 0
