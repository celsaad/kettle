package com.casco.kettle.shell.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.PlanSummary
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText

/**
 * "3 exercises, 12 sets", or "3 exercises" when the work isn't all sets; null with no exercises. Today's
 * card and Workout preview's header (#439) both say it, so a workout counts the same on each.
 */
@Composable
internal fun contentsText(summary: PlanSummary): String? {
    val exercises =
        summary.exercises.size.takeIf { it > 0 }?.let {
            pluralText(R.plurals.today_exercises, R.string.today_exercises_zero, it)
        } ?: return null
    return summary.sets?.let { sets ->
        stringResource(
            R.string.today_exercisesAndSets,
            exercises,
            pluralText(R.plurals.today_sets, R.string.today_sets_zero, sets),
        )
    } ?: exercises
}
