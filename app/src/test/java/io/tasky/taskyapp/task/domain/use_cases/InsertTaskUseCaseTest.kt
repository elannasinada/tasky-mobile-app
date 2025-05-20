package io.tasky.taskyapp.task.domain.use_cases

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.repository.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InsertTaskUseCaseTest {
    private lateinit var repository: TaskRepository
    private lateinit var useCase: InsertTaskUseCase
    private val userData = userData()
    
    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = InsertTaskUseCase(repository)
    }
    
    @Test
    fun `Inserting a task saves to repository`() = runTest {
        // Given
        val title = "Test Task"
        val desc = "Test Description"
        val taskType = "PERSONAL"
        val date = "2024-05-01"
        val time = "10:00"
        
        // When
        useCase(userData, title, desc, taskType, date, time, TaskStatus.PENDING)
        
        // Then
        coVerify { repository.insertTask(userData, any()) }
    }
    
    @Test
    fun `Inserting task with empty title throws exception`() = runTest {
        assertThrows<Exception> {
            useCase(
                userData = userData,
                title = "",
                description = "Description",
                taskType = "PERSONAL",
                deadlineDate = "2024-05-01",
                deadlineTime = "10:00",
                status = TaskStatus.PENDING
            )
        }
    }
    
    @Test
    fun `Inserting task with empty task type throws exception`() = runTest {
        assertThrows<Exception> {
            useCase(
                userData = userData,
                title = "Title",
                description = "Description",
                taskType = "",
                deadlineDate = "2024-05-01",
                deadlineTime = "10:00",
                status = TaskStatus.PENDING
            )
        }
    }
    
    @Test
    fun `Inserting a task with each possible status value`() = runTest {
        // Test all task statuses
        for (status in TaskStatus.values()) {
            // Given
            val title = "Test ${status.name}"
            val taskType = "PERSONAL"
            
            // When
            useCase(userData, title, "Description", taskType, "2024-05-01", "10:00", status)
            
            // Then
            coVerify { repository.insertTask(userData, any()) }
        }
    }
}