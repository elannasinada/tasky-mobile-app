package io.tasky.taskyapp.task.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClient
import io.tasky.taskyapp.task.data.repository.TaskRepositoryImpl
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import io.tasky.taskyapp.task.domain.use_cases.DeleteTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.GetTasksUseCase
import io.tasky.taskyapp.task.domain.use_cases.InsertTaskUseCase
import io.tasky.taskyapp.task.domain.use_cases.PredictTaskPriorityUseCase
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import io.tasky.taskyapp.task.domain.use_cases.UpdateTaskUseCase

@Module
@InstallIn(ViewModelComponent::class)
object TaskModule {
}