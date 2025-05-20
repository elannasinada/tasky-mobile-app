package io.tasky.taskyapp.task.presentation.details

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.core.service.GeminiService
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import io.tasky.taskyapp.core.domain.PremiumManager

@HiltViewModel
class TaskDetailsViewModel @Inject constructor(
    private val useCases: TaskUseCases,
    @ApplicationContext private val context: Context,
    private val notificationScheduler: NotificationScheduler,
    private val premiumManager: PremiumManager,
    private val geminiService: GeminiService
) : ViewModel() {
    private val _state = MutableStateFlow(TaskDetailsState())
    val state: StateFlow<TaskDetailsState> = _state

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    var userData: UserData? = null

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object ShowPremiumDialog : UiEvent()
        object Finish : UiEvent()
    }

    private companion object {
        const val TAG = "TaskDetailsViewModel"
    }

    fun onEvent(event: TaskDetailsEvent) {
        when (event) {
            is TaskDetailsEvent.RequestInsert -> {
                Log.d(TAG, "RequestInsert received with title: ${event.title}, date: ${event.date}, time: ${event.time}")
                insertTask(
                    title = event.title,
                    description = event.description,
                    date = event.date,
                    time = event.time,
                    status = event.status,
                    priority = event.priority,
                    isRecurring = event.isRecurring,
                    recurrencePattern = event.recurrencePattern,
                    recurrenceInterval = event.recurrenceInterval,
                    recurrenceEndDate = event.recurrenceEndDate
                )
            }
            is TaskDetailsEvent.RequestUpdate -> {
                Log.d(TAG, "RequestUpdate received with title: ${event.title}, date: ${event.date}, time: ${event.time}")
                updateTask(
                    title = event.title,
                    description = event.description,
                    date = event.date,
                    time = event.time,
                    status = event.status,
                    priority = event.priority,
                    isRecurring = event.isRecurring,
                    recurrencePattern = event.recurrencePattern,
                    recurrenceInterval = event.recurrenceInterval,
                    recurrenceEndDate = event.recurrenceEndDate
                )
            }
            is TaskDetailsEvent.SetTaskData -> {
                Log.d(TAG, "SetTaskData received: ${event.task}")
                if (event.task.taskType.isBlank()) {
                    // Set a default task type if none is provided
                    _state.value = _state.value.copy(task = event.task.copy(taskType = "PERSONAL"))
                    Log.d(TAG, "Set default task type to PERSONAL")
                } else {
                    _state.value = _state.value.copy(task = event.task)
                }
                
                // Get AI priority suggestion when task is loaded
                getSuggestedPriority(event.task)
            }
        }
    }

    private fun insertTask(
        title: String,
        description: String,
        date: String,
        time: String,
        status: String,
        priority: Int = 0,
        isRecurring: Boolean = false,
        recurrencePattern: String? = null,
        recurrenceInterval: Int = 1,
        recurrenceEndDate: String? = null
    ) {
        viewModelScope.launch {
            try {
                val stateTask = _state.value.task
                val taskType = stateTask?.taskType?.takeIf { it.isNotBlank() } ?: "PERSONAL"
                Log.d(TAG, "Starting task insertion process - task type: $taskType, title: $title")

                // Check premium limit before inserting
                try {
                    val userData = this@TaskDetailsViewModel.userData
                    if (userData == null) {
                        Log.e(TAG, "Cannot create task: User data is null")
                        _eventFlow.emit(UiEvent.ShowToast("Cannot create task: You need to be logged in"))
                        return@launch
                    }

                    // Validate task fields
                    if (title.isBlank()) {
                        Log.e(TAG, "Cannot create task: Title is empty")
                        _eventFlow.emit(UiEvent.ShowToast("Task title cannot be empty"))
                        return@launch
                    }

                    // Validate date and time if status is PENDING
                    if (status == "PENDING" && (date.isBlank() || time.isBlank())) {
                        _eventFlow.emit(UiEvent.ShowToast("Pending tasks require date and time"))
                        return@launch
                    }

                    // Get current tasks and check if premium limit is reached
                    useCases.getTasksUseCase(userData).collect { resource ->
                        if (resource.data != null) {
                            val currentTaskCount = resource.data.size
                            
                            // Log the current task count and premium status for debugging
                            val isPremium = premiumManager.isPremium.value
                            Log.d(TAG, "Current task count: $currentTaskCount, Premium user: $isPremium")
                            
                            // Check if we can add more tasks - limit is MAX_FREE_TASKS (10) for non-premium users
                            if (currentTaskCount >= PremiumManager.MAX_FREE_TASKS && !isPremium) {
                                Log.d(TAG, "Premium limit reached. Tasks: $currentTaskCount, Limit: ${PremiumManager.MAX_FREE_TASKS}")
                                _eventFlow.emit(UiEvent.ShowPremiumDialog)
                                return@collect
                            } else {
                                // If premium check passes, proceed with insertion
                                Log.d(TAG, "Premium check passed. Inserting task. Current count: $currentTaskCount")
                                insertTaskAfterPremiumCheck(userData, title, description, date, time, status, priority, isRecurring, 
                                    recurrencePattern, recurrenceInterval, recurrenceEndDate)
                                return@collect
                            }
                        }
                    }
                    return@launch // Return early as the insertion will be handled in the collect block
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking premium status", e)
                    // If we can't check premium status, allow the insertion to proceed
                    insertTaskAfterPremiumCheck(userData ?: return@launch, title, description, date, time, status, priority, isRecurring, 
                        recurrencePattern, recurrenceInterval, recurrenceEndDate)
                }
            } catch (e: Exception) {
                e.message?.let {
                    _eventFlow.emit(UiEvent.ShowToast(it))
                }
            }
        }
    }

    private suspend fun insertTaskAfterPremiumCheck(
        userData: UserData,
        title: String,
        description: String,
        date: String,
        time: String,
        status: String,
        priority: Int,
        isRecurring: Boolean,
        recurrencePattern: String?,
        recurrenceInterval: Int,
        recurrenceEndDate: String?
    ) {
        try {
            val taskType = _state.value.task?.taskType?.takeIf { it.isNotBlank() } ?: "PERSONAL"
            Log.d(TAG, "Inserting task: $title with type: $taskType, date: $date, time: $time")

            useCases.insertTaskUseCase(
                userData = userData,
                title = title,
                description = description,
                taskType = taskType,
                deadlineDate = date,
                deadlineTime = time,
                status = TaskStatus.valueOf(status),
                isRecurring = isRecurring,
                recurrencePattern = if (isRecurring) recurrencePattern else null,
                recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null
            )

            val createdTask = Task(
                uuid = UUID.randomUUID().toString(),
                title = title,
                description = description,
                taskType = taskType,
                deadlineDate = date.replace("/", "-"),
                deadlineTime = time,
                status = TaskStatus.valueOf(status).name,
                isRecurring = isRecurring,
                recurrencePattern = if (isRecurring) recurrencePattern else null,
                recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate?.replace("/", "-") else null,
                priority = priority
            )

            if (status == "PENDING") {
                Log.d(TAG, "Scheduling notification for newly created task: ${title}")
                scheduleNotificationForTask(createdTask)
            }

            _eventFlow.emit(UiEvent.Finish)
        } catch (e: Exception) {
            e.message?.let {
                _eventFlow.emit(UiEvent.ShowToast(it))
            }
        }
    }

    private fun updateTask(
        title: String,
        description: String,
        date: String,
        time: String,
        status: String,
        priority: Int = 0,
        isRecurring: Boolean = false,
        recurrencePattern: String? = null,
        recurrenceInterval: Int = 1,
        recurrenceEndDate: String? = null
    ) {
        viewModelScope.launch {
            try {
                val task = _state.value.task ?: return@launch

                // Validate date and time if status is PENDING
                if (status == "PENDING" && (date.isBlank() || time.isBlank())) {
                    _eventFlow.emit(UiEvent.ShowToast("Pending tasks require date and time"))
                    return@launch
                }

                notificationScheduler.cancelTaskReminder(task)
                
                useCases.updateTaskUseCase(
                    userData = userData ?: return@launch,
                    task = task,
                    title = title,
                    description = description,
                    taskType = task.taskType,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = TaskStatus.valueOf(status).name,
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null
                )
                
                val updatedTask = task.copy(
                    title = title,
                    description = description,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = TaskStatus.valueOf(status).name,
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null,
                    priority = priority
                )

                if (updatedTask.status == "PENDING") {
                    Log.d(TAG, "Scheduling notification for updated task: ${title}")
                    scheduleNotificationForTask(updatedTask)
                }

                _eventFlow.emit(UiEvent.Finish)
            } catch (e: Exception) {
                e.message?.let {
                    _eventFlow.emit(UiEvent.ShowToast(it))
                }
            }
        }
    }

    private fun scheduleNotificationForTask(task: Task) {
        if (task.status == "PENDING" && !task.deadlineDate.isNullOrEmpty() && !task.deadlineTime.isNullOrEmpty()) {
            Log.d(TAG, "Scheduling notification for new/updated task: ${task.title}")

            try {
                // Parse deadline datetime to check if it's soon
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = dateFormat.parse(task.deadlineDate!!) ?: return
                val time = timeFormat.parse(task.deadlineTime!!) ?: return

                val timeCal = Calendar.getInstance().apply {
                    setTime(time)
                }
                
                val deadlineCal = Calendar.getInstance().apply {
                    setTime(date)
                    set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                }

                val now = Calendar.getInstance()
                val diff = deadlineCal.timeInMillis - now.timeInMillis
                
                Log.d(TAG, "Time difference to deadline: ${diff/1000} seconds (${diff/60000} minutes)")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing deadline", e)
            }
            
            // Schedule the notification reminder
            notificationScheduler.scheduleTaskReminder(task)
            
            Log.d(TAG, "Notification scheduled for: ${task.deadlineDate} ${task.deadlineTime}")
        } else {
            Log.d(TAG, "Not scheduling notification: status=${task.status}, date=${task.deadlineDate}, time=${task.deadlineTime}")
        }
    }
    
    private fun getSuggestedPriority(task: Task) {
        viewModelScope.launch {
            try {
                if (task.title.isNotBlank()) {
                    val suggestedPriority = geminiService.suggestTaskPriority(task)
                    _state.value = _state.value.copy(
                        suggestedPriority = suggestedPriority
                    )
                    Log.d(TAG, "Got AI suggested priority: $suggestedPriority for task: ${task.title}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting AI priority suggestion: ${e.message}", e)
            }
        }
    }
}