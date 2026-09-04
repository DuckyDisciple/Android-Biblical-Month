package com.experiencingyah.bibliCal.ui.vm

import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experiencingyah.bibliCal.calendar.DeviceCalendarReader
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.UserEventRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.domain.FeastDay
import com.experiencingyah.bibliCal.domain.MonthStatus
import com.experiencingyah.bibliCal.util.MonthNames
import com.experiencingyah.bibliCal.util.SunsetCalculator
import com.experiencingyah.bibliCal.util.formatEventTime
import com.experiencingyah.bibliCal.widgets.WidgetHelper
import com.experiencingyah.bibliCal.work.StatusUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class UpcomingFeast(
    val title: String,
    val date: LocalDate,
    val daysUntil: Long,
)

data class SunsetInfo(
    val nextSunset: ZonedDateTime?,
    val timeZone: String? = null,
    val location: String? = null, // Location string like "40.0°N, 74.0°W"
) {
    // Calculate countdown text dynamically based on current time
    fun getCountdownText(): String {
        val nextSunset = this.nextSunset ?: return "Sunset passed"
        val now = ZonedDateTime.now(nextSunset.zone)
        val duration = Duration.between(now, nextSunset)
        
        return if (duration.isNegative) {
            "Sunset passed"
        } else {
            val hours = duration.toHours()
            val minutes = (duration.toMinutes() % 60).toInt()
            val seconds = (duration.seconds % 60).toInt()
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}

data class JerusalemTimeInfo(
    val currentTime: ZonedDateTime,
    val sunsetTime: ZonedDateTime?,
) {
    // Calculate countdown text dynamically based on current time
    fun getCountdownText(): String {
        val sunsetTime = this.sunsetTime ?: return "Sunset passed"
        val now = ZonedDateTime.now(sunsetTime.zone)
        val duration = Duration.between(now, sunsetTime)
        
        return if (duration.isNegative) {
            "Sunset passed"
        } else {
            val hours = duration.toHours()
            val minutes = (duration.toMinutes() % 60).toInt()
            val seconds = (duration.seconds % 60).toInt()
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}

data class ScheduleItem(
    val title: String,
    val timeLabel: String,
    val source: String, // "personal" or "device"
    val notes: String? = null,
    val deviceEventId: Long? = null,
    val deviceBeginMillis: Long? = null,
    val deviceEndMillis: Long? = null,
)

data class TodayUiState(
    val hasAnchor: Boolean = false,
    val lunarLabel: String = "—",
    val gregorianLabel: String = LocalDate.now().toString(),
    val gregorianDaytimeDate: String? = null,
    val gregorianSunsetDate: String? = null,
    val isAfterSunset: Boolean = false,
    val hint: String? = null,
    val sunsetInfo: SunsetInfo? = null,
    val jerusalemTimeInfo: JerusalemTimeInfo? = null,
    val upcomingFeasts: List<UpcomingFeast> = emptyList(),
    val todaysSchedule: List<ScheduleItem> = emptyList(),
    val currentDayOfMonth: Int = 0,
    val showNextMonthButton: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingSunset: Boolean = false,
    val isLoadingFeasts: Boolean = false,
    val showWidgetBanner: Boolean = false,
)

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LunarRepository(app)
    private val settings = SettingsRepository(app)
    private val userEvents = UserEventRepository(app)
    private val deviceReader = DeviceCalendarReader(app)
    private val geocoder = Geocoder(app)
    private val appContext = app.applicationContext

    private val _state = MutableStateFlow(TodayUiState(isLoading = true))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var currentLocation: Location? = null
    private var lastGeocodedLatLon: Pair<Double, Double>? = null
    private var isRefreshing = false

    init {
        // Load user location immediately so we can calculate sunset right away
        viewModelScope.launch {
            try {
                val userLocation = settings.getUserLocation()
                if (userLocation != null) {
                    val location = android.location.Location("user")
                    location.latitude = userLocation.latitude
                    location.longitude = userLocation.longitude
                    location.accuracy = 100f
                    location.time = System.currentTimeMillis()
                    currentLocation = location
                    if (userLocation.label != null) {
                        lastGeocodedLatLon = Pair(userLocation.latitude, userLocation.longitude)
                    }
                }
            } catch (e: Exception) {
                // Ignore errors loading location
            }
            // Refresh after attempting to load cached location
            refresh()
            // Check widget banner state
            updateWidgetBannerState()
        }
        
        // No need to update countdown every second - it's calculated dynamically in the UI
        // Only update when sunset time or location changes
        
        viewModelScope.launch {
            settings.showJerusalemTime.collect {
                updateJerusalemTimeInfo()
            }
        }
        viewModelScope.launch {
            settings.useCivilTwilightForCountdown.collect {
                refresh()
            }
        }
        viewModelScope.launch {
            settings.showDeviceCalendarEvents.collect {
                refresh()
            }
        }
        viewModelScope.launch {
            settings.hiddenDeviceEventIds.collect {
                refresh()
            }
        }
    }
    
    private fun getAppVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private suspend fun updateWidgetBannerState() {
        val hasWidgets = WidgetHelper.hasAnyWidgetInstalled(appContext)
        if (hasWidgets) {
            // User already has widgets, don't show banner
            _state.value = _state.value.copy(showWidgetBanner = false)
            return
        }
        
        val dismissedVersion = settings.getWidgetBannerDismissedVersion()
        val currentVersion = getAppVersionName()
        
        // Show banner if never dismissed OR dismissed for a different version
        val shouldShow = dismissedVersion == null || dismissedVersion != currentVersion
        _state.value = _state.value.copy(showWidgetBanner = shouldShow)
    }
    
    fun dismissWidgetBanner() {
        viewModelScope.launch {
            val currentVersion = getAppVersionName()
            settings.setWidgetBannerDismissedVersion(currentVersion)
            _state.value = _state.value.copy(showWidgetBanner = false)
        }
    }

    fun setLocation(location: Location?) {
        currentLocation = location
        lastGeocodedLatLon = null // Force re-geocode
        if (location != null) {
            viewModelScope.launch {
                val label = reverseGeocode(location.latitude, location.longitude)
                settings.cacheLocation(location.latitude, location.longitude, label)
            }
        }
        // Refresh to recalculate isAfterSunset with the new location
        refresh()
    }

    fun refresh() {
        // Prevent concurrent refreshes
        if (isRefreshing) {
            return
        }
        
        viewModelScope.launch {
            isRefreshing = true
            try {
                _state.value = _state.value.copy(isLoading = true)
            
            val hasAnchor = repo.hasAnyAnchor()
            if (!hasAnchor) {
                _state.value = _state.value.copy(
                    hasAnchor = false,
                    lunarLabel = "No anchor set",
                    hint = "Set an anchor (e.g., Month 1 Day 1) to begin tracking.",
                    isLoading = false,
                )
                updateUpcomingFeasts()
                return@launch
            }

            // Calculate sunset and daytime dates for display purposes
            // Use a consistent "now" time for all calculations to avoid race conditions
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val todayDate = now.toLocalDate()
            val yesterdayDate = todayDate.minusDays(1)
            val tomorrowDate = todayDate.plusDays(1)
            
            var daytimeDate: String? = null
            var sunsetDate: String? = null
            var isAfterSunset = false
            var dateToUseForBiblical = todayDate

            // Use same location source as widget/StatusUpdater for consistent sunset/day transition
            val (lat, lon) = currentLocation?.let { Pair(it.latitude, it.longitude) }
                ?: StatusUpdater.getLocation(settings)
            val zoneId = ZoneId.systemDefault()
            val dayTransitionElevation = if (settings.useCivilTwilightForCountdown.first()) {
                SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT
            } else {
                SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
            }
            val todaySunset = SunsetCalculator.calculateSunsetTime(todayDate, lat, lon, zoneId, dayTransitionElevation)
            val nextSunset = SunsetCalculator.nextSunsetFrom(now, todayDate, lat, lon, zoneId, dayTransitionElevation)

            if (todaySunset != null) {
                isAfterSunset = now.isAfter(todaySunset)
                if (isAfterSunset) {
                    daytimeDate = tomorrowDate.toString()
                    sunsetDate = todayDate.toString()
                    dateToUseForBiblical = tomorrowDate
                } else {
                    daytimeDate = todayDate.toString()
                    sunsetDate = yesterdayDate.toString()
                    dateToUseForBiblical = todayDate
                }
            } else {
                daytimeDate = todayDate.toString()
                sunsetDate = yesterdayDate.toString()
                dateToUseForBiblical = todayDate
                isAfterSunset = false
            }

            // Get biblical date using the appropriate Gregorian date (tomorrow if after sunset, today if before)
            val today = repo.resolveFor(dateToUseForBiblical)
            if (today == null) {
                _state.value = _state.value.copy(
                    hasAnchor = true,
                    lunarLabel = "Unable to compute",
                    hint = "Check that you have at least one month start saved.",
                )
                updateUpcomingFeasts()
                return@launch
            }

            // Format as "1st day of the 11th month, 6025"
            val dayOrdinal = when (today.dayOfMonth) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "${today.dayOfMonth}th"
            }
            val monthOrdinal = when (today.monthNumber) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "${today.monthNumber}th"
            }
            val lunarLabel = "$dayOrdinal day of the $monthOrdinal month, ${today.yearNumber}"

            // Update state atomically - day transition and countdown use the same nextSunset from above
            val currentState = _state.value
            val userLoc = settings.getUserLocation()
            val existingLocationLabel = when {
                userLoc == null -> "Location not set — used for sunset"
                userLoc.label != null &&
                    lastGeocodedLatLon?.first == userLoc.latitude &&
                    lastGeocodedLatLon?.second == userLoc.longitude -> userLoc.label
                else -> currentState.sunsetInfo?.location?.takeIf {
                    lastGeocodedLatLon?.first == lat && lastGeocodedLatLon?.second == lon
                }
            }
            val newSunsetInfo = nextSunset?.let {
                SunsetInfo(it, zoneId.id, existingLocationLabel)
            } ?: currentState.sunsetInfo
            val monthStartEpoch = today.monthStart.toEpochDay()
            val ackEpoch = settings.getMoonPromptAckMonthStartEpoch()
            val ackDay = settings.getMoonPromptAckCompletedDay()
            val moonPromptHandledForToday = ackEpoch == monthStartEpoch && (
                (today.dayOfMonth == 29 && ackDay >= 29) ||
                    (today.dayOfMonth == 30 && ackDay >= 30)
                )
            val newState = TodayUiState(
                hasAnchor = true,
                lunarLabel = lunarLabel,
                gregorianLabel = todayDate.toString(),
                gregorianDaytimeDate = daytimeDate,
                gregorianSunsetDate = sunsetDate,
                isAfterSunset = isAfterSunset,
                currentDayOfMonth = today.dayOfMonth,
                showNextMonthButton = (today.dayOfMonth == 29 || today.dayOfMonth == 30) && !moonPromptHandledForToday,
                hint = if (today.dayOfMonth == 29 || today.dayOfMonth == 30) {
                    "Day ${today.dayOfMonth}: you'll get a prompt to confirm if the new moon was seen."
                } else null,
                isLoading = false,
                sunsetInfo = newSunsetInfo,
                jerusalemTimeInfo = currentState.jerusalemTimeInfo,
                upcomingFeasts = currentState.upcomingFeasts,
                todaysSchedule = currentState.todaysSchedule,
                isLoadingSunset = currentState.isLoadingSunset,
                isLoadingFeasts = currentState.isLoadingFeasts,
                showWidgetBanner = currentState.showWidgetBanner,
            )
            
            // Always update state to ensure consistency - the comparison was causing issues
            _state.value = newState
            
            // Update async data (feasts, sunset countdown, jerusalem time) after setting the main state
            // This ensures the date calculation is shown immediately
            updateUpcomingFeasts()
            updateSunsetCountdown()
            updateJerusalemTimeInfo()
            updateTodaysSchedule(dateToUseForBiblical)
            } finally {
                isRefreshing = false
            }
        }
    }

    private suspend fun updateTodaysSchedule(biblicalGregorianDate: LocalDate) {
        val personal = userEvents.getForDay(biblicalGregorianDate).map { event ->
            ScheduleItem(
                title = event.title,
                timeLabel = formatEventTime(event.allDay, event.startMinutesFromMidnight),
                source = "personal",
                notes = event.notes,
            )
        }
        val device = if (settings.showDeviceCalendarEvents.first() && deviceReader.hasReadPermission()) {
            val ids = settings.getDeviceCalendarIds()
            val hidden = settings.getHiddenDeviceEventIds()
            deviceReader.getEventsForDay(biblicalGregorianDate, ids, hidden).map { event ->
                ScheduleItem(
                    title = event.title,
                    timeLabel = formatEventTime(event.allDay, event.startMinutesFromMidnight),
                    source = "device",
                    notes = event.calendarDisplayName,
                    deviceEventId = event.id,
                    deviceBeginMillis = event.beginMillis,
                    deviceEndMillis = event.endMillis,
                )
            }
        } else {
            emptyList()
        }
        val combined = (personal + device).sortedWith(
            compareBy(
                { it.timeLabel == "All day" },
                { it.timeLabel },
                { it.title },
            )
        )
        _state.value = _state.value.copy(todaysSchedule = combined)
    }

    private fun updateSunsetCountdown() {
        viewModelScope.launch {
            val currentInfo = _state.value.sunsetInfo
            if (currentInfo == null) {
                _state.value = _state.value.copy(isLoadingSunset = false)
                return@launch
            }

            val userLoc = settings.getUserLocation()
            if (userLoc == null) {
                _state.value = _state.value.copy(
                    sunsetInfo = currentInfo.copy(location = "Location not set — used for sunset"),
                    isLoadingSunset = false,
                )
                return@launch
            }

            val lat = userLoc.latitude
            val lon = userLoc.longitude
            // Skip re-geocode only when coords and label already match
            if (userLoc.label != null &&
                lastGeocodedLatLon?.first == lat &&
                lastGeocodedLatLon?.second == lon
            ) {
                _state.value = _state.value.copy(
                    sunsetInfo = currentInfo.copy(location = userLoc.label),
                    isLoadingSunset = false,
                )
                return@launch
            }

            val locationStr = reverseGeocode(lat, lon) ?: userLoc.label
            lastGeocodedLatLon = Pair(lat, lon)
            if (locationStr != null && locationStr != userLoc.label) {
                settings.setLocationLabel(locationStr)
            }
            _state.value = _state.value.copy(
                sunsetInfo = currentInfo.copy(location = locationStr),
                isLoadingSunset = false,
            )
        }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.locality ?: address.adminArea ?: address.countryName
                } else null
            } catch (e: Exception) {
                null
            }
        }

    private suspend fun updateUpcomingFeasts() {
        _state.value = _state.value.copy(isLoadingFeasts = true)
        
        val today = LocalDate.now()
        val endDate = today.plusDays(30)
        val feasts = mutableListOf<UpcomingFeast>()

        // Get feasts from current and next year
        val currentYear = repo.getToday()?.yearNumber
        if (currentYear != null) {
            val yearFeasts = repo.feastDaysForYear(currentYear)
            feasts.addAll(
                yearFeasts
                    .filter { it.date.isAfter(today.minusDays(1)) && it.date.isBefore(endDate.plusDays(1)) }
                    .map { UpcomingFeast(it.title, it.date, Duration.between(today.atStartOfDay(), it.date.atStartOfDay()).toDays()) }
            )

            // Also check next year
            val nextYearFeasts = repo.feastDaysForYear(currentYear + 1)
            feasts.addAll(
                nextYearFeasts
                    .filter { it.date.isBefore(endDate.plusDays(1)) }
                    .map { UpcomingFeast(it.title, it.date, Duration.between(today.atStartOfDay(), it.date.atStartOfDay()).toDays()) }
            )
        }

        // Add weekly Shabbat (Friday sunset to Saturday sunset)
        var checkDate = today
        while (checkDate.isBefore(endDate)) {
            // Find next Friday
            val daysUntilFriday = (DayOfWeek.FRIDAY.value - checkDate.dayOfWeek.value + 7) % 7
            val friday = if (daysUntilFriday == 0 && checkDate.dayOfWeek == DayOfWeek.FRIDAY) {
                checkDate
            } else {
                checkDate.plusDays(daysUntilFriday.toLong())
            }

            if (friday.isBefore(endDate.plusDays(1))) {
                val daysUntil = Duration.between(today.atStartOfDay(), friday.atStartOfDay()).toDays()
                feasts.add(UpcomingFeast("Shabbat", friday, daysUntil))
            }
            checkDate = friday.plusDays(7)
        }

        // Sort by date and remove duplicates
        val sortedFeasts = feasts
            .sortedBy { it.date }
            .distinctBy { "${it.title}-${it.date}" }
            .filter { it.daysUntil >= 0 }

        _state.value = _state.value.copy(upcomingFeasts = sortedFeasts, isLoadingFeasts = false)
    }

    fun setCurrentDate(year: Int, month: Int, day: Int, referenceDate: LocalDate) {
        viewModelScope.launch {
            // Use the reference date that was shown in the dialog
            // This ensures consistency - the same input produces the same result regardless of when it's confirmed
            // Calculate month start: if day X occurs on referenceDate, month started (day-1) days earlier
            val monthStart = referenceDate.minusDays((day - 1).toLong())
            repo.setAnchor(year = year, month = month, startDate = monthStart)
            refresh()
        }
    }

    /**
     * Called when user confirms moon was seen on day 29.
     * If currently day 29: next month starts at sundown tonight.
     * If currently day 30 (user pressed after sunset): month ended at 29 days, today is day 1 of next month.
     */
    fun confirmMoonSeenOnDay29(currentDayOfMonth: Int) {
        viewModelScope.launch {
            val lunar = StatusUpdater.resolveCurrentBiblicalLunarDate(repo, settings) ?: return@launch
            val monthStartEpoch = lunar.monthStart.toEpochDay()
            val startDate = StatusUpdater.gregorianStartForNextMonthAfterMoonPrompt(
                moonSeen = true,
                dayOfMonth = currentDayOfMonth,
                settings = settings,
            )
            repo.startNextMonthOn(startDate)
            settings.setMoonPromptAck(monthStartEpoch, currentDayOfMonth.coerceIn(29, 30))
            refresh()
        }
    }

    /**
     * Called when user confirms moon was not seen on day 29.
     * If day 29: month continues to day 30.
     * If day 30: next month starts at sundown tonight.
     */
    fun confirmMoonNotSeenOnDay29(currentDayOfMonth: Int) {
        viewModelScope.launch {
            val lunar = StatusUpdater.resolveCurrentBiblicalLunarDate(repo, settings) ?: return@launch
            val monthStartEpoch = lunar.monthStart.toEpochDay()
            if (currentDayOfMonth == 30) {
                val startDate = StatusUpdater.gregorianStartForNextMonthAfterMoonPrompt(
                    moonSeen = false,
                    dayOfMonth = 30,
                    settings = settings,
                )
                repo.startNextMonthOn(startDate)
                settings.setMoonPromptAck(monthStartEpoch, 30)
            } else {
                settings.setProjectedMonthLength(lunar.yearNumber, lunar.monthNumber, 30)
                settings.setMoonPromptAck(monthStartEpoch, 29)
            }
            refresh()
        }
    }

    private fun updateJerusalemTimeInfo() {
        viewModelScope.launch {
            val showJerusalemTime = settings.showJerusalemTime.first()
            
            if (!showJerusalemTime) {
                _state.value = _state.value.copy(jerusalemTimeInfo = null)
                return@launch
            }

            // Jerusalem coordinates
            val jerusalemLatitude = 31.7683
            val jerusalemLongitude = 35.2137
            val jerusalemZoneId = ZoneId.of("Asia/Jerusalem")

            // Get current time in Jerusalem
            val currentTime = ZonedDateTime.now(jerusalemZoneId)

            // Calculate next sunset in Jerusalem
            val nextSunset = SunsetCalculator.calculateNextSunset(
                latitude = jerusalemLatitude,
                longitude = jerusalemLongitude,
                timeZone = jerusalemZoneId.id
            )

            val jerusalemTimeInfo = JerusalemTimeInfo(
                currentTime = currentTime,
                sunsetTime = nextSunset
            )

            // Only update if the sunset time actually changed (current time changes every second, but we calculate countdown in UI)
            val currentInfo = _state.value.jerusalemTimeInfo
            if (currentInfo == null || 
                currentInfo.sunsetTime != jerusalemTimeInfo.sunsetTime) {
                _state.value = _state.value.copy(jerusalemTimeInfo = jerusalemTimeInfo)
            }
        }
    }
}

