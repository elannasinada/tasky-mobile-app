package io.tasky.taskyapp.task.presentation.details

import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus

sealed class TaskDetailsEvent {
    data class RequestInsert(
        val title: String,
        val description: String,
        val date: String,
        val time: String,
        val status: String = TaskStatus.PENDING.name,
        val isRecurring: Boolean = false,
        val recurrencePattern: String? = null,
        val recurrenceInterval: Int = 1,
        val recurrenceEndDate: String? = null
    ) : TaskDetailsEvent()

    data class RequestUpdate(
        val title: String,
        val description: String,
        val date: String,
        val time: String,
        val status: String,
        val isRecurring: Boolean = false,
        val recurrencePattern: String? = null,
        val recurrenceInterval: Int = 1,
        val recurrenceEndDate: String? = null
    ) : TaskDetailsEvent()
    
    data class SetTaskData(val task: Task) : TaskDetailsEvent()
}