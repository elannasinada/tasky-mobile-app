package io.tasky.taskyapp.core.service

import android.app.Notification
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
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION), android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build())
            }

            // General notifications channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
                enableVibration(true)
                setShowBadge(true)
                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION), android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build())
            }

            notificationManager.createNotificationChannels(listOf(dueChannel, generalChannel))
            Log.d(TAG, "Notification channels created successfully")
        }
    }

    /**
     * Send a notification for a task that is due
     */
    fun sendTaskDueNotification(
        context: Context,
        task: Task,
        title: String = "Task Due: ${task.title}",
        message: String = "This task is due soon"
    ) {
        try {
            // Ensure notification channels are created
            createNotificationChannels(context)

            val intent = Intent().apply {
                setClassName(
                    context.packageName,
                    MAIN_ACTIVITY_CLASS_NAME
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("taskId", task.uuid)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                task.uuid.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, TASK_DUE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(message)
                    .setBigContentTitle(title))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setLights(android.graphics.Color.RED, 3000, 3000)
                .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
                .build()

            with(NotificationManagerCompat.from(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        notify(task.uuid.hashCode(), notification)
                        Log.d(TAG, "Task due notification sent for: ${task.title} (Android 13+)")
                    } else {
                        Log.e(TAG, "Notification permission not granted for Android 13+")
                    }
                } else {
                    notify(task.uuid.hashCode(), notification)
                    Log.d(TAG, "Task due notification sent for: ${task.title} (Pre-Android 13)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending task due notification", e)
        }
    }

    /**
     * Send a general notification
     */
    fun sendGeneralNotification(context: Context, title: String, message: String) {
        try {
            // Ensure notification channels are created
            createNotificationChannels(context)

            val intent = Intent().apply {
                setClassName(
                    context.packageName,
                    MAIN_ACTIVITY_CLASS_NAME
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                title.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(message)
                    .setBigContentTitle(title))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
                .build()

            with(NotificationManagerCompat.from(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        notify(title.hashCode(), notification)
                        Log.d(TAG, "General notification sent: $title (Android 13+)")
                    } else {
                        Log.e(TAG, "Notification permission not granted for Android 13+")
                    }
                } else {
                    notify(title.hashCode(), notification)
                    Log.d(TAG, "General notification sent: $title (Pre-Android 13)")
                }
            }
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
}