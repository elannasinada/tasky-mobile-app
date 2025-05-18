package io.tasky.taskyapp.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.tasky.taskyapp.R
import io.tasky.taskyapp.task.domain.model.Task

/**
 * A notification service for Tasky app that handles different types of notifications
 */
object TaskyNotificationService {
    const val CHANNEL_ID = "tasky_notifications"
    const val TASK_DUE_CHANNEL_ID = "tasky_due_notifications"
    private const val MAIN_ACTIVITY_CLASS_NAME = "io.tasky.taskyapp.core.presentation.MainActivity"
    private const val TAG = "TaskyNotificationService"

    /**
     * Create notification channels
     */
    fun createNotificationChannels(context: Context) {
        Log.d(TAG, "Creating notification channels")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Task due reminders channel with highest importance
            val dueChannel = NotificationChannel(
                TASK_DUE_CHANNEL_ID,
                "Task Due Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for task due dates"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)  // Add vibration pattern
                setShowBadge(true)  // Show badge on app icon
                enableLights(true)  // Enable notification light
                lightColor = android.graphics.Color.RED  // Set light color to red
            }

            // General notifications channel with high importance
            val generalChannel = NotificationChannel(
                CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "General application notifications"
                enableVibration(true)
                setShowBadge(true)
            }

            // Create the channels
            notificationManager.createNotificationChannels(listOf(dueChannel, generalChannel))

            // Log the created channels
            val channels = notificationManager.notificationChannels
            Log.d(TAG, "Created ${channels.size} notification channels")
            for (channel in channels) {
                Log.d(TAG, "Channel: ${channel.id}, Importance: ${channel.importance}")
            }
        } else {
            Log.d(TAG, "Android version < O, no channels needed")
        }
    }

    /**
     * Send a task due notification with default title and message
     */
    fun sendTaskDueNotification(context: Context, task: Task) {
        sendTaskDueNotification(
            context,
            task,
            "Task Due: ${task.title}",
            task.description ?: "It's time to complete this task"
        )
    }

    /**
     * Send a task due notification with custom title and message
     */
    fun sendTaskDueNotification(
        context: Context,
        task: Task,
        title: String,
        message: String
    ) {
        val notificationManager = NotificationManagerCompat.from(context)

        Log.d(TAG, "Sending task notification: $title")

        // Create an intent to open the app
        val intent = createMainActivityIntent(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("taskId", task.uuid)
        }

        // Create pending intent with compatibility handling
        val pendingIntent = getPendingIntent(context, task.uuid.hashCode(), intent)

        // Create notification content
        val builder = NotificationCompat.Builder(context, TASK_DUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(longArrayOf(0, 250, 250, 250))  // Add vibration explicitly

        try {
            // Use a unique ID for each notification type
            val notificationId = (title + task.uuid).hashCode()
            notificationManager.notify(notificationId, builder.build())
            Log.d(TAG, "Notification sent successfully with ID: $notificationId")
        } catch (e: SecurityException) {
            // Handle the case where notification permission is not granted
            Log.e(TAG, "Permission denied when sending notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }

    /**
     * Send a general notification
     */
    fun sendGeneralNotification(context: Context, title: String, message: String) {
        val notificationManager = NotificationManagerCompat.from(context)

        Log.d(TAG, "Sending general notification: $title")

        // Create an intent using className instead of direct class reference
        val intent = createMainActivityIntent(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Create pending intent with compatibility handling
        val pendingIntent = getPendingIntent(context, 0, intent)

        // Create notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))  // Add vibration explicitly

        try {
            notificationManager.notify(title.hashCode(), builder.build())
            Log.d(TAG, "General notification sent successfully: $title")
        } catch (e: SecurityException) {
            // Handle permission not granted
            Log.e(TAG, "Permission denied when sending general notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending general notification", e)
        }
    }

    /**
     * Create intent for MainActivity using class name to avoid experimental API warnings
     */
    private fun createMainActivityIntent(context: Context): Intent {
        return Intent().apply {
            setClassName(context.packageName, MAIN_ACTIVITY_CLASS_NAME)
        }
    }

    /**
     * Helper method to create PendingIntent with appropriate flags for different Android versions
     */
    private fun getPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 (S) and above, FLAG_IMMUTABLE is required
            Log.d(TAG, "Creating immutable PendingIntent (Android 12+)")
            PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // For older Android versions
            Log.d(TAG, "Creating PendingIntent for older Android")
            PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    /**
     * Send an immediate test notification
     * Useful for debugging notification permissions
     */
    fun sendTestNotification(context: Context) {
        Log.d(TAG, "Sending immediate test notification")

        try {
            // Create a simple Task object for the notification
            val testTask = Task(
                uuid = "test-${System.currentTimeMillis()}",
                title = "Notification Test",
                description = "This is a test notification to verify that notifications are working properly.",
                status = "PENDING",
                taskType = "",
                deadlineDate = null,
                deadlineTime = null,
                isRecurring = false,
                recurrencePattern = null,
                recurrenceInterval = 0,
                recurrenceEndDate = null
            )

            // Send both types of notifications to test both channels
            sendTaskDueNotification(
                context,
                testTask,
                "⏰ Task Due Test",
                "This is a test of the task notification system"
            )

            sendGeneralNotification(
                context,
                "Notification System Test",
                "If you see this, notifications are working correctly!"
            )

            Log.d(TAG, "Test notifications sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send test notification", e)
        }
    }
}
