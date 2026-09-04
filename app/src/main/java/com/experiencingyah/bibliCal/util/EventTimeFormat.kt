package com.experiencingyah.bibliCal.util

fun formatEventTime(allDay: Boolean, startMinutes: Int?): String {
    if (allDay || startMinutes == null) return "All day"
    val h = startMinutes / 60
    val m = startMinutes % 60
    val amPm = if (h < 12) "AM" else "PM"
    val h12 = when (val mod = h % 12) {
        0 -> 12
        else -> mod
    }
    return String.format("%d:%02d %s", h12, m, amPm)
}
