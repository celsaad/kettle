package com.casco.kettle.shell.exerciseeditor

import com.casco.kettle.domain.ExerciseDraft
import com.casco.kettle.domain.UnitSystem

/** What a field's control needs: the draft, how to change it, and the reader's units. */
internal class FieldContext(
    val draft: ExerciseDraft,
    val onChange: (ExerciseDraft) -> Unit,
    val units: UnitSystem,
    /** The kilograms the weight was filled from, so the note says what Save would keep. */
    val previousWeightKg: Double?,
)
