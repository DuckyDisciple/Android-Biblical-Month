package com.experiencingyah.bibliCal.shabbat

import com.experiencingyah.bibliCal.util.SunsetCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure logic for computing the next Shabbat start and end. Used by [ShabbatProvider] and
 * testable with a fixed "now" for unit tests.
 *
 * Returns (startEpochMillis, zoneIdString, endEpochMillis) for the next Shabbat window,
 * or null if sunset calculation fails.
 */
object ShabbatTimes {

    /**
     * Computes the next Shabbat start (Friday sunset/twilight) and end (Saturday sunset/twilight) from [now].
     * Uses [solarElevationDegrees] so SabbatiCal and the app use the same transition time (e.g. civil twilight when enabled).
     *
     * @param solarElevationDegrees 0 = geometric sunset, -6 = civil twilight end (matches Use Civil Twilight setting).
     */
    fun computeNextShabbat(
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        now: ZonedDateTime,
        solarElevationDegrees: Double = SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
    ): Triple<Long, String, Long>? {
        val today = now.toLocalDate()

        val daysUntilFriday = (DayOfWeek.FRIDAY.value - today.dayOfWeek.value + 7) % 7
        val nextFriday = if (daysUntilFriday == 0 && today.dayOfWeek == DayOfWeek.FRIDAY) {
            val fridaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneId, solarElevationDegrees)
            if (fridaySunset != null && now.isBefore(fridaySunset)) today else today.plusDays(7)
        } else {
            today.plusDays(daysUntilFriday.toLong())
        }
        val nextSaturday = nextFriday.plusDays(1)

        val start = SunsetCalculator.calculateSunsetTime(nextFriday, latitude, longitude, zoneId, solarElevationDegrees)
            ?: return null
        val end = SunsetCalculator.calculateSunsetTime(nextSaturday, latitude, longitude, zoneId, solarElevationDegrees)
            ?: return null

        return Triple(
            start.toInstant().toEpochMilli(),
            zoneId.id,
            end.toInstant().toEpochMilli()
        )
    }
}
