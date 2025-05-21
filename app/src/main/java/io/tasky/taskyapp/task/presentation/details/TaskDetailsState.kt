package io.tasky.taskyapp.task.presentation.details

import io.tasky.taskyapp.task.domain.model.Task

data class TaskDetailsState(
    var task: Task? = null,
    var status: String? = null,
    var suggestedPriority: Int? = null,
    var isPriorityManuallySet: Boolean = false
)