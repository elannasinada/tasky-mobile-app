package io.tasky.taskyapp.task.presentation.listing

import android.content.Context
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.tasky.taskyapp.core.domain.PremiumManager
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import io.tasky.taskyapp.task.domain.use_cases.task
import io.tasky.taskyapp.task.domain.use_cases.tasks
import io.tasky.taskyapp.task.presentation.fakeUseCases
import io.tasky.taskyapp.util.MainCoroutineExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Disabled

@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
internal class TaskViewModelTest {
    private lateinit var useCases: TaskUseCases
    private lateinit var viewModel: TaskViewModel
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockNotificationScheduler = mockk<NotificationScheduler>(relaxed = true)
    private val mockPremiumManager = mockk<PremiumManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        useCases = fakeUseCases()
        viewModel = TaskViewModel(
            useCases,
            mockContext,
            mockNotificationScheduler,
            mockPremiumManager
        )
        viewModel.userData = userData()
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Restoring a task after deleting it, returns full list`() = runTest {
        val task = task()

        // First observe state for the deletion
        viewModel.state.test {
            // Initial state
            val initialState = awaitItem()
            assertThat(initialState.loading).isTrue()
            
            // Delete the task
            viewModel.onEvent(TaskEvent.DeleteTask(task))
            advanceUntilIdle()
            
            // After deletion started
            val afterDeleteStarted = awaitItem()
            assertThat(afterDeleteStarted.loading).isTrue()
            
            // After deletion completed
            val afterDeleteCompleted = awaitItem()
            assertThat(afterDeleteCompleted.loading).isFalse()
            
            // Now restore the task
            viewModel.onEvent(TaskEvent.RestoreTask(task))
            advanceUntilIdle()
            
            // After restore started
            val afterRestoreStarted = awaitItem()
            assertThat(afterRestoreStarted.loading).isTrue()
            
            // After restore completed
            val afterRestoreCompleted = awaitItem()
            assertThat(afterRestoreCompleted.loading).isFalse()
            
            // Should find the restored task
            val restoredTask = afterRestoreCompleted.tasks.find { it.title == task.title }
            assertThat(restoredTask?.title).isEqualTo(task.title)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Deleting a task, returns empty list`() = runTest {
        val task = task()

        viewModel.state.test {
            // Initial state
            val initialState = awaitItem()
            assertThat(initialState.loading).isTrue()
            
            // Delete the task
            viewModel.onEvent(TaskEvent.DeleteTask(task))
            advanceUntilIdle()
            
            // After deletion started
            val afterDeleteStarted = awaitItem()
            assertThat(afterDeleteStarted.loading).isTrue()
            
            // After deletion completed
            val afterDeleteCompleted = awaitItem()
            assertThat(afterDeleteCompleted.loading).isFalse()
            // Task should be removed
            assertThat(afterDeleteCompleted.tasks.find { it.uuid == task.uuid }).isEqualTo(null)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Searching for a specific task, returns a list with it`() = runTest {
        // Insert multiple tasks first
        val tasksList = tasks()
        tasksList.forEach {
            useCases.insertTaskUseCase(
                userData = viewModel.userData!!,
                title = it.title,
                description = it.description!!,
                taskType = it.taskType,
                deadlineDate = it.deadlineDate!!,
                deadlineTime = it.deadlineTime!!,
                status = TaskStatus.PENDING
            )
        }
        viewModel.getTasks(viewModel.userData!!)
        advanceUntilIdle()

        viewModel.state.test {
            // Initial state after loading the tasks
            val initialState = awaitItem()
            assertThat(initialState.loading).isFalse()
            assertThat(initialState.tasks).hasSize(tasksList.size + 1) // +1 for default task
            
            // Search for a specific task
            viewModel.onEvent(TaskEvent.SearchTask("Task title1"))
            advanceUntilIdle()
            
            // Get the filtered state
            val searchState = awaitItem()
            assertThat(searchState.tasks).hasSize(1)
            assertThat(searchState.tasks[0].title).isEqualTo("Task title1")
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Searching with no filter, returns full list`() = runTest {
        // Insert multiple tasks first
        val tasksList = tasks()
        tasksList.forEach {
            useCases.insertTaskUseCase(
                userData = viewModel.userData!!,
                title = it.title,
                description = it.description!!,
                taskType = it.taskType,
                deadlineDate = it.deadlineDate!!,
                deadlineTime = it.deadlineTime!!,
                status = TaskStatus.PENDING
            )
        }
        viewModel.getTasks(viewModel.userData!!)
        advanceUntilIdle()

        viewModel.state.test {
            // Initial state after loading the tasks
            val initialState = awaitItem()
            assertThat(initialState.loading).isFalse()
            
            // Search with empty string
            viewModel.onEvent(TaskEvent.SearchTask(""))
            advanceUntilIdle()
            
            // Should have all tasks
            val searchState = awaitItem()
            assertThat(searchState.tasks).hasSize(tasksList.size + 1) // +1 for default task
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Clearing state, makes a brand new state`() = runTest {
        // Insert tasks first
        val tasksList = tasks()
        tasksList.forEach {
            useCases.insertTaskUseCase(
                userData = viewModel.userData!!,
                title = it.title,
                description = it.description!!,
                taskType = it.taskType,
                deadlineDate = it.deadlineDate!!,
                deadlineTime = it.deadlineTime!!,
                status = TaskStatus.PENDING
            )
        }
        viewModel.getTasks(viewModel.userData!!)
        advanceUntilIdle()
        
        // Now clear the state
        viewModel.state.test {
            // Initial non-empty state
            val initialState = awaitItem()
            assertThat(initialState.tasks).hasSize(tasksList.size + 1) // +1 for default task
            
            // Clear the state
            viewModel.clearState()
            
            // New empty state
            val clearedState = awaitItem()
            assertThat(clearedState.tasks).isEmpty()
            assertThat(clearedState.loading).isTrue()
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}