package io.tasky.taskyapp.task.domain.use_cases

import app.cash.turbine.test
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.task.data.repository.FakeTaskRepository
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import io.tasky.taskyapp.util.MainCoroutineExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
internal class UpdateTaskUseCaseTest {
    private lateinit var repository: TaskRepository
    private lateinit var useCase: UpdateTaskUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeTaskRepository()
        useCase = UpdateTaskUseCase(repository)
    }

    @Test
    fun `Updating a single task, task has new values`() = runTest {
        val task = task()
        useCase.invoke(
            userData = userData(),
            task = task,
            title = "New Title",
            description = "",
            taskType = task.taskType,
            deadlineDate = "2024-01-01",
            deadlineTime = "23:23",
            status = task.status
        )
        advanceUntilIdle()

        repository.getTasks(userData()).test {
            val emission1 = awaitItem()

            assertThat(emission1.data).isNull()
            assertThat(emission1.message).isNullOrEmpty()

            val emission2 = awaitItem()

            assertThat(emission2.data?.first()).isEqualTo(
                Task(
                    uuid = task.uuid,
                    title = "New Title",
                    description = "",
                    taskType = task.taskType,
                    deadlineDate = "2024-01-01",
                    deadlineTime = "23:23",
                    status = TaskStatus.IN_PROGRESS.name
                )
            )
            assertThat(emission2.message).isNullOrEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Updating a single task without a title, throws exception`() = runTest {
        assertFailure {
            val task = task()
            useCase.invoke(
                userData = userData(),
                task = task,
                title = "",
                description = "New Description",
                taskType = task.taskType,
                deadlineDate = "2024-01-01",
                deadlineTime = "23:23",
                status = task.status
            )
        }
    }
    
    @Test
    fun `Updating a recurring task, task has updated recurrence details`() = runTest {
        // 1. Setup initial recurring task
        val initialRecurrencePattern = RecurrencePattern.DAILY.name
        val initialRecurrenceInterval = 1
        val initialRecurrenceEndDate = "2024-06-30"
        val initialTask = task().copy(
            title = "Initial Recurring Task",
            isRecurring = true,
            recurrencePattern = initialRecurrencePattern,
            recurrenceInterval = initialRecurrenceInterval,
            recurrenceEndDate = initialRecurrenceEndDate,
            status = TaskStatus.PENDING.name // Start with pending
        )
        repository.insertTask(userData(), initialTask)
        advanceUntilIdle() // Ensure insertion completes

        // 2. Define updated recurrence details and other fields
        val updatedRecurrencePattern = RecurrencePattern.WEEKLY.name
        val updatedRecurrenceInterval = 2
        val updatedRecurrenceEndDate = "2025-12-31"
        val updatedStatus = TaskStatus.IN_PROGRESS // Update status as well
        val updatedDeadlineDate = "2024-02-15"
        val updatedDeadlineTime = "11:00"

        // 3. Invoke the update use case
        useCase.invoke(
            userData = userData(),
            task = initialTask,
            title = "Updated Recurring Task", // Also update title for clarity
            description = initialTask.description ?: "",
            taskType = initialTask.taskType,
            deadlineDate = updatedDeadlineDate,
            deadlineTime = updatedDeadlineTime,
            status = updatedStatus.name, // Pass updated status name
            isRecurring = true, // Keep it recurring
            recurrencePattern = updatedRecurrencePattern,
            recurrenceInterval = updatedRecurrenceInterval,
            recurrenceEndDate = updatedRecurrenceEndDate
        )
        advanceUntilIdle() // Ensure update completes

        // 4. Verify the task is updated in the repository
        repository.getTasks(userData()).test {
            // Skip initial null emission
            awaitItem()

            // Get the tasks list after update
            val emission = awaitItem()
            val updatedTask = emission.data?.find { it.uuid == initialTask.uuid } // Find by UUID

            // Verify basic details are updated
            assertThat(updatedTask?.title).isEqualTo("Updated Recurring Task")
            assertThat(updatedTask?.status).isEqualTo(updatedStatus.name)
            assertThat(updatedTask?.deadlineDate).isEqualTo(updatedDeadlineDate)
            assertThat(updatedTask?.deadlineTime).isEqualTo(updatedDeadlineTime)

            // Verify recurrence details are updated
            assertThat(updatedTask?.isRecurring).isEqualTo(true)
            assertThat(updatedTask?.recurrencePattern).isEqualTo(updatedRecurrencePattern)
            assertThat(updatedTask?.recurrenceInterval).isEqualTo(updatedRecurrenceInterval)
            assertThat(updatedTask?.recurrenceEndDate).isEqualTo(updatedRecurrenceEndDate)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
