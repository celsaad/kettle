package com.casco.kettle.shell.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.theme.KettleColors
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The document as the board draws it: numbered lines, coloured, scrolling sideways rather than
 * wrapping, so an indent always reads as one. Selectable, for a part to be copied out; the numbers sit
 * outside the selection and out of TalkBack's reading, the code being what either is for.
 */
@Composable
internal fun YamlCode(
    yaml: String,
    modifier: Modifier = Modifier,
) {
    val colors = KettleTheme.colors
    val lines = remember(yaml) { yaml.trimEnd('\n').split('\n') }
    val coloured = remember(yaml, colors) { colouredYaml(lines, colors) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        // One text of all the numbers, in the code's own style: a text per number trimmed its line height
        // differently from the code's one block, and drifted out of line with it.
        Text(
            lines.indices.joinToString("\n") { "${it + 1}" },
            Modifier.widthIn(min = NUMBER_WIDTH).clearAndSetSemantics {},
            style = CodeStyle,
            color = colors.textSecondary,
            textAlign = TextAlign.End,
        )
        SelectionContainer(Modifier.horizontalScroll(rememberScrollState())) {
            Text(coloured, style = CodeStyle, softWrap = false)
        }
    }
}

private fun colouredYaml(
    lines: List<String>,
    colors: KettleColors,
): AnnotatedString =
    buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            if (i > 0) append('\n')
            for ((piece, part) in yamlParts(line)) withStyle(SpanStyle(color = part.colour(colors))) { append(piece) }
        }
    }

/** Keys in the accent, numbers in the calm accent, values in the text, the rest receding: all measured. */
private fun YamlPart.colour(colors: KettleColors): Color =
    when (this) {
        YamlPart.Key -> colors.accentText
        YamlPart.Number -> colors.accentCalmText
        YamlPart.Text -> colors.text
        YamlPart.Punctuation -> colors.textSecondary
    }

private val CodeStyle: TextStyle
    get() = KettleType.small.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

private val NUMBER_WIDTH = 20.dp
