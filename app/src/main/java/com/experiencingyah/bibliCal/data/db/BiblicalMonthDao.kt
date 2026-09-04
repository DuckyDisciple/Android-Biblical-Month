package com.experiencingyah.bibliCal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BiblicalMonthDao {
    @Query("SELECT * FROM month_starts ORDER BY startEpochDay ASC")
    suspend fun getAllMonthStarts(): List<MonthStartEntity>

    @Query("SELECT * FROM month_starts WHERE startEpochDay <= :epochDay ORDER BY startEpochDay DESC LIMIT 1")
    suspend fun getLatestStartOnOrBefore(epochDay: Long): MonthStartEntity?

    @Query("SELECT * FROM month_starts WHERE yearNumber = :year AND monthNumber = :month LIMIT 1")
    suspend fun getByYearMonth(year: Int, month: Int): MonthStartEntity?

    @Query("SELECT * FROM month_starts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MonthStartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthStart(entity: MonthStartEntity): Long

    /**
     * Removes confirmed month starts that are biblically after [year]/[month] and begin on or after [minEpochDay].
     * Used when the user re-anchors a month so an orphaned next-year month 1 (from "start next month") does not win in [resolveFor].
     */
    @Query(
        """
        DELETE FROM month_starts
        WHERE startEpochDay >= :minEpochDay
        AND (
            yearNumber > :year
            OR (yearNumber = :year AND monthNumber > :month)
        )
        """,
    )
    suspend fun deleteLaterMonthStarts(year: Int, month: Int, minEpochDay: Long)

    @Query("DELETE FROM month_starts")
    suspend fun deleteAllMonthStarts()

    @Query("SELECT * FROM year_decisions WHERE yearNumber = :year LIMIT 1")
    suspend fun getYearDecision(year: Int): YearDecisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertYearDecision(entity: YearDecisionEntity)

    // --- User events ---

    @Query("SELECT * FROM user_events WHERE id = :id LIMIT 1")
    suspend fun getUserEventById(id: Long): UserEventEntity?

    @Query(
        """
        SELECT * FROM user_events
        WHERE epochDay >= :startEpochDay AND epochDay <= :endEpochDay
        ORDER BY epochDay ASC, allDay DESC, startMinutesFromMidnight ASC, title ASC
        """,
    )
    suspend fun getUserEventsInRange(startEpochDay: Long, endEpochDay: Long): List<UserEventEntity>

    @Query(
        """
        SELECT * FROM user_events
        WHERE epochDay = :epochDay
        ORDER BY allDay DESC, startMinutesFromMidnight ASC, title ASC
        """,
    )
    suspend fun getUserEventsForDay(epochDay: Long): List<UserEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserEvent(entity: UserEventEntity): Long

    @Update
    suspend fun updateUserEvent(entity: UserEventEntity)

    @Query("DELETE FROM user_events WHERE id = :id")
    suspend fun deleteUserEvent(id: Long)
}

