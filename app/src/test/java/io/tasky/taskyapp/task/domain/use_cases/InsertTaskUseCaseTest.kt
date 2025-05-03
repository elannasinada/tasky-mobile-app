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
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.TaskType
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InsertTaskUseCaseTest {
    private lateinit var repository: TaskRepository
    private lateinit var useCase: InsertTaskUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeTaskRepository()
        useCase = InsertTaskUseCase(repository)
    }

    @Test
    fun `Inserting a single task, task has new values`() = runTest {
        useCase.invoke(
            userData = userData(),
            title = "New Title",
            description = "",
            taskType = TaskType.HOBBIES.name,
            deadlineDate = "2024-01-01",
            deadlineTime = "23:23",
            status = TaskStatus.IN_PROGRESS // Pass the enum directly, not the name
        )

        repository.getTasks(userData()).test {
            val emission1 = awaitItem()

            assertThat(emission1.data).isNull()
            assertThat(emission1.message).isNullOrEmpty()

            val emission2 = awaitItem()
            val newTask = emission2.data?.last()

            assertThat(newTask?.title).isEqualTo("New Title")
            assertThat(newTask?.description).isEqualTo("")
            assertThat(newTask?.taskType).isEqualTo(TaskType.HOBBIES.name)
            assertThat(newTask?.deadlineDate).isEqualTo("2024-01-01")
            assertThat(newTask?.deadlineTime).isEqualTo("23:23")
            assertThat(newTask?.status).isEqualTo(TaskStatus.IN_PROGRESS.name)
            assertThat(emission2.message).isNullOrEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Inserting a task with each possible status value`() = runTest {
        // Test PENDING status
        useCase.invoke(
            userData = userData(),
            title = "Pending Task",
            description = "This is a pending task",
            taskType = TaskType.HOBBIES.name,
            deadlineDate = "2024-01-01",
            deadlineTime = "12:00",
            status = TaskStatus.PENDING
        )

        // Test IN_PROGRESS status
        useCase.invoke(
            userData = userData(),
            title = "In Progress Task",
            description = "This is an in-progress task",
            taskType = TaskType.BUSINESS.name,
            deadlineDate = "2024-01-02",
            deadlineTime = "14:00",
            status = TaskStatus.IN_PROGRESS
        )

        // Test COMPLETED status
        useCase.invoke(
            userData = userData(),
            title = "Completed Task",
            description = "This is a completed task",
            taskType = TaskType.HOME.name,
            deadlineDate = "2024-01-03",
            deadlineTime = "16:00",
            status = TaskStatus.COMPLETED
        )

        // Test CANCELLED status
        useCase.invoke(
            userData = userData(),
            title = "Cancelled Task",
            description = "This is a cancelled task",
            taskType = TaskType.SHOPPING.name,
            deadlineDate = "2024-01-04",
            deadlineTime = "18:00",
            status = TaskStatus.CANCELLED
        )

        repository.getTasks(userData()).test {
            // Skip the initial null emission
            awaitItem()

            // Get the tasks list
            val tasksList = awaitItem().data

            // Verify we have all four tasks
            assertThat(tasksList?.size).isEqualTo(4)

            // Find and verify each task by status
            val pendingTask = tasksList?.find { it.status == TaskStatus.PENDING.name }
            assertThat(pendingTask?.title).isEqualTo("Pending Task")

            val inProgressTask = tasksList?.find { it.status == TaskStatus.IN_PROGRESS.name }
            assertThat(inProgressTask?.title).isEqualTo("In Progress Task")

            val completedTask = tasksList?.find { it.status == TaskStatus.COMPLETED.name }
            assertThat(completedTask?.title).isEqualTo("Completed Task")

            val cancelledTask = tasksList?.find { it.status == TaskStatus.CANCELLED.name }
            assertThat(cancelledTask?.title).isEqualTo("Cancelled Task")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Inserting a single task without a title, throws exception`() = runTest {
        assertFailure {
            useCase.invoke(
                userData = userData(),
                title = "",
                description = "New Description",
                taskType = TaskType.HOBBIES.name,
                deadlineDate = "2024-01-01",
                deadlineTime = "23:23",
                status = TaskStatus.IN_PROGRESS
            )
        }
    }

    @Test
    fun `Inserting a single task without a task type, throws exception`() = runTest {
        assertFailure {
            useCase.invoke(
                userData = userData(),
                title = "Title",
                description = "New Description",
                taskType = "",
                deadlineDate = "2024-01-01",
                deadlineTime = "23:23",
                status = TaskStatus.IN_PROGRESS
            )
        }
    }
    
    @Test
    fun `Inserting a recurring task, task has recurrence details`() = runTest {
        val recurrencePattern = RecurrencePattern.WEEKLY.name
        val recurrenceInterval = 2
        val recurrenceEndDate = "2024-12-31"

        useCase.invoke(
            userData = userData(),
            title = "Recurring Task Title",
            description = "Weekly recurring task",
            taskType = TaskType.HOME.name,
            deadlineDate = "2024-02-01",
            deadlineTime = "10:00",
            status = TaskStatus.PENDING,
            isRecurring = true,
            recurrencePattern = recurrencePattern,
            recurrenceInterval = recurrenceInterval,
            recurrenceEndDate = recurrenceEndDate
        )

        repository.getTasks(userData()).test {
            // Skip initial null emission
            awaitItem()

            // Get the tasks list
            val emission = awaitItem()
            val insertedTask = emission.data?.last()

            // Verify basic details
            assertThat(insertedTask?.title).isEqualTo("Recurring Task Title")
            assertThat(insertedTask?.status).isEqualTo(TaskStatus.PENDING.name)

            // Verify recurrence details
            assertThat(insertedTask?.isRecurring).isEqualTo(true)
            assertThat(insertedTask?.recurrencePattern).isEqualTo(recurrencePattern)
            assertThat(insertedTask?.recurrenceInterval).isEqualTo(recurrenceInterval)
            assertThat(insertedTask?.recurrenceEndDate).isEqualTo(recurrenceEndDate)

            cancelAndIgnoreRemainingEvents()
        }
    }
}