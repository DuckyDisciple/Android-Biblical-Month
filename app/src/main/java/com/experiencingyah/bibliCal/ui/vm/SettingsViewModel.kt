package com.experiencingyah.bibliCal.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.FirstfruitsRule
import com.experiencingyah.bibliCal.data.settings.LocationSource
import com.experiencingyah.bibliCal.data.settings.MonthNamingMode
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val statusNotificationEnabled: Boolean = true,
    val promptsEnabled: Boolean = true,
    val monthNamingMode: MonthNamingMode = MonthNamingMode.ORDINAL,
    val firstfruitsRule: FirstfruitsRule = FirstfruitsRule.FIXED_DAY_16,
    val selectedCalendarId: Long = -1L,
    val hasAnchor: Boolean = false,
    val includeHanukkah: Boolean = false,
    val includePurim: Boolean = false,
    val showJerusalemTime: Boolean = false,
    val useCivilTwilightForCountdown: Boolean = true,
    val locationLabel: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationSource: LocationSource = LocationSource.UNSET,
    val deviceTimezoneDisplay: String = "",
    val showDeviceCalendarEvents: Boolean = false,
    val deviceCalendarIds: Set<Long> = emptySet(),
    val hiddenDeviceEventCount: Int = 0,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsRepository(app)
    private val repo = LunarRepository(app)
    private val geocoder = android.location.Geocoder(app)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val userLoc = settings.getUserLocation()
            val zoneId = java.time.ZoneId.systemDefault()
            val zoneDisplay = try {
                val name = zoneId.getDisplayName(
                    java.time.format.TextStyle.FULL,
                    java.util.Locale.getDefault(),
                )
                "$name (${zoneId.id})"
            } catch (e: Exception) {
                zoneId.id
            }
            _state.value = SettingsUiState(
                statusNotificationEnabled = settings.statusNotificationEnabled.first(),
                promptsEnabled = settings.promptsEnabled.first(),
                monthNamingMode = settings.monthNamingMode.first(),
                firstfruitsRule = settings.firstfruitsRule.first(),
                selectedCalendarId = settings.selectedCalendarId.first(),
                hasAnchor = repo.hasAnyAnchor(),
                includeHanukkah = settings.includeHanukkah.first(),
                includePurim = settings.includePurim.first(),
                showJerusalemTime = settings.showJerusalemTime.first(),
                useCivilTwilightForCountdown = settings.useCivilTwilightForCountdown.first(),
                locationLabel = userLoc?.label,
                locationLatitude = userLoc?.latitude,
                locationLongitude = userLoc?.longitude,
                locationSource = userLoc?.source ?: LocationSource.UNSET,
                deviceTimezoneDisplay = zoneDisplay,
                showDeviceCalendarEvents = settings.showDeviceCalendarEvents.first(),
                deviceCalendarIds = settings.getDeviceCalendarIds(),
                hiddenDeviceEventCount = settings.getHiddenDeviceEventIds().size,
            )
        }
    }

    fun setStatusNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setStatusNotificationEnabled(enabled)
            refresh()
        }
    }

    fun setPromptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setPromptsEnabled(enabled)
            refresh()
        }
    }

    fun setMonthNamingMode(mode: MonthNamingMode) {
        viewModelScope.launch {
            settings.setMonthNamingMode(mode)
            refresh()
        }
    }

    fun setSelectedCalendarId(id: Long) {
        viewModelScope.launch {
            settings.setSelectedCalendarId(id)
            refresh()
        }
    }

    fun setFirstfruitsRule(rule: FirstfruitsRule) {
        viewModelScope.launch {
            settings.setFirstfruitsRule(rule)
            refresh()
        }
    }

    suspend fun currentYearNumberOrNull(): Int? = repo.getToday()?.yearNumber

    fun setIncludeHanukkah(enabled: Boolean) {
        viewModelScope.launch {
            settings.setIncludeHanukkah(enabled)
            refresh()
        }
    }

    fun setIncludePurim(enabled: Boolean) {
        viewModelScope.launch {
            settings.setIncludePurim(enabled)
            refresh()
        }
    }

    fun setShowJerusalemTime(enabled: Boolean) {
        viewModelScope.launch {
            settings.setShowJerusalemTime(enabled)
            refresh()
        }
    }

    fun setUseCivilTwilightForCountdown(enabled: Boolean) {
        viewModelScope.launch {
            settings.setUseCivilTwilightForCountdown(enabled)
            refresh()
        }
    }

    fun setLocationFromGps(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val label = reverseGeocode(latitude, longitude)
            settings.cacheLocation(latitude, longitude, label)
            refresh()
        }
    }

    fun searchAndSetCity(query: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                onResult(false, "Enter a city name")
                return@launch
            }
            val results = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(trimmed, 1)
                } catch (e: Exception) {
                    null
                }
            }
            val address = results?.firstOrNull()
            if (address == null) {
                onResult(false, "No results for \"$trimmed\"")
                return@launch
            }
            val label = address.locality
                ?: address.subAdminArea
                ?: address.adminArea
                ?: trimmed
            val display = listOfNotNull(
                label,
                address.adminArea?.takeIf { it != label },
                address.countryCode,
            ).distinct().joinToString(", ")
            settings.setManualLocation(address.latitude, address.longitude, display)
            refresh()
            onResult(true, "Location set to $display")
        }
    }

    fun setShowDeviceCalendarEvents(enabled: Boolean) {
        viewModelScope.launch {
            settings.setShowDeviceCalendarEvents(enabled)
            refresh()
        }
    }

    fun setDeviceCalendarIds(ids: Set<Long>) {
        viewModelScope.launch {
            settings.setDeviceCalendarIds(ids)
            refresh()
        }
    }

    fun toggleDeviceCalendarId(id: Long, allCalendarIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            val current = settings.getDeviceCalendarIds().toMutableSet()
            if (current.isEmpty() && allCalendarIds.isNotEmpty()) {
                // Empty means "all" — start from full set then remove the toggled id
                current.addAll(allCalendarIds)
                current.remove(id)
            } else if (!current.add(id)) {
                current.remove(id)
            }
            settings.setDeviceCalendarIds(current)
            refresh()
        }
    }

    fun clearHiddenDeviceEvents() {
        viewModelScope.launch {
            settings.clearHiddenDeviceEventIds()
            refresh()
        }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.locality ?: address.adminArea ?: address.countryName
                } else null
            } catch (e: Exception) {
                null
            }
        }
}

