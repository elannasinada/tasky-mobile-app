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
    var recurrenceEndDate: String? = null,
    var priority: Int = 0 // 0 = Low, 1 = Medium, 2 = High
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
                recurrenceEndDate = hashMap["recurrenceEndDate"]?.toString(),
                priority = when (val priority = hashMap["priority"]) {
                    is Number -> priority.toInt()
                    is String -> priority.toIntOrNull() ?: 0
                    else -> 0
                }
            )
        }
    }

    fun toJson() = Gson().toJson(this).toString()

    /**
     * Creates a copy of this task with the specified priority level.
     * @param priority The priority level (0=low, 1=medium, 2=high)
     * @return A new Task with the updated priority
     */
    fun withPriority(priority: Int): Task {
        return this.copy(priority = priority)
    }
}