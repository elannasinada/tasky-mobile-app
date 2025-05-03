package io.tasky.taskyapp.task.domain.repository

import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * Repository to communicate with the Realtime Database.
 */
interface TaskRepository {
    /**
     * Deletes an existent task.
     */
    suspend fun deleteTask(userData: UserData, task: Task)

    /**
     * Gets all tasks from an specific user.
     *
     * @return A coroutine flow with all task from the provided user.
     */
    fun getTasks(userData: UserData): Flow<Resource<List<Task>>>

    /**
     * Inserts a new task or updates an old one.
     */
    suspend fun insertTask(userData: UserData, task: Task)
}
