package io.tasky.taskyapp.task.domain.use_cases

import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.repository.TaskRepository

/**
 * Created a new task.
 */
class InsertTaskUseCase(
    private val repository: TaskRepository
) {
    /**
     * Created a new task.
     *
     * @param userData Data from the user to assign the task.
     * @param title Task title.
     * @param description Task description.
     * @param taskType Task type.
     * @param deadlineDate Deadline date.
     * @param deadlineTime Deadline time.
     * @param status Task status.
     * @param isRecurring Whether the task is recurring.
     * @param recurrencePattern Recurrence pattern.
     * @param recurrenceInterval Recurrence interval.
     * @param recurrenceEndDate Recurrence end date.
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
        recurrenceEndDate: String? = null
    ) {
        if (title.isBlank())
            throw Exception("You can't save without a title")

        if (taskType.isBlank())
            throw Exception("You can't save without a task type")

        repository.insertTask(
            userData = userData,
            task = Task(
                title = title,
                description = description,
                taskType = taskType,
                deadlineDate = deadlineDate.replace("/", "-"),
                deadlineTime = deadlineTime,
                status = status.toString(),
                isRecurring = isRecurring,
                recurrencePattern = recurrencePattern,
                recurrenceInterval = recurrenceInterval,
                recurrenceEndDate = recurrenceEndDate
            )
        )
    }
}