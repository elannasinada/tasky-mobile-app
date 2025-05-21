package io.tasky.taskyapp.task.domain.use_cases

import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.repository.TaskRepository

/**
 * Use case for creating a new task
 */
class InsertTaskUseCase(
    private val repository: TaskRepository,
    private val geminiPriorityUseCase: GeminiPriorityUseCase? = null
) {
    /**
     * Creates a new task with validation and optional AI priority suggestions
     * 
     * @throws Exception if title is blank or taskType is blank
     */
    suspend operator fun invoke(
        userData: UserData,
        title: String,
        description: String = "",
        taskType: String,
        deadlineDate: String,
        deadlineTime: String,
        status: TaskStatus,
        isRecurring: Boolean = false,
        recurrencePattern: String? = null,
        recurrenceInterval: Int = 1,
        recurrenceEndDate: String? = null,
        priority: Int = 0,
        isPriorityManuallySet: Boolean = false
    ) {
        if (title.isBlank())
            throw Exception("You can't save without a title")

        if (taskType.isBlank())
            throw Exception("You can't save without a task type")

        // Create the task
        val task = Task(
            title = title,
            description = description,
            taskType = taskType,
            deadlineDate = deadlineDate.replace("/", "-"),
            deadlineTime = deadlineTime,
            status = status.toString(),
            isRecurring = isRecurring,
            recurrencePattern = recurrencePattern,
            recurrenceInterval = recurrenceInterval,
            recurrenceEndDate = recurrenceEndDate,
            priority = priority,
            isPriorityManuallySet = isPriorityManuallySet
        )
        
        // Only predict priority if it wasn't manually set
        val taskWithPriority = if (!task.isPriorityManuallySet) {
            geminiPriorityUseCase?.let { predictor ->
                val predictedPriority = predictor.invoke(task)
                task.copy(priority = predictedPriority)
            } ?: task
        } else {
            task
        }
        
        // Insert the task with priority
        repository.insertTask(
            userData = userData,
            task = taskWithPriority
        )
    }
}