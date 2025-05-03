package io.tasky.taskyapp.task.presentation.listing

import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus

sealed class TaskEvent {
    data class SearchedForTask(val filter: String) : TaskEvent()
    data class RequestDelete(val task: Task) : TaskEvent()
    data class CompleteTask(val task: Task) : TaskEvent()
    data class UpdateTaskStatus(val task: Task, val newStatus: TaskStatus) : TaskEvent()
    object RestoreTask : TaskEvent()
}