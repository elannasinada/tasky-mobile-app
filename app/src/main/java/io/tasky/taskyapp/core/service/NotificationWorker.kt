package io.tasky.taskyapp.core.service

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.tasky.taskyapp.task.domain.model.Task

/**
 * Worker class for scheduling and showing task notifications
 * Uses WorkManager to handle background processing
 */
class NotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: Any
) {

    /**
     * Process the notification for a task
     */
    fun processNotification(taskId: String, title: String, description: String = ""): Boolean {
        try {
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
            TaskyNotificationService.sendTaskDueNotification(appContext, task)
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
}