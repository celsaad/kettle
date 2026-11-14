package com.casco.kettle.shell.programeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.addingDay
import com.casco.kettle.domain.addingWeek
import com.casco.kettle.domain.daysOf
import com.casco.kettle.domain.deletingWeek
import com.casco.kettle.domain.duplicatingWeek
import com.casco.kettle.domain.movingDay
import com.casco.kettle.domain.movingWeek
import com.casco.kettle.domain.weekNumbers

/**
 * Which week and which day of the program editor are open (#438), one of each at a time, and the week
 * and day moves, which keep them pointing at what the reader had open. It opens on the first week and
 * its first day, so a one-week program is ready to edit as it was before weeks folded.
 */
internal class EditorOutline(
    private val editor: Editor,
) {
    var openWeek by mutableStateOf(editor.draft.weekNumbers.firstOrNull())
        private set
    var openDay by mutableStateOf(
        openWeek?.let {
            editor.draft
                .daysOf(it)
                .firstOrNull()
                ?.key
        },
    )
        private set

    /** Opening a week opens its first day too: the common edit is to that week's one or two days. */
    fun toggleWeek(week: Long) {
        if (openWeek == week) {
            openWeek = null
            openDay = null
        } else {
            openWeek = week
            openDay =
                editor.draft
                    .daysOf(week)
                    .firstOrNull()
                    ?.key
        }
        editor.closePicker()
    }

    fun toggleDay(key: Int) {
        openDay = if (openDay == key) null else key
        editor.closePicker()
    }

    /** A new week, open on its one day. */
    fun addWeek(workouts: List<Workout>) {
        editor.edit { it.addingWeek(workouts) }
        val week = editor.draft.weekNumbers.last()
        openWeek = week
        openDay =
            editor.draft
                .daysOf(week)
                .lastOrNull()
                ?.key
    }

    fun addDay(
        week: Long,
        label: String,
        workouts: List<Workout>,
    ) {
        editor.edit { it.addingDay(week, label, workouts) }
        openDay =
            editor.draft
                .daysOf(week)
                .lastOrNull()
                ?.key
    }

    /** The open week moves with its days, so the reader is still looking at what they moved. */
    fun moveWeek(
        week: Long,
        by: Int,
    ) {
        val numbers = editor.draft.weekNumbers
        val other = numbers.getOrNull(numbers.indexOf(week) + by) ?: return
        editor.edit { it.movingWeek(week, by) }
        openWeek =
            when (openWeek) {
                week -> other
                other -> week
                else -> openWeek
            }
    }

    fun duplicateWeek(week: Long) {
        editor.edit { it.duplicatingWeek(week) }
        openWeek = openWeek?.let { if (it > week) it + 1 else it }
    }

    /** Its days' half-typed overrides go with them, and a later open week moves down with the rest. */
    fun deleteWeek(week: Long) {
        editor.draft.daysOf(week).forEach { editor.panels.remove(it.key) }
        editor.edit { it.deletingWeek(week) }
        openWeek =
            openWeek?.let {
                when {
                    it == week -> null
                    it > week -> it - 1
                    else -> it
                }
            }
        if (openWeek == null) openDay = null
    }

    fun moveDay(
        key: Int,
        by: Int,
    ) = editor.edit { it.movingDay(key, by) }
}
