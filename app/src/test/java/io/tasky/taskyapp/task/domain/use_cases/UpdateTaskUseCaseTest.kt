package io.tasky.taskyapp.task.domain.use_cases

import io.mockk.coVerify
import io.mockk.mockk
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import io.tasky.taskyapp.util.MainCoroutineExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
class UpdateTaskUseCaseTest {
    private lateinit var repository: TaskRepository
    private lateinit var useCase: UpdateTaskUseCase
    private val userData = userData()
    
    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = UpdateTaskUseCase(repository)
    }

    @Test
    fun `Updating a single task, task has new values`() = runTest {
        // Given
        val task = task()
        val newTitle = "New Title"
        val newDesc = "New Description"
        val newDate = "2024-01-01"
        val newTime = "23:23"
        
        // When
        useCase(
            userData = userData,
            task = task,
            title = newTitle,
            description = newDesc,
            taskType = task.taskType,
            deadlineDate = newDate,
            deadlineTime = newTime,
            status = task.status
        )
        
        // Then
        coVerify { repository.insertTask(userData, any()) }
    }

    @Test
    fun `Updating a single task without a title, throws exception`() = runTest {
        // Given
        val task = task()
        
        // When & Then
        assertThrows<Exception> {
            useCase(
                userData = userData,
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
    fun `Updating a single task without a task type, throws exception`() = runTest {
        // Given
        val task = task()
        
        // When & Then
        assertThrows<Exception> {
            useCase(
                userData = userData,
                task = task,
                title = "New Title",
                description = "New Description",
                taskType = "",
                deadlineDate = "2024-01-01",
                deadlineTime = "23:23",
                status = task.status
            )
        }
    }
}