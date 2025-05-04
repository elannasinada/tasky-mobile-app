package io.tasky.taskyapp.calendar.data

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val location: String
)