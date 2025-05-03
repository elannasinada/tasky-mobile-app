package io.tasky.taskyapp.task.data.remote

import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface RealtimeDatabaseClient {
    /**
     * Inserts a new task or updates an old one.
     */
    suspend fun insertTask(user: UserData, task: Task)

    /**
     * Deletes an existent task.
     */
    suspend fun deleteTask(user: UserData, task: Task)

    /**
     * Gets all tasks from an specific user.
     *
     * @return A coroutine flow with all task from the provided user.
     */
    fun getTasksFromUser(user: UserData): Flow<Resource<List<Task>>>
}
