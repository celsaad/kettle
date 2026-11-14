package com.casco.kettle.shell.sessioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.RemovedSet
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.exerciseName
import com.casco.kettle.shell.R
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * "Set 4 of Farmer's Carry removed · Undo", pinned under the list until the next removal or Undo: no
 * timeout, since nothing is written until Save and a bar that leaves on its own is one a screen reader
 * can miss. Announced as it appears.
 */
@Composable
internal fun UndoBar(
    removed: RemovedSet,
    editor: Editor,
    library: Library,
) {
    val colors = KettleTheme.colors
    val draft = editor.drafts[removed.draft]
    val name = exerciseName(library.exercises, draft.entry.exercise)
    val perSet = draft.entry is SessionEntry.Reps || draft.entry is SessionEntry.TimedHold
    val text =
        if (perSet && draft.forms.isNotEmpty()) {
            stringResource(R.string.sessionEditor_setRemoved, "${removed.row + 1}", name)
        } else {
            stringResource(R.string.sessionEditor_entryRemoved, name)
        }
    Row(
        Modifier
            .padding(vertical = Spacing.two)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.text)
            .padding(start = Spacing.three)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = KettleType.small,
            color = colors.background,
        )
        TextAction(
            stringResource(R.string.session_undo),
            KettleType.smallMedium,
            colors.background,
            onClick = editor::undo,
        )
    }
}
