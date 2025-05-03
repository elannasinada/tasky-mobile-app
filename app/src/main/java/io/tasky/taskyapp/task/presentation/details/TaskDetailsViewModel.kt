package io.tasky.taskyapp.task.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.lang.Exception
import javax.inject.Inject

@HiltViewModel
class TaskDetailsViewModel @Inject constructor(
    private val useCases: TaskUseCases,
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

                _eventFlow.emit(UiEvent.Finish)
            } catch (e: Exception) {
                e.message?.let {
                    _eventFlow.emit(UiEvent.ShowToast(it))
                }
            }
        }
    }
}