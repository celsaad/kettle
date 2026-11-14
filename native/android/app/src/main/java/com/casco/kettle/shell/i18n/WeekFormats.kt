package com.casco.kettle.shell.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.util.Date
import java.util.Locale

// Sessions' week labels (#414), beside LocaleFormats.kt's, which has its share of functions.

/**
 * The short weekday under a Sessions row's day (#414), "Wed" or "qua.": ICU's standalone form, as
 * [formatNarrowWeekday] reads it.
 */
fun formatShortWeekday(
    date: LocalDate,
    locale: Locale,
): String =
    android.icu.text
        .SimpleDateFormat("ccc", locale)
        .apply { timeZone = android.icu.util.TimeZone.GMT_ZONE }
        .format(Date(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()))

/**
 * A week's days as one range in the locale's own words (#414), "Nov 1 – 7", "Oct 29 – Nov 4" or
 * "1–7 de nov.": ICU's interval format, which says the month once when the two ends share it. With
 * [withYear], "Nov 2 – 8, 2025": a log longer than a year would otherwise title two weeks alike, and the
 * rows under them say only the day and weekday.
 */
fun formatDayRange(
    from: LocalDate,
    to: LocalDate,
    locale: Locale,
    withYear: Boolean = false,
): String {
    fun millis(date: LocalDate) = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val format =
        android.icu.text.DateIntervalFormat
            .getInstance(if (withYear) "yMMMd" else "MMMd", locale)
            .apply { timeZone = android.icu.util.TimeZone.GMT_ZONE }
    return format
        .format(android.icu.util.DateInterval(millis(from), millis(to)), StringBuffer(), java.text.FieldPosition(0))
        .toString()
}
