package com.experiencingyah.bibliCal.calendar

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class DeviceCalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val date: LocalDate,
    val allDay: Boolean,
    /** Minutes from midnight when not all-day; null for all-day. */
    val startMinutesFromMidnight: Int?,
    val calendarDisplayName: String? = null,
    val accountName: String? = null,
    /** Instance begin/end for opening this occurrence in the system calendar. */
    val beginMillis: Long = 0L,
    val endMillis: Long = 0L,
)

/**
 * Read-only access to device calendar instances for overlay on the biblical calendar.
 */
class DeviceCalendarReader(private val context: Context) {

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Events overlapping [startDate]..[endDate] (inclusive).
     * When [calendarIds] is empty, all calendars are included.
     *
     * Filters out cancelled instances, soft-deleted events, and declined invitations.
     * Calendar visibility is controlled via Settings calendar selection — not the
     * provider VISIBLE flag, which is unreliable with Google Calendar on Pixel.
     */
    fun getEventsInRange(
        startDate: LocalDate,
        endDate: LocalDate,
        calendarIds: Set<Long> = emptySet(),
        hiddenEventIds: Set<Long> = emptySet(),
    ): List<DeviceCalendarEvent> {
        if (!hasReadPermission()) return emptyList()

        val zone = ZoneId.systemDefault()
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        // Instances end is exclusive — use start of day after endDate
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.OWNER_ACCOUNT,
        )

        // Instances expands recurring masters; cancelled exceptions and soft-deletes
        // still appear unless filtered — Google Calendar hides those.
        // Do NOT filter Calendars.VISIBLE: on many devices (incl. Pixel + Google Calendar)
        // calendars the user actively views are still stored with visible=0.
        val filters = mutableListOf(
            "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ?)",
            "(${CalendarContract.Events.DELETED} IS NULL OR ${CalendarContract.Events.DELETED} = 0)",
            "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ?)",
        )
        val args = mutableListOf(
            CalendarContract.Events.STATUS_CANCELED.toString(),
            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString(),
        )
        if (calendarIds.isNotEmpty()) {
            val placeholders = calendarIds.joinToString(",") { "?" }
            filters += "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            args += calendarIds.map { it.toString() }
        }

        val results = mutableListOf<DeviceCalendarEvent>()
        context.contentResolver.query(
            uri,
            projection,
            filters.joinToString(" AND "),
            args.toTypedArray(),
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val calIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
            val accountIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.OWNER_ACCOUNT)

            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(idIdx)
                if (hiddenEventIds.contains(eventId)) continue

                val begin = cursor.getLong(beginIdx)
                val end = cursor.getLong(endIdx)
                val allDay = cursor.getInt(allDayIdx) == 1
                // All-day events are stored in UTC midnight; map to local calendar date carefully
                val date = if (allDay) {
                    Instant.ofEpochMilli(begin).atZone(ZoneId.of("UTC")).toLocalDate()
                } else {
                    Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                }
                if (date.isBefore(startDate) || date.isAfter(endDate)) continue

                val startMinutes = if (allDay) {
                    null
                } else {
                    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(begin), zone)
                    zdt.hour * 60 + zdt.minute
                }

                results.add(
                    DeviceCalendarEvent(
                        id = eventId,
                        calendarId = cursor.getLong(calIdIdx),
                        title = cursor.getString(titleIdx) ?: "(No title)",
                        date = date,
                        allDay = allDay,
                        startMinutesFromMidnight = startMinutes,
                        calendarDisplayName = cursor.getString(nameIdx),
                        accountName = cursor.getString(accountIdx),
                        beginMillis = begin,
                        endMillis = end,
                    )
                )
            }
        }
        return results
    }

    fun getEventsForDay(
        date: LocalDate,
        calendarIds: Set<Long> = emptySet(),
        hiddenEventIds: Set<Long> = emptySet(),
    ): List<DeviceCalendarEvent> =
        getEventsInRange(date, date, calendarIds, hiddenEventIds)

    fun epochDaysWithEvents(
        startDate: LocalDate,
        endDate: LocalDate,
        calendarIds: Set<Long> = emptySet(),
        hiddenEventIds: Set<Long> = emptySet(),
    ): Set<Long> =
        getEventsInRange(startDate, endDate, calendarIds, hiddenEventIds)
            .map { it.date.toEpochDay() }
            .toSet()

    companion object {
        /**
         * Opens this calendar instance in the device calendar app for viewing/editing.
         * Passes begin/end so recurring series open the correct occurrence.
         */
        fun openInDeviceCalendar(context: Context, event: DeviceCalendarEvent): Boolean {
            val uri = android.content.ContentUris.withAppendedId(
                CalendarContract.Events.CONTENT_URI,
                event.id,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = uri
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                // Fallback: try EDIT if no viewer handles VIEW
                try {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                            data = uri
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Deletes the event master from the device calendar provider.
     * For Google calendars this typically syncs as a full-series deletion.
     * Returns true if the provider accepted the change.
     */
    fun deleteEventSeries(eventId: Long): Boolean {
        if (!hasWritePermission()) return false
        val uri = android.content.ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            eventId,
        )
        return try {
            val deleted = context.contentResolver.delete(uri, null, null) > 0
            if (deleted) return true
            // Some providers ignore hard delete; mark soft-deleted so sync can pick it up.
            val values = android.content.ContentValues().apply {
                put(CalendarContract.Events.DELETED, 1)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
