package io.tasky.taskyapp.task.presentation.details

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.core.service.TaskyNotificationService
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TaskDetailsViewModel @Inject constructor(
    private val useCases: TaskUseCases,
    @ApplicationContext private val context: Context,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(TaskDetailsState())
    val state: StateFlow<TaskDetailsState> = _state

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    var userData: UserData? = null

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object Finish : UiEvent()
    }

    private companion object {
        const val TAG = "TaskDetailsViewModel"
    }

    fun onEvent(event: TaskDetailsEvent) {
        when (event) {
            is TaskDetailsEvent.RequestInsert -> {
                insertTask(
                    title = event.title,
                    description = event.description,
                    date = event.date,
                    time = event.time,
                    status = event.status,
                    isRecurring = event.isRecurring,
                    recurrencePattern = event.recurrencePattern,
                    recurrenceInterval = event.recurrenceInterval,
                    recurrenceEndDate = event.recurrenceEndDate
                )
            }
            is TaskDetailsEvent.RequestUpdate -> {
                updateTask(
                    title = event.title,
                    description = event.description,
                    date = event.date,
                    time = event.time,
                    status = event.status,
                    isRecurring = event.isRecurring,
                    recurrencePattern = event.recurrencePattern,
                    recurrenceInterval = event.recurrenceInterval,
                    recurrenceEndDate = event.recurrenceEndDate
                )
            }
            is TaskDetailsEvent.SetTaskData -> {
                _state.value = _state.value.copy(task = event.task)
            }
        }
    }

    private fun insertTask(
        title: String,
        description: String,
        date: String,
        time: String,
        status: String,
        isRecurring: Boolean = false,
        recurrencePattern: String? = null,
        recurrenceInterval: Int = 1,
        recurrenceEndDate: String? = null
    ) {
        viewModelScope.launch {
            try {
                _state.value.task ?: return@launch

                useCases.insertTaskUseCase(
                    userData = userData ?: return@launch,
                    title = title,
                    description = description,
                    taskType = _state.value.task!!.taskType,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = TaskStatus.valueOf(status),
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null
                )

                // Create a task object to use for notification scheduling
                val createdTask = Task(
                    title = title,
                    description = description,
                    taskType = _state.value.task!!.taskType,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = status,
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null
                )

                // Schedule notification for the newly created task
                scheduleNotificationForTask(createdTask)

                _eventFlow.emit(UiEvent.Finish)
            } catch (e: Exception) {
                e.message?.let {
                    _eventFlow.emit(UiEvent.ShowToast(it))
                }
            }
        }
    }

    private fun updateTask(
        title: String,
        description: String,
        date: String,
        time: String,
        status: String,
        isRecurring: Boolean = false,
        recurrencePattern: String? = null,
        recurrenceInterval: Int = 1,
        recurrenceEndDate: String? = null
    ) {
        viewModelScope.launch {
            try {
                val task = _state.value.task ?: return@launch

                useCases.updateTaskUseCase(
                    userData = userData ?: return@launch,
                    task = task,
                    title = title,
                    description = description,
                    taskType = task.taskType,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = status,
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null
                )

                // Create an updated task for notification scheduling
                val updatedTask = task.copy(
                    title = title,
                    description = description,
                    deadlineDate = date,
                    deadlineTime = time,
                    status = status,
                    isRecurring = isRecurring,
                    recurrencePattern = if (isRecurring) recurrencePattern else null,
                    recurrenceInterval = if (isRecurring) recurrenceInterval else 1,
                    recurrenceEndDate = if (isRecurring && !recurrenceEndDate.isNullOrBlank()) recurrenceEndDate else null
                )

                // Handle notifications for the updated task
                if (updatedTask.status == TaskStatus.PENDING.name) {
                    // If task is pending, schedule a notification
                    scheduleNotificationForTask(updatedTask)
                } else {
                    // Otherwise cancel any existing notifications
                    notificationScheduler.cancelTaskReminder(updatedTask)
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
     * Schedule notifications for a task if it's pending and has a deadline
     */
    private fun scheduleNotificationForTask(task: Task) {
        if (task.status == TaskStatus.PENDING.name && !task.deadlineDate.isNullOrEmpty() && !task.deadlineTime.isNullOrEmpty()) {
            Log.d(TAG, "Scheduling notification for new/updated task: ${task.title}")

            // Schedule the notification reminder (15 min before due time)
            notificationScheduler.scheduleTaskReminder(task)
            
            // Show a confirmation toast via event flow
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.ShowToast("Reminder scheduled for \"${task.title}\""))
            }
            
            Log.d(TAG, "Notification scheduled for: ${task.deadlineDate} ${task.deadlineTime}")
        }
    }
}