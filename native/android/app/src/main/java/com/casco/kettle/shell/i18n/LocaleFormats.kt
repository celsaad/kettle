package com.casco.kettle.shell.i18n

import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import android.text.format.DateFormat
import com.casco.kettle.domain.UnitSystem
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.text.SimpleDateFormat
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
