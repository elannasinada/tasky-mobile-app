package io.tasky.taskyapp.task.presentation.details

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import android.content.Context
import io.tasky.taskyapp.core.domain.PremiumManager
import io.tasky.taskyapp.core.service.GeminiService
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import io.tasky.taskyapp.task.domain.use_cases.task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.presentation.fakeUseCases
import io.tasky.taskyapp.util.MainCoroutineExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.mockk

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
        viewModel = TaskDetailsViewModel(
            useCases,
            mockContext,
            mockNotificationScheduler,
            mockPremiumManager,
            mockGeminiService
        )
        viewModel.userData = userData
    }

    @Test
    fun `Inserting a task, finishes screen`() = runTest {
        val task = task().copy(title = "New task title")
        viewModel.state.value.task = task

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
        }
    }

    @Test
    fun `Inserting a task without a title, shows toast`() = runTest {
        val task = task().copy(title = "")
        viewModel.state.value.task = task

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
        }
    }

    @Test
    fun `Inserting a task without a task type, shows toast`() = runTest {
        val task = task().copy(taskType = "")
        viewModel.state.value.task = task

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
        }
    }

    @Test
    fun `Updating a task, finishes screen`() = runTest {
        val task = task().copy(title = "New task title")
        viewModel.state.value.task = task

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
        }
    }

    @Test
    fun `Updating a task without a title, shows toast`() = runTest {
        val task = task().copy(title = "")
        viewModel.state.value.task = task

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
        }
    }

    @Test
    fun `Updating a task without a task type, shows toast`() = runTest {
        val task = task().copy(taskType = "")
        viewModel.state.value.task = task

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
        }
    }
}
