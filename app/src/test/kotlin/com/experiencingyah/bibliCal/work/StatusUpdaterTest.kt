package com.experiencingyah.bibliCal.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StatusUpdaterTest {

    @Test
    fun getDateForBiblicalCalculation_beforeSunset_returnsToday() {
        val zoneId = ZoneId.of("America/New_York")
        val date = LocalDate.of(2025, 6, 21)
        val lat = 40.0
        val lon = -74.0

        val sunset = com.experiencingyah.bibliCal.util.SunsetCalculator.calculateSunsetTime(
            date, lat, lon, zoneId
        )!!
        val now = sunset.minusHours(2)

        val result = StatusUpdater.getDateForBiblicalCalculation(now, lat, lon)

        assertEquals(date, result)
    }

    @Test
    fun getDateForBiblicalCalculation_afterSunset_returnsTomorrow() {
        val zoneId = ZoneId.of("America/New_York")
        val date = LocalDate.of(2025, 6, 21)
        val lat = 40.0
        val lon = -74.0

        val sunset = com.experiencingyah.bibliCal.util.SunsetCalculator.calculateSunsetTime(
            date, lat, lon, zoneId
        )!!
        val now = sunset.plusHours(1)

        val result = StatusUpdater.getDateForBiblicalCalculation(now, lat, lon)

        assertEquals(date.plusDays(1), result)
    }
}
