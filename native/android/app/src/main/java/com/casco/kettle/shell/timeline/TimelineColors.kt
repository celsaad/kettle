package com.casco.kettle.shell.timeline

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The timeline both read-only outlines draw, the runner's Coming up and Home's preview (#280), so the
 * two can't drift into looking like different things. Only the colours differ: the runner is always
 * dark, Home follows the scheme.
 */
@Immutable
data class TimelineColors(
    val text: Color,
    val textSecondary: Color,
    val rail: Color,
    val circuit: Color,
    val kicker: Color,
)
