package com.experiencingyah.bibliCal.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.util.MonthNames
import com.experiencingyah.bibliCal.util.SunsetCalculator
import com.experiencingyah.bibliCal.work.StatusUpdater
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class WidgetData(
    val lunarText: String,
    val gregorianText: String,
    val shabbatText: String,
    val shabbatLabel: String,
    val isShabbat: Boolean
)

object WidgetHelper {
    /**
     * Check if the user has any of the app's widgets installed on their home screen.
     */
    fun hasAnyWidgetInstalled(context: Context): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        val combinedIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, CombinedWidgetProvider::class.java)
        )
        val dateIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, DateWidgetProvider::class.java)
        )
        val shabbatIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ShabbatWidgetProvider::class.java)
        )
        
        return combinedIds.isNotEmpty() || dateIds.isNotEmpty() || shabbatIds.isNotEmpty()
    }
    
    fun getWidgetData(context: Context): WidgetData = runBlocking {
        withContext(Dispatchers.IO) {
            // Use same location source as StatusUpdater for consistent sunset/day transition
            val settings = SettingsRepository(context)
            val (lat, lon) = StatusUpdater.getLocation(settings)
            val repo = LunarRepository(context)
            
            val todayDate = LocalDate.now()
            val zoneId = ZoneId.systemDefault()
            val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
            val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC

            val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, lat, lon, zoneId, elevation)
            val tomorrowSunset = SunsetCalculator.calculateSunsetTime(todayDate.plusDays(1), lat, lon, zoneId, elevation)

            val now = ZonedDateTime.now(zoneId)
            val isAfterTodaySunset = todaySunset != null && now.isAfter(todaySunset)
            
            // Determine which dates to show and which date to use for biblical calculation
            val sunsetDate: LocalDate
            val nextSunsetDate: LocalDate
            val dateToUseForBiblical: LocalDate
            
            if (isAfterTodaySunset) {
                // After sunset: biblical day has advanced
                sunsetDate = todayDate // When the biblical day started
                nextSunsetDate = todayDate.plusDays(1)
                dateToUseForBiblical = todayDate.plusDays(1) // Use tomorrow for biblical calculation
            } else {
                // Before sunset: still in current biblical day
                sunsetDate = todayDate.minusDays(1) // When the current biblical day started
                nextSunsetDate = todayDate
                dateToUseForBiblical = todayDate // Use today for biblical calculation
            }
            
            // Get biblical date using the appropriate Gregorian date
            val today = repo.resolveFor(dateToUseForBiblical)
            val lunarText = if (today == null) {
                "Tap to set an anchor"
            } else {
                val dayOrdinal = when (today.dayOfMonth) {
                    1 -> "1st"
                    2 -> "2nd"
                    3 -> "3rd"
                    21 -> "21st"
                    22 -> "22nd"
                    23 -> "23rd"
                    31 -> "31st"
                    else -> "${today.dayOfMonth}th"
                }
                val monthOrdinal = when (today.monthNumber) {
                    1 -> "1st"
                    2 -> "2nd"
                    3 -> "3rd"
                    else -> "${today.monthNumber}th"
                }
                "$dayOrdinal Day of $monthOrdinal Month (${today.yearNumber})"
            }
            
            val gregorianText = if (todaySunset != null && tomorrowSunset != null) {
                val format = DateTimeFormatter.ofPattern("M/d")
                "Sunset ${sunsetDate.format(format)} - Sunset ${nextSunsetDate.format(format)}"
            } else {
                todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
            
            val isShabbat = isCurrentlyShabbat(now, todayDate, lat, lon, elevation)
            val shabbatLabel = if (isShabbat) "" else "Until Shabbat"
            val shabbatText = if (isShabbat) "Shabbat\nShalom" else calculateShabbatCountdown(lat, lon, elevation)
            
            WidgetData(lunarText, gregorianText, shabbatText, shabbatLabel, isShabbat)
        }
    }
    
    private fun isCurrentlyShabbat(now: ZonedDateTime, today: LocalDate, latitude: Double, longitude: Double, solarElevationDegrees: Double): Boolean {
        val zoneId = ZoneId.systemDefault()
        if (today.dayOfWeek == DayOfWeek.FRIDAY) {
            val fridaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneId, solarElevationDegrees)
                ?: today.atTime(18, 0).atZone(zoneId)
            if (now.isAfter(fridaySunset)) return true
        }
        if (today.dayOfWeek == DayOfWeek.SATURDAY) {
            val saturdaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneId, solarElevationDegrees)
                ?: today.atTime(18, 0).atZone(zoneId)
            val yesterday = today.minusDays(1)
            val fridaySunset = SunsetCalculator.calculateSunsetTime(yesterday, latitude, longitude, zoneId, solarElevationDegrees)
                ?: yesterday.atTime(18, 0).atZone(zoneId)
            return now.isAfter(fridaySunset) && now.isBefore(saturdaySunset)
        }
        return false
    }

    private fun calculateShabbatCountdown(latitude: Double, longitude: Double, solarElevationDegrees: Double): String {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zoneId)
        val today = now.toLocalDate()
        val daysUntilFriday = (DayOfWeek.FRIDAY.value - today.dayOfWeek.value + 7) % 7
        val nextFriday = if (daysUntilFriday == 0 && today.dayOfWeek == DayOfWeek.FRIDAY) {
            val fridaySunset = SunsetCalculator.calculateSunsetTime(today, latitude, longitude, zoneId, solarElevationDegrees)
            if (fridaySunset != null && now.isBefore(fridaySunset)) today else today.plusDays(7)
        } else {
            today.plusDays(daysUntilFriday.toLong())
        }
        val fridaySunset = SunsetCalculator.calculateSunsetTime(nextFriday, latitude, longitude, zoneId, solarElevationDegrees)
            ?: nextFriday.atTime(18, 0).atZone(zoneId) // Fallback to 6 PM
        
        // Both now and fridaySunset are in the same timezone (zoneId)
        // Calculate duration directly
        val duration = Duration.between(now, fridaySunset)
        val totalSeconds = duration.seconds
        
        return when {
            totalSeconds < 0 -> "Shabbat passed"
            totalSeconds < 3600 -> {
                // Less than 1 hour: show minutes only
                val minutes = (totalSeconds / 60.0).roundToInt()
                "${minutes}m"
            }
            totalSeconds < 86400 -> {
                // Less than 1 day: show hours and minutes
                val totalMinutes = (totalSeconds / 60.0).roundToInt()
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                "${hours}h ${minutes}m"
            }
            else -> {
                // 1 day or more: show days and hours
                // Calculate days and remaining hours separately to avoid rounding errors
                val totalMinutes = totalSeconds / 60
                val days = totalMinutes / (24 * 60)
                val remainingMinutes = totalMinutes % (24 * 60)
                val hours = remainingMinutes / 60
                "${days}d ${hours}h"
            }
        }
    }
}

