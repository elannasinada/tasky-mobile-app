package io.tasky.taskyapp.task.di

import dagger.Module
import dagger.Provides
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.testing.TestInstallIn
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClient
import io.tasky.taskyapp.task.data.repository.TaskRepositoryImpl
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import io.tasky.taskyapp.task.domain.use_cases.DeleteTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.GetTasksUseCase
import io.tasky.taskyapp.task.domain.use_cases.InsertTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import io.tasky.taskyapp.task.domain.use_cases.UpdateTaskUseCase

@Module
@TestInstallIn(
    components = [ViewModelComponent::class],
    replaces = [TaskModule::class]
)
object TestTaskModule {
    @Provides
    fun providesTaskRepository(
        realtimeDatabaseClient: RealtimeDatabaseClient,
    ): TaskRepository {
        return TaskRepositoryImpl(realtimeDatabaseClient)
    }

    @Provides
    fun providesTaskUseCases(repository: TaskRepository) =
        TaskUseCases(
            deleteTaskUseCase = DeleteTaskUseCase(repository),
            getTasksUseCase = GetTasksUseCase(repository),
            insertTaskUseCase = InsertTaskUseCase(repository),
            updateTaskUseCase = UpdateTaskUseCase(repository)
        )
}
