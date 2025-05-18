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

        if (taskId == null || taskTitle == null) {
            Log.e(TAG, "Task ID or title is null")
            return
        }

        Log.d(TAG, "Processing notification for task: $taskTitle (reminder: $isReminderNotification)")

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
            if (isReminderNotification) {
                // Send a 15-minute reminder notification
                Log.d(TAG, "Sending 15-minute reminder notification for task: ${task.title}")
                TaskyNotificationService.sendTaskDueNotification(
                    context,
                    task,
                    "Task Due Soon",
                    "\"${task.title}\" is due in 15 minutes"
                )
            } else {
                // Send the deadline reached notification
                Log.d(TAG, "Sending deadline notification for task: ${task.title}")
                TaskyNotificationService.sendTaskDueNotification(
                    context,
                    task,
                    "Task Due Now",
                    "\"${task.title}\" deadline has arrived"
                )
            }

            Log.d(TAG, "Notification sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }
}