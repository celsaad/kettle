package com.casco.kettle.shell.exerciseeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ConfigField
import com.casco.kettle.domain.ConfigKey
import com.casco.kettle.domain.configFields
import com.casco.kettle.domain.typing
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.InlineField
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType

// A stepper's count, tapped to type (#463): the value as a button, the field it opens, and how each
// keystroke reaches the draft.

/** The field a tapped count opens, starting from the count shown, each keystroke handed to [onText]. */
@Composable
internal fun TypedCount(
    shown: String,
    label: String,
    onText: (String) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf(shown) }
    InlineField(
        text,
        label,
        {
            text = it
            onText(it)
        },
        onClose,
    )
}

/** The keys a typed [field] can write: itself, and the max a reps min carries. */
internal fun typedKeys(field: ConfigField): List<ConfigKey> =
    if (field.key == ConfigKey.TargetRepsMin) listOf(field.key, ConfigKey.TargetRepsMax) else listOf(field.key)

/**
 * [text] applied to the draft as it stands, from what [opened] held (#463): written as typed, so a Save
 * tapped with the field still open saves it, but never from the keystroke before, which carried a min's
 * "155" on its way to "15" into the max. A blank or a "8.5" leaves what the field opened on; a field
 * whose type has since gone writes nothing.
 */
internal fun FieldContext.retyped(
    field: ConfigField,
    opened: Map<ConfigKey, String?>,
    text: String,
) {
    if (configFields(draft.type).none { it.key == field.key }) return
    val restored = (draft.values - opened.keys) + opened.mapNotNull { (key, value) -> value?.let { key to it } }
    onChange(draft.copy(values = restored).typing(field, text))
}

/** The count, a button that types a new one: named by its field and value, "Sets, 3", and announced as it moves. */
@Composable
internal fun CountValue(
    shown: String,
    label: String,
    onTap: () -> Unit,
) {
    val name = stringResource(R.string.exerciseEditor_countValue, label, shown)
    val action = stringResource(R.string.exerciseEditor_typeCount)
    Box(
        Modifier
            .widthIn(min = COUNT_WIDTH)
            .heightIn(min = MIN_TARGET)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onTap)
            // A live region still: TalkBack doesn't speak a focused button's state change (checked on the
            // AVD for #456), so this is what says "4" after a press; the buttons' state says it on focus.
            .semantics {
                contentDescription = name
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(shown, style = KettleType.heading, color = KettleTheme.colors.text, textAlign = TextAlign.Center)
    }
}

private val COUNT_WIDTH = 40.dp
private val MIN_TARGET = 44.dp
