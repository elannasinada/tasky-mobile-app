package io.tasky.taskyapp.task.data.repository

import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClient
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.repository.TaskRepository

class TaskRepositoryImpl(
    private val realtimeDatabaseClient: RealtimeDatabaseClient,
) : TaskRepository {
    override suspend fun deleteTask(userData: UserData, task: Task) {
        realtimeDatabaseClient.deleteTask(userData, task)
    }

    override fun getTasks(userData: UserData) = realtimeDatabaseClient.getTasksFromUser(userData)

    override suspend fun insertTask(userData: UserData, task: Task) {
        realtimeDatabaseClient.insertTask(userData, task)
    }
}
