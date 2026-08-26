package com.experiencingyah.bibliCal.shabbat

import com.experiencingyah.bibliCal.util.SunsetCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ShabbatTimesTest {

    private val zoneId = ZoneId.of("America/New_York")
    private val lat = 40.0
    private val lon = -74.0

    @Test
    fun computeNextShabbat_monday_returnsThisWeeksFridayAndSaturday() {
        // Monday June 16, 2025 – 10:00 local
        val monday = LocalDate.of(2025, 6, 16)
        assertTrue(monday.dayOfWeek == DayOfWeek.MONDAY)
        val now = monday.atTime(10, 0).atZone(zoneId)

        val result = ShabbatTimes.computeNextShabbat(lat, lon, zoneId, now)

        assertNotNull(result)
        val (startMillis, zoneIdStr, endMillis) = result!!
        assertEquals(zoneId.id, zoneIdStr)
        assertTrue("Start must be before end", startMillis < endMillis)

        val friday = LocalDate.of(2025, 6, 20)
        val saturday = LocalDate.of(2025, 6, 21)
        val expectedStart = SunsetCalculator.calculateSunsetTime(friday, lat, lon, zoneId)!!
        val expectedEnd = SunsetCalculator.calculateSunsetTime(saturday, lat, lon, zoneId)!!
        assertEquals(expectedStart.toInstant().toEpochMilli(), startMillis)
        assertEquals(expectedEnd.toInstant().toEpochMilli(), endMillis)
    }

    @Test
    fun computeNextShabbat_fridayBeforeSunset_returnsTodayFridayAndTomorrowSaturday() {
        val friday = LocalDate.of(2025, 6, 20)
        val sunset = SunsetCalculator.calculateSunsetTime(friday, lat, lon, zoneId)!!
        val now = sunset.minusHours(2)

        val result = ShabbatTimes.computeNextShabbat(lat, lon, zoneId, now)

        assertNotNull(result)
        val (startMillis, _, endMillis) = result!!
        assertEquals(sunset.toInstant().toEpochMilli(), startMillis)
        val saturdaySunset = SunsetCalculator.calculateSunsetTime(friday.plusDays(1), lat, lon, zoneId)!!
        assertEquals(saturdaySunset.toInstant().toEpochMilli(), endMillis)
    }

    @Test
    fun computeNextShabbat_fridayAfterSunset_returnsNextWeeksFridayAndSaturday() {
        val friday = LocalDate.of(2025, 6, 20)
        val sunset = SunsetCalculator.calculateSunsetTime(friday, lat, lon, zoneId)!!
        val now = sunset.plusHours(1)

        val result = ShabbatTimes.computeNextShabbat(lat, lon, zoneId, now)

        assertNotNull(result)
        val (startMillis, _, endMillis) = result!!
        val nextFriday = LocalDate.of(2025, 6, 27)
        val nextSaturday = LocalDate.of(2025, 6, 28)
        val expectedStart = SunsetCalculator.calculateSunsetTime(nextFriday, lat, lon, zoneId)!!
        val expectedEnd = SunsetCalculator.calculateSunsetTime(nextSaturday, lat, lon, zoneId)!!
        assertEquals(expectedStart.toInstant().toEpochMilli(), startMillis)
        assertEquals(expectedEnd.toInstant().toEpochMilli(), endMillis)
    }

    @Test
    fun computeNextShabbat_saturday_returnsNextFridayAndSaturday() {
        val saturday = LocalDate.of(2025, 6, 21)
        val now = saturday.atTime(12, 0).atZone(zoneId)

        val result = ShabbatTimes.computeNextShabbat(lat, lon, zoneId, now)

        assertNotNull(result)
        val (startMillis, zoneIdStr, endMillis) = result!!
        assertEquals(zoneId.id, zoneIdStr)
        assertTrue(startMillis < endMillis)

        val nextFriday = LocalDate.of(2025, 6, 27)
        val nextSat = LocalDate.of(2025, 6, 28)
        val expectedStart = SunsetCalculator.calculateSunsetTime(nextFriday, lat, lon, zoneId)!!
        val expectedEnd = SunsetCalculator.calculateSunsetTime(nextSat, lat, lon, zoneId)!!
        assertEquals(expectedStart.toInstant().toEpochMilli(), startMillis)
        assertEquals(expectedEnd.toInstant().toEpochMilli(), endMillis)
    }

    @Test
    fun computeNextShabbat_contract_startBeforeEnd_sameZone() {
        val wednesday = LocalDate.of(2025, 6, 18).atTime(15, 0).atZone(zoneId)
        val result = ShabbatTimes.computeNextShabbat(lat, lon, zoneId, wednesday)!!

        assertTrue(result.first < result.third)
        assertEquals(result.second, zoneId.id)
        assertTrue(result.second.isNotBlank())
    }

    @Test
    fun computeNextShabbat_differentTimezone_returnsTimesInThatZone() {
        val utc = ZoneId.of("UTC")
        val monday = LocalDate.of(2025, 6, 16).atTime(12, 0).atZone(utc)

        val result = ShabbatTimes.computeNextShabbat(lat, lon, utc, monday)

        assertNotNull(result)
        assertEquals("UTC", result!!.second)
        assertTrue(result.first < result.third)
    }

    @Test
    fun shabbatContract_columnNames_matchDocumentation() {
        // SabbatiCal and docs rely on these exact column names
        assertEquals("shabbat_start_millis", ShabbatContract.COL_SHABBAT_START_MILLIS)
        assertEquals("shabbat_start_zone_id", ShabbatContract.COL_SHABBAT_START_ZONE_ID)
        assertEquals("shabbat_end_millis", ShabbatContract.COL_SHABBAT_END_MILLIS)
        assertEquals("shabbat_end_zone_id", ShabbatContract.COL_SHABBAT_END_ZONE_ID)
        assertEquals("com.experiencingyah.bibliCal.shabbat", ShabbatContract.AUTHORITY)
    }
}
