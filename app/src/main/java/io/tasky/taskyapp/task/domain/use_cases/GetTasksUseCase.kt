package io.tasky.taskyapp.task.domain.use_cases

import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

/**
 * Gets all tasks from an specific user.
 */
class GetTasksUseCase(
    private val repository: TaskRepository
) {
    /**
     * Gets all tasks from an specific user.
     *
     * @return A coroutine flow with all task from the provided user.
     */
    operator fun invoke(userData: UserData): Flow<Resource<List<Task>>> {
        return repository.getTasks(userData)
    }
}
