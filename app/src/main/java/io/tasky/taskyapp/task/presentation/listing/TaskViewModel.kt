package io.tasky.taskyapp.task.presentation.listing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.core.domain.PremiumManager
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.core.service.TaskyNotificationService
import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val notificationScheduler: NotificationScheduler,
    private val premiumManager: PremiumManager
) : ViewModel() {
    private val _state = MutableStateFlow(TaskState())
    val state: StateFlow<TaskState> = _state.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val tasks = mutableListOf<Task>()
    private var searchJob: Job? = null
    private var getTasksJob: Job? = null

    private var recentlyDeletedTask: Task? = null
    var userData: UserData? = null

    private val TAG = "TaskViewModel"

    init {
        getTasks(null)
    }

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
            is TaskEvent.GetTasks -> {
                getTasks(event.userData)
            }
            is TaskEvent.DeleteTask -> {
                deleteTask(event.task)
            }
            is TaskEvent.CompleteTask -> {
                completeTask(event.task)
            }
            is TaskEvent.RestoreTask -> {
                restoreTask(event.task)
            }
            is TaskEvent.EnsureNotifications -> {
                ensureNotificationsForTasks()
            }
        }
    }

    fun getTasks(userData: UserData?) {
        this.userData = userData
        getTasksJob?.cancel()
        
        // Only proceed if userData is not null
        if (userData == null) {
            _state.update {
                it.copy(
                    loading = false,
                    tasks = emptyList()
                )
            }
            return
        }
        
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
                                showPremiumDialog = false  // Don't show premium dialog when loading tasks
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
        }
    }

    fun ensureNotificationsForTasks() {
        viewModelScope.launch {
            Log.d(TAG, "Ensuring notifications are working for all tasks")

            TaskyNotificationService.createNotificationChannels(context)
            
            val pendingTasks = tasks.filter {
                it.status == TaskStatus.PENDING.name &&
                !it.deadlineDate.isNullOrEmpty() &&
                !it.deadlineTime.isNullOrEmpty()
            }

            if (pendingTasks.isEmpty()) {
                TaskyNotificationService.sendGeneralNotification(
                    context,
                    "No Tasks With Deadlines",
                    "You don't have any pending tasks with deadlines to notify you about."
                )
            } else {
                Log.d(TAG, "Rescheduling notifications for ${pendingTasks.size} pending tasks")
                notificationScheduler.rescheduleAllTasks(pendingTasks)
                
                userData?.let {
                    val userLabel = it.userName ?: it.email ?: "User"
                    TaskyNotificationService.sendGeneralNotification(
                        context,
                        "Notifications Active",
                        "Hello $userLabel! Task deadline notifications are set up for ${pendingTasks.size} tasks."
                    )
                }
            }
        }
    }

    fun onPremiumDialogDismiss() {
        _state.update { it.copy(showPremiumDialog = false) }
    }

    fun onPremiumUpgrade(activity: Activity) {
        premiumManager.launchPremiumPurchase(activity)
    }

    fun onPremiumDialogShow() {
        _state.update { it.copy(showPremiumDialog = true) }
    }

    /**
     * Adds a task with AI enhancements (priority suggestion, insights, dependencies analysis)
     */
    fun addTask(task: Task) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true) }
                
                val priority = useCases.geminiPriorityUseCase?.invoke(task) ?: 3 // Default medium priority
                val insights = useCases.geminiPriorityUseCase?.getTaskInsights(task) ?: ""
                val dependencies = useCases.geminiPriorityUseCase?.analyzeTaskDependencies(task, _state.value.tasks) ?: ""
                
                val enhancedTask = task.copy(
                    priority = priority,
                    description = task.description + (if (insights.isNotEmpty()) 
                        "\n\nAI Insights: $insights" else "") +
                        (if (dependencies.isNotEmpty()) 
                        "\n\nDependencies: $dependencies" else "")
                )
                
                useCases.insertTaskUseCase(
                    userData = userData ?: throw IllegalStateException("User data is null"),
                    title = enhancedTask.title,
                    description = enhancedTask.description ?: "",
                    taskType = enhancedTask.taskType,
                    deadlineDate = enhancedTask.deadlineDate ?: "",
                    deadlineTime = enhancedTask.deadlineTime ?: "",
                    status = TaskStatus.valueOf(enhancedTask.status),
                    isRecurring = enhancedTask.isRecurring,
                    recurrencePattern = enhancedTask.recurrencePattern,
                    recurrenceInterval = enhancedTask.recurrenceInterval,
                    recurrenceEndDate = enhancedTask.recurrenceEndDate
                )
                
                // Schedule notifications if needed
                onTaskCreated(enhancedTask)
                
                // Reload tasks
                userData?.let { getTasks(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding task with AI: ${e.message}", e)
                
                // Fallback to basic task addition
                try {
                    useCases.insertTaskUseCase(
                        userData = userData ?: throw IllegalStateException("User data is null"),
                        title = task.title,
                        description = task.description ?: "",
                        taskType = task.taskType,
                        deadlineDate = task.deadlineDate ?: "",
                        deadlineTime = task.deadlineTime ?: "",
                        status = TaskStatus.valueOf(task.status),
                        isRecurring = task.isRecurring,
                        recurrencePattern = task.recurrencePattern,
                        recurrenceInterval = task.recurrenceInterval,
                        recurrenceEndDate = task.recurrenceEndDate
                    )
                    
                    onTaskCreated(task)
                    userData?.let { getTasks(it) }
                } finally {
                    _state.update { it.copy(loading = false) }
                }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
    
    fun orderTasksByPriority() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true) }
                
                val orderedTasks = useCases.geminiPriorityUseCase?.orderTasksByPriority(_state.value.tasks) 
                    ?: _state.value.tasks.sortedWith(
                        compareByDescending<Task> { it.priority }
                            .thenBy { task ->
                                task.deadlineDate?.let {
                                    try {
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                            .parse(it)?.time ?: Long.MAX_VALUE
                                    } catch (e: Exception) {
                                        Long.MAX_VALUE
                                    }
                                } ?: Long.MAX_VALUE
                            }
                    )
                
                _state.update {
                    it.copy(
                        tasks = orderedTasks,
                        loading = false
                    )
                }
                
                TaskyNotificationService.sendGeneralNotification(
                    context,
                    "Tasks Prioritized",
                    "Your tasks have been intelligently ordered by priority and urgency"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error ordering tasks: ${e.message}", e)
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun onSearchTask(filter: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            filter.takeIf {
                it.isNotEmpty()
            }?.let {
                delay(300L)
            }
            _state.update {
                it.copy(
                    tasks = tasks.filter { task ->
                        task.title.contains(filter, ignoreCase = true) ||
                        (task.description?.contains(filter, ignoreCase = true) ?: false)
                    }
                )
            }
        }
    }
}

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    object NavigateBack : UiEvent()
}