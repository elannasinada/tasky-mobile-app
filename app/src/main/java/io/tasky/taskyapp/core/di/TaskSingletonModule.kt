package io.tasky.taskyapp.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.tasky.taskyapp.core.service.GeminiService
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClient
import io.tasky.taskyapp.task.data.repository.TaskRepositoryImpl
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import io.tasky.taskyapp.task.domain.use_cases.DeleteTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.GeminiPriorityUseCase
import io.tasky.taskyapp.task.domain.use_cases.GetTasksUseCase
import io.tasky.taskyapp.task.domain.use_cases.InsertTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.PredictTaskPriorityUseCase
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import io.tasky.taskyapp.task.domain.use_cases.UpdateTaskUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TaskSingletonModule {
    
    @Provides
    @Singleton
    fun providesTaskRepository(
        realtimeDatabaseClient: RealtimeDatabaseClient,
    ): TaskRepository {
        return TaskRepositoryImpl(realtimeDatabaseClient)
    }

    @Provides
    @Singleton
    fun providesTaskUseCases(
        repository: TaskRepository,
        @ApplicationContext context: Context,
        geminiService: GeminiService
    ) = TaskUseCases(
        deleteTaskUseCase = DeleteTaskUseCase(repository),
        getTasksUseCase = GetTasksUseCase(repository),
        insertTaskUseCase = InsertTaskUseCase(
            repository = repository, 
            predictTaskPriorityUseCase = PredictTaskPriorityUseCase(context)
        ),
        updateTaskUseCase = UpdateTaskUseCase(
            repository = repository,
            predictTaskPriorityUseCase = PredictTaskPriorityUseCase(context)
        ),
        predictTaskPriorityUseCase = PredictTaskPriorityUseCase(context),
        geminiPriorityUseCase = GeminiPriorityUseCase(geminiService)
    )
    
    @Provides
    @Singleton
    fun providesGeminiService(@ApplicationContext context: Context): GeminiService {
        return GeminiService(context)
    }
}