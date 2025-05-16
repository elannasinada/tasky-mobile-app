package io.tasky.taskyapp.task.domain.use_cases

import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.repository.TaskRepository

/**
 * Updates an existent task.
 */
class UpdateTaskUseCase(
    private val repository: TaskRepository,
    private val predictTaskPriorityUseCase: PredictTaskPriorityUseCase? = null
) {
    /**
     * Updates an existent task.
     *
     * @param userData Data from the user to assign the task.
     * @param task The old task to be updated.
     * @param title New task title.
     * @param description New task description.
     * @param taskType New task type.
     * @param deadlineDate New deadline date.
     * @param deadlineTime New deadline time.
     * @param status New task status.
     * @param isRecurring Whether the task is recurring.
     * @param recurrencePattern The recurrence pattern of the task.
     * @param recurrenceInterval The recurrence interval of the task.
     * @param recurrenceEndDate The recurrence end date of the task.
     */
    suspend operator fun invoke(
        userData: UserData,
        task: Task,
        title: String,
        description: String = "",
        taskType: String,
        deadlineDate: String,
        deadlineTime: String,
        status: String = task.status,
        isRecurring: Boolean = task.isRecurring,
        recurrencePattern: String? = task.recurrencePattern,
        recurrenceInterval: Int = task.recurrenceInterval,
        recurrenceEndDate: String? = task.recurrenceEndDate
    ) {
        if (title.isBlank())
            throw Exception("You can't save without a title")

        if (taskType.isBlank())
            throw Exception("You can't save without a task type")

        // Create the updated task
        val updatedTask = task.copy(
            title = title,
            description = description,
            taskType = taskType,
            deadlineDate = deadlineDate.replace("/", "-"),
            deadlineTime = deadlineTime,
            status = status,
            isRecurring = isRecurring,
            recurrencePattern = recurrencePattern,
            recurrenceInterval = recurrenceInterval,
            recurrenceEndDate = recurrenceEndDate
        )

        // Predict priority if predictor is available
        val taskWithPriority = predictTaskPriorityUseCase?.let { predictor ->
            val priority = predictor.invoke(updatedTask)
            updatedTask.copy(priority = priority)
        } ?: updatedTask
        
        repository.insertTask(
            userData = userData,
            task = taskWithPriority
        )
    }
}