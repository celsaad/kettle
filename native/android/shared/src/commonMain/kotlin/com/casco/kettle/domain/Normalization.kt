package com.casco.kettle.domain

// Kotlin's common stdlib has no Unicode normalisation, so each platform supplies its own.

/** Canonical decomposition, so an accent becomes a combining mark `foldName` can strip. */
internal expect fun String.toNfd(): String

/** Canonical composition, so what survives the strip compares the same however it was typed. */
internal expect fun String.toNfc(): String
