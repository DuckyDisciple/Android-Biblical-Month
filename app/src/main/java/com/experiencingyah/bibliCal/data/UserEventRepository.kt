package com.experiencingyah.bibliCal.data

import android.content.Context
import com.experiencingyah.bibliCal.data.db.BiblicalMonthDatabase
import com.experiencingyah.bibliCal.data.db.UserEventEntity
import java.time.LocalDate

data class UserEvent(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val allDay: Boolean = true,
    val startMinutesFromMidnight: Int? = null,
    val notes: String? = null,
)

class UserEventRepository(context: Context) {
    private val dao = BiblicalMonthDatabase.get(context).dao()

    suspend fun getById(id: Long): UserEvent? =
        dao.getUserEventById(id)?.toDomain()

    suspend fun getForDay(date: LocalDate): List<UserEvent> =
        dao.getUserEventsForDay(date.toEpochDay()).map { it.toDomain() }

    suspend fun getInRange(start: LocalDate, end: LocalDate): List<UserEvent> =
        dao.getUserEventsInRange(start.toEpochDay(), end.toEpochDay()).map { it.toDomain() }

    /** Epoch days in [start, end] that have at least one user event. */
    suspend fun epochDaysWithEvents(start: LocalDate, end: LocalDate): Set<Long> =
        getInRange(start, end).map { it.date.toEpochDay() }.toSet()

    suspend fun insert(
        title: String,
        date: LocalDate,
        allDay: Boolean,
        startMinutesFromMidnight: Int?,
        notes: String?,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertUserEvent(
            UserEventEntity(
                title = title.trim(),
                epochDay = date.toEpochDay(),
                allDay = allDay,
                startMinutesFromMidnight = if (allDay) null else startMinutesFromMidnight,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        )
    }

    suspend fun update(event: UserEvent) {
        val existing = dao.getUserEventById(event.id) ?: return
        dao.updateUserEvent(
            existing.copy(
                title = event.title.trim(),
                epochDay = event.date.toEpochDay(),
                allDay = event.allDay,
                startMinutesFromMidnight = if (event.allDay) null else event.startMinutesFromMidnight,
                notes = event.notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(id: Long) {
        dao.deleteUserEvent(id)
    }

    private fun UserEventEntity.toDomain() = UserEvent(
        id = id,
        title = title,
        date = LocalDate.ofEpochDay(epochDay),
        allDay = allDay,
        startMinutesFromMidnight = startMinutesFromMidnight,
        notes = notes,
    )
}
