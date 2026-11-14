package com.casco.kettle.shell.exerciseeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ConfigField
import com.casco.kettle.domain.ConfigKey
import com.casco.kettle.domain.FORM_TYPES
import com.casco.kettle.domain.configFields
import com.casco.kettle.domain.withRequiredFilled
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The exercise editor's fields (#441): the name, the type, and the config in "Target", "Load" and
 * "Timing", each only where the type has something for it. An exercise that exists shows its type
 * locked, "Set when it was created": each type keys a different config. The workout editor's quick-add
 * keeps the typed form, [ExerciseFormFields], as agreed on the ticket.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseSheetFields(
    context: FieldContext,
    typeLocked: Boolean,
) {
    val draft = context.draft
    Label(stringResource(R.string.common_name), compact = false)
    FormField(
        draft.name,
        { context.onChange(draft.copy(name = it)) },
        name = stringResource(R.string.common_name),
        placeholder = stringResource(R.string.exerciseEditor_namePlaceholder),
        fieldLook(compact = false).copy(kind = FieldKind.Text),
    )
    Label(stringResource(R.string.exerciseEditor_type), compact = false)
    if (typeLocked) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two), verticalAlignment = Alignment.CenterVertically) {
            // Announced unavailable, not a pill whose tap does nothing.
            TypePill(draft.type, active = true, enabled = false, compact = false) {}
            Text(
                stringResource(R.string.exerciseEditor_typeLocked),
                style = KettleType.small,
                color = KettleTheme.colors.textSecondary,
            )
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.two - 2.dp)) {
            for (type in FORM_TYPES) {
                TypePill(
                    type,
                    type == draft.type,
                    enabled = true,
                    compact = false,
                ) { context.onChange(draft.copy(type = type).withRequiredFilled()) }
            }
        }
    }
    val bySection = configFields(draft.type).groupBy(::sectionOf)
    for (section in Section.entries) {
        val fields = bySection[section] ?: continue
        Text(
            stringResource(section.heading),
            Modifier.padding(top = Spacing.four).semantics { heading() },
            style = KettleType.heading,
            color = KettleTheme.colors.text,
        )
        for (field in fields) FieldControl(field, context)
    }
}

private enum class Section(
    val heading: Int,
) {
    Target(R.string.exerciseEditor_target),
    Load(R.string.exerciseEditor_load),
    Timing(R.string.exerciseEditor_timing),
}

/** The weight is the load; anything measured in time, a rest, a work interval, a cap, a duration, is timing. */
private fun sectionOf(field: ConfigField): Section =
    when (field.key) {
        ConfigKey.TargetWeightKg -> Section.Load

        ConfigKey.RestSec, ConfigKey.WorkSec, ConfigKey.IntervalSec, ConfigKey.TimeCapSec, ConfigKey.TotalMinutes,
        ConfigKey.DurationSec,
        -> Section.Timing

        else -> Section.Target
    }
