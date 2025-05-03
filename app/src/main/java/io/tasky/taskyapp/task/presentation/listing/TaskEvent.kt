package io.tasky.taskyapp.task.presentation.listing

import io.tasky.taskyapp.task.domain.model.Task

sealed class TaskEvent {
    data class SearchedForTask(val filter: String) : TaskEvent()
    data class RequestDelete(val task: Task) : TaskEvent()
    object RestoreTask : TaskEvent()
}
