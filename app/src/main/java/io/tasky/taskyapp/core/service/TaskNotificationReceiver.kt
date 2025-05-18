package io.tasky.taskyapp.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.tasky.taskyapp.task.domain.model.Task

/**
 * BroadcastReceiver to handle notifications for tasks
 */
class TaskNotificationReceiver : BroadcastReceiver() {
    private val TAG = "TaskNotificationReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "TaskNotificationReceiver onReceive called")

        if (context == null || intent == null) {
            Log.e(TAG, "Null context or intent")
            return
        }

        val taskId = intent.getStringExtra("taskId")
        val taskTitle = intent.getStringExtra("taskTitle")
        val taskDescription = intent.getStringExtra("taskDescription") ?: ""
        val taskType = intent.getStringExtra("taskType") ?: ""
        val taskDeadline = intent.getStringExtra("taskDeadline") ?: ""
        val isReminderNotification = intent.getBooleanExtra("isReminderNotification", false)
        val minutesRemaining = intent.getIntExtra("minutesRemaining", 15)

        if (taskId == null || taskTitle == null) {
            Log.e(TAG, "Task ID or title is null")
            return
        }

        Log.d(TAG, "Processing notification for task: $taskTitle (reminder: $isReminderNotification, minutes: $minutesRemaining)")

        // Create a simplified Task object for notification
        val task = Task(
            uuid = taskId,
            title = taskTitle,
            description = taskDescription,
            status = "PENDING", // Default for notification purposes
            taskType = taskType,
            deadlineDate = taskDeadline.split(" ").getOrNull(0),
            deadlineTime = taskDeadline.split(" ").getOrNull(1),
            isRecurring = false,
            recurrencePattern = null,
            recurrenceInterval = 0,
            recurrenceEndDate = null
        )

        try {
            // Ensure notification channels are created
            TaskyNotificationService.createNotificationChannels(context)

            if (isReminderNotification) {
                // Format the time message based on minutes remaining
                val timeMessage = when {
                    minutesRemaining <= 0 -> "Deadline has passed"
                    minutesRemaining == 1 -> "1 minute"
                    minutesRemaining < 60 -> "$minutesRemaining minutes"
                    else -> {
                        val hours = minutesRemaining / 60
                        val remainingMinutes = minutesRemaining % 60
                        when {
                            remainingMinutes == 0 -> "$hours hour${if (hours > 1) "s" else ""}"
                            else -> "$hours hour${if (hours > 1) "s" else ""} and $remainingMinutes minute${if (remainingMinutes > 1) "s" else ""}"
                        }
                    }
                }

                Log.d(TAG, "Sending reminder notification for task: ${task.title} with $timeMessage remaining")
                TaskyNotificationService.sendTaskDueNotification(
                    context,
                    task,
                    "Task Due Soon: ${task.title}",
                    "Only $timeMessage remaining to complete this task"
                )
            } else {
                // Send the deadline reached notification
                Log.d(TAG, "Sending deadline notification for task: ${task.title}")
                TaskyNotificationService.sendTaskDueNotification(
                    context,
                    task,
                    "Task Due Now: ${task.title}",
                    "Deadline has been reached for this task"
                )
            }

            Log.d(TAG, "Notification sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
            // Try to send an error notification
            try {
                TaskyNotificationService.sendGeneralNotification(
                    context,
                    "Notification Error",
                    "Failed to send notification for task: ${task.title}"
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to send error notification", e2)
            }
        }
    }
}