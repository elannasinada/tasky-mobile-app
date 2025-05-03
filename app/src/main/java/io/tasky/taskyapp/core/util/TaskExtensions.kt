package io.tasky.taskyapp.core.util

import io.tasky.taskyapp.task.domain.model.Task

fun List<Task>.filterBy(filter: String) =
    filter {
        filter.lowercase() in it.title.lowercase() ||
            filter.lowercase() in (it.description?.lowercase() ?: "")
    }
