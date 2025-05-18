package io.tasky.taskyapp.core.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.tasky.taskyapp.task.domain.model.Task

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Get task details from input data
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val title = inputData.getString("taskTitle") ?: return Result.failure()
        val description = inputData.getString("taskDescription") ?: ""

        // Create a simplified Task object for notification
        val task = Task(
            uuid = taskId,
            title = title,
            description = description,
            status = "PENDING",
            taskType = "",
            deadlineDate = null,
            deadlineTime = null,
            isRecurring = false,
            recurrencePattern = null,
            recurrenceInterval = 0,
            recurrenceEndDate = null
        )

        // Send the notification
        TaskyNotificationService.sendTaskDueNotification(applicationContext, task)

        return Result.success()
    }
}