package io.tasky.taskyapp.task.data.repository

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNullOrEmpty
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.task.data.remote.FakeRealtimeDatabaseClient
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClient
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskType
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import io.tasky.taskyapp.task.domain.use_cases.task
import io.tasky.taskyapp.task.domain.use_cases.tasks
import io.tasky.taskyapp.util.MainCoroutineExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
internal class TaskRepositoryImplTest {
    private lateinit var realtimeDatabaseClient: RealtimeDatabaseClient
    private lateinit var repository: TaskRepository

    @BeforeEach
    fun setUp() {
        realtimeDatabaseClient = FakeRealtimeDatabaseClient()
        repository = TaskRepositoryImpl(realtimeDatabaseClient)
    }

    @Test
    fun `Getting tasks, returns in the correct order`() = runTest {
        repository.deleteTask(userData(), task())

        tasks().shuffled().forEach {
            repository.insertTask(userData(), it)
            advanceUntilIdle()
        }

        repository.getTasks(userData()).test {
            val emission = awaitItem()
            val tasks = emission.data as List<Task>

            assertThat(tasks).isEqualTo(tasks())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Deleting a single task, list will be empty`() = runTest {
        val task = task()

        repository.deleteTask(userData(), task)

        repository.getTasks(userData()).test {
            val emission = awaitItem()
            val tasks = emission.data as List<Task>

            assertThat(tasks).isNotNull()
            assertThat(tasks).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Inserting a single task, task has new values`() = runTest {
        repository.insertTask(
            userData = userData(),
            task = Task(
                title = "New Title",
                description = "",
                taskType = TaskType.HOBBIES.name,
                deadlineDate = "2024-01-01",
                deadlineTime = "23:23"
            )
        )

        repository.getTasks(userData()).test {
            val emission = awaitItem()
            val newTask = emission.data?.last()

            assertThat(newTask?.title).isEqualTo("New Title")
            assertThat(newTask?.description).isEqualTo("")
            assertThat(newTask?.taskType).isEqualTo(TaskType.HOBBIES.name,)
            assertThat(newTask?.deadlineDate).isEqualTo("2024-01-01")
            assertThat(newTask?.deadlineTime).isEqualTo("23:23")
            assertThat(emission.message).isNullOrEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Updating a single task, task has new values`() = runTest {
        val task = task()

        repository.insertTask(
            userData = userData(),
            task = task.copy(
                title = "New Title",
                description = "",
                taskType = task.taskType,
                deadlineDate = "2024-01-01",
                deadlineTime = "23:23"
            ),
        )
        advanceUntilIdle()

        repository.getTasks(userData()).test {
            val emission = awaitItem()

            assertThat(emission.data?.first()).isEqualTo(
                Task(
                    uuid = task.uuid,
                    title = "New Title",
                    description = "",
                    taskType = task.taskType,
                    deadlineDate = "2024-01-01",
                    deadlineTime = "23:23"
                )
            )
            assertThat(emission.message).isNullOrEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
