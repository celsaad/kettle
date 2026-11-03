package com.casco.kettle.domain

/** Settings' "In your library" card, `src/app/settings.tsx`'s `counts`. */
data class SettingsCounts(
    val exercises: Int,
    val workouts: Int,
    val programs: Int,
    val sessions: Int,
)

/**
 * What [library] and a log of [sessions] count. The built-in rest is a pseudo-exercise rather than one
 * the user wrote, and Library's count leaves it out, so this does too and the two screens agree.
 */
fun settingsCounts(
    library: Library?,
    sessions: Int,
) = SettingsCounts(
    exercises = library?.exercises?.count { it.config !is ExerciseConfig.Rest } ?: 0,
    workouts = library?.workouts?.size ?: 0,
    programs = library?.programs?.size ?: 0,
    sessions = sessions,
)
