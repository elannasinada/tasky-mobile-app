package io.tasky.taskyapp.task.presentation.details

import io.tasky.taskyapp.task.domain.model.Task

data class TaskDetailsState(
    var task: Task? = null,
    var status: String? = null, // Added status field to track task status
    var suggestedPriority: Int? = null // AI suggested priority
)