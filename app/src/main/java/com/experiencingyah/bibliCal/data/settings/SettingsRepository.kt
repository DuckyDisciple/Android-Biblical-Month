package com.experiencingyah.bibliCal.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "biblical_month_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val STATUS_NOTIFICATION_ENABLED = booleanPreferencesKey("status_notification_enabled")
        val PROMPTS_ENABLED = booleanPreferencesKey("prompts_enabled")
        val SELECTED_CALENDAR_ID = longPreferencesKey("selected_calendar_id")
        val MONTH_NAMING_MODE = stringPreferencesKey("month_naming_mode")
        val FIRSTFRUITS_RULE = stringPreferencesKey("firstfruits_rule")
        val INCLUDE_HANUKKAH = booleanPreferencesKey("include_hanukkah")
        val INCLUDE_PURIM = booleanPreferencesKey("include_purim")
        val SHOW_JERUSALEM_TIME = booleanPreferencesKey("show_jerusalem_time")
        /** If true, countdown uses civil twilight end (-6°) instead of geometric sunset (0°). */
        val USE_CIVIL_TWILIGHT_FOR_COUNTDOWN = booleanPreferencesKey("use_civil_twilight_for_countdown")

        val LAST_MOON_PROMPT_EPOCH_DAY = longPreferencesKey("last_moon_prompt_epoch_day")
        /** Epoch day of the biblical month start for which the user last answered the moon prompt (day 29 and/or 30). */
        val MOON_PROMPT_ACK_MONTH_START_EPOCH = longPreferencesKey("moon_prompt_ack_month_start_epoch")
        /** Highest biblical day (29 or 30) answered for that month start. */
        val MOON_PROMPT_ACK_COMPLETED_DAY = intPreferencesKey("moon_prompt_ack_completed_day")
        val LAST_AVIV_PROMPT_EPOCH_DAY = longPreferencesKey("last_aviv_prompt_epoch_day")
        val LAST_SHABBAT_REMINDER_EPOCH_DAY = longPreferencesKey("last_shabbat_reminder_epoch_day")
        val LAST_FEAST_REMINDER_EPOCH_DAY = longPreferencesKey("last_feast_reminder_epoch_day")
        val PROJECT_EXTRA_MONTH = booleanPreferencesKey("project_extra_month")
        val PROJECTED_MONTH_LENGTHS = stringPreferencesKey("projected_month_lengths") // JSON: {"year-month": "29" or "30"}
        
        // User-chosen location for sunset calculations (no TTL once set)
        val USER_LATITUDE = doublePreferencesKey("user_latitude")
        val USER_LONGITUDE = doublePreferencesKey("user_longitude")
        val LOCATION_LABEL = stringPreferencesKey("location_label")
        val LOCATION_SOURCE = stringPreferencesKey("location_source")
        // Legacy cache keys (migrated into user location on read)
        val CACHED_LATITUDE = doublePreferencesKey("cached_latitude")
        val CACHED_LONGITUDE = doublePreferencesKey("cached_longitude")
        val CACHED_LOCATION_TIMESTAMP = longPreferencesKey("cached_location_timestamp")

        // Device calendar overlay
        val SHOW_DEVICE_CALENDAR_EVENTS = booleanPreferencesKey("show_device_calendar_events")
        val DEVICE_CALENDAR_IDS = stringPreferencesKey("device_calendar_ids") // comma-separated IDs
        /** Event IDs (masters) hidden from the BibliCal overlay only. */
        val HIDDEN_DEVICE_EVENT_IDS = stringPreferencesKey("hidden_device_event_ids")
        
        // Widget banner dismissal tracking (reappears on app update)
        val WIDGET_BANNER_DISMISSED_VERSION = stringPreferencesKey("widget_banner_dismissed_version")
    }

    val statusNotificationEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.STATUS_NOTIFICATION_ENABLED] ?: true }

    val promptsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PROMPTS_ENABLED] ?: true }

    val selectedCalendarId: Flow<Long> =
        context.dataStore.data.map { it[Keys.SELECTED_CALENDAR_ID] ?: -1L }

    val monthNamingMode: Flow<MonthNamingMode> =
        context.dataStore.data.map { prefs ->
            MonthNamingMode.fromStored(prefs[Keys.MONTH_NAMING_MODE])
        }

    val firstfruitsRule: Flow<FirstfruitsRule> =
        context.dataStore.data.map { prefs ->
            FirstfruitsRule.fromStored(prefs[Keys.FIRSTFRUITS_RULE])
        }

    val includeHanukkah: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INCLUDE_HANUKKAH] ?: false }

    val includePurim: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INCLUDE_PURIM] ?: false }

    val showJerusalemTime: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHOW_JERUSALEM_TIME] ?: false }

    val useCivilTwilightForCountdown: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.USE_CIVIL_TWILIGHT_FOR_COUNTDOWN] ?: true }

    val showDeviceCalendarEvents: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHOW_DEVICE_CALENDAR_EVENTS] ?: false }

    val locationLabel: Flow<String?> =
        context.dataStore.data.map { it[Keys.LOCATION_LABEL] }

    suspend fun setStatusNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STATUS_NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setPromptsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROMPTS_ENABLED] = enabled }
    }

    suspend fun setSelectedCalendarId(calendarId: Long) {
        context.dataStore.edit { it[Keys.SELECTED_CALENDAR_ID] = calendarId }
    }

    suspend fun setMonthNamingMode(mode: MonthNamingMode) {
        context.dataStore.edit { it[Keys.MONTH_NAMING_MODE] = mode.storedValue }
    }

    suspend fun setFirstfruitsRule(rule: FirstfruitsRule) {
        context.dataStore.edit { it[Keys.FIRSTFRUITS_RULE] = rule.storedValue }
    }

    suspend fun getLastMoonPromptEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_MOON_PROMPT_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastMoonPromptEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_MOON_PROMPT_EPOCH_DAY] = epochDay }
    }

    suspend fun getMoonPromptAckMonthStartEpoch(): Long =
        context.dataStore.data.first()[Keys.MOON_PROMPT_ACK_MONTH_START_EPOCH] ?: Long.MIN_VALUE

    suspend fun getMoonPromptAckCompletedDay(): Int =
        context.dataStore.data.first()[Keys.MOON_PROMPT_ACK_COMPLETED_DAY] ?: 0

    suspend fun setMoonPromptAck(monthStartEpochDay: Long, completedThroughDay: Int) {
        context.dataStore.edit {
            it[Keys.MOON_PROMPT_ACK_MONTH_START_EPOCH] = monthStartEpochDay
            it[Keys.MOON_PROMPT_ACK_COMPLETED_DAY] = completedThroughDay
        }
    }

    suspend fun getLastAvivPromptEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_AVIV_PROMPT_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastAvivPromptEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_AVIV_PROMPT_EPOCH_DAY] = epochDay }
    }

    suspend fun setIncludeHanukkah(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCLUDE_HANUKKAH] = enabled }
    }

    suspend fun setIncludePurim(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCLUDE_PURIM] = enabled }
    }

    suspend fun setShowJerusalemTime(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_JERUSALEM_TIME] = enabled }
    }

    suspend fun setUseCivilTwilightForCountdown(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_CIVIL_TWILIGHT_FOR_COUNTDOWN] = enabled }
    }

    suspend fun getLastShabbatReminderEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_SHABBAT_REMINDER_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastShabbatReminderEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_SHABBAT_REMINDER_EPOCH_DAY] = epochDay }
    }

    suspend fun getLastFeastReminderEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_FEAST_REMINDER_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastFeastReminderEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_FEAST_REMINDER_EPOCH_DAY] = epochDay }
    }

    val projectExtraMonth: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PROJECT_EXTRA_MONTH] ?: false }

    suspend fun setProjectExtraMonth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROJECT_EXTRA_MONTH] = enabled }
    }

    /**
     * Get projected month length (29 or 30) for a specific year-month, or null if not set
     */
    suspend fun getProjectedMonthLength(year: Int, month: Int): Int? {
        val jsonStr = context.dataStore.data.first()[Keys.PROJECTED_MONTH_LENGTHS] ?: return null
        if (jsonStr.isEmpty()) return null
        
        return try {
            val json = org.json.JSONObject(jsonStr)
            val key = "$year-$month"
            val value = json.optString(key, null)
            if (value != null && value.isNotEmpty()) {
                value.toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Set projected month length (29 or 30) for a specific year-month, or null to remove
     */
    suspend fun setProjectedMonthLength(year: Int, month: Int, length: Int?) {
        context.dataStore.edit { prefs ->
            val currentJsonStr = prefs[Keys.PROJECTED_MONTH_LENGTHS] ?: "{}"
            val json = try {
                org.json.JSONObject(currentJsonStr)
            } catch (e: Exception) {
                org.json.JSONObject()
            }
            
            val key = "$year-$month"
            if (length != null) {
                json.put(key, length.toString())
            } else {
                json.remove(key)
            }
            
            prefs[Keys.PROJECTED_MONTH_LENGTHS] = json.toString()
        }
    }

    /**
     * Get all projected month lengths for a given year
     */
    suspend fun getProjectedMonthsForYear(year: Int): Map<Int, Int> {
        val jsonStr = context.dataStore.data.first()[Keys.PROJECTED_MONTH_LENGTHS] ?: return emptyMap()
        if (jsonStr.isEmpty()) return emptyMap()
        
        return try {
            val json = org.json.JSONObject(jsonStr)
            val result = mutableMapOf<Int, Int>()
            json.keys().forEach { key ->
                if (key.startsWith("$year-")) {
                    val month = key.substringAfter("-").toIntOrNull()
                    val length = json.optString(key, null)?.toIntOrNull()
                    if (month != null && length != null) {
                        result[month] = length
                    }
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Cascade projected month lengths from a starting year-month forward using alternating 29/30 pattern.
     * This rewrites all future projected months, discarding any existing overrides.
     * 
     * @param startYear The year of the month that triggered the cascade
     * @param startMonth The month number (1-13) that triggered the cascade
     * @param startLength The length (29 or 30) of the starting month
     * @param yearsToProject How many years into the future to project (default 10)
     */
    suspend fun cascadeProjectedMonthLengths(
        startYear: Int,
        startMonth: Int,
        startLength: Int,
        yearsToProject: Int = 10
    ) {
        context.dataStore.edit { prefs ->
            val currentJsonStr = prefs[Keys.PROJECTED_MONTH_LENGTHS] ?: "{}"
            val json = try {
                org.json.JSONObject(currentJsonStr)
            } catch (e: Exception) {
                org.json.JSONObject()
            }
            
            // Remove all entries at or after the starting year-month
            val keysToRemove = mutableListOf<String>()
            json.keys().forEach { key ->
                val parts = key.split("-")
                if (parts.size == 2) {
                    val keyYear = parts[0].toIntOrNull()
                    val keyMonth = parts[1].toIntOrNull()
                    if (keyYear != null && keyMonth != null) {
                        // Remove if this key is at or after the start point
                        if (keyYear > startYear || (keyYear == startYear && keyMonth >= startMonth)) {
                            keysToRemove.add(key)
                        }
                    }
                }
            }
            keysToRemove.forEach { json.remove(it) }
            
            // Now write alternating lengths starting from startYear-startMonth
            var currentYear = startYear
            var currentMonth = startMonth
            var currentLength = startLength
            val endYear = startYear + yearsToProject
            
            while (currentYear <= endYear) {
                val key = "$currentYear-$currentMonth"
                json.put(key, currentLength.toString())
                
                // Alternate for next month
                currentLength = if (currentLength == 29) 30 else 29
                
                // Move to next month
                currentMonth++
                if (currentMonth > 13) {
                    currentMonth = 1
                    currentYear++
                } else if (currentMonth == 13) {
                    // Month 13 only exists in some years; always include it in projections
                    // and let the actual month calculation decide if it's used
                }
            }
            
            prefs[Keys.PROJECTED_MONTH_LENGTHS] = json.toString()
        }
    }

    /**
     * Persist GPS location as the user's chosen location (no expiry).
     * Also writes legacy cache keys for older call sites.
     */
    suspend fun cacheLocation(latitude: Double, longitude: Double, label: String? = null) {
        setUserLocation(latitude, longitude, label, LocationSource.GPS)
    }

    /**
     * Persist a manually chosen city/coordinates as the user's location.
     */
    suspend fun setManualLocation(latitude: Double, longitude: Double, label: String?) {
        setUserLocation(latitude, longitude, label, LocationSource.MANUAL)
    }

    suspend fun setUserLocation(
        latitude: Double,
        longitude: Double,
        label: String?,
        source: LocationSource,
    ) {
        context.dataStore.edit {
            it[Keys.USER_LATITUDE] = latitude
            it[Keys.USER_LONGITUDE] = longitude
            it[Keys.LOCATION_SOURCE] = source.storedValue
            if (label != null) {
                it[Keys.LOCATION_LABEL] = label
            } else {
                it.remove(Keys.LOCATION_LABEL)
            }
            // Keep legacy keys in sync for any remaining readers
            it[Keys.CACHED_LATITUDE] = latitude
            it[Keys.CACHED_LONGITUDE] = longitude
            it[Keys.CACHED_LOCATION_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun clearUserLocation() {
        context.dataStore.edit {
            it.remove(Keys.USER_LATITUDE)
            it.remove(Keys.USER_LONGITUDE)
            it.remove(Keys.LOCATION_LABEL)
            it[Keys.LOCATION_SOURCE] = LocationSource.UNSET.storedValue
            it.remove(Keys.CACHED_LATITUDE)
            it.remove(Keys.CACHED_LONGITUDE)
            it.remove(Keys.CACHED_LOCATION_TIMESTAMP)
        }
    }

    /**
     * User-chosen location, or null if never set.
     * Migrates legacy cached lat/lon (even if "stale") into the new keys once.
     */
    suspend fun getUserLocation(): UserLocation? {
        val prefs = context.dataStore.data.first()
        val userLat = prefs[Keys.USER_LATITUDE]
        val userLon = prefs[Keys.USER_LONGITUDE]
        if (userLat != null && userLon != null) {
            return UserLocation(
                latitude = userLat,
                longitude = userLon,
                label = prefs[Keys.LOCATION_LABEL],
                source = LocationSource.fromStored(prefs[Keys.LOCATION_SOURCE]),
            )
        }
        // Migrate legacy cache (ignore 24h TTL — treat as user location once known)
        val legacyLat = prefs[Keys.CACHED_LATITUDE]
        val legacyLon = prefs[Keys.CACHED_LONGITUDE]
        if (legacyLat != null && legacyLon != null) {
            setUserLocation(legacyLat, legacyLon, prefs[Keys.LOCATION_LABEL], LocationSource.GPS)
            return UserLocation(legacyLat, legacyLon, prefs[Keys.LOCATION_LABEL], LocationSource.GPS)
        }
        return null
    }

    /**
     * @deprecated Prefer [getUserLocation]. Returns coords only, or null if unset.
     */
    suspend fun getCachedLocation(): Pair<Double, Double>? =
        getUserLocation()?.let { Pair(it.latitude, it.longitude) }

    suspend fun setLocationLabel(label: String?) {
        context.dataStore.edit {
            if (label != null) it[Keys.LOCATION_LABEL] = label
            else it.remove(Keys.LOCATION_LABEL)
        }
    }

    suspend fun setShowDeviceCalendarEvents(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DEVICE_CALENDAR_EVENTS] = enabled }
    }

    suspend fun getDeviceCalendarIds(): Set<Long> {
        val raw = context.dataStore.data.first()[Keys.DEVICE_CALENDAR_IDS] ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    val deviceCalendarIds: Flow<Set<Long>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[Keys.DEVICE_CALENDAR_IDS] ?: return@map emptySet()
            if (raw.isBlank()) emptySet()
            else raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
        }

    suspend fun setDeviceCalendarIds(ids: Set<Long>) {
        context.dataStore.edit {
            it[Keys.DEVICE_CALENDAR_IDS] = ids.sorted().joinToString(",")
        }
    }

    suspend fun getHiddenDeviceEventIds(): Set<Long> {
        val raw = context.dataStore.data.first()[Keys.HIDDEN_DEVICE_EVENT_IDS] ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    val hiddenDeviceEventIds: Flow<Set<Long>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[Keys.HIDDEN_DEVICE_EVENT_IDS] ?: return@map emptySet()
            if (raw.isBlank()) emptySet()
            else raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
        }

    suspend fun hideDeviceEventId(eventId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_DEVICE_EVENT_IDS]
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()
            current.add(eventId)
            prefs[Keys.HIDDEN_DEVICE_EVENT_IDS] = current.sorted().joinToString(",")
        }
    }

    suspend fun clearHiddenDeviceEventIds() {
        context.dataStore.edit { it.remove(Keys.HIDDEN_DEVICE_EVENT_IDS) }
    }

    /**
     * Get the app version at which the widget banner was dismissed.
     * Returns null if never dismissed.
     */
    suspend fun getWidgetBannerDismissedVersion(): String? {
        return context.dataStore.data.first()[Keys.WIDGET_BANNER_DISMISSED_VERSION]
    }

    /**
     * Record that the widget banner was dismissed for the given app version.
     */
    suspend fun setWidgetBannerDismissedVersion(versionName: String) {
        context.dataStore.edit { it[Keys.WIDGET_BANNER_DISMISSED_VERSION] = versionName }
    }
}

enum class MonthNamingMode(val storedValue: String) {
    ORDINAL("ordinal"),
    NUMBERED("numbered");

    companion object {
        fun fromStored(stored: String?): MonthNamingMode =
            values().firstOrNull { it.storedValue == stored } ?: ORDINAL
    }
}

enum class FirstfruitsRule(val storedValue: String) {
    FIXED_DAY_16("fixed_day_16"),
    SUNDAY_DURING_UNLEAVENED_BREAD("sunday_during_unleavened_bread");

    companion object {
        fun fromStored(stored: String?): FirstfruitsRule =
            values().firstOrNull { it.storedValue == stored } ?: FIXED_DAY_16
    }
}

enum class LocationSource(val storedValue: String) {
    UNSET("unset"),
    GPS("gps"),
    MANUAL("manual");

    companion object {
        fun fromStored(stored: String?): LocationSource =
            values().firstOrNull { it.storedValue == stored } ?: UNSET
    }
}

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String?,
    val source: LocationSource,
)

