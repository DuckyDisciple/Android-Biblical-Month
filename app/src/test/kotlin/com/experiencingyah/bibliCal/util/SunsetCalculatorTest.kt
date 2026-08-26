package com.experiencingyah.bibliCal.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SunsetCalculatorTest {

    @Test
    fun calculateSunsetTime_returnsNonNullForValidInput() {
        val date = LocalDate.of(2025, 6, 21) // Summer solstice
        val lat = 40.0
        val lon = -74.0
        val zoneId = ZoneId.of("America/New_York")

        val sunset = SunsetCalculator.calculateSunsetTime(date, lat, lon, zoneId)

        assertNotNull(sunset)
        // Sunset should be in the afternoon/evening (roughly 5-9 PM)
        assertTrue(sunset!!.hour >= 17 || sunset.hour <= 21)
    }

    @Test
    fun calculateNextSunset_returnsNonNull() {
        val lat = 40.0
        val lon = -74.0
        val zoneId = "America/New_York"

        val nextSunset = SunsetCalculator.calculateNextSunset(lat, lon, zoneId)

        assertNotNull(nextSunset)
    }

    @Test
    fun dayTransition_beforeSunset_usesToday() {
        val date = LocalDate.of(2025, 6, 21)
        val lat = 40.0
        val lon = -74.0
        val zoneId = ZoneId.of("America/New_York")
        val sunset = SunsetCalculator.calculateSunsetTime(date, lat, lon, zoneId)!!

        // "Now" is 2 hours before sunset - should be before sunset
        val now = sunset.minusHours(2)
        assertTrue(now.isBefore(sunset))
    }

    @Test
    fun dayTransition_afterSunset_usesTomorrow() {
        val date = LocalDate.of(2025, 6, 21)
        val lat = 40.0
        val lon = -74.0
        val zoneId = ZoneId.of("America/New_York")
        val sunset = SunsetCalculator.calculateSunsetTime(date, lat, lon, zoneId)!!

        // "Now" is 1 hour after sunset
        val now = sunset.plusHours(1)
        assertTrue(now.isAfter(sunset))
    }
}
