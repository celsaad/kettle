package com.casco.kettle.domain

/** Kotlin's common stdlib has no Unicode normalisation, so each platform supplies its own. */
internal expect fun String.toNfd(): String

internal expect fun String.toNfc(): String
