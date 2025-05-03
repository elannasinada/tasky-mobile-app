package io.tasky.taskyapp.task.presentation

import io.tasky.taskyapp.task.data.repository.FakeTaskRepository
import io.tasky.taskyapp.task.domain.use_cases.DeleteTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.GetTasksUseCase
import io.tasky.taskyapp.task.domain.use_cases.InsertTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import io.tasky.taskyapp.task.domain.use_cases.UpdateTaskUseCase

fun fakeUseCases(): TaskUseCases {
    val repository = FakeTaskRepository()

    return TaskUseCases(
        deleteTaskUseCase = DeleteTaskUseCase(repository),
        getTasksUseCase = GetTasksUseCase(repository),
        insertTaskUseCase = InsertTaskUseCase(repository),
        updateTaskUseCase = UpdateTaskUseCase(repository),
    )
}
