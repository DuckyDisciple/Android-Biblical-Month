package com.experiencingyah.bibliCal.work

import android.content.Context
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.domain.LunarDate
import com.experiencingyah.bibliCal.notifications.Notifier
import com.experiencingyah.bibliCal.util.MonthNames
import com.experiencingyah.bibliCal.util.SunsetCalculator
import com.experiencingyah.bibliCal.widgets.CombinedWidgetProvider
import com.experiencingyah.bibliCal.widgets.DateWidgetProvider
import com.experiencingyah.bibliCal.widgets.ShabbatWidgetProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared logic for updating status notification and widgets based on biblical date.
 * Used by both DailyTickWorker and SunsetTickWorker to ensure consistent behavior.
 */
object StatusUpdater {

    /**
     * Updates the status notification and all widgets with the current biblical date.
     *
     * @param context Application context
     * @param repo LunarRepository for resolving biblical dates
     * @param settings SettingsRepository for user preferences
     * @param atSunset True when called at sunset (e.g. from SunsetTickWorker). When true and status
     *        notification is disabled, shows a one-time "new day" notification.
     * @return The resolved biblical date, or null if resolution failed
     */
    suspend fun updateStatusAndWidgets(
        context: Context,
        repo: LunarRepository,
        settings: SettingsRepository,
        atSunset: Boolean = false
    ): com.experiencingyah.bibliCal.domain.LunarDate? {
        Notifier.ensureChannels(context)

        // Get location (cached or default) to calculate sunset
        val cachedLocation = settings.getCachedLocation()
        val latitude = cachedLocation?.first ?: DEFAULT_LATITUDE
        val longitude = cachedLocation?.second ?: DEFAULT_LONGITUDE

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
        val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
        val dateToUseForBiblical = getDateForBiblicalCalculation(now, latitude, longitude, elevation)

        val today = repo.resolveFor(dateToUseForBiblical)
        if (today != null) {
            val namingMode = settings.monthNamingMode.first()
            val label = "${MonthNames.format(today.monthNumber, namingMode)} ${today.dayOfMonth}, Year ${today.yearNumber}"

            if (settings.statusNotificationEnabled.first()) {
                Notifier.showOrUpdateStatus(context, today, label)
            } else {
                Notifier.cancelStatus(context)
                if (atSunset) {
                    Notifier.showNewDayNotification(context, today)
                }
            }

            // Update all widgets
            DateWidgetProvider.updateAll(context)
            ShabbatWidgetProvider.updateAll(context)
            CombinedWidgetProvider.updateAll(context)
        }

        return today
    }

    /**
     * Determines which Gregorian date to use for biblical calculation based on sunset/twilight.
     * If after sunset (or civil twilight when setting is on), returns tomorrow's date.
     * Uses [solarElevationDegrees] so widget, notification, and Today screen stay in sync.
     */
    fun getDateForBiblicalCalculation(
        now: ZonedDateTime,
        latitude: Double,
        longitude: Double,
        solarElevationDegrees: Double = SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
    ): LocalDate {
        val todayDate = now.toLocalDate()
        val tomorrowDate = todayDate.plusDays(1)
        val zoneId = now.zone
        val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, latitude, longitude, zoneId, solarElevationDegrees)
        return if (todaySunset != null && now.isAfter(todaySunset)) {
            tomorrowDate
        } else {
            todayDate
        }
    }

    /**
     * Gets the cached location or defaults.
     */
    suspend fun getLocation(settings: SettingsRepository): Pair<Double, Double> {
        val cachedLocation = settings.getCachedLocation()
        return Pair(
            cachedLocation?.first ?: DEFAULT_LATITUDE,
            cachedLocation?.second ?: DEFAULT_LONGITUDE
        )
    }

    /**
     * Resolves the current biblical [LunarDate] using the same sunset/twilight rules as the Today screen and notifications.
     */
    suspend fun resolveCurrentBiblicalLunarDate(
        repo: LunarRepository,
        settings: SettingsRepository,
    ): LunarDate? {
        val (latitude, longitude) = getLocation(settings)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
        val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
        val dateForBiblical = getDateForBiblicalCalculation(now, latitude, longitude, elevation)
        return repo.resolveFor(dateForBiblical)
    }

    /**
     * Gregorian date (local) for day 1 of the next biblical month after the day 29/30 moon prompt.
     * After sunset, [getDateForBiblicalCalculation] is already "tomorrow"; "Not seen on day 30" must use
     * that date + 1 day, not [LocalDate.now().plusDays(1)], or the new month starts on the same civil day
     * as biblical "today" and the UI jumps to day 1 prematurely.
     */
    suspend fun gregorianStartForNextMonthAfterMoonPrompt(
        moonSeen: Boolean,
        dayOfMonth: Int,
        settings: SettingsRepository,
    ): LocalDate {
        val (latitude, longitude) = getLocation(settings)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
        val elevation = if (useCivilTwilight) SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT else SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
        val ref = getDateForBiblicalCalculation(now, latitude, longitude, elevation)
        return when {
            moonSeen && dayOfMonth == 30 -> ref
            moonSeen -> ref.plusDays(1)
            dayOfMonth == 30 -> ref.plusDays(1)
            else -> ref
        }
    }

    // Default location (approximately New York area)
    const val DEFAULT_LATITUDE = 40.0
    const val DEFAULT_LONGITUDE = -74.0
}
