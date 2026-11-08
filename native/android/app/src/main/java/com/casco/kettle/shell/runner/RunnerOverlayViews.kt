package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.rankByName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

/**
 * A dimmed backdrop that dismisses, and a sheet at the bottom. Drawn over the runner's body, which the
 * frame hides from accessibility while one is open, so a screen reader stays in here.
 */
@Composable
internal fun OverlaySheet(
    dismissLabel: String?,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA))
                .clickable(role = Role.Button, onClick = onDismiss)
                .then(
                    if (dismissLabel == null) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier.semantics { contentDescription = dismissLabel }
                    },
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .imePadding()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(RunnerColors.backgroundElement)
                // Absorbs the taps nothing inside handles, as M3's Surface does: they fell through to the
                // backdrop behind and closed the sheet, the pad losing what had been typed into it.
                .pointerInput(Unit) {}
                .semantics { paneTitle = title }
                .padding(Spacing.three),
            verticalArrangement = Arrangement.spacedBy(Spacing.two),
        ) {
            content()
        }
    }
}

/**
 * The number pad (§3.4): a keypad of its own rather than the OS keyboard, which would cover the
 * controls it edits on screens that don't scroll. Typing replaces the value ([PadEntry]).
 */
@Composable
internal fun NumberPad(
    request: PadRequest,
    onDismiss: () -> Unit,
) {
    var entry by remember(request) { mutableStateOf(PadEntry(request.seed, request.allowDecimal)) }
    OverlaySheet(stringResource(R.string.session_numberPad_dismiss), onDismiss, request.label) {
        Caps(request.label.uppercase())
        Row(verticalAlignment = Alignment.Bottom) {
            Numeral(entry.display, 56.sp)
            if (request.unit != null) {
                Text(
                    " ${request.unit}",
                    Modifier.padding(bottom = Spacing.two),
                    style = KettleType.heading,
                    color = RunnerColors.textSecondary,
                )
            }
        }
        val keys = DIGITS + listOf(if (request.allowDecimal) "." else null, "0", BACKSPACE)
        for (row in keys.chunked(KEYS_PER_ROW)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                for (key in row) {
                    val modifier = Modifier.weight(1f)
                    if (key == null) {
                        Box(modifier)
                    } else {
                        // A digit is named by its own text; only the backspace glyph needs a label.
                        val description = if (key == BACKSPACE) stringResource(R.string.common_delete) else null
                        PadKey(key, description, modifier) {
                            entry = if (key == BACKSPACE) entry.backspace() else entry.type(key.single())
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.three),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextControl(stringResource(R.string.common_cancel), onDismiss)
            val value = entry.value
            PrimaryButton(
                stringResource(R.string.session_numberPad_set),
                {
                    value?.let {
                        request.confirm(it)
                        onDismiss()
                    }
                },
                Modifier.weight(1f),
                enabled = value != null,
            )
        }
    }
}

@Composable
private fun PadKey(
    key: String,
    description: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, RunnerColors.border, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(key, style = KettleType.heading, color = RunnerColors.text)
    }
}

/**
 * The swap and add pickers (§3.5), one component: [replacing] is the name being swapped out, null when
 * adding. Search shows above eight candidates and matches anywhere, ignoring case and accents.
 */
@Composable
internal fun ExercisePicker(
    replacing: String?,
    candidates: List<Exercise>,
    onCancel: () -> Unit,
    onSelect: (Exercise) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(candidates, query) { rankByName(candidates, query) { it.name } }
    val title =
        stringResource(
            if (replacing ==
                null
            ) {
                R.string.session_adhoc_pickerTitle
            } else {
                R.string.session_swap_title
            },
        )
    OverlaySheet(stringResource(R.string.session_swap_dismiss), onCancel, title) {
        Caps(title)
        Text(
            if (replacing == null) {
                stringResource(R.string.session_adhoc_pickerBody)
            } else {
                stringResource(R.string.session_swap_replacing, replacing)
            },
            style = KettleType.small,
            color = RunnerColors.textSecondary,
        )
        if (candidates.size > SEARCH_THRESHOLD) PickerSearch(query) { query = it }
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.session_adhoc_noMatches, query.trim()),
                        Modifier.padding(vertical = Spacing.three),
                        style = KettleType.small,
                        color = RunnerColors.textSecondary,
                    )
                }
            }
            items(shown, key = { it.id }) { exercise ->
                Text(
                    exercise.name,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onSelect(exercise) }
                        .padding(vertical = Spacing.three),
                    style = KettleType.heading,
                    color = RunnerColors.text,
                )
            }
        }
        TextControl(stringResource(R.string.common_cancel), onCancel, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun PickerSearch(
    query: String,
    onChange: (String) -> Unit,
) {
    val label = stringResource(R.string.session_adhoc_search)
    BasicTextField(
        query,
        onChange,
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTarget)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, RunnerColors.border, RoundedCornerShape(12.dp))
            .semantics { contentDescription = label }
            .padding(horizontal = Spacing.three, vertical = Spacing.three),
        singleLine = true,
        textStyle = KettleType.default.copy(color = RunnerColors.text),
        cursorBrush = SolidColor(RunnerColors.accent),
        decorationBox = { field ->
            Box {
                if (query.isEmpty()) Text(label, style = KettleType.default, color = RunnerColors.textSecondary)
                field()
            }
        },
    )
}

private val DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
private const val BACKSPACE = "⌫"
private const val KEYS_PER_ROW = 3
private const val SEARCH_THRESHOLD = 8
private const val BACKDROP_ALPHA = 0.6f
