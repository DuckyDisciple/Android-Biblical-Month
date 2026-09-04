package com.experiencingyah.bibliCal.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "month_starts",
    indices = [Index(value = ["yearNumber", "monthNumber"], unique = true)]
)
data class MonthStartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearNumber: Int,
    val monthNumber: Int, // 1..13
    val startEpochDay: Long, // LocalDate.toEpochDay()
    val confirmed: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "year_decisions")
data class YearDecisionEntity(
    @PrimaryKey val yearNumber: Int,
    val barleyAviv: Boolean?, // null = unknown
    val decidedEpochDay: Long?,
)

@Entity(
    tableName = "user_events",
    indices = [Index(value = ["epochDay"])]
)
data class UserEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val epochDay: Long, // LocalDate.toEpochDay() — Gregorian civil date of the event
    val allDay: Boolean = true,
    /** Minutes from midnight local time; null when [allDay]. */
    val startMinutesFromMidnight: Int? = null,
    val notes: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

