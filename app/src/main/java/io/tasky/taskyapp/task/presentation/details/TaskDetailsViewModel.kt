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
import javax.inject.Inject
import io.tasky.taskyapp.core.domain.PremiumManager

/**
 * ViewModel for managing task details screen.
 * Handles task creation, updates, and AI priority suggestions.
 */
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
                viewModelScope.launch {
                    try {
                        val task = Task(
                            title = event.title,
                            description = event.description,
                            taskType = _state.value.task?.taskType ?: "PERSONAL",
                            deadlineDate = event.date,
                            deadlineTime = event.time,
                            status = event.status,
                            priority = event.priority,
                            isPriorityManuallySet = true
                        )
                        
                        if (!task.isPriorityManuallySet) {
                            val suggestedPriority = geminiService.suggestTaskPriority(task)
                            _state.value = _state.value.copy(suggestedPriority = suggestedPriority)
                            task.priority = suggestedPriority
                        }
                        
                        insertTask(
                            title = event.title,
                            description = event.description,
                            date = event.date,
                            time = event.time,
                            status = event.status,
                            priority = task.priority,
                            isRecurring = event.isRecurring,
                            recurrencePattern = event.recurrencePattern,
                            recurrenceInterval = event.recurrenceInterval,
                            recurrenceEndDate = event.recurrenceEndDate
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting AI suggestion: ${e.message}", e)
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
                }
            }
            is TaskDetailsEvent.RequestUpdate -> {
                Log.d(TAG, "RequestUpdate received with title: ${event.title}, date: ${event.date}, time: ${event.time}")
                viewModelScope.launch {
                    try {
                        val task = Task(
                            title = event.title,
                            description = event.description,
                            taskType = _state.value.task?.taskType ?: "PERSONAL",
                            deadlineDate = event.date,
                            deadlineTime = event.time,
                            status = event.status,
                            priority = event.priority,
                            isPriorityManuallySet = true
                        )
                        
                        if (!task.isPriorityManuallySet) {
                            val suggestedPriority = geminiService.suggestTaskPriority(task)
                            _state.value = _state.value.copy(suggestedPriority = suggestedPriority)
                            task.priority = suggestedPriority
                        }
                        
                        updateTask(
                            title = event.title,
                            description = event.description,
                            date = event.date,
                            time = event.time,
                            status = event.status,
                            priority = task.priority,
                            isRecurring = event.isRecurring,
                            recurrencePattern = event.recurrencePattern,
                            recurrenceInterval = event.recurrenceInterval,
                            recurrenceEndDate = event.recurrenceEndDate
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting AI suggestion: ${e.message}", e)
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
                }
            }
            is TaskDetailsEvent.SetTaskData -> {
                Log.d(TAG, "SetTaskData received: ${event.task}")
                if (event.task.taskType.isBlank()) {
                    _state.value = _state.value.copy(task = event.task.copy(taskType = "PERSONAL"))
                    Log.d(TAG, "Set default task type to PERSONAL")
                } else {
                    _state.value = _state.value.copy(task = event.task)
                }
                
                if (!event.task.isPriorityManuallySet) {
                    getSuggestedPriority(event.task)
                }
            }
        }
    }

    /**
     * Inserts a new task and schedules notifications if needed.
     */
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

                val userData = this@TaskDetailsViewModel.userData
                if (userData == null) {
                    Log.e(TAG, "Cannot create task: User data is null")
                    _eventFlow.emit(UiEvent.ShowToast("Cannot create task: You need to be logged in"))
                    return@launch
                }

                val task = Task(
                    title = title,
                    description = description,
                    taskType = taskType,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = status,
                    priority = priority,
                    isRecurring = isRecurring,
                    recurrencePattern = recurrencePattern,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceEndDate = recurrenceEndDate
                )

                useCases.insertTaskUseCase(
                    userData = userData,
                    title = title,
                    description = description,
                    taskType = taskType,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = TaskStatus.valueOf(status),
                    isRecurring = isRecurring,
                    recurrencePattern = recurrencePattern,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceEndDate = recurrenceEndDate
                )

                if (status == "PENDING" && date.isNotBlank() && time.isNotBlank()) {
                    try {
                        notificationScheduler.createNotificationsForTask(task)
                        Log.d(TAG, "Notification scheduled for task: $title")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule notification: ${e.message}", e)
                    }
                }

                _eventFlow.emit(UiEvent.Finish)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting task: ${e.message}", e)
                _eventFlow.emit(UiEvent.ShowToast("Error creating task: ${e.message}"))
            }
        }
    }

    /**
     * Updates an existing task and manages notifications.
     */
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

                if (status == "PENDING" && (date.isBlank() || time.isBlank())) {
                    _eventFlow.emit(UiEvent.ShowToast("Pending tasks require date and time"))
                    return@launch
                }

                notificationScheduler.cancelTaskReminder(task)

                val updatedTask = task.copy(
                    title = title,
                    description = description,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = status,
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null,
                    priority = priority,
                    isPriorityManuallySet = true
                )

                useCases.updateTaskUseCase(
                    userData = userData ?: return@launch,
                    task = updatedTask,
                    title = title,
                    description = description,
                    taskType = updatedTask.taskType,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = status,
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null
                )

                if (status == "PENDING" && date.isNotBlank() && time.isNotBlank()) {
                    try {
                        notificationScheduler.createNotificationsForTask(updatedTask)
                        Log.d(TAG, "Notification scheduled for updated task: $title")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule notification: ${e.message}", e)
                    }
                }

                _eventFlow.emit(UiEvent.Finish)
            } catch (e: Exception) {
                e.message?.let {
                    _eventFlow.emit(UiEvent.ShowToast(it))
                }
            }
        }
    }

    /**
     * Gets AI-suggested priority for a task.
     */
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
