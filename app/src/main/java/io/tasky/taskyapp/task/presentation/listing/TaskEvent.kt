package io.tasky.taskyapp.task.presentation.listing

import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task

sealed class TaskEvent {
    data class GetTasks(val userData: UserData?) : TaskEvent()
    data class DeleteTask(val task: Task) : TaskEvent()
    data class CompleteTask(val task: Task) : TaskEvent()
    data class RestoreTask(val task: Task) : TaskEvent()
    object EnsureNotifications : TaskEvent()
}