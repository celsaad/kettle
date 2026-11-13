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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
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
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerPalette
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * A dimmed backdrop that dismisses, and a sheet at the bottom. Drawn over the runner's body, which the
 * frame hides from accessibility while one is open, so a screen reader stays in here. [inset] keeps
 * the sheet inside the runner's column, as RN's pad and pickers are: only Coming up spans the width.
 */
@Composable
internal fun OverlaySheet(
    dismissLabel: String?,
    onDismiss: () -> Unit,
    title: String,
    inset: Boolean = true,
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
                .then(
                    if (inset) {
                        Modifier.padding(
                            start = Spacing.three,
                            end = Spacing.three,
                            bottom = Spacing.three,
                        )
                    } else {
                        Modifier
                    },
                ).clip(SheetShape)
                .background(runnerColors.backgroundElement)
                .topBorder(runnerColors)
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
        Caps(request.label.uppercase(), Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            Numeral(entry.display, PAD_NUMERAL, Modifier.alignByBaseline(), leading = PAD_LEADING)
            if (request.unit != null) {
                Text(
                    " ${request.unit}",
                    Modifier.alignByBaseline(),
                    style = KettleType.heading.copy(fontSize = PAD_UNIT),
                    color = runnerColors.textSecondary,
                )
            }
        }
        PadKeys(request.allowDecimal) { key ->
            entry =
                when (key) {
                    is PadKeyKind.Character -> entry.type(key.char)
                    PadKeyKind.Backspace -> entry.backspace()
                }
        }
        Row(
            Modifier.padding(top = Spacing.one),
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlineButton(
                stringResource(R.string.common_cancel),
                onDismiss,
                Modifier.weight(1f),
                style = QuietStyle,
                look = ButtonLook.Compact,
            )
            val value = entry.value
            PrimaryButton(
                stringResource(R.string.session_numberPad_set),
                {
                    value?.let {
                        request.confirm(it)
                        onDismiss()
                    }
                },
                Modifier.weight(SET_WEIGHT),
                enabled = value != null,
                look = ButtonLook.Compact,
            )
        }
    }
}

/** The 3×4 grid; [onKey] gets a digit, the point, or backspace. */
@Composable
private fun PadKeys(
    allowDecimal: Boolean,
    onKey: (PadKeyKind) -> Unit,
) {
    val point = if (allowDecimal) PadKeyKind.Character('.') else null
    val keys = DIGITS + listOf(point, PadKeyKind.Character('0'), PadKeyKind.Backspace)
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        for (row in keys.chunked(KEYS_PER_ROW)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                for (key in row) {
                    val modifier = Modifier.weight(1f)
                    if (key == null) {
                        // Drawn as an empty key, as RN's, so the grid reads whole; nothing to press.
                        Box(modifier.heightIn(min = KEY_HEIGHT).clip(KeyShape).keyFace(runnerColors))
                    } else {
                        PadKey(key, modifier) { onKey(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(
    key: PadKeyKind,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // A digit is named by its own text; only the backspace icon needs a label.
    val description = if (key is PadKeyKind.Backspace) stringResource(R.string.common_delete) else null
    Box(
        modifier
            .heightIn(min = KEY_HEIGHT)
            .clip(KeyShape)
            .keyFace(runnerColors)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val style = KettleType.heading.copy(fontSize = KEY_LABEL)
        when (key) {
            is PadKeyKind.Character -> Text(key.char.toString(), style = style, color = runnerColors.text)
            PadKeyKind.Backspace -> TypeIcon(R.drawable.icon_backspace, style, runnerColors.text)
        }
    }
}

/** RN's sheets draw only their top edge, round the corners: the shape's outline, cut off below them. */
private fun Modifier.topBorder(colors: RunnerPalette): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        clipRect(bottom = SHEET_RADIUS.toPx()) {
            inset(stroke / 2) {
                drawOutline(
                    SheetShape.createOutline(size, layoutDirection, this),
                    colors.border,
                    style = Stroke(stroke),
                )
            }
        }
    }

/** A key's look, and a picker option's: the screen's own dark inside the lighter sheet, outlined. */
internal fun Modifier.keyFace(colors: RunnerPalette): Modifier =
    background(colors.background).border(1.dp, colors.border, KeyShape)

/** A keypad key: one that types its character, drawn as that character, or backspace, drawn as its icon. */
private sealed interface PadKeyKind {
    data class Character(
        val char: Char,
    ) : PadKeyKind

    data object Backspace : PadKeyKind
}

private val DIGITS = ('1'..'9').map(PadKeyKind::Character)
private const val KEYS_PER_ROW = 3
private const val BACKDROP_ALPHA = 0.55f
private const val SET_WEIGHT = 1.6f
private val SHEET_RADIUS = 22.dp
private val SheetShape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS)
internal val KeyShape = RoundedCornerShape(14.dp)
private val KEY_HEIGHT = 54.dp
private val KEY_GAP = 6.dp
private val KEY_LABEL = 22.sp
private val PAD_NUMERAL = 44.sp

/** RN's pad value sets 50 on 44. */
private const val PAD_LEADING = 50f / 44f
private val PAD_UNIT = 18.sp
