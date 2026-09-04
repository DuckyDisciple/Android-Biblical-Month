package com.experiencingyah.bibliCal.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.experiencingyah.bibliCal.calendar.DeviceCalendarEvent
import com.experiencingyah.bibliCal.calendar.DeviceCalendarReader
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.UserEvent
import com.experiencingyah.bibliCal.data.UserEventRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.domain.FeastDay
import com.experiencingyah.bibliCal.domain.LunarMonth
import com.experiencingyah.bibliCal.domain.MonthStatus
import com.experiencingyah.bibliCal.util.MonthNames
import com.experiencingyah.bibliCal.util.SunsetCalculator
import com.experiencingyah.bibliCal.work.StatusUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class DayCell(
    val gregorianDate: LocalDate,
    val lunarDay: Int,
    val isToday: Boolean,
    val feastTitles: List<String>,
    /** Inclusive from Firstfruits (1) through Shavuot (50); null outside that range. */
    val omerDay: Int? = null,
    val hasPersonalEvents: Boolean = false,
)

data class ProjectedMonthInfo(
    val year: Int,
    val month: Int,
    val monthName: String,
)

data class DayDetailState(
    val date: LocalDate,
    val lunarDay: Int,
    val feastTitles: List<String>,
    val userEvents: List<UserEvent>,
    val deviceEvents: List<DeviceCalendarEvent>,
)

data class CalendarUiState(
    val title: String = "Calendar",
    val subtitle: String? = null,
    val month: LunarMonth? = null,
    val weeks: List<List<DayCell?>> = emptyList(),
    val feastsInMonth: List<FeastDay> = emptyList(),
    val projectExtraMonth: Boolean = false,
    val projectedMonths: Map<Pair<Int, Int>, Int> = emptyMap(),
    val projectedMonthInfos: List<ProjectedMonthInfo> = emptyList(),
    val currentMonthProjectedLength: Int? = null,
    val isCalendarLoading: Boolean = true,
    val selectedDay: DayDetailState? = null,
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LunarRepository(app)
    private val settings = SettingsRepository(app)
    private val userEvents = UserEventRepository(app)
    private val deviceReader = DeviceCalendarReader(app)

    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val today = repo.getToday()
            if (today != null) {
                selectedYear = today.yearNumber
                selectedMonth = today.monthNumber
                load()
            } else {
                _state.value = CalendarUiState(
                    title = "Calendar",
                    subtitle = "Set an anchor on the Today tab to begin.",
                    isCalendarLoading = false,
                )
            }
        }

        viewModelScope.launch {
            settings.includeHanukkah.collect { refresh() }
        }
        viewModelScope.launch {
            settings.includePurim.collect { refresh() }
        }
        viewModelScope.launch {
            settings.showDeviceCalendarEvents.collect { refresh() }
        }
        viewModelScope.launch {
            settings.deviceCalendarIds.collect { refresh() }
        }
        viewModelScope.launch {
            settings.hiddenDeviceEventIds.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val today = repo.getToday()
            if (today != null) {
                if (selectedYear == null || selectedMonth == null) {
                    selectedYear = today.yearNumber
                    selectedMonth = today.monthNumber
                }
                load()
            } else {
                _state.value = CalendarUiState(
                    title = "Calendar",
                    subtitle = "Set an anchor on the Today tab to begin.",
                    isCalendarLoading = false,
                )
            }
        }
    }

    fun nextMonth() {
        viewModelScope.launch {
            val y = selectedYear ?: return@launch
            val m = selectedMonth ?: return@launch
            val projectExtraMonth = settings.projectExtraMonth.first()
            val (ny, nm) = when (m) {
                12 -> {
                    if (repo.getBarleyDecision(y) == false) {
                        y to 13
                    } else if (projectExtraMonth) {
                        y to 13
                    } else {
                        (y + 1) to 1
                    }
                }
                13 -> (y + 1) to 1
                else -> y to (m + 1)
            }
            val nextMonthData = repo.getMonth(ny, nm) ?: return@launch
            selectedYear = ny
            selectedMonth = nm
            load()
        }
    }

    fun prevMonth() {
        viewModelScope.launch {
            val y = selectedYear ?: return@launch
            val m = selectedMonth ?: return@launch
            val (py, pm) = when (m) {
                1 -> {
                    val prevYear = (y - 1).coerceAtLeast(1)
                    val lastMonth = if (repo.getBarleyDecision(prevYear) == false) 13 else 12
                    prevYear to lastMonth
                }
                else -> y to (m - 1)
            }
            val prevMonthData = repo.getMonth(py, pm) ?: return@launch
            selectedYear = py
            selectedMonth = pm
            load()
        }
    }

    fun selectDay(cell: DayCell) {
        viewModelScope.launch {
            val showDevice = settings.showDeviceCalendarEvents.first()
            val calendarIds = settings.getDeviceCalendarIds()
            val hiddenIds = settings.getHiddenDeviceEventIds()
            val user = userEvents.getForDay(cell.gregorianDate)
            val device = if (showDevice && deviceReader.hasReadPermission()) {
                deviceReader.getEventsForDay(cell.gregorianDate, calendarIds, hiddenIds)
            } else {
                emptyList()
            }
            _state.value = _state.value.copy(
                selectedDay = DayDetailState(
                    date = cell.gregorianDate,
                    lunarDay = cell.lunarDay,
                    feastTitles = cell.feastTitles,
                    userEvents = user,
                    deviceEvents = device,
                )
            )
        }
    }

    fun dismissDayDetail() {
        _state.value = _state.value.copy(selectedDay = null)
    }

    fun addUserEvent(
        title: String,
        date: LocalDate,
        allDay: Boolean,
        startMinutes: Int?,
        notes: String?,
    ) {
        viewModelScope.launch {
            userEvents.insert(title, date, allDay, startMinutes, notes)
            reloadSelectedDay(date)
            load()
        }
    }

    fun updateUserEvent(event: UserEvent) {
        viewModelScope.launch {
            userEvents.update(event)
            reloadSelectedDay(event.date)
            load()
        }
    }

    fun deleteUserEvent(id: Long, date: LocalDate) {
        viewModelScope.launch {
            userEvents.delete(id)
            reloadSelectedDay(date)
            load()
        }
    }

    fun hideDeviceEvent(eventId: Long, date: LocalDate) {
        removeDeviceEventFromUi(eventId, date)
        viewModelScope.launch {
            settings.hideDeviceEventId(eventId)
            load()
        }
    }

    /**
     * Optimistically removes the event from the day sheet, then tries to delete it
     * from the phone calendar. Always hides in BibliCal so a failed/permission-blocked
     * delete still clears the ghost from this app.
     *
     * @return false if write permission is missing (caller should request it and retry).
     */
    fun deleteDeviceEventSeries(eventId: Long, date: LocalDate): Boolean {
        if (!deviceReader.hasWritePermission()) return false
        removeDeviceEventFromUi(eventId, date)
        viewModelScope.launch {
            settings.hideDeviceEventId(eventId)
            deviceReader.deleteEventSeries(eventId)
            load()
        }
        return true
    }

    private fun removeDeviceEventFromUi(eventId: Long, date: LocalDate) {
        val selected = _state.value.selectedDay ?: return
        if (selected.date != date) return
        _state.value = _state.value.copy(
            selectedDay = selected.copy(
                deviceEvents = selected.deviceEvents.filterNot { it.id == eventId },
            ),
        )
    }

    private suspend fun reloadSelectedDay(date: LocalDate) {
        val selected = _state.value.selectedDay ?: return
        if (selected.date != date) return
        val showDevice = settings.showDeviceCalendarEvents.first()
        val calendarIds = settings.getDeviceCalendarIds()
        val hiddenIds = settings.getHiddenDeviceEventIds()
        val user = userEvents.getForDay(date)
        val device = if (showDevice && deviceReader.hasReadPermission()) {
            deviceReader.getEventsForDay(date, calendarIds, hiddenIds)
        } else {
            emptyList()
        }
        _state.value = _state.value.copy(
            selectedDay = selected.copy(userEvents = user, deviceEvents = device)
        )
    }

    private fun load() {
        viewModelScope.launch {
            val y = selectedYear ?: return@launch
            val m = selectedMonth ?: return@launch
            val month = repo.getMonth(y, m)
            if (month == null) {
                _state.value = CalendarUiState(
                    title = "Calendar",
                    subtitle = "Missing month data; try setting an anchor.",
                    isCalendarLoading = false,
                )
                return@launch
            }

            val namingMode = settings.monthNamingMode.first()
            val projectExtraMonth = settings.projectExtraMonth.first()
            val title = "${MonthNames.format(month.monthNumber, namingMode)} Month — Year ${month.yearNumber}"

            val projectedMonthsForYear = settings.getProjectedMonthsForYear(y)
            val projectedMonthsMap = projectedMonthsForYear.mapKeys { (monthNum, _) -> y to monthNum }

            val projectedMonthInfos = if (month.status == MonthStatus.PROJECTED) {
                val monthName = MonthNames.format(month.monthNumber, namingMode)
                listOf(ProjectedMonthInfo(y, m, monthName))
            } else {
                emptyList()
            }

            var adjustedMonth = month
            val userProjectedLength = projectedMonthsMap[y to m]
            if (userProjectedLength != null && month.status == MonthStatus.PROJECTED) {
                adjustedMonth = month.copy(lengthDays = userProjectedLength)
            }

            val monthStart = adjustedMonth.startDate
            val monthEnd = monthStart.plusDays((adjustedMonth.lengthDays - 1).toLong())
            val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
            val startMonthName = monthStart.format(monthFormatter)
            val endMonthName = monthEnd.format(monthFormatter)
            val subtitle = if (startMonthName == endMonthName) startMonthName else "$startMonthName - $endMonthName"

            val currentProjectedLength = if (month.status == MonthStatus.PROJECTED) {
                userProjectedLength ?: repo.getProjectedLengthForMonth(y, m)
            } else {
                null
            }

            var feasts = repo.feastDaysForYear(y)
            if (m == 12 && settings.includePurim.first() && !feasts.any { it.title.contains("Purim") }) {
                val purimDate = adjustedMonth.startDate.plusDays(13)
                feasts = feasts + FeastDay("Purim (14/12)", purimDate, y, 12, 14)
            }
            val feastByDate = feasts.groupBy { it.date }.mapValues { it.value.map(FeastDay::title) }

            val omerFirst = feasts.firstOrNull { it.title == "Firstfruits" }?.date
            val omerLast = feasts.firstOrNull { it.title.contains("Shavuot", ignoreCase = true) }?.date
            fun omerDayFor(g: LocalDate): Int? {
                val first = omerFirst ?: return null
                val last = omerLast ?: return null
                if (g.isBefore(first) || g.isAfter(last)) return null
                return ChronoUnit.DAYS.between(first, g).toInt() + 1
            }

            val feastsInMonth = feasts.filter { it.date >= monthStart && it.date <= monthEnd }
                .map { feast ->
                    if (feast.dayOfMonth == 0) {
                        val daysFromStart = (ChronoUnit.DAYS.between(monthStart, feast.date) + 1).toInt()
                        if (daysFromStart > 0 && daysFromStart <= 30) {
                            feast.copy(dayOfMonth = daysFromStart)
                        } else {
                            val actualMonth = repo.getMonth(feast.yearNumber, feast.monthNumber)
                            if (actualMonth != null && feast.date >= actualMonth.startDate) {
                                val daysFromActualStart =
                                    (ChronoUnit.DAYS.between(actualMonth.startDate, feast.date) + 1).toInt()
                                if (daysFromActualStart > 0 && daysFromActualStart <= 30) {
                                    feast.copy(dayOfMonth = daysFromActualStart)
                                } else feast
                            } else feast
                        }
                    } else feast
                }

            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val (lat, lon) = StatusUpdater.getLocation(settings)
            val useCivilTwilight = settings.useCivilTwilightForCountdown.first()
            val elevation = if (useCivilTwilight) {
                SunsetCalculator.SOLAR_ELEVATION_CIVIL_TWILIGHT
            } else {
                SunsetCalculator.SOLAR_ELEVATION_GEOMETRIC
            }
            val dateToUseForToday = StatusUpdater.getDateForBiblicalCalculation(now, lat, lon, elevation)

            val personalDays = userEvents.epochDaysWithEvents(monthStart, monthEnd).toMutableSet()
            val showDevice = settings.showDeviceCalendarEvents.first()
            if (showDevice && deviceReader.hasReadPermission()) {
                val calendarIds = settings.getDeviceCalendarIds()
                val hiddenIds = settings.getHiddenDeviceEventIds()
                personalDays += deviceReader.epochDaysWithEvents(monthStart, monthEnd, calendarIds, hiddenIds)
            }

            val cells = (0 until adjustedMonth.lengthDays).map { offset ->
                val g = adjustedMonth.startDate.plusDays(offset.toLong())
                DayCell(
                    gregorianDate = g,
                    lunarDay = offset + 1,
                    isToday = g == dateToUseForToday,
                    feastTitles = feastByDate[g].orEmpty(),
                    omerDay = omerDayFor(g),
                    hasPersonalEvents = personalDays.contains(g.toEpochDay()),
                )
            }

            val previousSelected = _state.value.selectedDay
            _state.value = CalendarUiState(
                title = title,
                subtitle = subtitle,
                month = adjustedMonth,
                weeks = toWeeks(adjustedMonth.startDate, cells),
                feastsInMonth = feastsInMonth,
                projectExtraMonth = projectExtraMonth,
                projectedMonths = projectedMonthsMap,
                projectedMonthInfos = projectedMonthInfos,
                currentMonthProjectedLength = currentProjectedLength,
                isCalendarLoading = false,
                selectedDay = previousSelected,
            )
            if (previousSelected != null) {
                reloadSelectedDay(previousSelected.date)
            }
        }
    }

    fun setProjectExtraMonth(enabled: Boolean) {
        viewModelScope.launch {
            settings.setProjectExtraMonth(enabled)
            load()
        }
    }

    fun setProjectedMonthLength(year: Int, month: Int, length: Int?) {
        viewModelScope.launch {
            if (length != null) {
                settings.cascadeProjectedMonthLengths(year, month, length)
            } else {
                settings.setProjectedMonthLength(year, month, null)
            }
            load()
        }
    }

    private fun toWeeks(start: LocalDate, days: List<DayCell>): List<List<DayCell?>> {
        val startDow = start.dayOfWeek
        val leadingBlanks = startDow.value % 7
        val padded = mutableListOf<DayCell?>().apply {
            repeat(leadingBlanks) { add(null) }
            addAll(days)
        }
        val trailing = (7 - (padded.size % 7)).let { if (it == 7) 0 else it }
        repeat(trailing) { padded.add(null) }
        return padded.chunked(7)
    }
}
