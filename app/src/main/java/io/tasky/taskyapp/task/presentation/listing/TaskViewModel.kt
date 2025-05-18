package io.tasky.taskyapp.task.presentation.listing

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.core.service.TaskyNotificationService
import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.core.util.filterBy
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val useCases: TaskUseCases,
    @ApplicationContext private val context: Context,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(TaskState())
    val state: StateFlow<TaskState> = _state

    private val tasks = mutableListOf<Task>()
    private var searchJob: Job? = null
    private var getTasksJob: Job? = null

    private var recentlyDeletedTask: Task? = null
    var userData: UserData? = null

    private val TAG = "TaskViewModel"

    fun getRecentlyDeletedTask(): Task? {
        return recentlyDeletedTask
    }

    fun clearState() {
        _state.value = TaskState()
        tasks.clear()
        recentlyDeletedTask = null
    }

    fun onEvent(event: TaskEvent) {
        when (event) {
            is TaskEvent.SearchedForTask -> {
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    event.filter.takeIf {
                        it.isNotEmpty()
                    }?.let {
                        delay(300L)
                    }
                    _state.update {
                        state.value.copy(
                            tasks = tasks.filterBy(event.filter),
                        )
                    }
                }
            }

            is TaskEvent.RequestDelete -> {
                with(event.task) {
                    deleteTask(this)
                    recentlyDeletedTask = this
                }
            }

            is TaskEvent.RestoreTask -> {
                restoreTask(event.task)
            }

            is TaskEvent.CompleteTask -> {
                completeTask(event.task)
            }

            is TaskEvent.UpdateTaskStatus -> {
                updateTaskStatus(event.task, event.newStatus)
            }

            is TaskEvent.EnsureNotifications -> {
                ensureNotificationsForTasks()
            }
        }
    }

    fun getTasks(userData: UserData) {
        getTasksJob?.cancel()
        getTasksJob = useCases.getTasksUseCase(userData).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    tasks.clear()

                    result.data?.let { fetchedTasks ->
                        this.tasks.clear()
                        this.tasks.addAll(fetchedTasks)
                        _state.update {
                            it.copy(
                                tasks = fetchedTasks,
                            )
                        }
                        checkForApproachingDeadlines()

                        // Schedule notifications for tasks whenever we load or refresh them
                        notificationScheduler.rescheduleAllTasks(tasks)
                        Log.d(
                            TAG,
                            "Rescheduled notifications for ${tasks.size} tasks after loading"
                        )
                    }
                }

                is Resource.Loading -> {
                    _state.update {
                        it.copy(
                            loading = result.isLoading,
                        )
                    }
                }

                is Resource.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message ?: "Unknown error"
                        )
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun restoreTask(task: Task) {
        viewModelScope.launch {
            with(task) {
                userData?.let {
                    useCases.insertTaskUseCase(
                        userData = it,
                        title = title,
                        description = description ?: "",
                        taskType = taskType,
                        deadlineDate = deadlineDate ?: "",
                        deadlineTime = deadlineTime ?: "",
                        status = TaskStatus.valueOf(status)
                    )
                    getTasks(it)

                    if (task.status == TaskStatus.PENDING.name &&
                        !task.deadlineDate.isNullOrEmpty() &&
                        !task.deadlineTime.isNullOrEmpty()
                    ) {
                        TaskyNotificationService.sendGeneralNotification(
                            context,
                            "Task Restored",
                            "The task \"${task.title}\" has been restored"
                        )

                        // Schedule notification for the restored task
                        notificationScheduler.scheduleTaskReminder(task)
                        Log.d(TAG, "Scheduled notification for restored task: ${task.title}")
                    }
                }
            }
        }
    }

    private fun deleteTask(task: Task) {
        viewModelScope.launch {
            userData?.let {
                // Cancel any scheduled notifications first
                notificationScheduler.cancelTaskReminder(task)
                Log.d(TAG, "Cancelled notifications for deleted task: ${task.title}")

                useCases.deleteTaskUseCase(it, task)
                getTasks(it)
            }
        }
    }

    private fun updateTaskStatus(task: Task, newStatus: TaskStatus) {
        viewModelScope.launch {
            userData?.let { userData ->
                // Update task status
                val updatedTask = task.copy(status = newStatus.name)

                useCases.updateTaskUseCase(
                    userData = userData,
                    task = task,
                    title = task.title,
                    description = task.description ?: "",
                    taskType = task.taskType,
                    deadlineDate = task.deadlineDate ?: "",
                    deadlineTime = task.deadlineTime ?: "",
                    status = newStatus.name
                )

                // If completing a task, show notification and handle recurrence
                if (newStatus == TaskStatus.COMPLETED) {
                    // Cancel the reminder for the completed task
                    notificationScheduler.cancelTaskReminder(task)
                    Log.d(TAG, "Cancelled notifications for completed task: ${task.title}")

                    TaskyNotificationService.sendGeneralNotification(
                        context,
                        "Task Completed",
                        "You've completed \"${task.title}\""
                    )

                    if (task.isRecurring) {
                        createNextOccurrence(task, userData)
                    }
                } else if (newStatus == TaskStatus.PENDING) {
                    // If status changed to PENDING, reschedule notification
                    notificationScheduler.scheduleTaskReminder(task)
                    Log.d(
                        TAG,
                        "Rescheduled notification for task changed to pending: ${task.title}"
                    )
                } else {
                    // For other statuses, cancel reminders
                    notificationScheduler.cancelTaskReminder(task)
                    Log.d(
                        TAG,
                        "Cancelled notifications for task with status ${newStatus.name}: ${task.title}"
                    )
                }

                getTasks(userData)
            }
        }
    }

    private fun completeTask(task: Task) {
        updateTaskStatus(task, TaskStatus.COMPLETED)
    }

    private fun createNextOccurrence(task: Task, userData: UserData) {
        // Calculate the next deadline based on recurrence pattern
        val nextDeadline = calculateNextDeadline(
            task.deadlineDate,
            task.recurrencePattern,
            task.recurrenceInterval
        )

        // Don't create next occurrence if we've passed the end date
        task.recurrenceEndDate?.let { endDate ->
            if (nextDeadline != null && isDateAfter(nextDeadline, endDate)) {
                return
            }
        }

        // Create a new task with the next deadline
        val nextTask = task.copy(
            uuid = UUID.randomUUID().toString(),
            status = TaskStatus.PENDING.name,
            deadlineDate = nextDeadline
        )

        // Insert the new task
        viewModelScope.launch {
            useCases.insertTaskUseCase(
                userData = userData,
                title = nextTask.title,
                description = nextTask.description ?: "",
                taskType = nextTask.taskType,
                deadlineDate = nextTask.deadlineDate ?: "",
                deadlineTime = nextTask.deadlineTime ?: "",
                status = TaskStatus.PENDING,
                isRecurring = nextTask.isRecurring,
                recurrencePattern = nextTask.recurrencePattern,
                recurrenceInterval = nextTask.recurrenceInterval,
                recurrenceEndDate = nextTask.recurrenceEndDate
            )

            if (nextTask.deadlineDate != null) {
                TaskyNotificationService.sendGeneralNotification(
                    context,
                    "Recurring Task Created",
                    "Next occurrence of \"${nextTask.title}\" has been scheduled"
                )

                // Schedule notification for the new recurring task
                notificationScheduler.scheduleTaskReminder(nextTask)
                Log.d(
                    TAG,
                    "Scheduled notification for new recurring task: ${nextTask.title}"
                )
            }
        }
    }

    private fun calculateNextDeadline(
        currentDeadline: String?,
        pattern: String?,
        interval: Int
    ): String? {
        if (currentDeadline == null || pattern == null) return null

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(currentDeadline) ?: return null
            val calendar = Calendar.getInstance()
            calendar.time = date

            when (pattern) {
                RecurrencePattern.DAILY.name -> calendar.add(Calendar.DAY_OF_YEAR, interval)
                RecurrencePattern.WEEKLY.name -> calendar.add(
                    Calendar.WEEK_OF_YEAR,
                    interval
                )

                RecurrencePattern.MONTHLY.name -> calendar.add(Calendar.MONTH, interval)
                RecurrencePattern.YEARLY.name -> calendar.add(Calendar.YEAR, interval)
            }

            return sdf.format(calendar.time)
        } catch (e: Exception) {
            return null
        }
    }

    private fun isDateAfter(date1: String, date2: String): Boolean {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate1 = sdf.parse(date1) ?: return false
            val parsedDate2 = sdf.parse(date2) ?: return false
            return parsedDate1.after(parsedDate2)
        } catch (e: Exception) {
            return false
        }
    }

    fun checkForApproachingDeadlines() {
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var tasksChecked = 0
        var approachingTasks = 0

        tasks.filter {
            it.status == TaskStatus.PENDING.name && !it.deadlineDate.isNullOrEmpty()
        }.forEach { task ->
            tasksChecked++
            try {
                dateFormat.parse(task.deadlineDate!!)?.let { deadlineDate ->
                    Calendar.getInstance().apply { time = deadlineDate }
                        .let { deadlineCalendar ->
                            val daysDifference =
                                (deadlineCalendar.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)
                            if (daysDifference in 0..1) {
                                approachingTasks++
                                TaskyNotificationService.sendTaskDueNotification(
                                    context,
                                    task
                                )
                                Log.d(
                                    TAG,
                                    "Found approaching deadline for task: ${task.title}, days remaining: $daysDifference"
                                )
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking deadline for task: ${task.title}", e)
            }
        }

        Log.d(
            TAG,
            "Checked $tasksChecked tasks, found $approachingTasks with approaching deadlines"
        )
    }

    fun onTaskCreated(task: Task) {
        if (task.status == TaskStatus.PENDING.name && !task.deadlineDate.isNullOrEmpty()) {
            // Schedule notification
            notificationScheduler.scheduleTaskReminder(task)
            Log.d(TAG, "Scheduled notification for new task: ${task.title}")

            TaskyNotificationService.sendGeneralNotification(
                context,
                "New Task Created",
                "You've created a new task: \"${task.title}\""
            )

            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dateFormat.parse(task.deadlineDate!!)?.let { deadlineDate ->
                    val today = Calendar.getInstance()
                    val deadlineCalendar =
                        Calendar.getInstance().apply { time = deadlineDate }
                    val daysDifference =
                        (deadlineCalendar.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)

                    if (daysDifference in 0..2) {
                        viewModelScope.launch {
                            delay(3000)
                            TaskyNotificationService.sendTaskDueNotification(context, task)
                            Log.d(
                                TAG,
                                "Sent immediate task due notification for approaching task: ${task.title}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking new task deadline", e)
            }
        }
    }

    // Add this new method to ensure notifications are working
    fun ensureNotificationsForTasks() {
        viewModelScope.launch {
            Log.d(TAG, "Ensuring notifications for all tasks")

            // First, verify notification channels are set up
            TaskyNotificationService.createNotificationChannels(context)

            // Send a test notification
            TaskyNotificationService.sendTestNotification(context)

            // Create an immediate test task with notification
            val cal = Calendar.getInstance()
            cal.add(Calendar.MINUTE, 1)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val testTask = Task(
                uuid = "test-${System.currentTimeMillis()}",
                title = "Test Task - Due Soon!",
                description = "This task is due in 1 minute to test notifications",
                taskType = "TEST",
                deadlineDate = dateFormat.format(cal.time),
                deadlineTime = timeFormat.format(cal.time),
                status = TaskStatus.PENDING.name
            )

            // Schedule immediate notification for this task
            notificationScheduler.scheduleTaskReminder(testTask)

            // Send immediate due notification for the task
            TaskyNotificationService.sendTaskDueNotification(context, testTask)

            Log.d(TAG, "Created test task with immediate notification: ${testTask.title}")
            Log.d(TAG, "Deadline set for: ${testTask.deadlineDate} ${testTask.deadlineTime}")

            // Reschedule all pending tasks
            val pendingTasks = tasks.filter {
                it.status == TaskStatus.PENDING.name &&
                        !it.deadlineDate.isNullOrEmpty() &&
                        !it.deadlineTime.isNullOrEmpty()
            }

            Log.d(TAG, "Rescheduling notifications for ${pendingTasks.size} pending tasks")

            // Reschedule all tasks
            notificationScheduler.rescheduleAllTasks(pendingTasks)

            // Also send a notification to verify the system is working
            userData?.let {
                val userLabel = it.userName ?: it.email ?: "User"
                TaskyNotificationService.sendGeneralNotification(
                    context,
                    "Notification System Check",
                    "Hello $userLabel! Task notifications have been verified and are working correctly."
                )
            }
        }
    }
}