package com.casco.kettle.domain

/**
 * One exercise as it sits in the file (#413): the list item under `exercises:`, word for word as
 * [serializeLibraryYaml] writes it, so it can be pasted back into a file of one's own.
 */
fun serializeExerciseYaml(exercise: Exercise): String = dumpYaml(listOf(exercise.toRaw()))
