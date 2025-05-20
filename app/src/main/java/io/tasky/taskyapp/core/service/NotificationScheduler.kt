package io.tasky.taskyapp.core.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.ParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val TAG = "NotificationScheduler"

    /**
     * Schedule notifications for a task at:
     * 1. 15 minutes before deadline
     * 2. At the exact deadline time
     */
    fun createNotificationsForTask(task: Task) {
        Log.d(TAG, "Attempting to schedule reminders for task: ${task.title}")

        if (task.status != TaskStatus.PENDING.name) {
            Log.d(TAG, "Task status is not PENDING, skipping notification (status: ${task.status})")
            return
        }

        if (task.deadlineDate.isNullOrEmpty() || task.deadlineTime.isNullOrEmpty()) {
            Log.d(TAG, "Task has no deadline date or time, skipping notification")
            return
        }

        // For Android 12+ (API 31+), check if we can schedule exact alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "Cannot schedule exact alarms - permission not granted")
                // Send immediate notification about missing permission
                TaskyNotificationService.sendGeneralNotification(
                    context,
                    "Permission Required",
                    "Please grant exact alarm permission for task reminders to work properly"
                )
                return
            }
        }

        try {
            // Try both date formats
            val dateFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            )
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            
            // Try parsing the date with different formats
            var date: Date? = null
            var usedFormat: SimpleDateFormat? = null
            
            for (format in dateFormats) {
                try {
                    date = format.parse(task.deadlineDate!!)
                    if (date != null) {
                        usedFormat = format
                        Log.d(TAG, "Successfully parsed date using format: ${format.toPattern()}")
                        break
                    }
                } catch (e: ParseException) {
                    Log.d(TAG, "Failed to parse date with format ${format.toPattern()}: ${e.message}")
                }
            }
            
            if (date == null) {
                Log.e(TAG, "Failed to parse date: ${task.deadlineDate}")
                return
            }

            val time = timeFormat.parse(task.deadlineTime!!) ?: return

            val calendar = Calendar.getInstance()
            calendar.time = date

            val timeCalendar = Calendar.getInstance()
            timeCalendar.time = time

            calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)

            val now = Calendar.getInstance()

            // Calculate minutes remaining until deadline
            val minutesRemaining = (calendar.timeInMillis - now.timeInMillis) / (60 * 1000)

            // If deadline is in the past, don't schedule
            if (minutesRemaining <= 0) {
                Log.d(TAG, "Deadline is in the past, not scheduling notification")
                return
            }

            // If deadline is within 15 minutes, send immediate notification
            if (minutesRemaining <= 15) {
                val formattedMinutes = minutesRemaining.toInt()
                Log.d(TAG, "Deadline is within 15 minutes, sending immediate notification. Minutes remaining: $formattedMinutes")
                
                // Create intent for immediate broadcast instead of sending notification directly
                val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
                    putExtra("taskId", task.uuid)
                    putExtra("taskTitle", task.title)
                    putExtra("taskDescription", task.description ?: "")
                    putExtra("taskType", task.taskType)
                    putExtra("taskDeadline", "${task.deadlineDate} ${task.deadlineTime}")
                    putExtra("isReminderNotification", true)
                    putExtra("minutesRemaining", formattedMinutes)
                }
                
                // Send the intent to the receiver instead of sending notification directly
                // This ensures consistent notification handling
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                context.sendBroadcast(intent)
                Log.d(TAG, "Broadcast sent for immediate notification with $formattedMinutes minutes remaining")
            } else {
                // Schedule 15-minute reminder
                val reminderCalendar = calendar.clone() as Calendar
                reminderCalendar.add(Calendar.MINUTE, -15)
                
                if (reminderCalendar.after(now)) {
                    scheduleNotificationAtTime(
                        task,
                        reminderCalendar.timeInMillis,
                        true, // isReminderNotification
                        "reminder-${task.uuid}"
                    )
                    Log.d(TAG, "15-minute reminder scheduled for ${reminderCalendar.time}")
                }
            }
            
            // Schedule exact deadline notification
            scheduleNotificationAtTime(
                task,
                calendar.timeInMillis,
                false, // not a reminder, but the actual deadline
                "deadline-${task.uuid}"
            )
            Log.d(TAG, "Deadline notification scheduled for ${calendar.time}")

            Log.d(TAG, "Successfully scheduled notifications for task: ${task.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notifications", e)
            // Send error notification
            TaskyNotificationService.sendGeneralNotification(
                context,
                "Error Setting Reminder",
                "Failed to schedule notifications for task: ${task.title}"
            )
        }
    }

    /**
     * Helper method to schedule a notification at a specific time
     */
    private fun scheduleNotificationAtTime(
        task: Task,
        triggerTimeMillis: Long,
        isReminderNotification: Boolean,
        requestCodeString: String
    ) {
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            putExtra("taskId", task.uuid)
            putExtra("taskTitle", task.title)
            putExtra("taskDescription", task.description ?: "")
            putExtra("taskType", task.taskType)
            putExtra("taskDeadline", "${task.deadlineDate} ${task.deadlineTime}")
            putExtra("isReminderNotification", isReminderNotification)
            
            // If this is a reminder notification, it's the standard 15 minutes
            if (isReminderNotification) {
                putExtra("minutesRemaining", 15)
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeString.hashCode(),
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Successfully scheduled ${if (isReminderNotification) "reminder" else "deadline"} notification for ${task.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling ${if (isReminderNotification) "reminder" else "deadline"} notification", e)
        }
    }

    fun cancelTaskReminder(task: Task) {
        Log.d(TAG, "Cancelling reminders for task: ${task.title}")

        // Cancel 15-minute reminder
        cancelSpecificReminder(task, "reminder-${task.uuid}".hashCode())
        
        // Cancel exact deadline notification
        cancelSpecificReminder(task, "deadline-${task.uuid}".hashCode())
        
        // Also try canceling with the original task UUID for backward compatibility
        cancelSpecificReminder(task, task.uuid.hashCode())
    }

    private fun cancelSpecificReminder(task: Task, requestCode: Int) {
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            putExtra("taskId", task.uuid)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Cancelled notification with request code $requestCode for task: ${task.title}")
        }
    }

    fun rescheduleAllTasks(tasks: List<Task>) {
        Log.d(TAG, "Rescheduling all tasks: ${tasks.size} tasks")
        tasks.forEach { task ->
            if (task.status == TaskStatus.PENDING.name) {
                // Cancel existing notifications first to avoid duplicates
                cancelTaskReminder(task)
                // Schedule fresh notifications
                createNotificationsForTask(task)
            }
        }
    }
}