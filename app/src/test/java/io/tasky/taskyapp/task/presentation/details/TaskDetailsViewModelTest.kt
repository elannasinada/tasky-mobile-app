package io.tasky.taskyapp.task.presentation.details

import android.content.Context
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.tasky.taskyapp.core.domain.PremiumManager
import io.tasky.taskyapp.core.service.GeminiService
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import io.tasky.taskyapp.task.domain.use_cases.task
import io.tasky.taskyapp.task.presentation.fakeUseCases
import io.tasky.taskyapp.util.MainCoroutineExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Field
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Disabled

@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
internal class TaskDetailsViewModelTest {
    private lateinit var useCases: TaskUseCases
    private lateinit var viewModel: TaskDetailsViewModel
    private val userData = userData()
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockNotificationScheduler = mockk<NotificationScheduler>(relaxed = true)
    private val mockPremiumManager = mockk<PremiumManager>(relaxed = true)
    private val mockGeminiService = mockk<GeminiService>(relaxed = true)

    @BeforeEach
    fun setUp() {
        useCases = fakeUseCases()
        
        // Set up Gemini mock to return a default priority
        coEvery { mockGeminiService.suggestTaskPriority(any()) } returns 1
        
        viewModel = TaskDetailsViewModel(
            useCases,
            mockContext,
            mockNotificationScheduler,
            mockPremiumManager,
            mockGeminiService
        )
        viewModel.userData = userData
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    // Helper method to set task directly in the state
    private fun setTaskInState(task: Task) {
        val stateField = viewModel.javaClass.getDeclaredField("_state")
        stateField.isAccessible = true
        val state = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<*>
        val setValueMethod = state.javaClass.getMethod("setValue", Any::class.java)
        setValueMethod.invoke(state, TaskDetailsState(task = task))
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Inserting a task, finishes screen`() = runTest {
        val task = task().copy(title = "New task title")
        setTaskInState(task)

        viewModel.eventFlow.test {
            viewModel.onEvent(
                TaskDetailsEvent.RequestInsert(
                    title = task.title,
                    description = task.description!!,
                    date = task.deadlineDate!!,
                    time = task.deadlineTime!!,
                    status = TaskStatus.PENDING.name
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(TaskDetailsViewModel.UiEvent.Finish)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Inserting a task without a title, shows toast`() = runTest {
        val task = task().copy(title = "")
        setTaskInState(task)

        viewModel.eventFlow.test {
            viewModel.onEvent(
                TaskDetailsEvent.RequestInsert(
                    title = task.title,
                    description = task.description!!,
                    date = task.deadlineDate!!,
                    time = task.deadlineTime!!,
                    status = TaskStatus.PENDING.name
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                TaskDetailsViewModel.UiEvent.ShowToast(
                    "You can't save without a title"
                )
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Inserting a task without a task type, shows toast`() = runTest {
        val task = task().copy(taskType = "")
        setTaskInState(task)

        viewModel.eventFlow.test {
            viewModel.onEvent(
                TaskDetailsEvent.RequestInsert(
                    title = task.title,
                    description = task.description!!,
                    date = task.deadlineDate!!,
                    time = task.deadlineTime!!,
                    status = TaskStatus.PENDING.name
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                TaskDetailsViewModel.UiEvent.ShowToast(
                    "You can't save without a task type"
                )
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Updating a task, finishes screen`() = runTest {
        val task = task().copy(title = "New task title")
        setTaskInState(task)

        viewModel.eventFlow.test {
            viewModel.onEvent(
                TaskDetailsEvent.RequestUpdate(
                    title = task.title,
                    description = task.description!!,
                    date = task.deadlineDate!!,
                    time = task.deadlineTime!!,
                    status = TaskStatus.PENDING.name
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(TaskDetailsViewModel.UiEvent.Finish)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Updating a task without a title, shows toast`() = runTest {
        val task = task().copy(title = "")
        setTaskInState(task)

        viewModel.eventFlow.test {
            viewModel.onEvent(
                TaskDetailsEvent.RequestUpdate(
                    title = task.title,
                    description = task.description!!,
                    date = task.deadlineDate!!,
                    time = task.deadlineTime!!,
                    status = TaskStatus.PENDING.name
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                TaskDetailsViewModel.UiEvent.ShowToast(
                    "You can't save without a title"
                )
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Disabled("Test refactoring needed")
    fun `Updating a task without a task type, shows toast`() = runTest {
        val task = task().copy(taskType = "")
        setTaskInState(task)

        viewModel.eventFlow.test {
            viewModel.onEvent(
                TaskDetailsEvent.RequestUpdate(
                    title = task.title,
                    description = task.description!!,
                    date = task.deadlineDate!!,
                    time = task.deadlineTime!!,
                    status = TaskStatus.PENDING.name
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                TaskDetailsViewModel.UiEvent.ShowToast(
                    "You can't save without a task type"
                )
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}