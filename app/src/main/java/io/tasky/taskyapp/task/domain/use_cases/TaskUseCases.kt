package io.tasky.taskyapp.task.domain.use_cases

/**
 * All use cases for the task related features.
 */
data class TaskUseCases(
    val deleteTaskUseCase: DeleteTaskUseCase,
    val getTasksUseCase: GetTasksUseCase,
    val insertTaskUseCase: InsertTaskUseCase,
    val updateTaskUseCase: UpdateTaskUseCase,
    val predictTaskPriorityUseCase: PredictTaskPriorityUseCase
)