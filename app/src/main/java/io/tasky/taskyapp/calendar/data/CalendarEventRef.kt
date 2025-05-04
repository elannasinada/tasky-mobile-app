package io.tasky.taskyapp.calendar.data

import com.google.gson.Gson
import java.util.UUID

/**
 * Reference data for Google Calendar events stored in Firebase.
 * This model serves as a lightweight reference to actual calendar events,
 * storing only essential information for synchronization.
 */
data class CalendarEventRef(
    val uuid: String = UUID.randomUUID().toString(),
    val googleEventId: String,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true,
    val needsUpdate: Boolean = false,
    val isDeleted: Boolean = false,
    val taskId: String? = null
) {
    companion object {
        fun fromJson(json: String): CalendarEventRef = Gson().fromJson(json, CalendarEventRef::class.java)
        
        fun fromSnapshot(hashMap: HashMap<String, Any?>): CalendarEventRef {
            return CalendarEventRef(
                uuid = hashMap["uuid"]?.toString() ?: "",
                googleEventId = hashMap["googleEventId"]?.toString() ?: "",
                lastSyncTimestamp = when (val timestamp = hashMap["lastSyncTimestamp"]) {
                    is Number -> timestamp.toLong()
                    is String -> timestamp.toLongOrNull() ?: System.currentTimeMillis()
                    else -> System.currentTimeMillis()
                },
                isSynced = hashMap["isSynced"]?.toString()?.toBoolean() ?: true,
                needsUpdate = hashMap["needsUpdate"]?.toString()?.toBoolean() ?: false,
                isDeleted = hashMap["isDeleted"]?.toString()?.toBoolean() ?: false,
                taskId = hashMap["taskId"]?.toString()
            )
        }
    }

    fun toJson() = Gson().toJson(this).toString()
}