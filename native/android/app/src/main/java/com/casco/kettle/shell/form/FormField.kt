package com.casco.kettle.shell.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.FieldUnit
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// The editors' text fields: the session editor's (#239) and the exercise form's (#241), one look.

/**
 * What a field takes: a number, a line of text, notes, which wrap and grow, or code, which wraps and
 * grows too but is never capitalized or corrected: YAML is whitespace-significant, and a keyboard that
 * "helpfully" capitalizes an id or swaps in a smart quote breaks it on the way in.
 */
enum class FieldKind { Number, Text, Notes, Code }

/**
 * How a field looks and behaves beyond its value and words. [minHeight] is a floor, never a height.
 * [fill] is the surface it sits on's opposite: the screen's background inside a card (the default),
 * the card's on the screen. [focus] for a field the screen puts the keyboard on.
 */
data class FieldOptions(
    val kind: FieldKind = FieldKind.Number,
    val minHeight: Dp = 46.dp,
    val fill: Color = Color.Unspecified,
    val focus: FocusRequester? = null,
)

/** A field the user types into, named by [name], since the label above it isn't tied to it. */
@Composable
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    name: String,
    placeholder: String,
    options: FieldOptions = FieldOptions(),
) {
    val colors = KettleTheme.colors
    val singleLine = options.kind == FieldKind.Number || options.kind == FieldKind.Text
    val fill = options.fill.takeOrElse { colors.background }
    BasicTextField(
        value,
        onValueChange,
        Modifier
            .then(options.focus?.let { Modifier.focusRequester(it) } ?: Modifier)
            .fillMaxWidth()
            .semantics { contentDescription = name },
        textStyle = KettleType.default.copy(fontSize = 15.sp, color = colors.text),
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    if (options.kind ==
                        FieldKind.Number
                    ) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Text
                    },
                autoCorrectEnabled = options.kind != FieldKind.Code,
            ),
        singleLine = singleLine,
        cursorBrush = SolidColor(colors.text),
        decorationBox = { input ->
            Box(
                Modifier
                    .fillMaxWidth()
                    // RN's height, as a floor: heightIn, never height, which clips at large text sizes.
                    .heightIn(min = options.minHeight)
                    .clip(FieldShape)
                    .border(1.dp, colors.border, FieldShape)
                    .background(fill)
                    .padding(horizontal = Spacing.two, vertical = if (singleLine) 0.dp else Spacing.two),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = KettleType.default.copy(fontSize = 15.sp), color = colors.textSecondary)
                }
                input()
            }
        },
    )
}

/** The suffix beside a field's label: kilograms or pounds as the reader's setting says, or its unit. */
@Composable
internal fun unitSuffix(
    unit: FieldUnit?,
    units: UnitSystem,
): String? =
    when (unit) {
        FieldUnit.Weight -> stringResource(if (units == UnitSystem.IMPERIAL) R.string.units_lb else R.string.units_kg)
        FieldUnit.Seconds -> stringResource(R.string.units_seconds)
        FieldUnit.Minutes -> stringResource(R.string.units_minutes)
        FieldUnit.Metres -> stringResource(R.string.units_metres)
        null -> null
    }

/** [label] with [unit]'s suffix, "Weight (lb)", or alone for a field with no unit. */
@Composable
internal fun withUnit(
    label: String,
    unit: FieldUnit?,
    units: UnitSystem,
): String = unitSuffix(unit, units)?.let { stringResource(R.string.exerciseForm_fieldWithUnit, label, it) } ?: label

private val FieldShape = RoundedCornerShape(12.dp)
