package io.tasky.taskyapp.task.domain.use_cases

import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.repository.TaskRepository

/**
 * Deletes an existent task.
 */
class DeleteTaskUseCase(
    private val repository: TaskRepository
) {
    /**
     * Deletes an existent task.
     */
    suspend operator fun invoke(userData: UserData, task: Task) {
        repository.deleteTask(userData, task)
    }
}
