package com.casco.kettle.shell.programdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.OverrideNote
import com.casco.kettle.domain.OverrideTarget
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.shell.R

// An override's lines, as Program detail lists them and the program editor's rows do (#243).

/**
 * One line per patched key, plus a trailing line when the patch doesn't reach the runner at all. An
 * unknown key is noted on its own line instead: it is dropped alone while the rest still runs. Keys
 * and values are the user's YAML, verbatim; numbers as JS writes them, since that is how RN drew them.
 */
@Composable
internal fun overrideLines(note: OverrideNote): List<String> {
    val name =
        when (val target = note.target) {
            is OverrideTarget.Exercise -> target.name
            is OverrideTarget.Circuit -> stringResource(R.string.overrideEditor_circuitTitle, target.id)
            is OverrideTarget.Missing -> target.id
        }
    val unknown = stringResource(R.string.programs_overrideIgnoredUnknownKey)
    val lines =
        note.keys.map { key ->
            val line = "$name: ${key.key} → ${key.value.shown}"
            if (key.unknown) "$line · $unknown" else line
        }
    return when {
        note.target is OverrideTarget.Missing -> lines + stringResource(R.string.programs_overrideIgnoredMissing)
        note.refused -> lines + stringResource(R.string.programs_overrideIgnoredInvalid)
        else -> lines
    }
}

private val OverrideValue.shown: String
    get() =
        when (this) {
            is OverrideValue.Numeric -> jsNumber(value)
            is OverrideValue.Text -> value
        }
