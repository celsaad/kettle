package com.casco.kettle.shell.sessioneditor

import com.casco.kettle.domain.EntryDraft
import com.casco.kettle.domain.EntryField
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.entryFields

/** One exercise's table and what it needs: which draft it is, the editor, and the reader's units. */
internal class TableContext(
    val editor: Editor,
    val index: Int,
    val draft: EntryDraft,
    val units: UnitSystem,
    /** The exercise's name, the user's, verbatim. */
    val name: String,
) {
    val fields: List<EntryField> = entryFields(draft.entry).orEmpty()

    /** Reps and holds log sets, numbered; the other types log one result, a single row with no number. */
    val perSet: Boolean = draft.entry is SessionEntry.Reps || draft.entry is SessionEntry.TimedHold
}
