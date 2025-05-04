package io.tasky.taskyapp.task.domain.model

import com.google.gson.Gson
import java.util.UUID

/**
 * Data from a single task.
 */
data class Task(
    val uuid: String = UUID.randomUUID().toString(),
    var title: String,
    var description: String? = null,
    val taskType: String,
    var deadlineDate: String? = null,
    var deadlineTime: String? = null,
    var status: String = TaskStatus.PENDING.name,
    var isRecurring: Boolean = false,
    var recurrencePattern: String? = null, 
    var recurrenceInterval: Int = 1, 
    var recurrenceEndDate: String? = null
) {
    companion object {
        fun fromJson(json: String): Task = Gson().fromJson(json, Task::class.java)

        fun fromSnapshot(hashMap: HashMap<String, Any?>): Task {
            return Task(
                uuid = hashMap["uuid"]?.toString() ?: "",
                title = hashMap["title"]?.toString() ?: "",
                description = hashMap["description"]?.toString(),
                taskType = hashMap["taskType"]?.toString() ?: "",
                deadlineDate = hashMap["deadlineDate"]?.toString(),
                deadlineTime = hashMap["deadlineTime"]?.toString(),
                status = hashMap["status"]?.toString() ?: TaskStatus.PENDING.name,
                isRecurring = hashMap["recurring"]?.toString()?.toBoolean() ?: false,
                recurrencePattern = hashMap["recurrencePattern"]?.toString(),
                recurrenceInterval = when (val interval = hashMap["recurrenceInterval"]) {
                    is Number -> interval.toInt()
                    is String -> interval.toIntOrNull() ?: 1
                    else -> 1
                },
                recurrenceEndDate = hashMap["recurrenceEndDate"]?.toString()
            )
        }
    }

    fun toJson() = Gson().toJson(this).toString()
}
