package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.rankByName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * The add picker (§3.5); the swap's is a sheet of its own ([SwapSheet], #417). Search shows above eight
 * candidates and matches anywhere, ignoring case and accents.
 */
@Composable
internal fun ExercisePicker(
    candidates: List<Exercise>,
    onCancel: () -> Unit,
    onSelect: (Exercise) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(candidates, query) { rankByName(candidates, query) { it.name } }
    val title = stringResource(R.string.session_adhoc_pickerTitle)
    OverlaySheet(stringResource(R.string.common_cancel), onCancel, title) {
        Caps(title, Modifier.align(Alignment.CenterHorizontally))
        Text(
            stringResource(R.string.session_adhoc_pickerBody),
            Modifier.fillMaxWidth(),
            style = KettleType.small,
            color = runnerColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (candidates.size > SEARCH_THRESHOLD) PickerSearch(query) { query = it }
        PickerOptions(shown, query, onSelect)
        OutlineButton(
            stringResource(R.string.common_cancel),
            onCancel,
            Modifier.fillMaxWidth(),
            style = QuietStyle,
            look = ButtonLook.Compact,
        )
    }
}

/**
 * The candidates as rows with hairlines between, as every list of peers in the app is (AGENTS.md):
 * RN's picker drew each as a card, which the native one doesn't copy. [ListRow]'s metrics, with the
 * runner's own hairline, since it's dark whatever the scheme. Capped so a long library scrolls above
 * Cancel.
 */
@Composable
private fun PickerOptions(
    shown: List<Exercise>,
    query: String,
    onSelect: (Exercise) -> Unit,
) {
    LazyColumn(Modifier.heightIn(max = 320.dp)) {
        if (shown.isEmpty()) item { NoMatches(query) }
        itemsIndexed(shown, key = { _, exercise -> exercise.id }) { index, exercise ->
            if (index > 0) RunnerHairline()
            Box(Modifier.clickable(role = Role.Button) { onSelect(exercise) }) {
                ListRow { Text(exercise.name, style = KettleType.heading, color = runnerColors.text) }
            }
        }
    }
}

/** "No exercise matches …", where search leaves a picker empty. */
@Composable
internal fun NoMatches(query: String) {
    Text(
        stringResource(R.string.session_adhoc_noMatches, query.trim()),
        Modifier.fillMaxWidth().padding(vertical = Spacing.three),
        style = KettleType.small,
        color = runnerColors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

/** The hairline between a picker's rows: the runner's own, since its sheets are dark whatever the scheme. */
@Composable
internal fun RunnerHairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(runnerColors.border))

@Composable
internal fun PickerSearch(
    query: String,
    onChange: (String) -> Unit,
) {
    val label = stringResource(R.string.session_adhoc_search)
    val textStyle = KettleType.default.copy(fontSize = SEARCH_TEXT)
    BasicTextField(
        query,
        onChange,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTarget)
            .clip(KeyShape)
            .keyFace(runnerColors)
            .semantics { contentDescription = label }
            .padding(horizontal = CARD_PAD),
        singleLine = true,
        textStyle = textStyle.copy(color = runnerColors.text),
        cursorBrush = SolidColor(runnerColors.accent),
        decorationBox = { field ->
            Row(
                Modifier.heightIn(min = MinTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            ) {
                // Decorative: the field is named by its description, and the icon says nothing more.
                TypeIcon(R.drawable.icon_search, KettleType.default, runnerColors.textSecondary)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(label, style = textStyle, color = runnerColors.textSecondary)
                    field()
                }
            }
        },
    )
}

private const val SEARCH_THRESHOLD = 8
private val SEARCH_TEXT = 14.sp
