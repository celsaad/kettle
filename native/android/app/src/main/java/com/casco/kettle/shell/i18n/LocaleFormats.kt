package com.casco.kettle.shell.i18n

import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import android.text.format.DateFormat
import com.casco.kettle.domain.UnitSystem
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * `formatMonthDay` in `src/i18n/format.ts`: Intl's `{ month: 'short', day: 'numeric' }`, which is the
 * locale's own best pattern for those fields, "Aug 4" or "4 de ago.". `SimpleDateFormat` rather than
 * `java.time`, which needs API 26 where the shell's floor is 24.
 */
fun formatMonthDay(
    at: Instant,
    locale: Locale,
    zone: TimeZone,
): String = format(at, locale, zone, "MMMd")

/**
 * RN's `{ weekday: 'long', month: 'short', day: 'numeric' }`, "Tuesday, Aug 4": what its
 * `formatFullDate` did before its last caller went native (#200).
 */
fun formatFullDate(
    at: Instant,
    locale: Locale,
    zone: TimeZone,
): String = format(at, locale, zone, "EEEEMMMd")

/**
 * [formatMonthDay] for a calendar date, which has no zone of its own: Stats' calendar labels its weeks
 * by the day they start on, already in the reader's zone.
 */
fun formatMonthDay(
    date: LocalDate,
    locale: Locale,
): String = formatMonthDay(date.atStartOfDayIn(TimeZone.UTC), locale, TimeZone.UTC)

/**
 * `formatMonthBadge` in `src/i18n/format.ts`: the short month on a History row's date badge, "JUL" or
 * "JUL.", uppercased with the locale's rules rather than the default's, which are wrong for Turkish.
 */
fun formatMonthBadge(
    date: LocalDate,
    locale: Locale,
): String = format(date.atStartOfDayIn(TimeZone.UTC), locale, TimeZone.UTC, "MMM").uppercase(locale)

/**
 * `formatWeekday(date, 'narrow')` in `src/i18n/format.ts`: "M", "T", or "S" for both Saturday and
 * Sunday. ICU's formatter, not `SimpleDateFormat`: the locale's pattern for it is the standalone
 * `ccccc`, which `java.text` refuses, and on a JVM `java.text` reads `EEEEE` as the full name.
 */
fun formatNarrowWeekday(
    date: LocalDate,
    locale: Locale,
): String =
    android.icu.text
        .SimpleDateFormat("ccccc", locale)
        .apply { timeZone = android.icu.util.TimeZone.GMT_ZONE }
        .format(Date(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()))

/**
 * What `firstWeekdayIndex` in `src/i18n/index.ts` read before its last caller went native (#202): the
 * day the reader's calendar starts its week on, which expo-localization reads from `Calendar` in the
 * device's locale, as this does.
 */
fun firstWeekday(locale: Locale): DayOfWeek {
    // Calendar counts from Sunday = 1, ISO from Monday = 1, so only Sunday doesn't shift down by one.
    val first = Calendar.getInstance(locale).firstDayOfWeek
    return if (first == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek(first - 1)
}

private fun format(
    at: Instant,
    locale: Locale,
    zone: TimeZone,
    skeleton: String,
): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .apply { timeZone = java.util.TimeZone.getTimeZone(zone.id) }
        .format(Date(at.toEpochMilliseconds()))

/**
 * `deviceUnitSystem` in `src/i18n/index.ts`: what RN shows until the reader picks one in Settings,
 * imperial where the locale measures in US units. Decided as expo-localization decides what RN reads:
 * ICU's answer from API 28, where it arrived, and expo's own list of regions below that.
 */
fun deviceUnitSystem(locale: Locale): UnitSystem {
    val us =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocaleData.getMeasurementSystem(ULocale.forLocale(locale)) == LocaleData.MeasurementSystem.US
        } else {
            locale.country in US_UNIT_REGIONS
        }
    return if (us) UnitSystem.IMPERIAL else UnitSystem.METRIC
}

/** expo-localization's `USES_IMPERIAL`. */
private val US_UNIT_REGIONS = setOf("US", "LR", "MM")
