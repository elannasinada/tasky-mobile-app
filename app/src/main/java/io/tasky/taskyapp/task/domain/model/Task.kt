package io.tasky.taskyapp.task.domain.model

import com.google.gson.Gson
import java.util.UUID

/**
 * Represents a task with its properties and state
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
    var priority: Int = 0,
    var isPriorityManuallySet: Boolean = false
) {
    companion object {
        /**
         * Creates a Task object from its JSON representation
         */
        fun fromJson(json: String): Task = Gson().fromJson(json, Task::class.java)

        /**
         * Creates a Task from a database snapshot HashMap
         */
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
                },
                isPriorityManuallySet = hashMap["isPriorityManuallySet"]?.toString()?.toBoolean() ?: false
            )
        }
    }

    /**
     * Converts task to JSON string
     */
    fun toJson() = Gson().toJson(this).toString()

    /**
     * Creates a copy of this task with the specified priority level.
     * @param priority The priority level (0=low, 1=medium, 2=high)
     * @return A new Task with the updated priority
     */
    fun withPriority(priority: Int): Task {
        return this.copy(priority = priority)
    }
    
    /**
     * Creates a copy of this task with a manually set priority level.
     * @param priority The priority level (0=low, 1=medium, 2=high)
     * @return A new Task with the updated priority marked as manually set
     */
    fun withManualPriority(priority: Int): Task {
        return this.copy(priority = priority, isPriorityManuallySet = true)
    }
}